package de.gabriel.ankimatch;

import android.content.ContentResolver;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.OperationCanceledException;
import android.os.RemoteException;
import android.text.Html;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnkiRepository {
    private static final String FIELD_SEPARATOR = "\u001f";
    private static final Pattern CLOZE = Pattern.compile(
        "\\{\\{c\\d+::(.*?)(?:::(.*?))?\\}\\}",
        Pattern.DOTALL
    );
    private static final Pattern SOUND = Pattern.compile("\\[sound:[^\\]]+\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOUND_FILE = Pattern.compile("\\[sound:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIO_EXTENSION = Pattern.compile(
        ".+\\.(?:mp3|mp4|m4a|aac|ogg|oga|opus|wav|flac|webm|3gp|amr)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMAGE = Pattern.compile("<img\\b[^>]*?(?:alt=[\"']([^\"']*)[\"'])?[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_STYLE = Pattern.compile("<(script|style)\\b[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ContentResolver resolver;

    AnkiRepository(ContentResolver resolver) {
        this.resolver = resolver;
    }

    List<Models.DeckInfo> loadDecks() throws ApiException {
        List<Models.DeckInfo> decks = new ArrayList<>();
        String[] projection = {
            AnkiContract.Deck.ID,
            AnkiContract.Deck.NAME,
            AnkiContract.Deck.COUNTS,
            AnkiContract.Deck.DYNAMIC,
        };
        try (Cursor cursor = resolver.query(AnkiContract.Deck.ALL, projection, null, null, null)) {
            requireCursor(cursor, "AnkiDroid hat keine Stapelliste zurückgegeben.");
            int idColumn = requireColumn(cursor, AnkiContract.Deck.ID);
            int nameColumn = requireColumn(cursor, AnkiContract.Deck.NAME);
            int countsColumn = requireColumn(cursor, AnkiContract.Deck.COUNTS);
            int dynamicColumn = cursor.getColumnIndex(AnkiContract.Deck.DYNAMIC);
            while (cursor.moveToNext()) {
                int[] counts = parseCounts(cursor.getString(countsColumn));
                decks.add(new Models.DeckInfo(
                    cursor.getLong(idColumn),
                    cursor.getString(nameColumn),
                    counts[0],
                    counts[1],
                    counts[2],
                    dynamicColumn >= 0 && cursor.getInt(dynamicColumn) != 0
                ));
            }
        } catch (SecurityException error) {
            throw new ApiException("AnkiDroid hat den Datenzugriff nicht erlaubt.", error);
        } catch (IllegalStateException error) {
            throw new ApiException("Bitte schließe zuerst die Ersteinrichtung in AnkiDroid ab.", error);
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Die Stapel konnten nicht aus AnkiDroid geladen werden.", error);
        }
        decks.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
        return decks;
    }

    Models.DeckContext loadDeckContext(long deckId) throws ApiException {
        List<ScheduledRef> scheduled = queryScheduled(deckId, 64);
        if (scheduled.isEmpty()) {
            return null;
        }
        NoteData firstNote = loadNote(scheduled.get(0).noteId);
        Models.ModelInfo model = loadModel(firstNote.modelId);
        int leading = 0;
        for (ScheduledRef ref : scheduled) {
            NoteData note = loadNote(ref.noteId);
            if (note.modelId != firstNote.modelId) {
                break;
            }
            leading++;
        }
        return new Models.DeckContext(model, leading);
    }

    Models.DeckSetupData loadDeckSetup(Models.DeckInfo deck) throws ApiException {
        Models.DeckContext queueContext = loadDeckContext(deck.id);
        List<Models.ModelInfo> models = loadModelsForDeck(deck.name);
        return new Models.DeckSetupData(models, queueContext);
    }

    List<Models.MatchItem> loadSpeedrunPool(
        String deckName,
        long modelId,
        int[] fieldIndices,
        boolean audioEnabled,
        int audioFieldIndex
    ) throws ApiException {
        List<Models.MatchItem> items = new ArrayList<>();
        String[] projection = {
            AnkiContract.Note.ID,
            AnkiContract.Note.MODEL_ID,
            AnkiContract.Note.FIELDS,
        };
        String browserSearch = "deck:\"" + escapeSearchValue(deckName) + "\"";
        try (Cursor cursor = resolver.query(AnkiContract.Note.ALL, projection, browserSearch, null, null)) {
            requireCursor(cursor, "AnkiDroid hat keine Notizen für den Speedrun zurückgegeben.");
            int idColumn = requireColumn(cursor, AnkiContract.Note.ID);
            int modelColumn = requireColumn(cursor, AnkiContract.Note.MODEL_ID);
            int fieldsColumn = requireColumn(cursor, AnkiContract.Note.FIELDS);
            while (cursor.moveToNext()) {
                if (cursor.getLong(modelColumn) != modelId) {
                    continue;
                }
                List<String> rawFields = splitFields(cursor.getString(fieldsColumn));
                List<String> values = new ArrayList<>();
                boolean valid = true;
                for (int fieldIndex : fieldIndices) {
                    if (fieldIndex < 0 || fieldIndex >= rawFields.size()) {
                        valid = false;
                        break;
                    }
                    String display = toDisplayText(rawFields.get(fieldIndex));
                    values.add(display.isEmpty() ? "(leer)" : display);
                }
                if (valid) {
                    // Speedrun operates at note level; one entry therefore collapses all sibling templates.
                    List<String> audioFiles = audioEnabled && audioFieldIndex >= 0 && audioFieldIndex < rawFields.size()
                        ? audioFilesFromField(rawFields.get(audioFieldIndex))
                        : java.util.Collections.emptyList();
                    items.add(new Models.MatchItem(cursor.getLong(idColumn), 0, values, audioFiles));
                }
            }
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Der vollständige Speedrun-Pool konnte nicht geladen werden.", error);
        }
        items.sort((left, right) -> Long.compare(left.noteId, right.noteId));
        return items;
    }

    Models.RoundLoadResult loadRound(
        long deckId,
        long expectedModelId,
        int batchSize,
        int[] fieldIndices,
        boolean audioEnabled,
        int audioFieldIndex
    ) throws ApiException {
        ensureDeckSelected(deckId);
        List<ScheduledRef> scheduled = querySelectedSchedule(batchSize);
        if (scheduled.size() < batchSize) {
            return Models.RoundLoadResult.stopped(
                scheduled.size(),
                "Nur noch " + scheduled.size() + " passende Karte(n) stehen am Anfang der Warteschlange. "
                    + "Für eine vollständige Runde werden " + batchSize + " benötigt."
            );
        }

        // Normal review mode needs a durable post-write marker. AnkiDroid 2.24 exposes the
        // stored review counter through its public Card provider; older providers cannot
        // distinguish a swallowed scheduler exception from a successful update.
        readReviewCount(scheduled.get(0).noteId, scheduled.get(0).cardOrd);

        List<Models.MatchItem> items = new ArrayList<>();
        Set<Long> noteIds = new HashSet<>();
        List<Set<String>> visibleKeys = new ArrayList<>();
        for (int column = 0; column < fieldIndices.length; column++) {
            visibleKeys.add(new HashSet<>());
        }

        for (ScheduledRef ref : scheduled) {
            NoteData note = loadNote(ref.noteId);
            if (note.modelId != expectedModelId) {
                return Models.RoundLoadResult.stopped(
                    items.size(),
                    "Ein anderer Notiztyp steht vor der nächsten vollständigen Matching-Runde. "
                        + "Die übrigen Karten bleiben regulär in AnkiDroid fällig."
                );
            }
            if (!noteIds.add(note.id)) {
                return Models.RoundLoadResult.stopped(
                    batchSize,
                    "Zwei Karten derselben Notiz würden in dieselbe Runde gelangen. "
                        + "Die Runde wurde zum Schutz der Scheduler-Reihenfolge nicht gestartet."
                );
            }

            List<String> values = new ArrayList<>();
            for (int column = 0; column < fieldIndices.length; column++) {
                int fieldIndex = fieldIndices[column];
                if (fieldIndex < 0 || fieldIndex >= note.fields.size()) {
                    throw new ApiException("Die gespeicherte Feldauswahl passt nicht mehr zum Notiztyp.");
                }
                String display = toDisplayText(note.fields.get(fieldIndex));
                String key = identityKey(display);
                if (key.isEmpty()) {
                    return Models.RoundLoadResult.stopped(
                        batchSize,
                        "Mindestens eines der ausgewählten Felder ist in dieser Runde leer."
                    );
                }
                if (!visibleKeys.get(column).add(key)) {
                    return Models.RoundLoadResult.stopped(
                        batchSize,
                        "Mindestens zwei sichtbare Werte in derselben Spalte sind identisch. "
                            + "Eine eindeutige Zuordnung wäre dadurch nicht möglich."
                    );
                }
                values.add(display);
            }
            List<String> audioFiles = audioEnabled && audioFieldIndex >= 0 && audioFieldIndex < note.fields.size()
                ? audioFilesFromField(note.fields.get(audioFieldIndex))
                : java.util.Collections.emptyList();
            items.add(new Models.MatchItem(ref.noteId, ref.cardOrd, values, audioFiles));
        }
        return Models.RoundLoadResult.ready(new Models.MatchRound(items, fieldIndices.length));
    }

    Models.SubmissionResult answerRound(
        long deckId,
        Models.MatchRound round,
        Map<String, Models.SolvedMatch> solved
    ) throws ApiException {
        RoundSubmissionCoordinator coordinator = new RoundSubmissionCoordinator(
            new RoundSubmissionCoordinator.Gateway() {
                @Override
                public void ensureDeckSelected(long selectedDeckId) throws ApiException {
                    AnkiRepository.this.ensureDeckSelected(selectedDeckId);
                }

                @Override
                public RoundSubmissionCoordinator.QueueCard readHead() throws ApiException {
                    List<ScheduledRef> head = querySelectedSchedule(1);
                    if (head.isEmpty()) {
                        return null;
                    }
                    ScheduledRef card = head.get(0);
                    return new RoundSubmissionCoordinator.QueueCard(card.noteId, card.cardOrd);
                }

                @Override
                public long readReviewCount(RoundSubmissionCoordinator.QueueCard card) throws ApiException {
                    return AnkiRepository.this.readReviewCount(card.noteId, card.cardOrd);
                }

                @Override
                public void answerIfStillHead(
                    RoundSubmissionCoordinator.QueueCard expected,
                    Models.SolvedMatch result
                ) throws RoundSubmissionCoordinator.QueueConflictException, ApiException {
                    applyConditionalAnswer(expected, result);
                }
            }
        );
        return coordinator.submit(deckId, round, solved);
    }

    private long readReviewCount(long noteId, int cardOrd) throws ApiException {
        String[] projection = {AnkiContract.Card.REPS};
        try (Cursor cursor = resolver.query(
            AnkiContract.Card.byNoteAndOrd(noteId, cardOrd),
            projection,
            null,
            null,
            null
        )) {
            requireCursor(cursor, "Der persistente Kartenstand konnte nicht gelesen werden.");
            if (!cursor.moveToFirst()) {
                throw new ApiException("Die zu bewertende Karte existiert nicht mehr.");
            }
            int repsColumn = cursor.getColumnIndex(AnkiContract.Card.REPS);
            if (repsColumn < 0 || cursor.isNull(repsColumn)) {
                throw confirmationUnavailable(null);
            }
            return cursor.getLong(repsColumn);
        } catch (IllegalArgumentException error) {
            throw confirmationUnavailable(error);
        } catch (SecurityException error) {
            throw new ApiException("AnkiDroid hat die sichere Kartenprüfung nicht erlaubt.", error);
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Der persistente Kartenstand konnte nicht aus AnkiDroid gelesen werden.", error);
        }
    }

    private ApiException confirmationUnavailable(Throwable cause) {
        return new ApiException(
            "Für sicher bestätigte Scheduler-Bewertungen benötigt der Normalmodus AnkiDroid 2.24 "
                + "oder neuer. Bitte aktualisiere AnkiDroid; der Speedrun-Modus bleibt ohne Scheduler-Schreibzugriff nutzbar.",
            cause
        );
    }

    private void applyConditionalAnswer(
        RoundSubmissionCoordinator.QueueCard expected,
        Models.SolvedMatch result
    ) throws RoundSubmissionCoordinator.QueueConflictException, ApiException {
        ContentValues expectedHead = new ContentValues();
        expectedHead.put(AnkiContract.Review.NOTE_ID, expected.noteId);
        expectedHead.put(AnkiContract.Review.CARD_ORD, expected.cardOrd);

        ContentValues answer = new ContentValues();
        answer.put(AnkiContract.Review.NOTE_ID, result.item.noteId);
        answer.put(AnkiContract.Review.CARD_ORD, result.item.cardOrd);
        answer.put(AnkiContract.Review.EASE, result.ease);
        answer.put(
            AnkiContract.Review.TIME_TAKEN,
            Math.max(0L, Math.min(result.timeTakenMs, 86_400_000L))
        );

        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        operations.add(
            ContentProviderOperation.newAssertQuery(AnkiContract.Review.SCHEDULE)
                .withSelection("limit=?", new String[]{"1"})
                .withValues(expectedHead)
                .withExpectedCount(1)
                .build()
        );
        operations.add(
            ContentProviderOperation.newUpdate(AnkiContract.Review.SCHEDULE)
                .withValues(answer)
                .withExpectedCount(1)
                .build()
        );

        try {
            ContentProviderResult[] results = resolver.applyBatch(AnkiContract.AUTHORITY, operations);
            if (results == null
                || results.length != 2
                || results[1] == null
                || results[1].count == null
                || results[1].count <= 0) {
                throw new ApiException("AnkiDroid hat den bedingten Scheduler-Schreibzugriff nicht bestätigt.");
            }
        } catch (android.content.OperationApplicationException error) {
            throw new RoundSubmissionCoordinator.QueueConflictException(
                "Die Scheduler-Spitze hat sich unmittelbar vor dem Schreiben geändert.",
                error
            );
        } catch (RemoteException | OperationCanceledException error) {
            throw new ApiException("Der bedingte Scheduler-Schreibzugriff wurde unterbrochen.", error);
        } catch (SecurityException error) {
            throw new ApiException("AnkiDroid hat den bedingten Scheduler-Schreibzugriff nicht erlaubt.", error);
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ApiException("Der bedingte Scheduler-Schreibzugriff ist fehlgeschlagen.", error);
        }
    }

    private List<ScheduledRef> queryScheduled(long deckId, int limit) throws ApiException {
        return queryScheduledInternal(limit, "limit=?, deckID=?", new String[]{
            Integer.toString(limit),
            Long.toString(deckId),
        });
    }

    private List<ScheduledRef> querySelectedSchedule(int limit) throws ApiException {
        return queryScheduledInternal(limit, "limit=?", new String[]{Integer.toString(limit)});
    }

    private List<ScheduledRef> queryScheduledInternal(
        int limit,
        String selection,
        String[] args
    ) throws ApiException {
        List<ScheduledRef> cards = new ArrayList<>();
        String[] projection = {
            AnkiContract.Review.NOTE_ID,
            AnkiContract.Review.CARD_ORD,
        };
        try (Cursor cursor = resolver.query(
            AnkiContract.Review.SCHEDULE,
            projection,
            selection,
            args,
            null
        )) {
            requireCursor(cursor, "AnkiDroid hat keine Scheduler-Warteschlange zurückgegeben.");
            int noteColumn = requireColumn(cursor, AnkiContract.Review.NOTE_ID);
            int ordColumn = requireColumn(cursor, AnkiContract.Review.CARD_ORD);
            while (cursor.moveToNext()) {
                cards.add(new ScheduledRef(cursor.getLong(noteColumn), cursor.getInt(ordColumn)));
            }
        } catch (SecurityException error) {
            throw new ApiException("AnkiDroid hat den Scheduler-Zugriff nicht erlaubt.", error);
        } catch (IllegalStateException error) {
            throw new ApiException("Bitte schließe zuerst die Ersteinrichtung in AnkiDroid ab.", error);
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Die fälligen Karten konnten nicht aus AnkiDroid geladen werden.", error);
        }
        return cards;
    }

    private void ensureDeckSelected(long deckId) throws ApiException {
        String[] projection = {AnkiContract.Deck.ID};
        try (Cursor cursor = resolver.query(AnkiContract.Deck.SELECTED, projection, null, null, null)) {
            requireCursor(cursor, "Der aktuell ausgewählte AnkiDroid-Stapel konnte nicht gelesen werden.");
            if (cursor.moveToFirst()
                && cursor.getLong(requireColumn(cursor, AnkiContract.Deck.ID)) == deckId) {
                return;
            }
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Der aktuell ausgewählte AnkiDroid-Stapel konnte nicht gelesen werden.", error);
        }

        ContentValues values = new ContentValues();
        values.put(AnkiContract.Deck.ID, deckId);
        try {
            int updated = resolver.update(AnkiContract.Deck.SELECTED, values, null, null);
            if (updated <= 0) {
                throw new ApiException("AnkiDroid konnte den gewählten Stapel nicht als Lernstapel aktivieren.");
            }
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("AnkiDroid konnte den gewählten Stapel nicht als Lernstapel aktivieren.", error);
        }
    }

    private NoteData loadNote(long noteId) throws ApiException {
        String[] projection = {
            AnkiContract.Note.ID,
            AnkiContract.Note.MODEL_ID,
            AnkiContract.Note.FIELDS,
        };
        try (Cursor cursor = resolver.query(AnkiContract.Note.byId(noteId), projection, null, null, null)) {
            requireCursor(cursor, "Notiz " + noteId + " konnte nicht geladen werden.");
            if (!cursor.moveToFirst()) {
                throw new ApiException("Notiz " + noteId + " existiert nicht mehr.");
            }
            long id = cursor.getLong(requireColumn(cursor, AnkiContract.Note.ID));
            long modelId = cursor.getLong(requireColumn(cursor, AnkiContract.Note.MODEL_ID));
            String rawFields = cursor.getString(requireColumn(cursor, AnkiContract.Note.FIELDS));
            return new NoteData(id, modelId, splitFields(rawFields));
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Notiz " + noteId + " konnte nicht aus AnkiDroid gelesen werden.", error);
        }
    }

    private Models.ModelInfo loadModel(long modelId) throws ApiException {
        String[] projection = {
            AnkiContract.Model.ID,
            AnkiContract.Model.NAME,
            AnkiContract.Model.FIELD_NAMES,
        };
        try (Cursor cursor = resolver.query(AnkiContract.Model.byId(modelId), projection, null, null, null)) {
            requireCursor(cursor, "Der Notiztyp konnte nicht geladen werden.");
            if (!cursor.moveToFirst()) {
                throw new ApiException("Der Notiztyp " + modelId + " existiert nicht mehr.");
            }
            return new Models.ModelInfo(
                cursor.getLong(requireColumn(cursor, AnkiContract.Model.ID)),
                cursor.getString(requireColumn(cursor, AnkiContract.Model.NAME)),
                splitFields(cursor.getString(requireColumn(cursor, AnkiContract.Model.FIELD_NAMES)))
            );
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Der Notiztyp konnte nicht aus AnkiDroid gelesen werden.", error);
        }
    }

    private List<Models.ModelInfo> loadModelsForDeck(String deckName) throws ApiException {
        Set<Long> modelIds = new HashSet<>();
        String[] projection = {AnkiContract.Note.MODEL_ID};
        String browserSearch = "deck:\"" + escapeSearchValue(deckName) + "\"";
        try (Cursor cursor = resolver.query(AnkiContract.Note.ALL, projection, browserSearch, null, null)) {
            requireCursor(cursor, "Die Notiztypen des Stapels konnten nicht geladen werden.");
            int modelColumn = requireColumn(cursor, AnkiContract.Note.MODEL_ID);
            while (cursor.moveToNext()) {
                modelIds.add(cursor.getLong(modelColumn));
            }
        } catch (Exception error) {
            if (error instanceof ApiException) {
                throw (ApiException) error;
            }
            throw new ApiException("Die Notiztypen des Stapels konnten nicht aus AnkiDroid gelesen werden.", error);
        }
        List<Models.ModelInfo> models = new ArrayList<>();
        for (Long modelId : modelIds) {
            models.add(loadModel(modelId));
        }
        models.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
        return models;
    }

    private static String escapeSearchValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int[] parseCounts(String raw) {
        int[] result = {0, 0, 0};
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int index = 0; index < Math.min(3, array.length()); index++) {
                result[index] = Math.max(0, array.optInt(index, 0));
            }
        } catch (Exception ignored) {
            // Older provider versions may omit counts. Deck loading still remains useful.
        }
        return result;
    }

    static String toDisplayText(String raw) {
        if (raw == null) {
            return "";
        }
        String value = SCRIPT_STYLE.matcher(raw).replaceAll(" ");
        value = replaceClozeMarkup(value);
        value = SOUND.matcher(value).replaceAll(" ");

        Matcher imageMatcher = IMAGE.matcher(value);
        StringBuffer imageBuffer = new StringBuffer();
        while (imageMatcher.find()) {
            String alt = imageMatcher.group(1);
            String replacement = alt == null || alt.trim().isEmpty() ? " [Bild] " : " " + alt + " ";
            imageMatcher.appendReplacement(imageBuffer, Matcher.quoteReplacement(replacement));
        }
        imageMatcher.appendTail(imageBuffer);

        String plain = Html.fromHtml(imageBuffer.toString(), Html.FROM_HTML_MODE_COMPACT).toString();
        return plain.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    static String replaceClozeMarkup(String value) {
        Matcher clozeMatcher = CLOZE.matcher(value);
        StringBuffer clozeBuffer = new StringBuffer();
        while (clozeMatcher.find()) {
            clozeMatcher.appendReplacement(clozeBuffer, Matcher.quoteReplacement(clozeMatcher.group(1)));
        }
        clozeMatcher.appendTail(clozeBuffer);
        return clozeBuffer.toString();
    }

    static List<String> audioFilesFromField(String raw) {
        if (raw == null || raw.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        LinkedHashSet<String> files = new LinkedHashSet<>();
        Matcher matcher = SOUND_FILE.matcher(raw);
        while (matcher.find()) {
            String filename = decodeBasicEntities(matcher.group(1)).trim();
            if (filename.isEmpty()
                || filename.contains("/")
                || filename.contains("\\")
                || filename.equals(".")
                || filename.equals("..")
                || !AUDIO_EXTENSION.matcher(filename).matches()) {
                continue;
            }
            files.add(filename);
        }
        return new ArrayList<>(files);
    }

    private static String decodeBasicEntities(String value) {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    }

    private static String identityKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static List<String> splitFields(String raw) {
        List<String> fields = new ArrayList<>();
        if (raw == null) {
            return fields;
        }
        String[] parts = raw.split(FIELD_SEPARATOR, -1);
        java.util.Collections.addAll(fields, parts);
        return fields;
    }

    private static void requireCursor(Cursor cursor, String message) throws ApiException {
        if (cursor == null) {
            throw new ApiException(message);
        }
    }

    private static int requireColumn(Cursor cursor, String name) throws ApiException {
        int index = cursor.getColumnIndex(name);
        if (index < 0) {
            throw new ApiException(
                "Die installierte AnkiDroid-Version stellt das API-Feld '" + name + "' nicht bereit."
            );
        }
        return index;
    }

    static class ApiException extends Exception {
        private static final long serialVersionUID = 1L;

        ApiException(String message) {
            super(message);
        }

        ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class SubmissionException extends ApiException {
        private static final long serialVersionUID = 1L;
        final Models.SubmissionResult partial;

        SubmissionException(String message, Throwable cause, Models.SubmissionResult partial) {
            super(message, cause);
            this.partial = partial;
        }
    }

    private static final class ScheduledRef {
        final long noteId;
        final int cardOrd;

        ScheduledRef(long noteId, int cardOrd) {
            this.noteId = noteId;
            this.cardOrd = cardOrd;
        }


        String key() {
            return noteId + ":" + cardOrd;
        }
    }

    private static final class NoteData {
        final long id;
        final long modelId;
        final List<String> fields;

        NoteData(long id, long modelId, List<String> fields) {
            this.id = id;
            this.modelId = modelId;
            this.fields = fields;
        }
    }
}

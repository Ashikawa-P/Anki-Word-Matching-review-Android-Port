package de.gabriel.ankimatch;

import android.net.Uri;

/**
 * The small, stable subset of AnkiDroid's public content-provider contract used by this app.
 * Constants intentionally mirror FlashCardsContract without bundling AnkiDroid itself.
 */
final class AnkiContract {
    static final String AUTHORITY = "com.ichi2.anki.flashcards";
    static final String PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE";
    private static final Uri ROOT = Uri.parse("content://" + AUTHORITY);

    static final class Deck {
        static final Uri ALL = Uri.withAppendedPath(ROOT, "decks");
        static final Uri SELECTED = Uri.withAppendedPath(ROOT, "selected_deck");
        static final String NAME = "deck_name";
        static final String ID = "deck_id";
        static final String COUNTS = "deck_count";
        static final String DYNAMIC = "deck_dyn";
    }

    static final class Review {
        static final Uri SCHEDULE = Uri.withAppendedPath(ROOT, "schedule");
        static final String NOTE_ID = "note_id";
        static final String CARD_ORD = "ord";
        static final String EASE = "answer_ease";
        static final String TIME_TAKEN = "time_taken";
    }

    static final class Card {
        static final String REPS = "reps";

        static Uri byNoteAndOrd(long noteId, int cardOrd) {
            Uri note = Note.byId(noteId);
            Uri cards = Uri.withAppendedPath(note, "cards");
            return Uri.withAppendedPath(cards, Integer.toString(cardOrd));
        }
    }

    static final class Note {
        static final Uri ALL = Uri.withAppendedPath(ROOT, "notes");
        static final String ID = "_id";
        static final String MODEL_ID = "mid";
        static final String FIELDS = "flds";

        static Uri byId(long noteId) {
            return Uri.withAppendedPath(ALL, Long.toString(noteId));
        }
    }

    static final class Model {
        static final Uri ALL = Uri.withAppendedPath(ROOT, "models");
        static final String ID = "_id";
        static final String NAME = "name";
        static final String FIELD_NAMES = "field_names";

        static Uri byId(long modelId) {
            return Uri.withAppendedPath(ALL, Long.toString(modelId));
        }
    }

    private AnkiContract() {
    }
}

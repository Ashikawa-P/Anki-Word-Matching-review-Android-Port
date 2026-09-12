package de.gabriel.ankimatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.UriPermission;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 41;
    private static final int MEDIA_TREE_REQUEST = 42;
    private static final String MEDIA_TREE_PREFERENCE = "collection_media_tree";
    private static final int BRAND = Color.rgb(83, 103, 248);
    private static final int BRAND_DARK = Color.rgb(52, 72, 218);
    private static final int PAGE = Color.rgb(245, 247, 251);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(25, 34, 57);
    private static final int MUTED = Color.rgb(103, 113, 137);
    private static final int BORDER = Color.rgb(222, 227, 238);
    private static final int SUCCESS = Color.rgb(27, 150, 103);
    private static final int SUCCESS_BG = Color.rgb(232, 250, 241);
    private static final int ERROR = Color.rgb(207, 55, 73);
    private static final int ERROR_BG = Color.rgb(255, 236, 239);
    private static final int SELECTED_BG = Color.rgb(234, 239, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private AnkiRepository repository;
    private CollectionAudioPlayer audioPlayer;
    private SharedPreferences preferences;
    private boolean destroyed;

    private List<Models.DeckInfo> decks = Collections.emptyList();
    private Models.DeckInfo selectedDeck;
    private Models.DeckContext selectedContext;
    private Models.DeckSetupData selectedDeckSetup;

    private Spinner deckSpinner;
    private Spinner noteTypeSpinner;
    private Spinner fieldOneSpinner;
    private Spinner fieldTwoSpinner;
    private Spinner fieldThreeSpinner;
    private Spinner batchSpinner;
    private Spinner speedrunOrderSpinner;
    private Spinner audioFieldSpinner;
    private Spinner audioTriggerSpinner;
    private Switch thirdColumnSwitch;
    private Switch speedrunSwitch;
    private Switch audioSwitch;
    private TextView setupStatus;
    private TextView startButton;
    private TextView noteTypeLabel;
    private LinearLayout thirdFieldSection;
    private LinearLayout speedrunOrderSection;
    private LinearLayout audioOptionsSection;
    private TextView audioFolderStatus;
    private boolean deckListenerEnabled;

    private boolean sessionActive;
    private long sessionDeckId;
    private long sessionModelId;
    private int sessionBatchSize;
    private int[] sessionFieldIndices;
    private String[] sessionFieldNames;
    private int sessionGood;
    private int sessionAgain;
    private int sessionSubmitted;
    private boolean sessionSpeedrun;
    private boolean sessionSpeedrunRandomized;
    private boolean sessionAudioEnabled;
    private int sessionAudioFieldIndex;
    private int sessionAudioTriggerColumn;
    private Uri sessionMediaTree;
    private SpeedrunSequence speedrunSequence;
    private int speedrunCompleted;
    private int speedrunRounds;
    private long speedrunStartedElapsed;
    private int sessionGeneration;

    private Models.MatchRound currentRound;
    private final Map<String, Models.SolvedMatch> solvedThisRound = new LinkedHashMap<>();
    private MatchingEngine matchingEngine;
    private TileCell[] selectedTiles;
    private boolean boardLocked;
    private int matchedInRound;
    private long solveStartElapsed;
    private long wrongFeedbackStarted;
    private TextView gameStatus;
    private TextView scoreText;
    private LinearLayout gamePage;
    private LinearLayout gameHeaderText;
    private TextView gameHeaderTitle;
    private TextView gameHeaderDeck;
    private TextView gameCloseButton;
    private LinearLayout gameStatsCard;
    private View gameHeaderSpacer;
    private View gameStatsSpacer;
    private final List<LinearLayout> gameColumns = new ArrayList<>();
    private final List<TextView> gameColumnHeadings = new ArrayList<>();
    private final List<TileCell> gameTiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        preferences = getSharedPreferences("anki_match", MODE_PRIVATE);
        repository = new AnkiRepository(getContentResolver());
        audioPlayer = new CollectionAudioPlayer(getContentResolver());
        bootstrap();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (sessionActive && gamePage != null) {
            applyGameOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
        }
    }

    private void bootstrap() {
        showLoading("AnkiDroid wird verbunden …");
        if (getPackageManager().resolveContentProvider(AnkiContract.AUTHORITY, 0) == null) {
            showPrerequisite(
                "AnkiDroid fehlt",
                "Anki Match liest deine Decks ausschließlich über die öffentliche AnkiDroid-API. "
                    + "Installiere und öffne AnkiDroid zuerst.",
                "Erneut prüfen",
                this::bootstrap,
                null,
                null
            );
            return;
        }
        if (checkSelfPermission(AnkiContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{AnkiContract.PERMISSION}, PERMISSION_REQUEST);
            return;
        }
        showSetup();
        loadDecks();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showSetup();
            loadDecks();
        } else {
            showPrerequisite(
                "Zugriff benötigt",
                "Ohne die von AnkiDroid bereitgestellte Datenbankberechtigung kann die App weder fällige "
                    + "Karten lesen noch Antworten an den Scheduler zurückgeben.",
                "Berechtigung anfragen",
                () -> requestPermissions(new String[]{AnkiContract.PERMISSION}, PERMISSION_REQUEST),
                "App-Einstellungen",
                this::openOwnSettings
            );
        }
    }

    private void showSetup() {
        if (audioPlayer != null) {
            audioPlayer.stop();
        }
        sessionActive = false;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);

        LinearLayout root = column();
        root.setPadding(dp(20), dp(18), dp(20), dp(36));
        scroll.addView(root, scrollMatchWrap());

        root.addView(buildHero(), matchWrap());
        addSpace(root, 18);

        LinearLayout card = surfaceCard();
        root.addView(card, matchWrap());

        TextView sectionTitle = label("Lernsitzung", 19, TEXT, true);
        card.addView(sectionTitle, matchWrap());
        addSpace(card, 4);
        card.addView(label(
            "Die Karten kommen direkt aus Ankis aktueller Warteschlange.",
            14,
            MUTED,
            false
        ), matchWrap());
        addSpace(card, 20);

        card.addView(fieldLabel("Stapel"), matchWrap());
        deckSpinner = spinner();
        card.addView(spinnerSurface(deckSpinner), matchWrap());
        addSpace(card, 16);

        card.addView(fieldLabel("Notiztyp"), matchWrap());
        noteTypeSpinner = spinner();
        card.addView(spinnerSurface(noteTypeSpinner), matchWrap());
        addSpace(card, 8);
        noteTypeLabel = label("Queue-Anfang: –", 13, MUTED, false);
        card.addView(noteTypeLabel, matchWrap());
        addSpace(card, 16);

        fieldOneSpinner = spinner();
        fieldTwoSpinner = spinner();
        fieldThreeSpinner = spinner();
        card.addView(labeledSpinner("Spalte 1 · Schriftform / Vorderseite", fieldOneSpinner), matchWrap());
        addSpace(card, 12);
        card.addView(labeledSpinner("Spalte 2 · Lesung / Rückseite", fieldTwoSpinner), matchWrap());
        addSpace(card, 12);

        LinearLayout switchRow = row();
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView switchLabel = label("Dritte Matching-Spalte", 15, TEXT, true);
        switchRow.addView(switchLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        thirdColumnSwitch = new Switch(this);
        switchRow.addView(thirdColumnSwitch, wrapWrap());
        card.addView(switchRow, matchWrap());

        thirdFieldSection = column();
        addSpace(thirdFieldSection, 12);
        thirdFieldSection.addView(labeledSpinner("Spalte 3 · Bedeutung", fieldThreeSpinner), matchWrap());
        thirdFieldSection.setVisibility(View.GONE);
        card.addView(thirdFieldSection, matchWrap());
        thirdColumnSwitch.setOnCheckedChangeListener((button, checked) -> {
            thirdFieldSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            int preferredTrigger = audioTriggerSpinner == null
                ? 1
                : audioTriggerSpinner.getSelectedItemPosition() + 1;
            refreshAudioTriggerOptions(preferredTrigger);
            refreshSetupAvailability();
        });
        addSpace(card, 16);

        batchSpinner = spinner();
        ArrayAdapter<String> batchAdapter = simpleAdapter(Arrays.asList("3 Karten", "4 Karten", "5 Karten", "6 Karten", "7 Karten", "8 Karten"));
        batchSpinner.setAdapter(batchAdapter);
        batchSpinner.setSelection(Math.max(0, Math.min(5, preferences.getInt("batch_size", 5) - 3)));
        card.addView(labeledSpinner("Kartenzahl pro Runde", batchSpinner), matchWrap());
        addSpace(card, 16);

        LinearLayout audioRow = row();
        audioRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout audioText = column();
        audioText.addView(label("Audio abspielen", 15, TEXT, true), matchWrap());
        audioText.addView(label("Audiofeld und auslösende Matching-Spalte frei wählen", 12, MUTED, false), matchWrap());
        audioRow.addView(audioText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        audioSwitch = new Switch(this);
        audioRow.addView(audioSwitch, wrapWrap());
        card.addView(audioRow, matchWrap());

        audioOptionsSection = column();
        addSpace(audioOptionsSection, 10);
        audioFieldSpinner = spinner();
        audioTriggerSpinner = spinner();
        audioOptionsSection.addView(labeledSpinner("Audiofeld", audioFieldSpinner), matchWrap());
        addSpace(audioOptionsSection, 10);
        audioOptionsSection.addView(labeledSpinner("Abspielen bei Auswahl von", audioTriggerSpinner), matchWrap());
        addSpace(audioOptionsSection, 10);
        audioFolderStatus = label("Medienordner: nicht ausgewählt", 12, MUTED, false);
        audioOptionsSection.addView(audioFolderStatus, matchWrap());
        addSpace(audioOptionsSection, 8);
        TextView chooseMediaFolder = secondaryButton("AnkiDroid-Ordner „collection.media“ wählen");
        chooseMediaFolder.setGravity(Gravity.CENTER);
        chooseMediaFolder.setOnClickListener(view -> chooseCollectionMediaFolder());
        audioOptionsSection.addView(chooseMediaFolder, matchWrap());
        audioOptionsSection.addView(label(
            "Wähle hier den öffentlichen Ordner „collection.media“, nachdem AnkiDroids vollständiger Datenordner gemäß README einmalig aus Android/data verschoben wurde.",
            11,
            MUTED,
            false
        ), matchWrap());
        audioOptionsSection.setVisibility(View.GONE);
        card.addView(audioOptionsSection, matchWrap());
        refreshAudioTriggerOptions(1);
        audioSwitch.setOnCheckedChangeListener((button, checked) -> {
            audioOptionsSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            refreshAudioFolderStatus();
            refreshSetupAvailability();
        });
        addSpace(card, 16);

        LinearLayout speedrunRow = row();
        speedrunRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout speedrunText = column();
        speedrunText.addView(label("Speedrun-Modus", 15, TEXT, true), matchWrap());
        speedrunText.addView(label("Ganzer Stapel · ein Fehler beendet den Lauf · kein SRS-Fortschritt", 12, MUTED, false), matchWrap());
        speedrunRow.addView(speedrunText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        speedrunSwitch = new Switch(this);
        speedrunRow.addView(speedrunSwitch, wrapWrap());
        card.addView(speedrunRow, matchWrap());

        speedrunOrderSection = column();
        addSpace(speedrunOrderSection, 10);
        speedrunOrderSpinner = spinner();
        speedrunOrderSpinner.setAdapter(simpleAdapter(Arrays.asList("Ordered · stabile Stapelreihenfolge", "Randomized · pro Lauf neu gemischt")));
        speedrunOrderSpinner.setSelection(preferences.getBoolean("speedrun_randomized", false) ? 1 : 0);
        speedrunOrderSection.addView(labeledSpinner("Speedrun-Reihenfolge", speedrunOrderSpinner), matchWrap());
        speedrunOrderSection.setVisibility(View.GONE);
        card.addView(speedrunOrderSection, matchWrap());
        speedrunSwitch.setChecked(preferences.getBoolean("speedrun_enabled", false));
        speedrunOrderSection.setVisibility(speedrunSwitch.isChecked() ? View.VISIBLE : View.GONE);
        speedrunSwitch.setOnCheckedChangeListener((button, checked) -> {
            speedrunOrderSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            refreshSetupAvailability();
        });
        addSpace(card, 18);

        setupStatus = label("AnkiDroid wird gelesen …", 14, MUTED, false);
        setupStatus.setGravity(Gravity.CENTER);
        setupStatus.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.addView(setupStatus, matchWrap());
        addSpace(card, 10);

        startButton = actionButton("Matching starten", true);
        startButton.setEnabled(false);
        startButton.setAlpha(0.55f);
        startButton.setOnClickListener(view -> startConfiguredSession());
        card.addView(startButton, matchWrap());

        addSpace(root, 16);
        TextView footer = label(
            "Normal: Good/Again mit FSRS · Speedrun: vollständiger Stapel, keinerlei Scheduler-Schreibzugriff",
            12,
            MUTED,
            false
        );
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, matchWrap());

        setContentView(scroll);
    }

    private View buildHero() {
        LinearLayout hero = column();
        hero.setPadding(dp(22), dp(22), dp(22), dp(22));
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(64, 82, 229), Color.rgb(103, 83, 226)}
        );
        background.setCornerRadius(dp(24));
        hero.setBackground(background);
        hero.setElevation(dp(5));

        TextView eyebrow = label("MOBILE REVIEW", 12, Color.rgb(214, 220, 255), true);
        eyebrow.setLetterSpacing(0.12f);
        hero.addView(eyebrow, matchWrap());
        addSpace(hero, 6);
        hero.addView(label("Anki Match", 30, Color.WHITE, true), matchWrap());
        addSpace(hero, 6);
        hero.addView(label(
            "Ordne Schriftform, Lesung und Bedeutung zu – mit deinem echten Anki-Lernplan.",
            15,
            Color.rgb(238, 240, 255),
            false
        ), matchWrap());
        return hero;
    }

    private void loadDecks() {
        setSetupStatus("Stapel werden geladen …", MUTED);
        setStartEnabled(false);
        io.execute(() -> {
            try {
                List<Models.DeckInfo> loaded = repository.loadDecks();
                runOnUi(() -> applyDecks(loaded));
            } catch (AnkiRepository.ApiException error) {
                runOnUi(() -> {
                    setSetupStatus(error.getMessage(), ERROR);
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyDecks(List<Models.DeckInfo> loaded) {
        decks = loaded;
        deckListenerEnabled = false;
        List<String> names = new ArrayList<>();
        for (Models.DeckInfo deck : decks) {
            names.add(deck.displayName());
        }
        deckSpinner.setAdapter(simpleAdapter(names));
        if (decks.isEmpty()) {
            setSetupStatus("In AnkiDroid wurden keine Stapel gefunden.", ERROR);
            return;
        }

        long storedDeckId = preferences.getLong("last_deck_id", -1L);
        int selection = 0;
        for (int index = 0; index < decks.size(); index++) {
            if (decks.get(index).id == storedDeckId) {
                selection = index;
                break;
            }
        }
        deckSpinner.setSelection(selection);
        deckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (deckListenerEnabled && position >= 0 && position < decks.size()) {
                    loadDeckContext(decks.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        deckListenerEnabled = true;
        loadDeckContext(decks.get(selection));
    }

    private void loadDeckContext(Models.DeckInfo deck) {
        selectedDeck = deck;
        selectedContext = null;
        selectedDeckSetup = null;
        setStartEnabled(false);
        setSetupStatus("Notiztypen und fällige Karten werden geprüft …", MUTED);
        noteTypeLabel.setText(R.string.queue_loading);
        io.execute(() -> {
            try {
                Models.DeckSetupData setup = repository.loadDeckSetup(deck);
                runOnUi(() -> applyDeckSetup(deck, setup));
            } catch (AnkiRepository.ApiException error) {
                runOnUi(() -> setSetupStatus(error.getMessage(), ERROR));
            }
        });
    }

    private void applyDeckSetup(Models.DeckInfo deck, Models.DeckSetupData setup) {
        if (selectedDeck == null || selectedDeck.id != deck.id) {
            return;
        }
        selectedDeckSetup = setup;
        if (setup.models.isEmpty()) {
            noteTypeLabel.setText(R.string.queue_none);
            setSetupStatus("Dieser Stapel enthält keine passenden Notizen.", MUTED);
            setStartEnabled(false);
            return;
        }
        List<String> modelNames = new ArrayList<>();
        for (Models.ModelInfo model : setup.models) {
            modelNames.add(model.name);
        }
        noteTypeSpinner.setAdapter(simpleAdapter(modelNames));
        long storedModelId = preferences.getLong("last_model_" + deck.id, -1L);
        long preferredModelId = storedModelId >= 0
            ? storedModelId
            : setup.queueContext == null ? setup.models.get(0).id : setup.queueContext.model.id;
        int selectedIndex = 0;
        for (int index = 0; index < setup.models.size(); index++) {
            if (setup.models.get(index).id == preferredModelId) {
                selectedIndex = index;
                break;
            }
        }
        noteTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < setup.models.size()) {
                    applySelectedModel(deck, setup, setup.models.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        noteTypeSpinner.setSelection(selectedIndex);
        applySelectedModel(deck, setup, setup.models.get(selectedIndex));
    }

    private void applySelectedModel(
        Models.DeckInfo deck,
        Models.DeckSetupData setup,
        Models.ModelInfo model
    ) {
        if (selectedDeck == null || selectedDeck.id != deck.id || selectedDeckSetup != setup) {
            return;
        }
        int leading = setup.queueContext != null && setup.queueContext.model.id == model.id
            ? setup.queueContext.availableAtQueueFront
            : 0;
        selectedContext = new Models.DeckContext(model, leading);
        if (setup.queueContext == null) {
            noteTypeLabel.setText(R.string.queue_empty_speedrun);
        } else if (setup.queueContext.model.id == model.id) {
            noteTypeLabel.setText(getString(R.string.queue_leading, leading));
        } else {
            noteTypeLabel.setText(getString(R.string.queue_other_model, setup.queueContext.model.name));
        }
        ArrayAdapter<String> fields = simpleAdapter(model.fieldNames);
        fieldOneSpinner.setAdapter(fields);
        fieldTwoSpinner.setAdapter(simpleAdapter(model.fieldNames));
        fieldThreeSpinner.setAdapter(simpleAdapter(model.fieldNames));
        audioFieldSpinner.setAdapter(simpleAdapter(model.fieldNames));

        String keyPrefix = "mapping_" + deck.id + "_" + model.id + "_";
        int first = preferences.getInt(keyPrefix + "one", chooseDefault(
            model.fieldNames,
            new String[]{"word", "wort", "expression", "front", "kanji", "漢字", "単語", "表現", "書き方"},
            0
        ));
        int second = preferences.getInt(keyPrefix + "two", chooseDefault(
            model.fieldNames,
            new String[]{"reading", "pronunciation", "kana", "hiragana", "katakana", "lesung", "読み", "よみ", "発音", "かな", "仮名"},
            Math.min(1, model.fieldNames.size() - 1)
        ));
        int third = preferences.getInt(keyPrefix + "three", chooseDefault(
            model.fieldNames,
            new String[]{"translation", "übersetzung", "meaning", "bedeutung", "back", "意味", "翻訳"},
            Math.min(2, model.fieldNames.size() - 1)
        ));
        int audio = preferences.getInt(keyPrefix + "audio", chooseDefault(
            model.fieldNames,
            new String[]{"audio", "sound", "pronunciation", "reading", "lesung", "音声", "発音", "読み"},
            0
        ));
        fieldOneSpinner.setSelection(validIndex(first, model.fieldNames.size()));
        fieldTwoSpinner.setSelection(validIndex(second, model.fieldNames.size()));
        fieldThreeSpinner.setSelection(validIndex(third, model.fieldNames.size()));
        audioFieldSpinner.setSelection(validIndex(audio, model.fieldNames.size()));

        boolean canUseThree = model.fieldNames.size() >= 3;
        thirdColumnSwitch.setEnabled(canUseThree);
        boolean storedThird = canUseThree && preferences.getBoolean(keyPrefix + "third_enabled", false);
        thirdColumnSwitch.setChecked(storedThird);
        thirdFieldSection.setVisibility(storedThird ? View.VISIBLE : View.GONE);

        int storedTrigger = preferences.getInt(keyPrefix + "audio_trigger", 1);
        refreshAudioTriggerOptions(storedTrigger);
        boolean storedAudio = preferences.getBoolean(keyPrefix + "audio_enabled", false);
        audioSwitch.setChecked(storedAudio);
        audioOptionsSection.setVisibility(storedAudio ? View.VISIBLE : View.GONE);
        refreshAudioFolderStatus();

        if (model.fieldNames.size() < 2) {
            setSetupStatus("Dieser Notiztyp benötigt mindestens zwei Felder.", ERROR);
            setStartEnabled(false);
            return;
        }
        refreshSetupAvailability();
    }

    private void refreshSetupAvailability() {
        if (selectedContext == null || selectedContext.model.fieldNames.size() < 2 || speedrunSwitch == null) {
            setStartEnabled(false);
            return;
        }
        if (audioSwitch != null && audioSwitch.isChecked() && !hasCollectionMediaAccess()) {
            setSetupStatus(
                "Für Audio muss einmal der öffentlich zugängliche AnkiDroid-Ordner „collection.media“ freigegeben werden.",
                ERROR
            );
            setStartEnabled(false);
            return;
        }
        if (speedrunSwitch.isChecked()) {
            setSetupStatus("Speedrun bereit: Der gesamte Stapel wird nur gelesen; FSRS bleibt unverändert.", SUCCESS);
            setStartEnabled(true);
        } else if (selectedContext.availableAtQueueFront > 0) {
            setSetupStatus("Normalmodus bereit. AnkiDroid und FSRS übernehmen weiterhin die Planung.", SUCCESS);
            setStartEnabled(true);
        } else {
            setSetupStatus("Der gewählte Notiztyp steht nicht am Anfang der aktuellen Lernwarteschlange.", ERROR);
            setStartEnabled(false);
        }
    }

    private void refreshAudioTriggerOptions(int preferredColumn) {
        if (audioTriggerSpinner == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.add("Spalte 1");
        options.add("Spalte 2");
        if (thirdColumnSwitch != null && thirdColumnSwitch.isChecked()) {
            options.add("Spalte 3");
        }
        audioTriggerSpinner.setAdapter(simpleAdapter(options));
        int valid = preferredColumn >= 1 && preferredColumn <= options.size() ? preferredColumn : 1;
        audioTriggerSpinner.setSelection(valid - 1);
    }

    private Uri collectionMediaTree() {
        String stored = preferences.getString(MEDIA_TREE_PREFERENCE, null);
        if (stored == null || stored.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(stored);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasCollectionMediaAccess() {
        Uri stored = collectionMediaTree();
        if (stored == null) {
            return false;
        }
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission() && stored.equals(permission.getUri())) {
                return true;
            }
        }
        return false;
    }

    private void refreshAudioFolderStatus() {
        if (audioFolderStatus == null) {
            return;
        }
        if (hasCollectionMediaAccess()) {
            audioFolderStatus.setText("Medienordner: freigegeben");
            audioFolderStatus.setTextColor(SUCCESS);
        } else {
            audioFolderStatus.setText("Medienordner: nicht freigegeben");
            audioFolderStatus.setTextColor(ERROR);
        }
    }

    private void chooseCollectionMediaFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        Uri current = collectionMediaTree();
        if (current != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, current);
        }
        startActivityForResult(intent, MEDIA_TREE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MEDIA_TREE_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            preferences.edit().putString(MEDIA_TREE_PREFERENCE, tree.toString()).apply();
            refreshAudioFolderStatus();
            refreshSetupAvailability();
        } catch (SecurityException error) {
            Toast.makeText(
                this,
                "Der gewählte Ordner konnte nicht dauerhaft zum Lesen freigegeben werden.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void startConfiguredSession() {
        if (selectedDeck == null || selectedContext == null) {
            return;
        }
        int columns = thirdColumnSwitch.isChecked() ? 3 : 2;
        int[] indices = columns == 3
            ? new int[]{fieldOneSpinner.getSelectedItemPosition(), fieldTwoSpinner.getSelectedItemPosition(), fieldThreeSpinner.getSelectedItemPosition()}
            : new int[]{fieldOneSpinner.getSelectedItemPosition(), fieldTwoSpinner.getSelectedItemPosition()};
        for (int left = 0; left < indices.length; left++) {
            for (int right = left + 1; right < indices.length; right++) {
                if (indices[left] == indices[right]) {
                    setSetupStatus("Jede aktive Spalte muss ein anderes Feld verwenden.", ERROR);
                    return;
                }
            }
        }
        if (audioSwitch.isChecked() && !hasCollectionMediaAccess()) {
            setSetupStatus(
                "Bitte gib zuerst den öffentlich zugänglichen AnkiDroid-Ordner „collection.media“ für Audio frei.",
                ERROR
            );
            return;
        }

        sessionActive = true;
        sessionGeneration++;
        sessionSpeedrun = speedrunSwitch.isChecked();
        sessionSpeedrunRandomized = sessionSpeedrun && speedrunOrderSpinner.getSelectedItemPosition() == 1;
        sessionAudioEnabled = audioSwitch.isChecked();
        sessionAudioFieldIndex = sessionAudioEnabled ? audioFieldSpinner.getSelectedItemPosition() : -1;
        sessionAudioTriggerColumn = audioTriggerSpinner.getSelectedItemPosition() + 1;
        sessionMediaTree = sessionAudioEnabled ? collectionMediaTree() : null;
        sessionDeckId = selectedDeck.id;
        sessionModelId = selectedContext.model.id;
        sessionBatchSize = batchSpinner.getSelectedItemPosition() + 3;
        sessionFieldIndices = indices;
        sessionFieldNames = new String[indices.length];
        for (int index = 0; index < indices.length; index++) {
            sessionFieldNames[index] = selectedContext.model.fieldNames.get(indices[index]);
        }
        sessionGood = 0;
        sessionAgain = 0;
        sessionSubmitted = 0;
        speedrunCompleted = 0;
        speedrunRounds = 0;
        speedrunStartedElapsed = 0L;

        String keyPrefix = "mapping_" + sessionDeckId + "_" + sessionModelId + "_";
        SharedPreferences.Editor editor = preferences.edit()
            .putLong("last_deck_id", sessionDeckId)
            .putLong("last_model_" + sessionDeckId, sessionModelId)
            .putInt("batch_size", sessionBatchSize)
            .putBoolean("speedrun_enabled", sessionSpeedrun)
            .putBoolean("speedrun_randomized", sessionSpeedrunRandomized)
            .putInt(keyPrefix + "one", indices[0])
            .putInt(keyPrefix + "two", indices[1])
            .putBoolean(keyPrefix + "third_enabled", columns == 3)
            .putBoolean(keyPrefix + "audio_enabled", sessionAudioEnabled)
            .putInt(keyPrefix + "audio", Math.max(0, sessionAudioFieldIndex))
            .putInt(keyPrefix + "audio_trigger", sessionAudioTriggerColumn);
        if (columns == 3) {
            editor.putInt(keyPrefix + "three", indices[2]);
        }
        editor.apply();

        if (sessionSpeedrun) {
            loadSpeedrunPool(selectedDeck.name);
        } else {
            showLoading("Erste Matching-Runde wird vorbereitet …");
            loadNextRound();
        }
    }

    private void loadSpeedrunPool(String deckName) {
        showLoading("Der vollständige Speedrun-Pool wird gelesen …");
        int generation = sessionGeneration;
        io.execute(() -> {
            try {
                List<Models.MatchItem> pool = repository.loadSpeedrunPool(
                    deckName,
                    sessionModelId,
                    sessionFieldIndices,
                    sessionAudioEnabled,
                    sessionAudioFieldIndex
                );
                runOnUi(() -> {
                    if (!sessionActive || generation != sessionGeneration) {
                        return;
                    }
                    if (pool.isEmpty()) {
                        showSpeedrunResult(false, "Für diese Feld- und Notiztyp-Auswahl wurden keine Karten gefunden.");
                        return;
                    }
                    speedrunSequence = new SpeedrunSequence(pool, sessionSpeedrunRandomized);
                    startSpeedrunRun();
                });
            } catch (AnkiRepository.ApiException error) {
                runOnUi(() -> {
                    if (sessionActive && generation == sessionGeneration) {
                        showSpeedrunResult(false, error.getMessage());
                    }
                });
            }
        });
    }

    private void startSpeedrunRun() {
        if (speedrunSequence == null) {
            return;
        }
        speedrunSequence.reset();
        speedrunCompleted = 0;
        speedrunRounds = 0;
        speedrunStartedElapsed = SystemClock.elapsedRealtime();
        sessionActive = true;
        loadNextRound();
    }

    private void loadNextRound() {
        if (sessionSpeedrun) {
            List<Models.MatchItem> batch = speedrunSequence.nextBatch(sessionBatchSize);
            if (batch.isEmpty()) {
                showSpeedrunResult(true, "Perfekter Lauf – der vollständige Pool wurde gelöst.");
                return;
            }
            showRound(new Models.MatchRound(batch, sessionFieldIndices.length));
            return;
        }
        int generation = sessionGeneration;
        io.execute(() -> {
            try {
                Models.RoundLoadResult result = repository.loadRound(
                    sessionDeckId,
                    sessionModelId,
                    sessionBatchSize,
                    sessionFieldIndices,
                    sessionAudioEnabled,
                    sessionAudioFieldIndex
                );
                runOnUi(() -> {
                    if (!sessionActive || generation != sessionGeneration) {
                        return;
                    }
                    if (result.isReady()) {
                        showRound(result.round);
                    } else {
                        showSessionEnd("Matching beendet", result.stopReason, false);
                    }
                });
            } catch (AnkiRepository.ApiException error) {
                runOnUi(() -> {
                    if (sessionActive && generation == sessionGeneration) {
                        showSessionEnd("AnkiDroid-Fehler", error.getMessage(), true);
                    }
                });
            }
        });
    }

    private void showRound(Models.MatchRound round) {
        audioPlayer.stop();
        currentRound = round;
        solvedThisRound.clear();
        matchingEngine = new MatchingEngine();
        selectedTiles = new TileCell[round.columnCount];
        boardLocked = false;
        matchedInRound = 0;
        solveStartElapsed = SystemClock.elapsedRealtime();

        gameColumns.clear();
        gameColumnHeadings.clear();
        gameTiles.clear();

        LinearLayout page = column();
        gamePage = page;
        page.setBackgroundColor(PAGE);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headerText = column();
        gameHeaderText = headerText;
        gameHeaderTitle = label("Anki Match", 23, TEXT, true);
        headerText.addView(gameHeaderTitle, matchWrap());
        String deckName = selectedDeck == null ? "AnkiDroid" : selectedDeck.name;
        gameHeaderDeck = label(deckName, 13, MUTED, false);
        gameHeaderDeck.setMaxLines(1);
        gameHeaderDeck.setEllipsize(TextUtils.TruncateAt.END);
        headerText.addView(gameHeaderDeck, matchWrap());
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = label("Beenden", 14, BRAND_DARK, true);
        gameCloseButton = close;
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(13), dp(8), dp(13), dp(8));
        close.setBackground(ripple(Color.WHITE, 12, BORDER));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(view -> confirmLeaveSession());
        header.addView(close, wrapWrap());
        page.addView(header, matchWrap());
        gameHeaderSpacer = new View(this);
        page.addView(gameHeaderSpacer, new LinearLayout.LayoutParams(1, dp(12)));

        LinearLayout statsCard = surfaceCard();
        gameStatsCard = statsCard;
        statsCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        scoreText = label(scoreLine(), 14, TEXT, true);
        statsCard.addView(scoreText, matchWrap());
        addSpace(statsCard, 5);
        gameStatus = label(
            sessionSpeedrun
                ? "Perfekter Lauf: Wähle je einen Eintrag pro Spalte. Eine falsche Kombination beendet den Run."
                : "Wähle je einen Eintrag pro Spalte. Tippe erneut, um eine Auswahl zu lösen.",
            13,
            MUTED,
            false
        );
        statsCard.addView(gameStatus, matchWrap());
        page.addView(statsCard, matchWrap());
        gameStatsSpacer = new View(this);
        page.addView(gameStatsSpacer, new LinearLayout.LayoutParams(1, dp(12)));

        LinearLayout board = row();
        board.setGravity(Gravity.TOP);
        board.setBaselineAligned(false);

        for (int column = 0; column < round.columnCount; column++) {
            LinearLayout columnView = column();
            gameColumns.add(columnView);
            int horizontal = round.columnCount == 3 ? 4 : 6;
            columnView.setPadding(dp(horizontal), 0, dp(horizontal), 0);
            TextView heading = label(sessionFieldNames[column], 13, BRAND_DARK, true);
            gameColumnHeadings.add(heading);
            heading.setGravity(Gravity.CENTER);
            heading.setMaxLines(2);
            heading.setEllipsize(TextUtils.TruncateAt.END);
            columnView.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ));

            List<Models.MatchItem> shuffled = new ArrayList<>(round.items);
            Collections.shuffle(shuffled);
            for (Models.MatchItem item : shuffled) {
                TileCell tile = new TileCell(item, column);
                gameTiles.add(tile);
                tile.setText(item.values.get(column));
                tile.setOnClickListener(view -> onTileClicked(tile));
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                );
                tileParams.setMargins(0, dp(4), 0, dp(4));
                columnView.addView(tile, tileParams);
            }
            board.addView(columnView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        page.addView(board, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));
        setContentView(page);
        applyGameOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    private void applyGameOrientation(boolean landscape) {
        if (gamePage == null) {
            return;
        }
        gamePage.setPadding(
            dp(landscape ? 8 : 14),
            dp(landscape ? 5 : 12),
            dp(landscape ? 8 : 14),
            dp(landscape ? 5 : 10)
        );
        if (gameStatsCard != null) {
            gameStatsCard.setVisibility(landscape ? View.GONE : View.VISIBLE);
        }
        if (gameStatsSpacer != null) {
            gameStatsSpacer.getLayoutParams().height = landscape ? 0 : dp(12);
            gameStatsSpacer.setVisibility(landscape ? View.GONE : View.VISIBLE);
            gameStatsSpacer.requestLayout();
        }
        if (gameHeaderSpacer != null) {
            gameHeaderSpacer.getLayoutParams().height = dp(landscape ? 4 : 12);
            gameHeaderSpacer.requestLayout();
        }
        if (gameHeaderTitle != null) {
            gameHeaderTitle.setVisibility(landscape ? View.GONE : View.VISIBLE);
        }
        if (gameHeaderDeck != null) {
            gameHeaderDeck.setTextSize(landscape ? 12 : 13);
            gameHeaderDeck.setTypeface(Typeface.DEFAULT, landscape ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (gameHeaderText != null) {
            gameHeaderText.setGravity(Gravity.CENTER_VERTICAL);
        }
        if (gameCloseButton != null) {
            gameCloseButton.setTextSize(landscape ? 13 : 14);
            gameCloseButton.setPadding(
                dp(landscape ? 11 : 13),
                dp(landscape ? 5 : 8),
                dp(landscape ? 11 : 13),
                dp(landscape ? 5 : 8)
            );
        }

        int columns = currentRound == null ? 2 : currentRound.columnCount;
        int horizontalPadding = landscape ? 2 : (columns == 3 ? 4 : 6);
        for (LinearLayout column : gameColumns) {
            column.setPadding(dp(horizontalPadding), 0, dp(horizontalPadding), 0);
        }
        for (TextView heading : gameColumnHeadings) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) heading.getLayoutParams();
            params.height = dp(landscape ? 28 : 40);
            heading.setLayoutParams(params);
            heading.setTextSize(landscape ? 11 : 13);
        }
        int tileMargin = dp(landscape ? 2 : 4);
        for (TileCell tile : gameTiles) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.getLayoutParams();
            params.setMargins(0, tileMargin, 0, tileMargin);
            tile.setLayoutParams(params);
            tile.applyCompactMode(landscape);
        }
        gamePage.requestLayout();
    }

    private void onTileClicked(TileCell tile) {
        if (boardLocked || tile.getVisibility() != View.VISIBLE) {
            return;
        }
        TileCell current = selectedTiles[tile.column];
        if (current == tile) {
            current.showNormal();
            selectedTiles[tile.column] = null;
            return;
        }
        if (current != null) {
            current.showNormal();
        }
        selectedTiles[tile.column] = tile;
        tile.showSelected();
        playAudioForSelection(tile);

        for (TileCell selected : selectedTiles) {
            if (selected == null) {
                return;
            }
        }
        evaluateSelection();
    }

    private void playAudioForSelection(TileCell tile) {
        if (!sessionAudioEnabled
            || sessionMediaTree == null
            || tile.column + 1 != sessionAudioTriggerColumn
            || tile.item.audioFiles.isEmpty()) {
            return;
        }
        audioPlayer.play(sessionMediaTree, tile.item.audioFiles.get(0), filename -> {
            // As in the desktop add-on, missing or invalid files are ignored silently.
        });
    }

    private void evaluateSelection() {
        boardLocked = true;
        List<String> keys = new ArrayList<>();
        for (TileCell tile : selectedTiles) {
            keys.add(tile.item.key());
        }
        boolean correct = matchingEngine.isCorrect(keys);
        if (correct) {
            Models.MatchItem item = selectedTiles[0].item;
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - solveStartElapsed);
            for (TileCell tile : selectedTiles) {
                tile.showCorrect();
            }
            if (sessionSpeedrun) {
                setGameStatus("Richtig · weiter im perfekten Lauf", SUCCESS);
            } else {
                int ease = matchingEngine.ratingFor(item.key());
                solvedThisRound.put(item.key(), new Models.SolvedMatch(item, ease, elapsed));
                if (ease == MatchingEngine.GOOD) {
                    setGameStatus("Richtig beim ersten Versuch → Good vorgemerkt", SUCCESS);
                } else {
                    setGameStatus("Richtig, aber zuvor verwechselt → Again vorgemerkt", ERROR);
                }
            }
            TileCell[] solvedTiles = selectedTiles.clone();
            handler.postDelayed(() -> removeSolvedTiles(solvedTiles), sessionSpeedrun ? 150L : 260L);
        } else {
            wrongFeedbackStarted = SystemClock.elapsedRealtime();
            for (TileCell tile : selectedTiles) {
                tile.showWrong();
            }
            if (sessionSpeedrun) {
                setGameStatus("Fehler · der Speedrun ist beendet.", ERROR);
                handler.postDelayed(
                    () -> showSpeedrunResult(false, "Eine falsche Kombination hat den perfekten Lauf beendet."),
                    260L
                );
            } else {
                setGameStatus("Diese Einträge gehören nicht zusammen.", ERROR);
                TileCell[] wrongTiles = selectedTiles.clone();
                handler.postDelayed(() -> resetWrongTiles(wrongTiles), 480L);
            }
        }
    }

    private void removeSolvedTiles(TileCell[] tiles) {
        if (!sessionActive || destroyed) {
            return;
        }
        for (TileCell tile : tiles) {
            tile.setVisibility(View.INVISIBLE);
        }
        selectedTiles = new TileCell[currentRound.columnCount];
        matchedInRound++;
        if (sessionSpeedrun) {
            speedrunCompleted++;
        }
        solveStartElapsed = SystemClock.elapsedRealtime();
        if (scoreText != null) {
            scoreText.setText(scoreLine());
        }
        if (matchedInRound >= currentRound.items.size()) {
            boardLocked = true;
            if (sessionSpeedrun) {
                speedrunRounds++;
                setGameStatus("Runde vollständig · nächste Speedrun-Gruppe …", BRAND_DARK);
                handler.postDelayed(this::loadNextRound, 80L);
            } else {
                setGameStatus("Runde vollständig · Bewertungen werden in Anki-Reihenfolge übertragen …", BRAND_DARK);
                submitCurrentRound();
            }
        } else {
            boardLocked = false;
        }
    }

    private void resetWrongTiles(TileCell[] tiles) {
        if (!sessionActive || destroyed) {
            return;
        }
        long feedbackDuration = Math.max(0L, SystemClock.elapsedRealtime() - wrongFeedbackStarted);
        solveStartElapsed += feedbackDuration;
        for (TileCell tile : tiles) {
            if (tile.getVisibility() == View.VISIBLE) {
                tile.showNormal();
            }
        }
        selectedTiles = new TileCell[currentRound.columnCount];
        boardLocked = false;
        setGameStatus("Versuche es erneut.", MUTED);
    }

    private void submitCurrentRound() {
        Models.MatchRound submittedRound = currentRound;
        Map<String, Models.SolvedMatch> submittedResults = new HashMap<>(solvedThisRound);
        long submittedDeckId = sessionDeckId;
        int generation = sessionGeneration;
        io.execute(() -> {
            try {
                Models.SubmissionResult submission = repository.answerRound(
                    submittedDeckId,
                    submittedRound,
                    submittedResults
                );
                runOnUi(() -> {
                    if (!sessionActive || generation != sessionGeneration) {
                        return;
                    }
                    applySubmissionResult(submission);
                    if (submission.recoveredQueue()) {
                        showLoading(
                            submission.submitted() + " Bewertung(en) gespeichert · "
                                + submission.discarded + " wegen neuer Queue-Reihenfolge verworfen und weiter fällig · "
                                + "nächste oberste Gruppe wird geladen …"
                        );
                    } else {
                        showLoading("Runde gespeichert · nächste oberste Gruppe wird geladen …");
                    }
                    handler.postDelayed(this::loadNextRound, submission.recoveredQueue() ? 260L : 120L);
                });
            } catch (AnkiRepository.SubmissionException error) {
                runOnUi(() -> {
                    if (sessionActive && generation == sessionGeneration) {
                        applySubmissionResult(error.partial);
                        showSessionEnd(
                            "AnkiDroid-Übertragung unterbrochen",
                            error.getMessage() + " " + error.partial.submitted()
                                + " zuvor bestätigte Bewertung(en) bleiben gespeichert; die übrigen wurden nicht erneut gesendet.",
                            true
                        );
                    }
                });
            } catch (AnkiRepository.ApiException error) {
                runOnUi(() -> {
                    if (sessionActive && generation == sessionGeneration) {
                        showSessionEnd(
                            "Übertragung gestoppt",
                            error.getMessage() + " Bereits bestätigte Einzelbewertungen können in AnkiDroid gespeichert sein; "
                                + "die App versucht deshalb nicht automatisch erneut zu senden.",
                            true
                        );
                    }
                });
            }
        });
    }

    private void applySubmissionResult(Models.SubmissionResult result) {
        sessionGood += result.good;
        sessionAgain += result.again;
        sessionSubmitted += result.submitted();
    }

    private void showSpeedrunResult(boolean success, String message) {
        audioPlayer.stop();
        sessionActive = false;
        handler.removeCallbacksAndMessages(null);
        long elapsed = speedrunStartedElapsed == 0L
            ? 0L
            : Math.max(0L, SystemClock.elapsedRealtime() - speedrunStartedElapsed);
        int total = speedrunSequence == null ? 0 : speedrunSequence.total();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(root, scrollMatchWrap());

        TextView badge = label(success ? "✓" : "×", 32, success ? SUCCESS : ERROR, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(success ? SUCCESS_BG : ERROR_BG, 38, success ? SUCCESS : ERROR, 1));
        root.addView(badge, new LinearLayout.LayoutParams(dp(76), dp(76)));
        addSpace(root, 16);
        TextView title = label(success ? "Speedrun geschafft" : "Speedrun beendet", 27, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());
        addSpace(root, 8);
        TextView explanation = label(message, 15, MUTED, false);
        explanation.setGravity(Gravity.CENTER);
        root.addView(explanation, matchWrap());
        addSpace(root, 20);

        LinearLayout stats = surfaceCard();
        String order = sessionSpeedrunRandomized ? "Randomized" : "Ordered";
        TextView statsText = label(
            "Gelöst  " + speedrunCompleted + " / " + total
                + "\nRunden  " + speedrunRounds
                + "\nZeit  " + formatDuration(elapsed)
                + "\nModus  " + order + " · " + sessionFieldIndices.length + " Spalten · Zielgruppe " + sessionBatchSize,
            16,
            TEXT,
            true
        );
        statsText.setGravity(Gravity.CENTER);
        stats.addView(statsText, matchWrap());
        root.addView(stats, matchWrap());
        addSpace(root, 10);
        TextView zeroWrite = label("0 Scheduler-Antworten · 0 geänderte Fälligkeiten · 0 Review-Verlauf", 12, SUCCESS, true);
        zeroWrite.setGravity(Gravity.CENTER);
        root.addView(zeroWrite, matchWrap());
        addSpace(root, 18);

        if (speedrunSequence != null) {
            TextView retry = actionButton("Speedrun erneut starten", true);
            retry.setOnClickListener(view -> startSpeedrunRun());
            root.addView(retry, matchWrap());
            addSpace(root, 10);
        }
        TextView setup = secondaryButton("Zur Stapelauswahl");
        setup.setGravity(Gravity.CENTER);
        setup.setOnClickListener(view -> {
            sessionGeneration++;
            showSetup();
            loadDecks();
        });
        root.addView(setup, matchWrap());
        setContentView(scroll);
    }

    private void showSessionEnd(String title, String message, boolean error) {
        audioPlayer.stop();
        sessionActive = false;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(36), dp(24), dp(36));
        scroll.addView(root, scrollMatchWrap());

        TextView badge = label(error ? "!" : "✓", 30, error ? ERROR : SUCCESS, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(error ? ERROR_BG : SUCCESS_BG, 36, error ? ERROR : SUCCESS, 1));
        root.addView(badge, new LinearLayout.LayoutParams(dp(72), dp(72)));
        addSpace(root, 18);
        TextView titleView = label(title, 27, TEXT, true);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, matchWrap());
        addSpace(root, 10);
        TextView messageView = label(message == null ? "" : message, 15, MUTED, false);
        messageView.setGravity(Gravity.CENTER);
        root.addView(messageView, matchWrap());
        addSpace(root, 22);

        LinearLayout stats = surfaceCard();
        TextView statsText = label(
            "An Scheduler übertragen\n" + sessionSubmitted + " Karten\n\nGood  " + sessionGood + "     Again  " + sessionAgain,
            16,
            TEXT,
            true
        );
        statsText.setGravity(Gravity.CENTER);
        stats.addView(statsText, matchWrap());
        root.addView(stats, matchWrap());
        addSpace(root, 18);

        TextView setup = actionButton("Zur Stapelauswahl", true);
        setup.setOnClickListener(view -> {
            showSetup();
            loadDecks();
        });
        root.addView(setup, matchWrap());
        addSpace(root, 10);
        TextView anki = secondaryButton("AnkiDroid öffnen");
        anki.setGravity(Gravity.CENTER);
        anki.setOnClickListener(view -> openAnkiDroid());
        root.addView(anki, matchWrap());
        setContentView(scroll);
    }

    private void confirmLeaveSession() {
        if (!sessionActive) {
            showSetup();
            loadDecks();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(sessionSpeedrun ? "Speedrun beenden?" : "Matching beenden?")
            .setMessage(
                sessionSpeedrun
                    ? "Der aktuelle Lauf wird beendet. Der Anki-Lernfortschritt bleibt vollständig unverändert."
                    : "Nur vollständig übertragene Runden zählen. Die aktuell offene Runde bleibt beim Beenden unverändert in AnkiDroid fällig."
            )
            .setNegativeButton("Weiterlernen", null)
            .setPositiveButton("Beenden", (dialog, which) -> {
                audioPlayer.stop();
                sessionActive = false;
                sessionGeneration++;
                handler.removeCallbacksAndMessages(null);
                showSetup();
                loadDecks();
            })
            .show();
    }

    @Override
    public void onBackPressed() {
        if (sessionActive) {
            confirmLeaveSession();
        } else {
            super.onBackPressed();
        }
    }

    private void showLoading(String message) {
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(PAGE);
        ProgressBar progress = new ProgressBar(this);
        progress.getIndeterminateDrawable().setTint(BRAND);
        root.addView(progress, new LinearLayout.LayoutParams(dp(52), dp(52)));
        addSpace(root, 18);
        TextView text = label(message, 15, MUTED, true);
        text.setGravity(Gravity.CENTER);
        root.addView(text, matchWrap());
        setContentView(root);
    }

    private void showPrerequisite(
        String title,
        String message,
        String primaryText,
        Runnable primaryAction,
        String secondaryText,
        Runnable secondaryAction
    ) {
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(26), dp(36), dp(26), dp(36));
        root.setBackgroundColor(PAGE);
        TextView icon = label("↔", 34, BRAND, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(SELECTED_BG, 36, BRAND, 1));
        root.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        addSpace(root, 18);
        TextView heading = label(title, 26, TEXT, true);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading, matchWrap());
        addSpace(root, 10);
        TextView body = label(message, 15, MUTED, false);
        body.setGravity(Gravity.CENTER);
        root.addView(body, matchWrap());
        addSpace(root, 24);
        TextView primary = actionButton(primaryText, true);
        primary.setOnClickListener(view -> primaryAction.run());
        root.addView(primary, matchWrap());
        if (secondaryText != null && secondaryAction != null) {
            addSpace(root, 10);
            TextView secondary = secondaryButton(secondaryText);
            secondary.setGravity(Gravity.CENTER);
            secondary.setOnClickListener(view -> secondaryAction.run());
            root.addView(secondary, matchWrap());
        }
        setContentView(root);
    }

    private void openOwnSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openAnkiDroid() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.ichi2.anki");
        if (intent == null) {
            Toast.makeText(this, "AnkiDroid konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(intent);
    }

    private int chooseDefault(List<String> names, String[] preferred, int fallback) {
        List<String> lowered = new ArrayList<>();
        for (String name : names) {
            lowered.add(name.toLowerCase(Locale.ROOT));
        }
        for (String candidate : preferred) {
            int exact = lowered.indexOf(candidate.toLowerCase(Locale.ROOT));
            if (exact >= 0) {
                return exact;
            }
        }
        for (String candidate : preferred) {
            String needle = candidate.toLowerCase(Locale.ROOT);
            for (int index = 0; index < lowered.size(); index++) {
                if (lowered.get(index).contains(needle)) {
                    return index;
                }
            }
        }
        return validIndex(fallback, names.size());
    }

    private int validIndex(int candidate, int size) {
        return Math.max(0, Math.min(Math.max(0, size - 1), candidate));
    }

    private String scoreLine() {
        if (sessionSpeedrun) {
            String mode = sessionSpeedrunRandomized ? "Randomized" : "Ordered";
            return "Speedrun: " + speedrunCompleted + "/" + (speedrunSequence == null ? 0 : speedrunSequence.total())
                + "  ·  Runde " + (speedrunRounds + 1) + "  ·  " + mode
                + "  ·  " + formatDuration(SystemClock.elapsedRealtime() - speedrunStartedElapsed);
        }
        int pendingGood = 0;
        int pendingAgain = 0;
        for (Models.SolvedMatch result : solvedThisRound.values()) {
            if (result.ease == MatchingEngine.GOOD) {
                pendingGood++;
            } else {
                pendingAgain++;
            }
        }
        return "Scheduler: Good " + sessionGood + "  ·  Again " + sessionAgain
            + "  ·  Runde " + (matchedInRound + 1) + "/" + sessionBatchSize
            + (pendingGood + pendingAgain > 0 ? "  ·  vorgemerkt " + (pendingGood + pendingAgain) : "");
    }

    private String formatDuration(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long rest = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    private void setSetupStatus(String message, int color) {
        if (setupStatus != null) {
            setupStatus.setText(message);
            setupStatus.setTextColor(color);
        }
    }

    private void setGameStatus(String message, int color) {
        if (gameStatus != null) {
            gameStatus.setText(message);
            gameStatus.setTextColor(color);
        }
    }

    private void setStartEnabled(boolean enabled) {
        if (startButton != null) {
            startButton.setEnabled(enabled);
            startButton.setAlpha(enabled ? 1f : 0.55f);
        }
    }

    private void runOnUi(Runnable action) {
        if (!destroyed) {
            handler.post(() -> {
                if (!destroyed) {
                    action.run();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (audioPlayer != null) {
            audioPlayer.stop();
        }
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout surfaceCard() {
        LinearLayout layout = column();
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackground(rounded(SURFACE, 20, BORDER, 1));
        layout.setElevation(dp(2));
        return layout;
    }

    private LinearLayout labeledSpinner(String text, Spinner spinner) {
        LinearLayout layout = column();
        layout.addView(fieldLabel(text), matchWrap());
        addSpace(layout, 6);
        layout.addView(spinnerSurface(spinner), matchWrap());
        return layout;
    }

    private TextView fieldLabel(String text) {
        return label(text, 13, MUTED, true);
    }

    private FrameLayout spinnerSurface(Spinner spinner) {
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(6), dp(2), dp(6), dp(2));
        frame.setBackground(rounded(Color.rgb(249, 250, 253), 12, BORDER, 1));
        frame.addView(spinner, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ));
        return frame;
    }

    private Spinner spinner() {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        spinner.setPopupBackgroundDrawable(rounded(Color.WHITE, 10, BORDER, 1));
        return spinner;
    }

    private ArrayAdapter<String> simpleAdapter(List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(15);
                view.setPadding(dp(10), 0, dp(8), 0);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(15);
                view.setPadding(dp(16), dp(12), dp(16), dp(12));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private TextView actionButton(String text, boolean filled) {
        TextView button = label(text, 16, filled ? Color.WHITE : BRAND_DARK, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        int background = filled ? BRAND : Color.WHITE;
        int stroke = filled ? BRAND_DARK : BORDER;
        button.setBackground(ripple(background, 14, stroke));
        button.setMinHeight(dp(54));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView secondaryButton(String text) {
        return actionButton(text, false);
    }

    private TextView label(String text, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.15f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private void addSpace(LinearLayout parent, int dp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private ScrollView.LayoutParams scrollMatchWrap() {
        return new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private RippleDrawable ripple(int color, int radiusDp, int strokeColor) {
        return new RippleDrawable(
            ColorStateList.valueOf(Color.argb(35, 40, 52, 100)),
            rounded(color, radiusDp, strokeColor, 1),
            rounded(Color.WHITE, radiusDp, Color.TRANSPARENT, 0)
        );
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class TileCell extends TextView {
        final Models.MatchItem item;
        final int column;

        TileCell(Models.MatchItem item, int column) {
            super(MainActivity.this);
            this.item = item;
            this.column = column;
            setGravity(Gravity.CENTER);
            setTextColor(TEXT);
            setTextSize(16);
            setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            setSingleLine(false);
            setMaxLines(Integer.MAX_VALUE);
            setMovementMethod(ScrollingMovementMethod.getInstance());
            setVerticalScrollBarEnabled(true);
            setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            setElevation(dp(1));
            setClickable(true);
            setFocusable(true);
            applyCompactMode(false);
            showNormal();
        }

        void applyCompactMode(boolean compact) {
            setPadding(
                dp(compact ? 5 : 8),
                dp(compact ? 2 : 7),
                dp(compact ? 5 : 8),
                dp(compact ? 2 : 7)
            );
            setAutoSizeTextTypeUniformWithConfiguration(
                compact ? 8 : 10,
                compact ? 16 : 18,
                1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            );
        }

        void showNormal() {
            setTextColor(TEXT);
            setBackground(ripple(Color.WHITE, 15, BORDER));
        }

        void showSelected() {
            setTextColor(BRAND_DARK);
            setBackground(rounded(SELECTED_BG, 15, BRAND, 2));
        }

        void showCorrect() {
            setTextColor(SUCCESS);
            setBackground(rounded(SUCCESS_BG, 15, SUCCESS, 2));
        }

        void showWrong() {
            setTextColor(ERROR);
            setBackground(rounded(ERROR_BG, 15, ERROR, 2));
        }
    }
}

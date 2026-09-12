# Anki Match – Android

An Android port of [Word Matching Review](https://github.com/Ashikawa-P/Anki-Word-Matching-review), bringing its matching-based review mode to AnkiDroid.

Anki Match is a separate Android application inspired by and based on the behavior of the original desktop add-on. It recreates its core two- and three-column matching workflow, normal Anki scheduling, Speedrun Mode and configurable audio while adapting them to AnkiDroid and Android.

It is not a 1:1 copy of the desktop add-on. The Android version uses AnkiDroid's public API and has its own interface, storage handling and platform-specific behavior.

[Original desktop add-on](https://github.com/Ashikawa-P/Anki-Word-Matching-review) · [AnkiWeb version](https://ankiweb.net/shared/info/635808794)

## Setup

Anki Match reads decks, note types, fields and scheduling information from AnkiDroid.

Choose a deck and note type, then configure the fields that should be used for matching.

You can configure:

* **Column 1**
* **Column 2**
* optional **Column 3**
* **3 to 8 cards per round**
* an optional **audio field**
* the column whose selection should trigger audio playback
* optional **Speedrun Mode**
* Speedrun order: **Ordered** or **Randomized**

Field mappings and audio settings are remembered for the selected deck and note type.

The app attempts to choose useful default fields automatically from names such as `Word`, `Front`, `Kanji`, `Reading`, `Kana`, `Meaning`, `Translation` or `Audio`, but every field can be selected manually.

## Two-column matching

The standard layout displays two independently shuffled columns.

Select one entry from each column to reconnect the values belonging to the same Anki note.

For example:

**Japanese word → Meaning**

**Question → Answer**

**Kanji → Reading**

When the selected entries belong together, they disappear from the board and the next pair can be matched immediately.

An incorrect combination briefly receives visual feedback without revealing the correct answer.

## Three-column matching

An optional third field can be enabled for relationships that contain more than two pieces of information.

All three columns are shuffled independently. A match is accepted only when the selected entries from all three columns belong to the same note.

For Japanese vocabulary this allows setups such as:

**Kanji / written form → Kana reading → Meaning**

Other examples include:

**Country → Capital → Currency**

**Chemical element → Symbol → Atomic number**

**Term → Definition → Example**

Disabling the third column returns the app to normal two-column matching.

## Normal review mode

Normal review mode works with AnkiDroid's current scheduler instead of implementing a separate scheduling system.

The cards are taken from the beginning of the currently active AnkiDroid review queue.

A card solved correctly on the first attempt is submitted as:

**Good**

A card that was involved in an incorrect matching attempt before being solved is submitted as:

**Again**

AnkiDroid and its scheduler remain responsible for due dates, intervals and FSRS behavior.

Ratings are not blindly written to the collection. Anki Match verifies the current queue position before submitting an answer and checks the card's persistent review counter afterwards to confirm that the scheduler update was actually stored.

If AnkiDroid changes the queue between solving a round and submitting its results, Anki Match refreshes the queue state instead of forcing an answer onto the wrong card.

Already confirmed answers remain saved. Cards that could not safely be submitted remain available in AnkiDroid.

Normal scheduler review requires **AnkiDroid 2.24 or newer**.

## Speedrun Mode

Speedrun Mode is completely separate from normal Anki scheduling.

Instead of using only the cards currently due, it loads the selected deck and note type as an in-memory matching challenge.

During a Speedrun:

* no scheduler or FSRS progress is written
* due dates and intervals are not changed
* review history is not modified
* one incorrect combination immediately ends the run
* correct matches continue until the complete pool has been cleared
* the result screen shows completed matches, rounds and total run time
* a failed or completed run can be started again immediately

Speedrun Mode works with both two-column and three-column matching.

### Ordered

**Ordered** uses a stable deck-wide item order.

The same notes therefore tend to appear in the same groups between runs, while their positions inside each matching column are still shuffled independently.

### Randomized

**Randomized** shuffles the complete Speedrun pool again at the beginning of every run.

The notes appearing together in one round will therefore usually differ between runs.

The entries inside the individual matching columns are shuffled independently as well.

## Configurable audio

Audio is independent from the fields visible on the matching board.

Any field of the selected note type can be used as the audio source. The field may contain Anki-style references such as:

`[sound:example.mp3]`

You can separately choose when that audio should play:

* when selecting **Column 1**
* when selecting **Column 2**
* when selecting **Column 3**, if enabled

This makes configurations such as

**Kanji → Kana → Meaning + pronunciation audio**

possible without requiring pronunciation to occupy another visible matching column.

Anki Match recognizes common audio formats including MP3, M4A, AAC, OGG, Opus, WAV, FLAC, WebM, 3GP and AMR.

If an audio file is missing or cannot be played, the matching session continues normally.

## Audio and `collection.media` on Android

Anki Match accesses cards, decks and scheduler information through AnkiDroid's public API.

AnkiDroid's existing media files, however, are not exposed as readable files through that API. Anki Match therefore needs read access to the same `collection.media` directory used by AnkiDroid.

Because modern Android versions normally prevent another application from accessing AnkiDroid's private directory inside `Android/data`, the complete AnkiDroid data directory needs to be placed in a user-accessible location if audio playback should be used.

Before changing the directory, complete an AnkiDroid synchronization and create a full collection export including scheduling information and media.

The normal private AnkiDroid directory is located below:

`/storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid`

A possible shared location is:

`/storage/emulated/0/AnkiDroidShared`

A safe migration is:

1. Complete synchronization in AnkiDroid and wait for media synchronization to finish. Create a full collection export including scheduling information and media.
2. Force-stop AnkiDroid from Android's app settings so that its collection database is closed.
3. Using a file manager that is able to access AnkiDroid's private directory, copy the **complete contents** of the AnkiDroid directory to a public folder such as `Internal storage/AnkiDroidShared`. Do not delete the original directory yet.
4. In **AnkiDroid → Settings → Advanced → AnkiDroid directory**, change the directory to `/storage/emulated/0/AnkiDroidShared`. Grant AnkiDroid file access if Android requests it and restart AnkiDroid.
5. Confirm that decks, scheduling information and media still work. Run AnkiDroid's database and media checks and synchronize again before removing the old copy.
6. In Anki Match, enable audio and choose `AnkiDroidShared/collection.media` as the media directory.

Android stores this folder permission, so selecting `collection.media` normally only needs to be done once.

Changing AnkiDroid's configured directory does **not** move the collection automatically. The files need to be copied before changing the path.

For normal use, open **AnkiDroid first**, allow synchronization to complete and then start **Anki Match**.

Avoid reviewing cards simultaneously in AnkiDroid and Anki Match.

## Android interface

The mobile interface is designed around touch input and dynamically adapts the matching board to the available display space.

In portrait orientation, the current session statistics and instructions remain visible above the board.

In landscape orientation, the statistics and instruction area is hidden and the matching board expands to use the remaining screen space. Every row is resized so that the complete current round remains visible without requiring the entire page to scroll.

If the contents of an individual tile are too large, only that tile becomes scrollable.

Each active matching column is shuffled independently.

## Field handling

Anki Match reads the raw note fields supplied by AnkiDroid and converts them into a representation suitable for the Android matching interface.

Cloze markup is converted to its visible text.

HTML formatting is reduced to plain text.

`[sound:...]` references are removed from the visible matching text and handled separately by the audio system.

Images are currently **not rendered directly inside matching tiles**. If an image contains alternative text, that text is displayed; otherwise the image is represented as `[Bild]`.

This is one of the differences between the Android port and the desktop add-on.

## Matching safeguards

Anki Match avoids creating rounds for which an unambiguous answer could not safely be determined.

In normal scheduler mode, a round is stopped if selected fields are empty, if two visible values in the same column are identical, if multiple cards belonging to the same note would enter the same round, or if another note type interrupts the required group at the front of AnkiDroid's queue.

The remaining cards are not discarded. They remain available for normal review in AnkiDroid.

Speedrun Mode builds its groups independently and attempts to avoid identical visible values inside the same matching column.

## Requirements

Anki Match requires:

* **Android 8.0 or newer**
* **AnkiDroid**
* permission to use AnkiDroid's public database API
* **AnkiDroid 2.24 or newer for normal scheduler review**

Speedrun Mode does not write scheduler data.

Audio additionally requires read access to a user-accessible `collection.media` directory as described above.

## Installation

Download the latest APK from this repository's **Releases** section and install it on your Android device.

Because the application is distributed directly as an APK, Android may ask you to allow installation from the application you used to open the APK.

AnkiDroid must already be installed and its initial setup must be complete.

On first launch, Anki Match requests the AnkiDroid database permission required to read decks and cards and to submit scheduler answers.

## Building from source

The project can be built with:

* **JDK 17**
* **Android SDK 35**

Run:

```bash
./gradlew test assembleDebug
```

The resulting debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Why matching?

Matching still involves retrieval and recognition of learned information, but visible alternatives make it more strongly cued than a traditional free-recall Anki card.

Anki Match is therefore intended primarily as an alternative review mode for material that is already reasonably familiar rather than a complete replacement for traditional Anki reviewing.

The format allows several possible answers to be compared simultaneously and provides a faster, more game-like interaction for larger review sessions.

Three-column matching can additionally reinforce relationships between several representations of the same information. For language learning, for example, written form, pronunciation and meaning can be practiced together.

Traditional Anki cards remain useful when the goal is to produce an answer without visible alternatives.

## Relationship to Word Matching Review

Anki Match is an Android port based on the concept and core behavior of [Word Matching Review](https://github.com/Ashikawa-P/Anki-Word-Matching-review).

The desktop project is an Anki add-on written for desktop Anki. This repository contains a separate Android application using AnkiDroid's public API.

The projects therefore share the same basic matching concept and many behavioral rules, but they are separate implementations and do not necessarily have identical features or version numbers.

Anki Match is not an official AnkiDroid, Anki or AnkiWeb project.

## License

This project is released under the **Unlicense**.

See the `LICENSE` file for details.

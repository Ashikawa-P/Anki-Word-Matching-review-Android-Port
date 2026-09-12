package de.gabriel.ankimatch;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class AnkiRepositoryAudioTest {
    @Test
    public void extractsValidSoundFilesInFieldOrderAndDeduplicates() {
        assertEquals(
            Arrays.asList("wort.mp3", "satz.ogg", "A&B.m4a"),
            AnkiRepository.audioFilesFromField(
                "[sound:wort.mp3] Text [SOUND:satz.ogg] [sound:wort.mp3] [sound:A&amp;B.m4a]"
            )
        );
    }

    @Test
    public void rejectsPathsAndNonAudioReferences() {
        assertEquals(
            Collections.emptyList(),
            AnkiRepository.audioFilesFromField(
                "[sound:../secret.mp3] [sound:folder/file.wav] [sound:image.png] [sound:..]"
            )
        );
    }
}

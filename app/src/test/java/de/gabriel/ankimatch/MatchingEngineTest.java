package de.gabriel.ankimatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class MatchingEngineTest {
    @Test
    public void clozeMarkupIsConvertedWithoutRegexFailure() {
        assertEquals(
            "東京 und 大阪",
            AnkiRepository.replaceClozeMarkup("{{c1::東京::Hauptstadt}} und {{c2::大阪}}")
        );
    }

    @Test
    public void firstTryMatchBecomesGood() {
        MatchingEngine engine = new MatchingEngine();
        assertTrue(engine.isCorrect(Arrays.asList("1:0", "1:0", "1:0")));
        assertEquals(MatchingEngine.GOOD, engine.ratingFor("1:0"));
    }

    @Test
    public void mismatchedFirstColumnBecomesAgainWhenEventuallySolved() {
        MatchingEngine engine = new MatchingEngine();
        assertFalse(engine.isCorrect(Arrays.asList("1:0", "2:0")));
        assertTrue(engine.isCorrect(Arrays.asList("1:0", "1:0")));
        assertEquals(MatchingEngine.AGAIN, engine.ratingFor("1:0"));
        assertEquals(MatchingEngine.GOOD, engine.ratingFor("2:0"));
    }
}

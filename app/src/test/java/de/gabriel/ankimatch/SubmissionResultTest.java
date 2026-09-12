package de.gabriel.ankimatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubmissionResultTest {
    @Test
    public void recordsCommittedAndDiscardedRatingsSeparately() {
        Models.SubmissionResult result = new Models.SubmissionResult(1, 1, 3);

        assertEquals(2, result.submitted());
        assertEquals(3, result.discarded);
        assertTrue(result.recoveredQueue());
    }

    @Test
    public void completeSubmissionDoesNotNeedRecovery() {
        Models.SubmissionResult result = new Models.SubmissionResult(4, 1, 0);

        assertEquals(5, result.submitted());
        assertFalse(result.recoveredQueue());
    }
}

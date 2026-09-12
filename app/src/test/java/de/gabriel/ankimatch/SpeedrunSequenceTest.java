package de.gabriel.ankimatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SpeedrunSequenceTest {
    private Models.MatchItem item(long id, String left, String right) {
        return new Models.MatchItem(id, 0, Arrays.asList(left, right));
    }

    @Test
    public void orderedModeKeepsStableGroups() {
        List<Models.MatchItem> items = new ArrayList<>();
        for (int id = 1; id <= 10; id++) {
            items.add(item(id, "l" + id, "r" + id));
        }
        SpeedrunSequence sequence = new SpeedrunSequence(items, false);
        assertEquals(5, sequence.nextBatch(5).size());
        assertEquals(5, sequence.nextBatch(5).size());
        sequence.reset();
        assertEquals(1L, sequence.nextBatch(5).get(0).noteId);
    }

    @Test
    public void remainderOfOneIsRebalanced() {
        List<Models.MatchItem> items = new ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            items.add(item(id, "l" + id, "r" + id));
        }
        SpeedrunSequence sequence = new SpeedrunSequence(items, false);
        assertEquals(4, sequence.nextBatch(5).size());
        assertEquals(2, sequence.nextBatch(5).size());
    }

    @Test
    public void duplicateVisibleValuesAreDeferred() {
        SpeedrunSequence sequence = new SpeedrunSequence(Arrays.asList(
            item(1, "gleich", "x1"),
            item(2, "gleich", "x2"),
            item(3, "anders", "x3")
        ), false);
        List<Models.MatchItem> first = sequence.nextBatch(2);
        assertEquals(Arrays.asList(1L, 3L), Arrays.asList(first.get(0).noteId, first.get(1).noteId));
        assertEquals(2L, sequence.nextBatch(2).get(0).noteId);
        assertTrue(sequence.nextBatch(2).isEmpty());
    }
}

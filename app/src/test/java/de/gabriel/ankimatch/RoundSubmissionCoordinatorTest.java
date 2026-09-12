package de.gabriel.ankimatch;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RoundSubmissionCoordinatorTest {
    @Test
    public void confirmsEverySuccessfulWriteFromPersistentReviewCount() throws Exception {
        FakeGateway gateway = FakeGateway.withQueue("1:0", "2:0");
        Models.SubmissionResult result = coordinator(gateway).submit(7L, round(1L, 2L), solved(1L, 2L));

        assertEquals(1, result.good);
        assertEquals(1, result.again);
        assertEquals(0, result.discarded);
        assertEquals(2, gateway.attempts.size());
    }

    @Test
    public void rejectsPositiveProviderResultWhenPersistentCountDidNotChange() throws Exception {
        FakeGateway gateway = FakeGateway.withQueue("1:0", "2:0");
        gateway.falsePositiveKey = "1:0";

        try {
            coordinator(gateway).submit(7L, round(1L, 2L), solved(1L, 2L));
            fail("Expected a submission failure");
        } catch (AnkiRepository.SubmissionException error) {
            assertEquals(0, error.partial.submitted());
            assertEquals(2, error.partial.discarded);
            assertTrue(error.getMessage().contains("Wiederholungszähler"));
        }
    }

    @Test
    public void replansWhenQueueChangesToAnotherPendingCardBeforeCommit() throws Exception {
        FakeGateway gateway = FakeGateway.withQueue("1:0", "2:0", "3:0");
        gateway.reorderOnceFrom = "1:0";
        gateway.reorderOnceTo = list("3:0", "1:0", "2:0");

        Models.SubmissionResult result = coordinator(gateway).submit(
            7L,
            round(1L, 2L, 3L),
            solved(1L, 2L, 3L)
        );

        assertEquals(3, result.submitted());
        assertEquals(0, result.discarded);
        assertEquals(list("1:0", "3:0", "1:0", "2:0"), gateway.attempts);
    }

    @Test
    public void keepsConfirmedRatingsAndDiscardsBlockedRemainder() throws Exception {
        FakeGateway gateway = FakeGateway.withQueue("1:0", "2:0", "3:0");
        gateway.insertOutsideAfterCommitOf = "1:0";

        Models.SubmissionResult result = coordinator(gateway).submit(
            7L,
            round(1L, 2L, 3L),
            solved(1L, 2L, 3L)
        );

        assertEquals(1, result.submitted());
        assertEquals(2, result.discarded);
        assertEquals(1L, gateway.reviews.get("1:0").longValue());
        assertEquals(0L, gateway.reviews.get("2:0").longValue());
    }

    @Test
    public void acceptsDurableCommitEvenWhenTransportReportsFailure() throws Exception {
        FakeGateway gateway = FakeGateway.withQueue("1:0", "2:0");
        gateway.throwAfterCommitKey = "1:0";

        Models.SubmissionResult result = coordinator(gateway).submit(7L, round(1L, 2L), solved(1L, 2L));

        assertEquals(2, result.submitted());
        assertEquals(0, result.discarded);
    }

    private static RoundSubmissionCoordinator coordinator(FakeGateway gateway) {
        return new RoundSubmissionCoordinator(gateway);
    }

    private static Models.MatchRound round(long... noteIds) {
        List<Models.MatchItem> items = new ArrayList<>();
        for (long noteId : noteIds) {
            items.add(new Models.MatchItem(noteId, 0, list("a", "b")));
        }
        return new Models.MatchRound(items, 2);
    }

    private static Map<String, Models.SolvedMatch> solved(long... noteIds) {
        Map<String, Models.SolvedMatch> solved = new HashMap<>();
        for (int index = 0; index < noteIds.length; index++) {
            Models.MatchItem item = new Models.MatchItem(noteIds[index], 0, list("a", "b"));
            int ease = index % 2 == 0 ? MatchingEngine.GOOD : MatchingEngine.AGAIN;
            solved.put(item.key(), new Models.SolvedMatch(item, ease, 250L));
        }
        return solved;
    }

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        List<T> result = new ArrayList<>();
        java.util.Collections.addAll(result, values);
        return result;
    }

    private static final class FakeGateway implements RoundSubmissionCoordinator.Gateway {
        final List<String> queue = new ArrayList<>();
        final Map<String, Long> reviews = new LinkedHashMap<>();
        final List<String> attempts = new ArrayList<>();
        String falsePositiveKey;
        String reorderOnceFrom;
        List<String> reorderOnceTo;
        String insertOutsideAfterCommitOf;
        String throwAfterCommitKey;

        static FakeGateway withQueue(String... keys) {
            FakeGateway result = new FakeGateway();
            for (String key : keys) {
                result.queue.add(key);
                result.reviews.put(key, 0L);
            }
            result.reviews.put("99:0", 0L);
            return result;
        }

        @Override
        public void ensureDeckSelected(long deckId) {
            // The fake has one selected deck.
        }

        @Override
        public RoundSubmissionCoordinator.QueueCard readHead() {
            return queue.isEmpty() ? null : card(queue.get(0));
        }

        @Override
        public long readReviewCount(RoundSubmissionCoordinator.QueueCard card) {
            return reviews.get(card.key());
        }

        @Override
        public void answerIfStillHead(
            RoundSubmissionCoordinator.QueueCard expected,
            Models.SolvedMatch solved
        ) throws RoundSubmissionCoordinator.QueueConflictException, AnkiRepository.ApiException {
            attempts.add(expected.key());
            if (expected.key().equals(reorderOnceFrom)) {
                queue.clear();
                queue.addAll(reorderOnceTo);
                reorderOnceFrom = null;
                throw new RoundSubmissionCoordinator.QueueConflictException("changed", null);
            }
            if (queue.isEmpty() || !queue.get(0).equals(expected.key())) {
                throw new RoundSubmissionCoordinator.QueueConflictException("changed", null);
            }
            if (expected.key().equals(falsePositiveKey)) {
                return;
            }

            reviews.put(expected.key(), reviews.get(expected.key()) + 1L);
            queue.remove(0);
            if (expected.key().equals(insertOutsideAfterCommitOf)) {
                queue.add(0, "99:0");
            }
            if (expected.key().equals(throwAfterCommitKey)) {
                throwAfterCommitKey = null;
                throw new AnkiRepository.ApiException("transport failed");
            }
        }

        private static RoundSubmissionCoordinator.QueueCard card(String key) {
            String[] parts = key.split(":", -1);
            return new RoundSubmissionCoordinator.QueueCard(
                Long.parseLong(parts[0]),
                Integer.parseInt(parts[1])
            );
        }
    }
}

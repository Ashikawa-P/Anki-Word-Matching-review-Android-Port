package de.gabriel.ankimatch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coordinates scheduler submissions without trusting a provider update count as proof of a commit.
 *
 * The gateway performs the Android-specific provider calls. Keeping the state machine here makes the
 * queue-conflict and false-positive paths deterministic and directly testable on the JVM.
 */
final class RoundSubmissionCoordinator {
    private static final int MAX_QUEUE_REPLANS = 32;

    interface Gateway {
        void ensureDeckSelected(long deckId) throws AnkiRepository.ApiException;

        QueueCard readHead() throws AnkiRepository.ApiException;

        long readReviewCount(QueueCard card) throws AnkiRepository.ApiException;

        void answerIfStillHead(QueueCard expected, Models.SolvedMatch solved)
            throws QueueConflictException, AnkiRepository.ApiException;
    }

    static final class QueueCard {
        final long noteId;
        final int cardOrd;

        QueueCard(long noteId, int cardOrd) {
            this.noteId = noteId;
            this.cardOrd = cardOrd;
        }

        String key() {
            return noteId + ":" + cardOrd;
        }
    }

    static final class QueueConflictException extends AnkiRepository.ApiException {
        private static final long serialVersionUID = 1L;

        QueueConflictException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Gateway gateway;

    RoundSubmissionCoordinator(Gateway gateway) {
        this.gateway = gateway;
    }

    Models.SubmissionResult submit(
        long deckId,
        Models.MatchRound round,
        Map<String, Models.SolvedMatch> solved
    ) throws AnkiRepository.ApiException {
        LinkedHashMap<String, Models.SolvedMatch> pending = validatedPending(round, solved);
        gateway.ensureDeckSelected(deckId);

        int good = 0;
        int again = 0;
        int queueReplans = 0;
        while (!pending.isEmpty()) {
            QueueCard head = gateway.readHead();
            if (head == null || !pending.containsKey(head.key())) {
                return new Models.SubmissionResult(good, again, pending.size());
            }

            Models.SolvedMatch result = pending.get(head.key());
            final long reviewsBefore;
            try {
                reviewsBefore = gateway.readReviewCount(head);
            } catch (AnkiRepository.ApiException error) {
                throw interrupted(
                    "Der unveränderte Kartenstand konnte vor der Bewertung nicht sicher gelesen werden.",
                    error,
                    good,
                    again,
                    pending.size()
                );
            }

            try {
                gateway.answerIfStillHead(head, result);
            } catch (QueueConflictException conflict) {
                if (wasCommitted(head, reviewsBefore, good, again, pending.size())) {
                    good += result.ease == MatchingEngine.GOOD ? 1 : 0;
                    again += result.ease == MatchingEngine.GOOD ? 0 : 1;
                    pending.remove(head.key());
                    queueReplans = 0;
                    continue;
                }

                QueueCard current = gateway.readHead();
                if (current == null || !pending.containsKey(current.key())) {
                    return new Models.SubmissionResult(good, again, pending.size());
                }
                queueReplans++;
                if (queueReplans > MAX_QUEUE_REPLANS) {
                    throw interrupted(
                        "Die AnkiDroid-Warteschlange hat sich während der Übertragung zu oft geändert.",
                        conflict,
                        good,
                        again,
                        pending.size()
                    );
                }
                continue;
            } catch (AnkiRepository.ApiException error) {
                if (wasCommitted(head, reviewsBefore, good, again, pending.size())) {
                    good += result.ease == MatchingEngine.GOOD ? 1 : 0;
                    again += result.ease == MatchingEngine.GOOD ? 0 : 1;
                    pending.remove(head.key());
                    queueReplans = 0;
                    continue;
                }
                throw interrupted(
                    "AnkiDroid konnte die Bewertung für Karte " + head.key() + " nicht sicher übernehmen.",
                    error,
                    good,
                    again,
                    pending.size()
                );
            }

            if (!wasCommitted(head, reviewsBefore, good, again, pending.size())) {
                throw interrupted(
                    "AnkiDroid meldete eine erfolgreiche Übertragung, aber der persistente Wiederholungszähler "
                        + "der Karte blieb unverändert. Die Bewertung wird nicht automatisch erneut gesendet.",
                    null,
                    good,
                    again,
                    pending.size()
                );
            }

            pending.remove(head.key());
            if (result.ease == MatchingEngine.GOOD) {
                good++;
            } else {
                again++;
            }
            queueReplans = 0;
        }
        return new Models.SubmissionResult(good, again, 0);
    }

    private boolean wasCommitted(
        QueueCard card,
        long reviewsBefore,
        int good,
        int again,
        int pending
    ) throws AnkiRepository.SubmissionException {
        try {
            return gateway.readReviewCount(card) > reviewsBefore;
        } catch (AnkiRepository.ApiException error) {
            throw new AnkiRepository.SubmissionException(
                "AnkiDroid hat geantwortet, aber der anschließende Kartenstand konnte nicht verifiziert werden.",
                error,
                new Models.SubmissionResult(good, again, pending)
            );
        }
    }

    private static LinkedHashMap<String, Models.SolvedMatch> validatedPending(
        Models.MatchRound round,
        Map<String, Models.SolvedMatch> solved
    ) throws AnkiRepository.ApiException {
        LinkedHashMap<String, Models.SolvedMatch> pending = new LinkedHashMap<>();
        for (Models.MatchItem item : round.items) {
            Models.SolvedMatch result = solved.get(item.key());
            if (result == null) {
                throw new AnkiRepository.ApiException(
                    "Die Runde ist unvollständig und wurde deshalb nicht bewertet."
                );
            }
            pending.put(item.key(), result);
        }
        return pending;
    }

    private static AnkiRepository.SubmissionException interrupted(
        String message,
        Throwable cause,
        int good,
        int again,
        int pending
    ) {
        return new AnkiRepository.SubmissionException(
            message,
            cause,
            new Models.SubmissionResult(good, again, pending)
        );
    }
}

package de.gabriel.ankimatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Models {
    static final class DeckInfo {
        final long id;
        final String name;
        final int learning;
        final int review;
        final int fresh;
        final boolean dynamic;

        DeckInfo(long id, String name, int learning, int review, int fresh, boolean dynamic) {
            this.id = id;
            this.name = name;
            this.learning = learning;
            this.review = review;
            this.fresh = fresh;
            this.dynamic = dynamic;
        }

        int dueCount() {
            return learning + review + fresh;
        }

        String displayName() {
            return name + "  ·  " + dueCount() + " fällig";
        }
    }

    static final class ModelInfo {
        final long id;
        final String name;
        final List<String> fieldNames;

        ModelInfo(long id, String name, List<String> fieldNames) {
            this.id = id;
            this.name = name;
            this.fieldNames = Collections.unmodifiableList(new ArrayList<>(fieldNames));
        }
    }

    static final class DeckContext {
        final ModelInfo model;
        final int availableAtQueueFront;

        DeckContext(ModelInfo model, int availableAtQueueFront) {
            this.model = model;
            this.availableAtQueueFront = availableAtQueueFront;
        }
    }

    static final class DeckSetupData {
        final List<ModelInfo> models;
        final DeckContext queueContext;

        DeckSetupData(List<ModelInfo> models, DeckContext queueContext) {
            this.models = Collections.unmodifiableList(new ArrayList<>(models));
            this.queueContext = queueContext;
        }
    }

    static final class MatchItem {
        final long noteId;
        final int cardOrd;
        final List<String> values;
        final List<String> audioFiles;

        MatchItem(long noteId, int cardOrd, List<String> values) {
            this(noteId, cardOrd, values, Collections.emptyList());
        }

        MatchItem(long noteId, int cardOrd, List<String> values, List<String> audioFiles) {
            this.noteId = noteId;
            this.cardOrd = cardOrd;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
            this.audioFiles = Collections.unmodifiableList(new ArrayList<>(audioFiles));
        }

        String key() {
            return noteId + ":" + cardOrd;
        }
    }

    static final class MatchRound {
        final List<MatchItem> items;
        final int columnCount;

        MatchRound(List<MatchItem> items, int columnCount) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.columnCount = columnCount;
        }
    }

    static final class RoundLoadResult {
        final MatchRound round;
        final int remaining;
        final String stopReason;

        private RoundLoadResult(MatchRound round, int remaining, String stopReason) {
            this.round = round;
            this.remaining = remaining;
            this.stopReason = stopReason;
        }

        static RoundLoadResult ready(MatchRound round) {
            return new RoundLoadResult(round, round.items.size(), null);
        }

        static RoundLoadResult stopped(int remaining, String stopReason) {
            return new RoundLoadResult(null, remaining, stopReason);
        }

        boolean isReady() {
            return round != null;
        }
    }

    static final class SolvedMatch {
        final MatchItem item;
        final int ease;
        final long timeTakenMs;

        SolvedMatch(MatchItem item, int ease, long timeTakenMs) {
            this.item = item;
            this.ease = ease;
            this.timeTakenMs = timeTakenMs;
        }
    }

    static final class SubmissionResult {
        final int good;
        final int again;
        final int discarded;

        SubmissionResult(int good, int again, int discarded) {
            this.good = Math.max(0, good);
            this.again = Math.max(0, again);
            this.discarded = Math.max(0, discarded);
        }

        int submitted() {
            return good + again;
        }

        boolean recoveredQueue() {
            return discarded > 0;
        }
    }

    private Models() {
    }
}

package de.gabriel.ankimatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** In-memory, zero-write ordering and batching equivalent to the desktop add-on's SpeedrunSequence. */
final class SpeedrunSequence {
    private final List<Models.MatchItem> base;
    private final boolean randomized;
    private final List<Models.MatchItem> remaining = new ArrayList<>();

    SpeedrunSequence(List<Models.MatchItem> items, boolean randomized) {
        this.base = new ArrayList<>(items);
        this.randomized = randomized;
        reset();
    }

    int total() {
        return base.size();
    }

    void reset() {
        remaining.clear();
        remaining.addAll(base);
        if (randomized) {
            Collections.shuffle(remaining);
        }
    }

    List<Models.MatchItem> nextBatch(int requestedBatchSize) {
        int target = Math.max(1, requestedBatchSize);
        if (remaining.isEmpty()) {
            return Collections.emptyList();
        }
        if (target > 2 && remaining.size() > target && remaining.size() % target == 1) {
            target--;
        }

        List<Models.MatchItem> chosen = new ArrayList<>();
        List<Integer> chosenIndexes = new ArrayList<>();
        List<Set<String>> keySets = null;
        for (int index = 0; index < remaining.size(); index++) {
            Models.MatchItem item = remaining.get(index);
            if (keySets == null) {
                keySets = new ArrayList<>();
                for (int column = 0; column < item.values.size(); column++) {
                    keySets.add(new HashSet<>());
                }
            }
            boolean duplicate = false;
            for (int column = 0; column < item.values.size(); column++) {
                if (keySets.get(column).contains(key(item.values.get(column)))) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                continue;
            }
            chosen.add(item);
            chosenIndexes.add(index);
            for (int column = 0; column < item.values.size(); column++) {
                keySets.get(column).add(key(item.values.get(column)));
            }
            if (chosen.size() >= target) {
                break;
            }
        }
        for (int index = chosenIndexes.size() - 1; index >= 0; index--) {
            remaining.remove((int) chosenIndexes.get(index));
        }
        return chosen;
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}

package de.gabriel.ankimatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure matching/rating state kept independent from Android for unit testing. */
final class MatchingEngine {
    static final int AGAIN = 1;
    static final int GOOD = 3;

    private final Set<String> failedFirstColumn = new HashSet<>();

    boolean isCorrect(List<String> selectedItemKeys) {
        if (selectedItemKeys.isEmpty()) {
            return false;
        }
        String first = selectedItemKeys.get(0);
        for (String key : selectedItemKeys) {
            if (!first.equals(key)) {
                failedFirstColumn.add(first);
                return false;
            }
        }
        return true;
    }

    int ratingFor(String itemKey) {
        return failedFirstColumn.contains(itemKey) ? AGAIN : GOOD;
    }
}

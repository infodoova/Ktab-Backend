package com.doova.ktab.utils.text;

import java.util.Objects;

public final class StringDiffUtils {

    private StringDiffUtils() {
    }

    public static String correctOverlapFuzzy(
            String context,
            String chunk,
            int minOverlap,
            double ratioThreshold
    ) {
        Objects.requireNonNull(chunk, "chunk must not be null");

        if (context == null || context.isEmpty() || chunk.isEmpty()) {
            return chunk;
        }
        if (minOverlap < 1) {
            throw new IllegalArgumentException("minOverlap must be greater than 0");
        }
        if (ratioThreshold < 0.0 || ratioThreshold > 1.0) {
            throw new IllegalArgumentException("ratioThreshold must be between 0.0 and 1.0");
        }

        int contextLength = context.length();
        int chunkLength = chunk.length();
        int maxOverlap = Math.min(contextLength, chunkLength);

        if (minOverlap > maxOverlap) {
            return chunk;
        }

        for (int length = maxOverlap; length >= minOverlap; length--) {
            String suffix = context.substring(contextLength - length);
            String prefix = chunk.substring(0, length);

            if (calculateSimilarity(suffix, prefix) >= ratioThreshold) {
                return chunk.substring(length);
            }
        }

        return chunk;
    }

    private static double calculateSimilarity(String first, String second) {
        if (first.equals(second)) {
            return 1.0;
        }

        int maxLength = Math.max(first.length(), second.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(first, second);
        return 1.0 - ((double) distance / maxLength);
    }

    private static int levenshteinDistance(String first, String second) {
        if (first.length() < second.length()) {
            return levenshteinDistance(second, first);
        }

        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];

        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;

            for (int j = 1; j <= second.length(); j++) {
                int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;

                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost
                );
            }

            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[second.length()];
    }
}

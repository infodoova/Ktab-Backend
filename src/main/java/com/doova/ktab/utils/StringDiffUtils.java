package com.doova.ktab.utils;

public class StringDiffUtils {
    public static String correctOverlapFuzzy(String context, String chunk, int minOverlap, double ratioThreshold) {
        if (context == null || context.isEmpty()) return chunk;
        int n = context.length();
        int m = chunk.length();

        // Search for the longest suffix of context that matches a prefix of chunk
        for (int len = Math.min(n, m); len >= minOverlap; len--) {
            String suffix = context.substring(n - len);
            String prefix = chunk.substring(0, len);

            double ratio = calculateSimilarity(suffix, prefix);
            if (ratio >= ratioThreshold) {
                System.out.println("Fuzzy overlap detected! Trimming " + len + " chars.");
                return chunk.substring(len);
            }
        }
        return chunk;
    }

    private static double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / Math.max(s1.length(), s2.length()));
    }

    private static int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }
}
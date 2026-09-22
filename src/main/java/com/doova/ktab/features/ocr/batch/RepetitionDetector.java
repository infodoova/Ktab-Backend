package com.doova.ktab.features.ocr.batch;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RepetitionDetector {

    /**
     * Checks if the markdown contains an OCR repetition loop (e.g. line repeated N times
     * or extremely low unique n-gram ratio).
     */
    public boolean hasRepetition(String markdown, int maxRepeatedLines) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }

        String[] lines = markdown.split("\n");
        int consecutiveCount = 1;
        String prevLine = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (line.equals(prevLine)) {
                consecutiveCount++;
                if (consecutiveCount >= maxRepeatedLines) {
                    return true;
                }
            } else {
                consecutiveCount = 1;
                prevLine = line;
            }
        }

        // Check 3-gram repetition for token-level loops
        String[] tokens = markdown.trim().split("\\s+");
        if (tokens.length >= 40) {
            List<String> trigrams = new ArrayList<>();
            for (int i = 0; i < tokens.length - 2; i++) {
                trigrams.add(tokens[i] + " " + tokens[i + 1] + " " + tokens[i + 2]);
            }
            Set<String> uniqueTrigrams = new HashSet<>(trigrams);
            double uniqueRatio = (double) uniqueTrigrams.size() / trigrams.size();
            if (uniqueRatio < 0.20) {
                return true;
            }
        }

        return false;
    }
}

package com.doova.ktab.features.ocr.harmonize;

import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import org.springframework.stereotype.Component;

@Component
public class HarmonizationGuard {

    private static final double DEFAULT_MIN_SIMILARITY = 0.92;
    private static final double MAX_LENGTH_DELTA_RATIO = 0.08;

    /**
     * Validates that harmonized text preserves original text content without unauthorized rewrites.
     */
    public boolean isValid(String raw, String clean, double minSimilarity) {
        if (raw == null || raw.isBlank()) {
            return clean == null || clean.isBlank();
        }
        if (clean == null || clean.isBlank()) {
            return false;
        }

        // Check length change bounds (±8%)
        int rawLen = raw.trim().length();
        int cleanLen = clean.trim().length();
        double lengthDelta = (double) Math.abs(cleanLen - rawLen) / Math.max(1, rawLen);

        if (lengthDelta > MAX_LENGTH_DELTA_RATIO) {
            return false;
        }

        // Check normalized text similarity
        double sim = ArabicTextNormalizer.similarity(raw, clean);
        return sim >= (minSimilarity > 0 ? minSimilarity : DEFAULT_MIN_SIMILARITY);
    }

    public boolean isValid(String raw, String clean) {
        return isValid(raw, clean, DEFAULT_MIN_SIMILARITY);
    }
}

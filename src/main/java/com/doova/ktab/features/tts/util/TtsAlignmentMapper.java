package com.doova.ktab.features.tts.util;

import com.doova.ktab.features.tts.dto.Alignment;
import com.doova.ktab.features.tts.dto.WordTiming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TtsAlignmentMapper {

    /**
     * Aggregates character-level timings into word-level timings
     * AND preserves absolute character indices for highlighting.
     */
    public static List<WordTiming> mapToWordTimings(Alignment alignment, int baseCharIndex) {
        if (alignment == null || alignment.characters() == null || alignment.characters().isEmpty()) {
            return Collections.emptyList();
        }

        List<WordTiming> wordTimings = new ArrayList<>();

        StringBuilder currentWord = new StringBuilder();
        Double wordStartTime = null;

        int wordStartChar = -1;
        int globalCharIndex = baseCharIndex;

        List<String> chars = alignment.characters();
        List<Double> starts = alignment.startTimes();
        List<Double> ends = alignment.endTimes();

        for (int i = 0; i < chars.size(); i++) {
            String c = chars.get(i);
            boolean isWhitespace = c.isBlank();

            // Start of a word
            if (!isWhitespace && currentWord.isEmpty()) {
                wordStartTime = starts.get(i);
                wordStartChar = globalCharIndex;
            }

            if (!isWhitespace) {
                currentWord.append(c);
            }

            // End of word (on whitespace OR last character)
            boolean isLastChar = (i == chars.size() - 1);

            if ((isWhitespace || isLastChar) && !currentWord.isEmpty()) {
                int lastCharIndex = isWhitespace ? i - 1 : i;

                double wordEndTime = ends.get(lastCharIndex);
                int wordEndChar = globalCharIndex - 1;

                wordTimings.add(new WordTiming(currentWord.toString(), wordStartTime, wordEndTime, wordEndTime - wordStartTime, wordStartChar, wordEndChar));

                currentWord.setLength(0);
                wordStartTime = null;
                wordStartChar = -1;
            }

            globalCharIndex++;
        }

        return wordTimings;
    }

}

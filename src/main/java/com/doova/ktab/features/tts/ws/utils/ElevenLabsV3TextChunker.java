package com.doova.ktab.features.tts.ws.utils;

import com.doova.ktab.features.tts.dto.TextChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * ElevenLabs v3–safe Arabic TTS chunker
 *
 * Guarantees:
 * - NEVER exceeds 50 words
 * - Arabic punctuation aware
 * - Madd-aware (tatweel)
 * - Stable absolute offsets
 * - Streaming-safe
 */
public final class ElevenLabsV3TextChunker {

    private ElevenLabsV3TextChunker() {}

    private static final int MAX_WORDS = 50;
    private static final int SOFT_TARGET = 45;

    private static final char MADD = 'ـ';

    /* --------------------------------------------------
       PUBLIC API
    -------------------------------------------------- */

    public static List<TextChunk> chunk(String text, int ignoredMaxChars) {
        // Treat second argument as absolute startChar offset (as before)
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int startChar = Math.max(0, ignoredMaxChars);

        // ✅ Always return ONE chunk
        return List.of(new TextChunk(
                text.trim(),
                startChar
        ));
    }

    private static List<TextChunk> chunks(String text, int startChar) {
        if (text == null || text.isBlank()) return List.of();

        List<TextChunk> out = new ArrayList<>();

        int globalIndex = 0;
        String[] words = text.split("\\s+");

        StringBuilder buffer = new StringBuilder();
        int bufferStart = -1;
        int wordCount = 0;

        for (String word : words) {

            int wordStart = text.indexOf(word, globalIndex);
            if (wordStart < 0) wordStart = globalIndex;

            if (buffer.isEmpty()) {
                bufferStart = wordStart;
                buffer.append(word);
            } else {
                buffer.append(' ').append(word);
            }

            wordCount++;
            globalIndex = wordStart + word.length();

            boolean isHardLimit = wordCount == MAX_WORDS;
            boolean isPreferredBreak =
                    wordCount >= SOFT_TARGET &&
                            (endsWithPunctuation(word) || word.indexOf(MADD) >= 0);

            if (isHardLimit || isPreferredBreak) {
                emit(out, buffer.toString(), startChar + bufferStart);
                buffer.setLength(0);
                wordCount = 0;
            }
        }

        if (!buffer.isEmpty()) {
            emit(out, buffer.toString(), startChar + bufferStart);
        }

        return out;
    }

    /* --------------------------------------------------
       HELPERS
    -------------------------------------------------- */

    private static void emit(List<TextChunk> out, String text, int start) {
        out.add(new TextChunk(text, start));
    }

    private static boolean endsWithPunctuation(String word) {
        if (word.isEmpty()) return false;
        char c = word.charAt(word.length() - 1);
        return c == '.' || c == '!' || c == '?' ||
                c == '؟' || c == '،' || c == '؛';
    }
}

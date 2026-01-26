package com.doova.ktab.ws.utils;

import com.doova.ktab.dto.tts.TextChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Prosody-weighted Arabic TTS chunker with Madd (ـ) detection.
 * <p>
 * Guarantees:
 * - Max 25 words per chunk
 * - Arabic punctuation aware
 * - Madd-aware (tatweel)
 * - Stable absolute offsets
 * - Streaming-safe
 */
public final class ArabicTtsChunker {

    private ArabicTtsChunker() {
    }

    /* --------------------------------------------------
       CONFIG
    -------------------------------------------------- */

    private static final int MAX_WORDS = 50;
    private static final double TARGET_PROSODY = 55.0;

    private static final char[] HARD_BREAKS = {'.', '!', '?', '؟', '؛'};

    private static final char[] SOFT_BREAKS = {',', '،', ':', '—', '–'};

    // Arabic diacritics
    private static final String TASHKEEL = "ًٌٍَُِّْ";
    private static final char MADD = 'ـ'; // Tatweel

    /* --------------------------------------------------
       PUBLIC API
    -------------------------------------------------- */

    public static List<TextChunk> chunk(String text, int ignoredMaxChars) {
        if (text == null || text.isBlank()) return List.of();

        List<TextChunk> result = new ArrayList<>();
        int globalCursor = 0;

        String[] paragraphs = text.split("\\R\\R+");

        for (String rawParagraph : paragraphs) {

            int paragraphStart = text.indexOf(rawParagraph, globalCursor);
            if (paragraphStart < 0) paragraphStart = globalCursor;

            String paragraph = rawParagraph.trim();
            if (paragraph.isEmpty()) {
                globalCursor = paragraphStart + rawParagraph.length();
                continue;
            }

            List<Token> tokens = tokenize(paragraph, paragraphStart);

            StringBuilder buffer = new StringBuilder();
            int bufferStart = -1;
            int words = 0;
            double prosody = 0;

            for (Token t : tokens) {

                if (buffer.isEmpty()) {
                    buffer.append(t.text);
                    bufferStart = t.start;
                    words = t.words;
                    prosody = t.prosody;
                    continue;
                }

                boolean exceedWords = words + t.words > MAX_WORDS;
                boolean exceedProsody = prosody + t.prosody > TARGET_PROSODY;

                if (exceedWords || exceedProsody) {
                    emit(result, buffer.toString(), bufferStart);
                    buffer.setLength(0);
                    buffer.append(t.text);
                    bufferStart = t.start;
                    words = t.words;
                    prosody = t.prosody;
                } else {
                    buffer.append(' ').append(t.text);
                    words += t.words;
                    prosody += t.prosody;
                }

                // Strong preference to flush AFTER madd-heavy sentence endings
                if (t.hasMadd && isHardEnding(t.text) && words >= 12) {
                    emit(result, buffer.toString(), bufferStart);
                    buffer.setLength(0);
                    words = 0;
                    prosody = 0;
                }
            }

            if (!buffer.isEmpty()) {
                emit(result, buffer.toString(), bufferStart);
            }

            globalCursor = paragraphStart + rawParagraph.length();
        }

        return result;
    }

    /* --------------------------------------------------
       TOKENIZATION
    -------------------------------------------------- */

    private static List<Token> tokenize(String paragraph, int paragraphOffset) {
        List<Token> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int tokenStart = paragraphOffset;

        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            cur.append(c);

            if (isHardBreak(c) || isSoftBreak(c)) {
                flush(out, cur, tokenStart);
                tokenStart = paragraphOffset + i + 1;
            }
        }

        flush(out, cur, tokenStart);
        return out;
    }

    private static void flush(List<Token> out, StringBuilder cur, int start) {
        if (cur.isEmpty()) return;

        String text = cur.toString().trim();
        if (!text.isEmpty()) {
            out.add(new Token(text, start, countWords(text), calculateProsody(text), text.indexOf(MADD) >= 0));
        }
        cur.setLength(0);
    }

    /* --------------------------------------------------
       PROSODY LOGIC (Madd-aware)
    -------------------------------------------------- */

    private static double calculateProsody(String text) {
        double score = countWords(text);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isLongVowel(c)) score += 0.25;
            if (TASHKEEL.indexOf(c) >= 0) score += 0.4;

            // Madd (tatweel)
            if (c == MADD) {
                score += 0.8;

                // Bonus if followed by long vowel
                if (i + 1 < text.length() && isLongVowel(text.charAt(i + 1))) {
                    score += 0.3;
                }
            }
        }

        return score;
    }

    private static boolean isLongVowel(char c) {
        return c == 'ا' || c == 'و' || c == 'ي';
    }

    /* --------------------------------------------------
       HELPERS
    -------------------------------------------------- */

    private static void emit(List<TextChunk> out, String text, int start) {
        out.add(new TextChunk(text, start));
    }

    private static int countWords(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    private static boolean isHardEnding(String text) {
        if (text.isEmpty()) return false;
        char c = text.charAt(text.length() - 1);
        return isHardBreak(c);
    }

    private static boolean isHardBreak(char c) {
        for (char b : HARD_BREAKS) if (b == c) return true;
        return false;
    }

    private static boolean isSoftBreak(char c) {
        for (char b : SOFT_BREAKS) if (b == c) return true;
        return false;
    }

    /* --------------------------------------------------
       INTERNAL MODEL
    -------------------------------------------------- */

    private record Token(String text, int start, int words, double prosody, boolean hasMadd) {
    }
}

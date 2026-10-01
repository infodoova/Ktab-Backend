package com.doova.ktab.features.nativetts;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits chapter text into TTS requests. PDF text has hard line wraps, so each paragraph is first reflowed into one
 * line; then it is cut at sentence ends, then at whitespace, and only as a last resort inside a single huge word.
 * Paragraphs are never merged, so a pause always falls where the book has one. Letters and diacritics are untouched.
 */
public final class TextChunker {

    private static final Pattern PARAGRAPH = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!؟?؛…])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextChunker() {
    }

    public static List<String> chunk(String text, int max) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        for (String paragraph : PARAGRAPH.split(text)) {
            String p = WHITESPACE.matcher(paragraph).replaceAll(" ").strip();
            if (p.isEmpty()) {
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String sentence : SENTENCE_END.split(p)) {
                if (sentence.length() > max) {
                    flush(current, chunks);
                    splitLong(sentence, max, chunks);
                } else if (current.length() == 0) {
                    current.append(sentence);
                } else if (current.length() + 1 + sentence.length() <= max) {
                    current.append(' ').append(sentence);
                } else {
                    flush(current, chunks);
                    current.append(sentence);
                }
            }
            flush(current, chunks);
        }
        return chunks;
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (current.length() > 0) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private static void splitLong(String sentence, int max, List<String> chunks) {
        StringBuilder current = new StringBuilder();
        for (String word : sentence.split(" ")) {
            if (word.length() > max) {
                flush(current, chunks);
                for (int i = 0; i < word.length(); i += max) {
                    chunks.add(word.substring(i, Math.min(word.length(), i + max)));
                }
            } else if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= max) {
                current.append(' ').append(word);
            } else {
                flush(current, chunks);
                current.append(word);
            }
        }
        flush(current, chunks);
    }
}

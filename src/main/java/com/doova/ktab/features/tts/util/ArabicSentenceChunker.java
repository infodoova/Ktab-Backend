package com.doova.ktab.features.tts.util;

import java.util.ArrayList;
import java.util.List;

public final class ArabicSentenceChunker {

    private ArabicSentenceChunker() {}

    // Arabic punctuation: "؟" "،" "؛" plus "." "!" ":" etc.
    private static final char[] HARD_BREAKS = new char[] {'.', '!', '?', '؟', '؛'};
    private static final char[] SOFT_BREAKS = new char[] {',', '،', ':', '：', '—', '–'};

    public static List<TextChunk> chunk(String text, int maxChars) {
        if (text == null || text.isBlank()) return List.of();
        maxChars = Math.max(120, maxChars);

        // Normalize line endings but KEEP paragraphs
        String[] paragraphs = text.split("\\R\\R+"); // blank line separates paragraphs

        List<TextChunk> chunks = new ArrayList<>();
        int cursor = 0;

        for (String p : paragraphs) {
            String paragraph = p.trim();
            if (paragraph.isEmpty()) {
                cursor += p.length() + 2;
                continue;
            }

            // If paragraph fits, take it
            if (paragraph.length() <= maxChars) {
                chunks.add(new TextChunk(paragraph));
                cursor += p.length() + 2;
                continue;
            }

            // Otherwise, sentence-split with punctuation awareness
            List<String> sentences = splitSentences(paragraph);

            StringBuilder buf = new StringBuilder();
            for (String s : sentences) {
                if (buf.length() == 0) {
                    buf.append(s);
                } else if (buf.length() + 1 + s.length() <= maxChars) {
                    buf.append(' ').append(s);
                } else {
                    chunks.add(new TextChunk(buf.toString().trim()));
                    buf.setLength(0);
                    buf.append(s);
                }
            }
            if (buf.length() > 0) chunks.add(new TextChunk(buf.toString().trim()));

            cursor += p.length() + 2;
        }

        // Final safety: ensure no chunk > maxChars by fallback word-splitting
        return enforceMaxByWord(chunks, maxChars);
    }

    private static List<String> splitSentences(String paragraph) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            cur.append(c);

            if (isHardBreak(c)) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }

            // Soft breaks: allow split if we’re already “long enough”
            if (isSoftBreak(c) && cur.length() > 80) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
        }

        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    private static boolean isHardBreak(char c) {
        for (char b : HARD_BREAKS) if (b == c) return true;
        return false;
    }

    private static boolean isSoftBreak(char c) {
        for (char b : SOFT_BREAKS) if (b == c) return true;
        return false;
    }

    private static List<TextChunk> enforceMaxByWord(List<TextChunk> chunks, int maxChars) {
        List<TextChunk> out = new ArrayList<>();
        for (TextChunk ch : chunks) {
            String t = ch.text();
            if (t.length() <= maxChars) {
                out.add(ch);
                continue;
            }
            // fallback split on whitespace (never break words)
            String[] words = t.split("\\s+");
            StringBuilder buf = new StringBuilder();
            for (String w : words) {
                if (buf.length() == 0) buf.append(w);
                else if (buf.length() + 1 + w.length() <= maxChars) buf.append(' ').append(w);
                else {
                    out.add(new TextChunk(buf.toString()));
                    buf.setLength(0);
                    buf.append(w);
                }
            }
            if (buf.length() > 0) out.add(new TextChunk(buf.toString()));
        }
        return out;
    }

    public record TextChunk(String text) {}
}

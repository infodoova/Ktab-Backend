package com.doova.ktab.features.storybook.story;

import java.util.Arrays;
import java.util.regex.Pattern;

public final class ArabicText {

    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!؟?]+");

    private ArabicText() {
    }

    public static boolean isTashkeel(char c) {
        return (c >= 0x064B && c <= 0x0652) || c == 0x0670;
    }

    public static boolean isTanween(char c) {
        return c >= 0x064B && c <= 0x064D;
    }

    public static boolean isShadda(char c) {
        return c == 0x0651;
    }

    public static String stripTashkeel(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (!isTashkeel(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean containsTashkeel(String s) {
        for (char c : s.toCharArray()) {
            if (isTashkeel(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLatinLetters(String s) {
        return LATIN.matcher(s).find();
    }

    public static int wordCount(String s) {
        String trimmed = s.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    public static int sentenceCount(String s) {
        return (int) Arrays.stream(SENTENCE_END.split(s.strip()))
                .filter(part -> !part.isBlank())
                .count();
    }
}

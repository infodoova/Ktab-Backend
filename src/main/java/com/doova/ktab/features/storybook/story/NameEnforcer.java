package com.doova.ktab.features.storybook.story;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes every mention of the child's name byte-identical to what the parent typed
 * (spec: "written exactly as the parent typed it"), instead of rejecting pages where
 * the model vocalized the name or picked a different alif.
 */
public final class NameEnforcer {

    private static final String MARKS = "[\\u064B-\\u0652\\u0670]*";
    private static final String PROCLITIC = "((?:[وف]" + MARKS + ")?(?:[بلك]" + MARKS + ")?)";
    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{M}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{M}])";

    private NameEnforcer() {
    }

    public static String enforce(String text, String typedName) {
        Pattern pattern = Pattern.compile(NOT_LETTER_BEFORE + PROCLITIC + namePattern(typedName) + NOT_LETTER_AFTER);
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String proclitic = ArabicText.stripTashkeel(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(proclitic + typedName));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String namePattern(String typedName) {
        String bare = ArabicText.stripTashkeel(typedName);
        StringBuilder sb = new StringBuilder("(?:");
        for (int i = 0; i < bare.length(); i++) {
            char c = bare.charAt(i);
            sb.append(letterClass(c, i == bare.length() - 1)).append(MARKS);
        }
        return sb.append(')').toString();
    }

    private static String letterClass(char c, boolean last) {
        return switch (c) {
            case 'ا', 'أ', 'إ', 'آ', 'ٱ' -> "[اأإآٱ]";
            case 'ة', 'ه' -> last ? "[ةه]" : Pattern.quote(String.valueOf(c));
            case 'ى', 'ي' -> last ? "[ىي]" : Pattern.quote(String.valueOf(c));
            default -> Pattern.quote(String.valueOf(c));
        };
    }
}

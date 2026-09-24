package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TashkeelLevel;

/** Applied at render time; stored text is always the fully vocalized version (decision D3). */
public final class TashkeelFilter {

    private TashkeelFilter() {
    }

    public static String apply(String text, TashkeelLevel level) {
        if (level == null || level == TashkeelLevel.FULL) {
            return text;
        }
        if (level == TashkeelLevel.NONE) {
            return ArabicText.stripTashkeel(text);
        }
        if (level == TashkeelLevel.PARTIAL) {
            StringBuilder sb = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                if (!ArabicText.isTashkeel(c) || ArabicText.isShadda(c) || ArabicText.isTanween(c)) {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return text;
    }
}

package com.doova.ktab.features.extraction.text;

import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Western (123), Arabic-Indic (١٢٣) and Persian (۱۲۳) digits. Digits are normalized only for parsing. */
public final class ArabicNumberUtils {

    private static final Pattern PAGE_NUMBER = Pattern.compile("^[\\s\\-–—()\\[\\].]*(\\d{1,4})[\\s\\-–—()\\[\\].]*$");

    private ArabicNumberUtils() {
    }

    public static String toAsciiDigits(String value) {
        return ArabicTextNormalizer.convertDigitsToAscii(value);
    }

    /** A line that is only a page number (optionally wrapped in dashes or brackets); never a number inside text. */
    public static OptionalInt parsePageNumber(String line) {
        if (line == null || line.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher m = PAGE_NUMBER.matcher(toAsciiDigits(line));
        return m.matches() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }
}

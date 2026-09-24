package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.enums.CharacterKind;

import java.util.Locale;

public final class StorybookKeys {

    private StorybookKeys() {
    }

    public static String characterSheet(Long bookId, CharacterKind kind, int version) {
        return "storybook/" + bookId + "/characters/" + kind.name().toLowerCase(Locale.ROOT) + "/v" + version + ".png";
    }

    public static String pageImage(Long bookId, int pageIndex, int generation) {
        return "storybook/" + bookId + "/pages/" + pageIndex + "/g" + generation + ".png";
    }

    public static String photo(Long bookId) {
        return "storybook/" + bookId + "/photo/source.enc";
    }

    public static String pdf(Long bookId, int renderRound) {
        return "storybook/" + bookId + "/book-r" + renderRound + ".pdf";
    }
}

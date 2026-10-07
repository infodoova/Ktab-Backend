package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.enums.CharacterKind;

import java.util.Locale;

public final class StorybookKeys {

    private StorybookKeys() {
    }

    public static String characterSheet(Long bookId, CharacterKind kind, int version) {
        return "storybook/" + bookId + "/characters/" + kind.name().toLowerCase(Locale.ROOT) + "/v" + version + ".png";
    }

    /** The id is the parent's own text, so it is reduced to a safe path segment. */
    public static String supportingSheet(Long bookId, String characterId, int version) {
        String slug = characterId == null ? "" : characterId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "c" + Integer.toHexString(characterId == null ? 0 : characterId.hashCode());
        }
        return "storybook/" + bookId + "/characters/supporting/" + slug + "/v" + version + ".png";
    }

    public static String pageImage(Long bookId, int pageIndex, int generation) {
        return "storybook/" + bookId + "/pages/" + pageIndex + "/g" + generation + ".png";
    }

    /** The downscaled JPEG copy of a page image that readers load. */
    public static String pageImageWeb(Long bookId, int pageIndex, int generation) {
        return "storybook/" + bookId + "/pages/" + pageIndex + "/g" + generation + ".web.jpg";
    }

    public static String photo(Long bookId) {
        return "storybook/" + bookId + "/photo/source.enc";
    }

    public static String characterPhoto(Long bookId, String characterId) {
        if (characterId == null || characterId.isBlank() || "child".equalsIgnoreCase(characterId)) {
            return photo(bookId);
        }
        String slug = characterId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "c" + Integer.toHexString(characterId.hashCode());
        }
        return "storybook/" + bookId + "/photo/" + slug + ".enc";
    }

    public static String pdf(Long bookId, int renderRound) {
        return "storybook/" + bookId + "/book-r" + renderRound + ".pdf";
    }
}

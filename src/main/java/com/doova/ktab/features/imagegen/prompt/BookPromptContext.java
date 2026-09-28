package com.doova.ktab.features.imagegen.prompt;

import lombok.Builder;

/**
 * Encapsulates the metadata and visual reference of the book entity
 * to ground the AI image generator in the book's world, lore, and visual style.
 */
@Builder
public record BookPromptContext(
        String title,
        String author,
        String genre,
        String description,
        String coverImageUrl
) {
    public static BookPromptContext empty() {
        return new BookPromptContext(null, null, null, null, null);
    }
}

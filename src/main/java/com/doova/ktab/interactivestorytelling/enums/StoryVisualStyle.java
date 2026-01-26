package com.doova.ktab.interactivestorytelling.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StoryVisualStyle {

    CINEMATIC_STORYBOOK, REALISTIC, ANIME, COMIC_BOOK, WATERCOLOR, PIXAR_3D, NOIR;

    /**
     * Spring + Jackson safe valueOf.
     * Accepts: cinematic-storybook, CINEMATIC_STORYBOOK, cinematic_storybook
     */
    @JsonCreator
    public static StoryVisualStyle valueOfSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        try {
            return StoryVisualStyle.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid StoryVisualStyle: " + value);
        }
    }

    /**
     * Controls JSON serialization output
     */
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}

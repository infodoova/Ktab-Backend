package com.doova.ktab.features.storybook.illustration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record VisualQaResponse(
        @JsonPropertyDescription("The CHILD clearly matches the character sheet") boolean identityMatch,
        @JsonPropertyDescription("The setting, environment, time of day and lighting match the scene description") boolean sceneMatch,
        @JsonPropertyDescription("Any letters, words, numbers, logos or writing-like marks appear") boolean strayText,
        @JsonPropertyDescription("No extra or missing fingers, limbs or eyes, no distorted faces or bodies") boolean anatomyOk,
        @JsonPropertyDescription("Nothing frightening, violent, immodest or unsuitable for a young child") boolean safeForChildren,
        @JsonPropertyDescription("One short sentence per issue found; empty if none") List<String> problems
) {
    public VisualQaResponse(boolean identityMatch, boolean strayText, boolean anatomyOk, boolean safeForChildren, List<String> problems) {
        this(identityMatch, true, strayText, anatomyOk, safeForChildren, problems);
    }

    public boolean passed() {
        return identityMatch && sceneMatch && !strayText && anatomyOk && safeForChildren;
    }
}

package com.doova.ktab.features.storybook.illustration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record VisualQaResponse(
        @JsonPropertyDescription("The CHILD clearly matches the character sheet") boolean identityMatch,
        @JsonPropertyDescription("Any letters, words, numbers, logos or writing-like marks appear") boolean strayText,
        @JsonPropertyDescription("No extra or missing fingers, limbs or eyes, no distorted faces or bodies") boolean anatomyOk,
        @JsonPropertyDescription("Nothing frightening, violent, immodest or unsuitable for a young child") boolean safeForChildren,
        @JsonPropertyDescription("One short sentence per issue found; empty if none") List<String> problems
) {
    public boolean passed() {
        return identityMatch && !strayText && anatomyOk && safeForChildren;
    }
}

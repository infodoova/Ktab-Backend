package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.llm.LenientBooleanDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VisualQaResponse(
        @JsonDeserialize(using = LenientBooleanDeserializer.class)
        @JsonPropertyDescription("The CHILD clearly matches the character sheet") boolean identityMatch,
        @JsonDeserialize(using = LenientBooleanDeserializer.class)
        @JsonPropertyDescription("Any letters, words, numbers, logos or writing-like marks appear") boolean strayText,
        @JsonDeserialize(using = LenientBooleanDeserializer.class)
        @JsonPropertyDescription("No extra or missing fingers, limbs or eyes, no distorted faces or bodies") boolean anatomyOk,
        @JsonDeserialize(using = LenientBooleanDeserializer.class)
        @JsonPropertyDescription("Nothing frightening, violent, immodest or unsuitable for a young child") boolean safeForChildren,
        @JsonPropertyDescription("One short sentence per issue found; empty if none") List<String> problems
) {
    public boolean passed() {
        return identityMatch && !strayText && anatomyOk && safeForChildren;
    }
}

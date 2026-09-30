package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterBibleResponse(
        @JsonPropertyDescription("List of character specifications with visual locks") List<CharacterVisualSpec> characters,
        @JsonPropertyDescription("Global artistic and visual consistency notes") String visualStyleNotes,
        @JsonPropertyDescription("Brief summary of character relationships and cast dynamics") String summary
) {
}

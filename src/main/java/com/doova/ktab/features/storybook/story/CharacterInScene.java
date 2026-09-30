package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterInScene(
        @JsonPropertyDescription("CHILD or COMPANION") String ref,
        @JsonPropertyDescription("The character's emotion on this page, in English, e.g. excited, shy, proud") String emotion
) {
}

package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterInScene(
        @JsonPropertyDescription("CHILD, COMPANION, or a tag such as SUPPORT_1 from the request") String ref,
        @JsonPropertyDescription("The character's emotion on this page, in English, e.g. excited, shy, proud") String emotion
) {
}

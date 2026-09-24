package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CharacterInScene(
        @JsonPropertyDescription("CHILD or COMPANION") String ref,
        @JsonPropertyDescription("The character's emotion on this page, in English, e.g. excited, shy, proud") String emotion
) {
}

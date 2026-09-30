package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoryBlueprintResponse(
        @JsonPropertyDescription("Conceptual title for the story") String titleConcept,
        @JsonPropertyDescription("Core narrative premise and emotional arc") String premise,
        @JsonPropertyDescription("Page-by-page narrative beats") List<BlueprintPageBeat> beats
) {
}

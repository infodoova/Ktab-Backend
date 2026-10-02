package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoryBlueprintResponse(
        @JsonAlias({"title", "titleAr", "title_ar", "storyTitle", "story_title"})
        @JsonPropertyDescription("Conceptual title for the story") String titleConcept,
        @JsonAlias({"summary", "synopsis", "logline", "arc"})
        @JsonPropertyDescription("Core narrative premise and emotional arc") String premise,
        @JsonAlias({"pages", "story_beats", "storyBeats", "page_beats", "pageBeats", "blueprint"})
        @JsonPropertyDescription("Page-by-page narrative beats") List<BlueprintPageBeat> beats
) implements com.doova.ktab.features.storybook.llm.ValidatedLlmResponse {
    @Override
    public java.util.List<String> problems() {
        if (beats == null || beats.isEmpty()) {
            return java.util.List.of("the blueprint has no beats");
        }
        if (beats.stream().anyMatch(b -> b == null || b.beat() == null || b.beat().isBlank())) {
            return java.util.List.of("a blueprint beat has no text");
        }
        return java.util.List.of();
    }

}

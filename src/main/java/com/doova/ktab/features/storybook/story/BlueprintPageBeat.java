package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintPageBeat(
        @JsonPropertyDescription("1-based page number") int pageNumber,
        @JsonPropertyDescription("Concise plot beat action moving the narrative forward") String beat,
        @JsonPropertyDescription("Emotional arc / mood of this beat (e.g. curious, nervous, excited, proud, calm)") String emotionalArc,
        @JsonPropertyDescription("Setting or visual environment anchor for this page") String sceneSetting,
        @JsonPropertyDescription("Characters appearing on this page") List<String> characters
) {
}

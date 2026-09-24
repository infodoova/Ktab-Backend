package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TextZone;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record PagePlan(
        @JsonPropertyDescription("1-based page number") int pageNumber,
        @JsonPropertyDescription("The Arabic text printed on this page") String textAr,
        @JsonPropertyDescription("English description of the illustration; characters named CHILD and COMPANION; no text in the picture") String sceneEn,
        List<CharacterInScene> characters,
        @JsonPropertyDescription("Which third of the picture is left calm and empty for the text") TextZone textZone
) {
    public PagePlan withText(String newText) {
        return new PagePlan(pageNumber, newText, sceneEn, characters, textZone);
    }
}

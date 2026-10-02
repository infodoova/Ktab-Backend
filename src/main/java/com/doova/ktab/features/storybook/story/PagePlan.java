package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TextZone;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PagePlan(
        @JsonAlias({"page_number", "page", "number", "pageIndex", "page_index"})
        @JsonPropertyDescription("1-based page number") int pageNumber,
        @JsonAlias({"text", "text_ar", "arabicText", "arabic_text", "content", "body", "story", "arabic", "text_arabic", "narration", "textAr"})
        @JsonPropertyDescription("The Arabic text printed on this page") String textAr,
        @JsonAlias({"scene", "scene_en", "illustration", "sceneDescription", "visualDescription", "scene_description", "sceneEn"})
        @JsonPropertyDescription("English description of the illustration; characters named CHILD and COMPANION; no text in the picture") String sceneEn,
        @JsonAlias({"cast", "character_list"})
        List<CharacterInScene> characters,
        @JsonAlias({"text_zone", "zone", "textPlacement", "placement", "textZone"})
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.doova.ktab.features.storybook.llm.LenientTextZoneDeserializer.class)
        @JsonPropertyDescription("Which third of the picture is left calm and empty for the text") TextZone textZone
) implements com.doova.ktab.features.storybook.llm.ValidatedLlmResponse {
    @Override
    public List<String> problems() {
        return textAr == null || textAr.isBlank() ? List.of("the page has no text") : List.of();
    }

    public PagePlan withText(String newText) {
        return new PagePlan(pageNumber, newText, sceneEn, characters, textZone);
    }

    public PagePlan normalized(int expectedPageNumber) {
        TextZone zone = (textZone != null) ? textZone : TextZone.BOTTOM;
        String scene = (sceneEn != null && !sceneEn.isBlank()) ? SceneText.withoutOutfit(sceneEn) : "Illustration showing CHILD in the garden";
        return new PagePlan(expectedPageNumber, textAr, scene, characters == null ? List.of() : characters, zone);
    }
}

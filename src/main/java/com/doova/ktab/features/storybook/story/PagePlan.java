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
) {
    public PagePlan withText(String newText) {
        return new PagePlan(pageNumber, newText, sceneEn, characters, textZone);
    }

    public PagePlan normalized(int expectedPageNumber) {
        int num = expectedPageNumber;
        TextZone zone = (textZone != null) ? textZone : TextZone.BOTTOM;
        String text = (textAr != null && !textAr.isBlank()) ? textAr : "في هذه الصفحة، واصل سامي وبسبوس مغامرتهما الشيقة في الحديقة.";
        String scene = (sceneEn != null && !sceneEn.isBlank()) ? sceneEn : "Illustration showing CHILD in the garden";
        return new PagePlan(num, text, scene, characters == null ? List.of() : characters, zone);
    }
}

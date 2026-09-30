package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TextZone;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoryPlanResponse(
        @JsonAlias({"title", "title_ar", "bookTitle", "book_title", "titleArabic", "arabicTitle", "storyTitle", "titleConcept"})
        @JsonPropertyDescription("Book title, 2 to 5 words, same language variety as the pages") String titleAr,
        @JsonAlias({"coverScene", "cover_scene", "cover_scene_en", "coverIllustration", "cover_illustration", "coverSceneDescription"})
        @JsonPropertyDescription("English description of the cover illustration; calm empty TOP third for the title") String coverSceneEn,
        @JsonAlias({"story_pages", "storyPages", "page_plans", "pagePlans"})
        List<PagePlan> pages
) {
    public StoryPlanResponse withPage(PagePlan replacement) {
        return new StoryPlanResponse(titleAr, coverSceneEn, pages.stream()
                .map(p -> p.pageNumber() == replacement.pageNumber() ? replacement : p)
                .toList());
    }

    public StoryPlanResponse normalized(String fallbackTitle, String fallbackCoverScene, int expectedPageCount) {
        String effTitle = (titleAr != null && !titleAr.isBlank()) ? titleAr : fallbackTitle;
        String effCover = (coverSceneEn != null && !coverSceneEn.isBlank()) ? coverSceneEn : fallbackCoverScene;
        List<PagePlan> current = (pages == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(pages);
        List<PagePlan> effPages = new java.util.ArrayList<>();
        for (int i = 0; i < expectedPageCount; i++) {
            if (i < current.size()) {
                effPages.add(current.get(i).normalized(i + 1));
            } else {
                effPages.add(new PagePlan(i + 1,
                        "وفي ختام المغامرة، عاد سامي وصديقه بسبوس مسرورين بالنجاح الكبير بعد يوم رائع.",
                        "Illustration showing CHILD and COMPANION smiling happily together in the school garden",
                        List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy")),
                        TextZone.BOTTOM));
            }
        }
        return new StoryPlanResponse(effTitle, effCover, effPages);
    }
}

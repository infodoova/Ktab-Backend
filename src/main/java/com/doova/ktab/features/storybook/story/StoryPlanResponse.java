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
) implements com.doova.ktab.features.storybook.llm.ValidatedLlmResponse {
    @Override
    public List<String> problems() {
        return pages == null || pages.isEmpty() ? List.of("the story plan has no pages") : List.of();
    }

    public StoryPlanResponse withTitle(String newTitle) {
        return new StoryPlanResponse(newTitle, coverSceneEn, pages);
    }

    public StoryPlanResponse withPage(PagePlan replacement) {
        return new StoryPlanResponse(titleAr, coverSceneEn, pages.stream()
                .map(p -> p.pageNumber() == replacement.pageNumber() ? replacement : p)
                .toList());
    }

    /** Fills only a missing title, cover scene, scene or text zone. Pages are never added, dropped or renumbered: a wrong count or numbering is the writer's to reject. */
    public StoryPlanResponse normalized(String fallbackTitle, String fallbackCoverScene) {
        String effTitle = (titleAr != null && !titleAr.isBlank()) ? titleAr : fallbackTitle;
        String effCover = (coverSceneEn != null && !coverSceneEn.isBlank()) ? SceneText.withoutOutfit(coverSceneEn) : fallbackCoverScene;
        List<PagePlan> effPages = pages == null ? null : pages.stream().map(p -> p.normalized(p.pageNumber())).toList();
        return new StoryPlanResponse(effTitle, effCover, effPages);
    }
}

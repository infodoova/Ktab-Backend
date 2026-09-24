package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record StoryPlanResponse(
        @JsonPropertyDescription("Book title, 2 to 5 words, same language variety as the pages") String titleAr,
        @JsonPropertyDescription("English description of the cover illustration; calm empty TOP third for the title") String coverSceneEn,
        List<PagePlan> pages
) {
    public StoryPlanResponse withPage(PagePlan replacement) {
        return new StoryPlanResponse(titleAr, coverSceneEn, pages.stream()
                .map(p -> p.pageNumber() == replacement.pageNumber() ? replacement : p)
                .toList());
    }
}

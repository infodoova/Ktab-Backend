package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryRequest;

/** Plain snapshot handed to handlers so no entity is touched outside a transaction. */
public record StoryContext(
        Long bookId,
        StorybookStatus status,
        StoryRequest request,
        StoryPlanResponse storedPlan,
        String characterBible,
        String storyBlueprint,
        String theme,
        String storyTone,
        String lesson,
        String storyIdea,
        java.util.List<String> thingsToAvoid,
        java.util.List<com.doova.ktab.features.storybook.story.SupportingCast> supporting
) {
    public StoryContext {
        supporting = supporting == null ? java.util.List.of() : java.util.List.copyOf(supporting);
    }

    public StoryContext(Long bookId, StorybookStatus status, StoryRequest request, StoryPlanResponse storedPlan,
                        String characterBible, String storyBlueprint, String theme, String storyTone, String lesson,
                        String storyIdea, java.util.List<String> thingsToAvoid) {
        this(bookId, status, request, storedPlan, characterBible, storyBlueprint, theme, storyTone, lesson, storyIdea, thingsToAvoid, null);
    }

    public StoryContext(Long bookId, StorybookStatus status, StoryRequest request, StoryPlanResponse storedPlan) {
        this(bookId, status, request, storedPlan, null, null, null, null, null, null, null, null);
    }

    public StoryContext withSupporting(java.util.List<com.doova.ktab.features.storybook.story.SupportingCast> cast) {
        return new StoryContext(bookId, status, request, storedPlan, characterBible, storyBlueprint, theme, storyTone, lesson,
                storyIdea, thingsToAvoid, cast);
    }
}

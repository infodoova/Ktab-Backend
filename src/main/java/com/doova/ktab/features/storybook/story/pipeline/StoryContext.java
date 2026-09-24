package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryRequest;

/** Plain snapshot handed to handlers so no entity is touched outside a transaction. */
public record StoryContext(Long bookId, StorybookStatus status, StoryRequest request, StoryPlanResponse storedPlan) {
}

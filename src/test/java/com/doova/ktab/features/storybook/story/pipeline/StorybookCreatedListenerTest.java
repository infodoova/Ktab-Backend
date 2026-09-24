package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorybookCreatedListenerTest {

    @Test
    void enqueuesTheFirstStoryPlan() {
        JobEnqueuer enqueuer = mock(JobEnqueuer.class);
        new StorybookCreatedListener(enqueuer).onCreated(new StorybookCreatedEvent(42L));
        verify(enqueuer).enqueue(42L, JobStep.STORY_PLAN, -1, 0);
    }
}

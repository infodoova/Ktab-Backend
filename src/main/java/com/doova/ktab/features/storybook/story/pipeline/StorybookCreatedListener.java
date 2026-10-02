package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronous on purpose: runs inside the creating transaction, so book and first job commit together. */
@Component
@RequiredArgsConstructor
public class StorybookCreatedListener {

    private final JobEnqueuer enqueuer;

    @EventListener
    public void onCreated(StorybookCreatedEvent event) {
        enqueuer.enqueue(event.storybookId(), JobStep.CHARACTER_BIBLE, -1, 0);
    }
}

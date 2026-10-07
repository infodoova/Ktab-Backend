package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spec: the photo is deleted right after the character sheet is made (D7). */
@Component
@RequiredArgsConstructor
public class PhotoPurgeHandler implements StepHandler {

    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;

    @Override
    public JobStep step() {
        return JobStep.PURGE_PHOTO;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        for (String key : persistence.allPhotoKeys(job.getStorybookId())) {
            store.delete(key);
        }
        persistence.markPhotoPurged(job.getStorybookId());
        return StepOutcome.success();
    }
}

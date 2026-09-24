package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class PhotoPurgeHandlerTest {

    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final PhotoPurgeHandler handler = new PhotoPurgeHandler(store, persistence);

    private static StorybookJob job() {
        StorybookJob j = new StorybookJob();
        j.setStorybookId(9L);
        j.setStep(JobStep.PURGE_PHOTO);
        return j;
    }

    @Test
    void deletesThenMarks() {
        when(persistence.photoKey(9L)).thenReturn("storybook/9/photo/source.enc");
        handler.handle(job());
        var order = inOrder(store, persistence);
        order.verify(store).delete("storybook/9/photo/source.enc");
        order.verify(persistence).markPhotoPurged(9L);
    }

    @Test
    void nothingToPurgeIsFine() {
        when(persistence.photoKey(9L)).thenReturn(null);
        handler.handle(job());
        verifyNoInteractions(store);
    }
}

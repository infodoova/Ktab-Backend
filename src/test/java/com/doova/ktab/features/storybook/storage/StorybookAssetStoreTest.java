package com.doova.ktab.features.storybook.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorybookAssetStoreTest {

    private final S3Client s3 = mock(S3Client.class);
    private final StorybookAssetStore store = new StorybookAssetStore(s3, "ktab-bucket");

    @Test
    void putWritesToTheExactKey() {
        store.put("storybook/1/pages/1/g1.png", new byte[]{1, 2, 3}, "image/png");

        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(req.capture(), any(RequestBody.class));
        assertThat(req.getValue().bucket()).isEqualTo("ktab-bucket");
        assertThat(req.getValue().key()).isEqualTo("storybook/1/pages/1/g1.png");
        assertThat(req.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void existsIsFalseOn404() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());
        assertThat(store.exists("missing")).isFalse();
    }

    @Test
    void existsIsTrueWhenHeadSucceeds() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        assertThat(store.exists("present")).isTrue();
    }
}

package com.doova.ktab.features.imagegen.service;

import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.service.impl.CloudflareImageStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudflareImageStorageServiceImplTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private ImageGenProperties properties;
    private CloudflareImageStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        properties = new ImageGenProperties();
        properties.setStoragePrefix("books");
        properties.setPresignedExpirationMinutes(120);

        storageService = new CloudflareImageStorageServiceImpl(
                s3Client,
                s3Presigner,
                properties,
                "ktab-bucket",
                "https://media.ktab.ai",
                "test-account-id",
                "PUBLIC_READ"
        );
    }

    @Test
    @DisplayName("uploadImage should invoke S3Client putObject with immutable cache-control")
    void uploadImage_validBytes_uploadsToR2WithImmutableCacheControl() {
        UUID imageId = UUID.randomUUID();
        byte[] payload = "fake-png-bytes".getBytes();

        String storageKey = storageService.uploadImage(42L, 100L, imageId, payload, "image/png");

        assertNotNull(storageKey);
        assertEquals("books/42/generated-images/100/" + imageId + ".png", storageKey);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.builder().build().getClass());
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertEquals("ktab-bucket", captured.bucket());
        assertEquals(storageKey, captured.key());
        assertEquals("image/png", captured.contentType());
        assertEquals("public, max-age=31536000, immutable", captured.cacheControl());
    }

    @Test
    @DisplayName("uploadImage should reject null or empty byte arrays")
    void uploadImage_emptyBytes_throwsIllegalArgumentException() {
        UUID imageId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                storageService.uploadImage(42L, 100L, imageId, null, "image/png"));

        assertThrows(IllegalArgumentException.class, () ->
                storageService.uploadImage(42L, 100L, imageId, new byte[0], "image/png"));
    }

    @Test
    @DisplayName("resolveImageUrl should return public CDN URL when strategy is PUBLIC_READ and publicUrl is configured")
    void resolveImageUrl_publicReadStrategy_returnsCdnUrl() {
        String key = "books/42/generated-images/100/sample.png";
        String resolved = storageService.resolveImageUrl(key);

        assertEquals("https://media.ktab.ai/books/42/generated-images/100/sample.png", resolved);
    }

    @Test
    @DisplayName("resolveImageUrl should return presigned URL when strategy is SIGNED")
    void resolveImageUrl_signedStrategy_returnsPresignedUrl() throws Exception {
        CloudflareImageStorageServiceImpl signedStorage = new CloudflareImageStorageServiceImpl(
                s3Client,
                s3Presigner,
                properties,
                "ktab-bucket",
                "",
                "test-account-id",
                "SIGNED"
        );

        PresignedGetObjectRequest presignedMock = mock(PresignedGetObjectRequest.class);
        when(presignedMock.url()).thenReturn(URI.create("https://ktab-bucket.r2.cloudflarestorage.com/test.png?sig=123").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedMock);

        String resolved = signedStorage.resolveImageUrl("books/42/generated-images/100/test.png");

        assertNotNull(resolved);
        assertTrue(resolved.contains("ktab-bucket.r2.cloudflarestorage.com"));
    }

    @Test
    @DisplayName("deleteImage should invoke S3Client deleteObject")
    void deleteImage_validKey_callsS3Delete() {
        storageService.deleteImage("books/42/generated-images/100/sample.png");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals("ktab-bucket", requestCaptor.getValue().bucket());
        assertEquals("books/42/generated-images/100/sample.png", requestCaptor.getValue().key());
    }
}

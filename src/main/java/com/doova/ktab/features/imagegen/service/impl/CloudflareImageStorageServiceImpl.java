package com.doova.ktab.features.imagegen.service.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.S3UploadException;
import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.service.CloudflareImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class CloudflareImageStorageServiceImpl implements CloudflareImageStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageGenProperties properties;

    private final String bucketName;
    private final String publicUrl;
    private final String accountId;
    private final String urlStrategy;

    public CloudflareImageStorageServiceImpl(
            S3Client s3Client,
            S3Presigner s3Presigner,
            ImageGenProperties properties,
            @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}") String bucketName,
            @Value("${cloudflare.r2.publicUrl:}") String publicUrl,
            @Value("${cloudflare.r2.accountId:${aws.s3.accountId:}}") String accountId,
            @Value("${cloudflare.r2.url-strategy:SIGNED}") String urlStrategy
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.bucketName = bucketName;
        this.publicUrl = publicUrl;
        this.accountId = accountId;
        this.urlStrategy = urlStrategy;
    }

    @Override
    public String uploadImage(Long bookId, Long userId, UUID imageId, byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Cannot upload empty image payload");
        }

        String effectiveMime = (mimeType != null && !mimeType.isBlank()) ? mimeType : "image/png";
        String storageKey = String.format(
                "%s/%d/generated-images/%d/%s.png",
                properties.getStoragePrefix(),
                bookId,
                userId,
                imageId
        );

        log.info("Uploading generated image to Cloudflare R2 bucket: {}, key: {}, size: {} bytes",
                bucketName, storageKey, imageBytes.length);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(effectiveMime)
                    .contentLength((long) imageBytes.length)
                    // Edge caching: immutable generated images can be cached permanently by Cloudflare Edge & browsers
                    .cacheControl("public, max-age=31536000, immutable")
                    .metadata(Map.of(
                            "book-id", String.valueOf(bookId),
                            "user-id", String.valueOf(userId),
                            "image-id", imageId.toString()
                    ))
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));
            log.info("Successfully uploaded image {} to Cloudflare R2", storageKey);
            return storageKey;

        } catch (Exception e) {
            log.error("Failed to upload image {} to Cloudflare R2: {}", storageKey, e.getMessage(), e);
            throw new S3UploadException(ApiMessageKey.S3_UPLOAD_UNEXPECTED_ERROR, e);
        }
    }

    @Override
    public String resolveImageUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }

        if ("PUBLIC_READ".equalsIgnoreCase(urlStrategy) && publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.replaceAll("/+$", "") + "/" + storageKey;
        }

        try {
            Duration duration = Duration.ofMinutes(properties.getPresignedExpirationMinutes());
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();

        } catch (Exception e) {
            log.warn("Failed to generate presigned URL for key {}: {}", storageKey, e.getMessage());
            if (publicUrl != null && !publicUrl.isBlank()) {
                return publicUrl.replaceAll("/+$", "") + "/" + storageKey;
            }
            if (accountId != null && !accountId.isBlank()) {
                return String.format("https://%s.%s.r2.cloudflarestorage.com/%s", bucketName, accountId, storageKey);
            }
            return storageKey;
        }
    }

    @Override
    public void deleteImage(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        log.info("Deleting image from Cloudflare R2: bucket={}, key={}", bucketName, storageKey);
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Successfully deleted image {} from Cloudflare R2", storageKey);
        } catch (Exception e) {
            log.error("Failed to delete image {} from Cloudflare R2: {}", storageKey, e.getMessage(), e);
        }
    }
}

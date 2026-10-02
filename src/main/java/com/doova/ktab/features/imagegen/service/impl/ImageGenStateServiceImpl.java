package com.doova.ktab.features.imagegen.service.impl;

import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.doova.ktab.features.imagegen.model.GeneratedImage;
import com.doova.ktab.features.imagegen.repository.GeneratedImageRepository;
import com.doova.ktab.features.imagegen.service.ImageGenStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenStateServiceImpl implements ImageGenStateService {

    private final GeneratedImageRepository repository;

    @Override
    @Transactional
    public void markProcessing(UUID imageId) {
        repository.findById(imageId).ifPresent(entity -> {
            entity.setStatus(ImageGenerationStatus.PROCESSING);
            log.debug("Marked image {} as PROCESSING", imageId);
        });
    }

    @Override
    @Transactional
    public void markCompleted(UUID imageId, String storageKey, String publicUrl, String mimeType, long fileSizeBytes) {
        repository.findById(imageId).ifPresent(entity -> {
            entity.setStatus(ImageGenerationStatus.COMPLETED);
            entity.setStorageKey(storageKey);
            entity.setCfPublicUrl(publicUrl);
            entity.setMimeType(mimeType);
            entity.setFileSizeBytes(fileSizeBytes);
            entity.setCompletedAt(Instant.now());
            entity.setFailureReason(null);
            log.info("Marked image {} as COMPLETED (key: {}, size: {} bytes)", imageId, storageKey, fileSizeBytes);
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID imageId, String failureReason) {
        repository.findById(imageId).ifPresent(entity -> {
            entity.setStatus(ImageGenerationStatus.FAILED);
            entity.setFailureReason(failureReason);
            log.warn("Marked image {} as FAILED: {}", imageId, failureReason);
        });
    }

    @Override
    @Transactional
    public void incrementRetry(UUID imageId, int nextRetryCount) {
        repository.findById(imageId).ifPresent(entity -> {
            entity.setRetryCount(nextRetryCount);
            entity.setStatus(ImageGenerationStatus.QUEUED);
            log.info("Incremented retry count for image {} to {}", imageId, nextRetryCount);
        });
    }
}

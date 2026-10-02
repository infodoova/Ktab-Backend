package com.doova.ktab.features.imagegen.service;

import java.util.UUID;

/**
 * Isolated transactional boundary for updating GeneratedImage lifecycle states.
 * Guarantees database transactions are never kept open during external AI or storage network calls.
 */
public interface ImageGenStateService {

    void markProcessing(UUID imageId);

    void markCompleted(UUID imageId, String storageKey, String publicUrl, String mimeType, long fileSizeBytes);

    void markFailed(UUID imageId, String failureReason);

    void incrementRetry(UUID imageId, int nextRetryCount);
}

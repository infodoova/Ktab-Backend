package com.doova.ktab.features.imagegen.event.model;

import java.util.UUID;

/**
 * Domain event dispatched when an image generation task is accepted and queued.
 */
public record ImageGenerationRequestedEvent(
        UUID imageId,
        Long bookId,
        Long userId,
        String prompt,
        String aspectRatio,
        int retryCount,
        String bookTitle,
        String authorName
) {
    public ImageGenerationRequestedEvent(
            UUID imageId,
            Long bookId,
            Long userId,
            String prompt,
            String aspectRatio,
            int retryCount
    ) {
        this(imageId, bookId, userId, prompt, aspectRatio, retryCount, null, null);
    }
}

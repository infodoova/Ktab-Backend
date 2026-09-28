package com.doova.ktab.features.imagegen.event.listener;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.event.model.ImageGenerationRequestedEvent;
import com.doova.ktab.features.imagegen.exception.ImageGenerationException;
import com.doova.ktab.features.imagegen.service.BookVisualSearchService;
import com.doova.ktab.features.imagegen.service.CloudflareImageStorageService;
import com.doova.ktab.features.imagegen.service.ImageGenStateService;
import com.doova.ktab.features.imagegen.service.ImageModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenerationListener {

    private final ImageModelClient imageModelClient;
    private final CloudflareImageStorageService storageService;
    private final ImageGenStateService stateService;
    private final ImageGenProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageSource messageSource;
    private final BookVisualSearchService visualSearchService;

    @Async
    @EventListener
    public void onImageRequested(ImageGenerationRequestedEvent event) {
        UUID imageId = event.imageId();
        log.info("Processing async image generation for book {}, imageId: {}, attempt: {}",
                event.bookId(), imageId, event.retryCount() + 1);

        try {
            // 1. Transition state to PROCESSING
            stateService.markProcessing(imageId);

            String effectivePrompt = event.prompt();

            // 2. Live Google Web Search grounding
            if (event.bookTitle() != null && !event.bookTitle().isBlank()) {
                String webLore = visualSearchService.searchBookVisualLore(event.bookTitle(), event.authorName());
                if (webLore != null && !webLore.isBlank()) {
                    effectivePrompt = effectivePrompt + """

                            LIVE GOOGLE WEB SEARCH GROUNDING:
                            %s
                            """.formatted(webLore);
                }
            }

            // 3. Call AI Generation (outside DB transaction)
            ImageModelClient.ImagePayload payload = imageModelClient.generateImage(effectivePrompt, event.aspectRatio());

            // 4. Upload binary to Cloudflare R2 (outside DB transaction)
            String storageKey = storageService.uploadImage(
                    event.bookId(),
                    event.userId(),
                    imageId,
                    payload.bytes(),
                    payload.mimeType()
            );

            // 5. Resolve delivery URL (Cloudflare CDN or Presigned)
            String resolvedUrl = storageService.resolveImageUrl(storageKey);

            // 6. Complete state
            stateService.markCompleted(
                    imageId,
                    storageKey,
                    resolvedUrl,
                    payload.mimeType(),
                    payload.bytes().length
            );

            log.info("Image generation workflow successfully completed for imageId: {}", imageId);

        } catch (ImageGenerationException e) {
            handleFailure(event, e.isRetryable(), e.getMessage(), e);
        } catch (Exception e) {
            handleFailure(event, false, e.getMessage(), e);
        }
    }

    private void handleFailure(ImageGenerationRequestedEvent event, boolean retryable, String errorReason, Exception ex) {
        UUID imageId = event.imageId();
        int currentRetries = event.retryCount();

        if (retryable && currentRetries < properties.getMaxRetries()) {
            int nextRetry = currentRetries + 1;
            long backoffMs = (long) (Math.pow(2, currentRetries) * 2000L);
            log.warn("Image generation failed (retryable). Scheduling retry {}/{} after {} ms for imageId: {}. Reason: {}",
                    nextRetry, properties.getMaxRetries(), backoffMs, imageId, errorReason);

            stateService.incrementRetry(imageId, nextRetry);

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                String localizedError = ApiMessageKey.IMAGE_GEN_FAILED.getMessage(messageSource);
                stateService.markFailed(imageId, localizedError);
                return;
            }

            eventPublisher.publishEvent(new ImageGenerationRequestedEvent(
                    imageId,
                    event.bookId(),
                    event.userId(),
                    event.prompt(),
                    event.aspectRatio(),
                    nextRetry,
                    event.bookTitle(),
                    event.authorName()
            ));
        } else {
            log.error("Image generation permanently failed for imageId {}: {}", imageId, errorReason, ex);
            String localizedError = ApiMessageKey.IMAGE_GEN_FAILED.getMessage(messageSource);
            stateService.markFailed(imageId, localizedError);
        }
    }
}

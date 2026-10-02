package com.doova.ktab.features.imagegen.service.impl;

import com.doova.ktab.config.ai.GlobalAiProperties;
import com.doova.ktab.features.imagegen.config.ImageGenProperties;
import com.doova.ktab.features.imagegen.exception.ImageGenerationException;
import com.doova.ktab.features.imagegen.service.ImageModelClient;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiImageModelClient implements ImageModelClient {

    private static final List<String> HARM_CATEGORIES = List.of(
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_HARASSMENT"
    );

    private final Client vertexGenAiClient;
    private final ImageGenProperties properties;
    private final GlobalAiProperties globalAi;

    @Override
    public ImagePayload generateImage(String prompt, String aspectRatio) {
        String primary = globalAi.getImage().getPrimary();
        String fallback = globalAi.getImage().getFallback();
        try {
            return doGenerate(primary, prompt, aspectRatio);
        } catch (ImageGenerationException ex) {
            if (ex.isRetryable()) {
                log.warn("[GeminiImageModelClient] Primary model '{}' failed ({}), retrying with fallback '{}'",
                        primary, ex.getMessage(), fallback);
                return doGenerate(fallback, prompt, aspectRatio);
            }
            throw ex;
        }
    }

    private ImagePayload doGenerate(String model, String prompt, String aspectRatio) {
        log.info("[GeminiImageModelClient] Generating image via model={}, aspectRatio={}", model, aspectRatio);

        List<SafetySetting> safetySettings = new ArrayList<>();
        for (String category : HARM_CATEGORIES) {
            safetySettings.add(SafetySetting.builder()
                    .category(category)
                    .threshold("BLOCK_MEDIUM_AND_ABOVE")
                    .build());
        }

        ImageConfig.Builder imageConfigBuilder = ImageConfig.builder();
        if (aspectRatio != null && !aspectRatio.isBlank()) {
            imageConfigBuilder.aspectRatio(aspectRatio);
        }
        if (properties.getImageSize() != null && !properties.getImageSize().isBlank()) {
            imageConfigBuilder.imageSize(properties.getImageSize());
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(imageConfigBuilder.build())
                .safetySettings(safetySettings)
                .candidateCount(1)
                .build();

        Content content = Content.builder()
                .role("user")
                .parts(List.of(Part.fromText(prompt)))
                .build();

        GenerateContentResponse response;
        try {
            response = vertexGenAiClient.models.generateContent(model, List.of(content), config);
        } catch (ApiException e) {
            log.error("[GeminiImageModelClient] Gemini API error (HTTP {}): {}", e.code(), e.getMessage());
            boolean retryable = e.code() == 429 || e.code() >= 500;
            throw new ImageGenerationException("Gemini API error: HTTP " + e.code(), retryable, e);
        } catch (Exception e) {
            log.error("[GeminiImageModelClient] Unexpected error: {}", e.getMessage(), e);
            throw new ImageGenerationException("Image generation failed unexpectedly", true, e);
        }

        List<Part> parts = null;
        try {
            parts = response.parts();
        } catch (IllegalArgumentException e) {
            log.warn("Gemini response finished without parts or with special reason: {}", e.getMessage());
        }

        if (parts != null) {
            for (Part part : parts) {
                if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) {
                    byte[] bytes = part.inlineData().get().data().get();
                    String mime = part.inlineData().get().mimeType().orElse("image/png");
                    log.info("Extracted generated image bytes ({} bytes, mime: {})", bytes.length, mime);
                    return new ImagePayload(bytes, mime, model);
                }
            }
        }

        String finishReason = "unknown";
        String refusalText = null;
        try {
            if (response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
                Candidate candidate = response.candidates().get().get(0);
                finishReason = candidate.finishReason().map(Object::toString).orElse("unknown");
                if (candidate.content().isPresent() && candidate.content().get().parts().isPresent()) {
                    for (Part p : candidate.content().get().parts().get()) {
                        if (p.text().isPresent() && !p.text().get().isBlank()) {
                            refusalText = p.text().get();
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        log.warn("Gemini returned no image parts. FinishReason: {}, RefusalText: {}", finishReason, refusalText);
        boolean retryable = !"SAFETY".equalsIgnoreCase(finishReason) && !"NO_IMAGE".equalsIgnoreCase(finishReason);
        String errorMsg = (refusalText != null && !refusalText.isBlank())
                ? "Gemini declined generation: " + refusalText
                : "No image generated by Gemini (finishReason=" + finishReason + ")";
        throw new ImageGenerationException(errorMsg, retryable);
    }
}

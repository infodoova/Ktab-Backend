package com.doova.ktab.features.storybook.image;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Nano Banana (Gemini image) on Vertex AI through Ktab's existing {@code vertexGenAiClient}.
 * Deliberately NOT built on features.story.image.VertexImageClient: that class turns every
 * safety filter OFF, which is unacceptable for children's books.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiImageProvider implements ImageProvider {

    private static final List<String> HARM_CATEGORIES = List.of(
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_HARASSMENT");

    private final Client vertexGenAiClient;
    private final StorybookProperties properties;

    @Override
    public ImageResult generate(ImageRequest request) {
        long started = System.nanoTime();
        GenerateContentResponse response;
        try {
            response = vertexGenAiClient.models.generateContent(
                    request.model(), List.of(buildContent(request)), buildConfig(properties.getImage()));
        } catch (ApiException e) {
            boolean retryable = e.code() == 429 || e.code() >= 500;
            throw new ImageGenerationException("Image generation failed with HTTP " + e.code(), retryable, e);
        } catch (RuntimeException e) {
            throw new ImageGenerationException("Image generation failed", true, e);
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        List<Part> parts = response.parts();
        if (parts != null) {
            for (Part part : parts) {
                if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) {
                    String mime = part.inlineData().get().mimeType().orElse("image/png");
                    return new ImageResult(part.inlineData().get().data().get(), mime, request.model(), latencyMs);
                }
            }
        }
        // Usually a safety block. Retrying is worthwhile: a harmless children's scene is often
        // accepted on a second attempt, and the retry count is bounded by the caller.
        String finish = "unknown";
        try {
            if (response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
                finish = response.candidates().get().get(0).finishReason().map(Object::toString).orElse("unknown");
            }
        } catch (Exception ignored) {}
        throw new ImageGenerationException("No image returned (finishReason=" + finish + ")", true, null);
    }

    static GenerateContentConfig buildConfig(StorybookProperties.Image cfg) {
        List<SafetySetting> safety = new ArrayList<>();
        for (String category : HARM_CATEGORIES) {
            safety.add(SafetySetting.builder().category(category).threshold("BLOCK_LOW_AND_ABOVE").build());
        }
        return GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(cfg.getAspectRatio())
                        .imageSize(cfg.getImageSize())
                        .build())
                .candidateCount(1)
                .safetySettings(safety)
                .build();
    }

    static Content buildContent(ImageRequest request) {
        List<Part> parts = new ArrayList<>();
        for (ReferenceImage reference : request.references()) {
            parts.add(Part.fromBytes(reference.bytes(), reference.mimeType()));
        }
        parts.add(Part.fromText(request.prompt()));
        return Content.builder().role("user").parts(parts).build();
    }
}

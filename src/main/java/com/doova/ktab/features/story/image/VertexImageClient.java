package com.doova.ktab.features.story.image;

import com.doova.ktab.config.ai.GlobalAiProperties;
import com.google.genai.Client;
import com.google.genai.types.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VertexImageClient {

    private final Client vertexGenAiClient;
    private final GlobalAiProperties globalAi;

    /**
     * Generates an image via Gemini, automatically falling back to the configured
     * fallback model if the primary fails.
     */
    public byte[] generateImage(String prompt, byte[] inputImageBytes, String mimeType) {
        String primary = globalAi.getImage().getPrimary();
        String fallback = globalAi.getImage().getFallback();

        try {
            return doGenerate(primary, prompt, inputImageBytes, mimeType);
        } catch (Exception primaryEx) {
            log.warn("[VertexImageClient] Primary model '{}' failed ({}), trying fallback '{}'",
                    primary, primaryEx.getMessage(), fallback);
            try {
                return doGenerate(fallback, prompt, inputImageBytes, mimeType);
            } catch (Exception fallbackEx) {
                log.error("[VertexImageClient] Fallback model '{}' also failed", fallback, fallbackEx);
                throw new RuntimeException("Gemini image generation failed on both primary and fallback", fallbackEx);
            }
        }
    }

    private byte[] doGenerate(String model, String prompt, byte[] inputImageBytes, String mimeType) {
        log.info("[VertexImageClient] Generating image with model={}", model);

        Part textPart = Part.fromText(prompt);

        List<Part> parts;
        if (inputImageBytes != null && inputImageBytes.length > 0) {
            Part imagePart = Part.fromBytes(inputImageBytes, mimeType != null ? mimeType : "image/png");
            parts = List.of(textPart, imagePart);
        } else {
            parts = List.of(textPart);
        }

        Content content = Content.builder().role("user").parts(parts).build();

        List<SafetySetting> safetySettings = List.of(
                SafetySetting.builder().category("HARM_CATEGORY_HATE_SPEECH").threshold("OFF").build(),
                SafetySetting.builder().category("HARM_CATEGORY_DANGEROUS_CONTENT").threshold("OFF").build(),
                SafetySetting.builder().category("HARM_CATEGORY_SEXUALLY_EXPLICIT").threshold("OFF").build(),
                SafetySetting.builder().category("HARM_CATEGORY_HARASSMENT").threshold("OFF").build()
        );

        String imageSize = globalAi.getImage().getImageSize() != null ? globalAi.getImage().getImageSize() : "1K";
        ImageConfig imageConfig = ImageConfig.builder()
                .imageSize(imageSize)
                .aspectRatio("1:1")
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(imageConfig)
                .temperature(1.0f)
                .topP(0.95f)
                .safetySettings(safetySettings)
                .build();

        GenerateContentResponse response = vertexGenAiClient.models.generateContent(model, List.of(content), config);

        return extractImage(response).orElseThrow(
                () -> new IllegalStateException("No image data extracted from Gemini response (model=" + model + ")")
        );
    }

    private Optional<byte[]> extractImage(GenerateContentResponse response) {
        List<Part> parts = response.parts();
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("No parts returned by Gemini");
        }
        for (Part part : parts) {
            if (part.inlineData().isPresent()) {
                return part.inlineData().get().data();
            }
        }
        throw new IllegalStateException("No image data found in Gemini response parts");
    }
}

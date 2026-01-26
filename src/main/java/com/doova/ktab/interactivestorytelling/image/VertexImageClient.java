package com.doova.ktab.interactivestorytelling.image;

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

    public byte[] generateImage(String prompt, byte[] inputImageBytes, String mimeType) {

        try {
            // ---- Build multimodal parts
            Part textPart = Part.fromText(prompt);

            List<Part> parts;
            if (inputImageBytes != null && inputImageBytes.length > 0) {
                Part imagePart = Part.fromBytes(inputImageBytes, mimeType != null ? mimeType : "image/png");
                parts = List.of(textPart, imagePart);
            } else {
                parts = List.of(textPart);
            }

            Content content = Content.builder().role("user").parts(parts).build();

            // ---- Safety settings
            List<SafetySetting> safetySettings = List.of(SafetySetting.builder().category("HARM_CATEGORY_HATE_SPEECH").threshold("OFF").build(), SafetySetting.builder().category("HARM_CATEGORY_DANGEROUS_CONTENT").threshold("OFF").build(), SafetySetting.builder().category("HARM_CATEGORY_SEXUALLY_EXPLICIT").threshold("OFF").build(), SafetySetting.builder().category("HARM_CATEGORY_HARASSMENT").threshold("OFF").build());

            GenerateContentConfig config = GenerateContentConfig.builder().responseModalities(List.of("IMAGE")).temperature(1.0f).topP(0.95f).mediaResolution(MediaResolution.Known.MEDIA_RESOLUTION_HIGH).safetySettings(safetySettings).build();

            //          String model = "gemini-3-pro-image-preview";
            String model = "gemini-2.5-flash-image";
            GenerateContentResponse response = vertexGenAiClient.models.generateContent(model, List.of(content), config);

            Optional<byte[]> aiImageBytes = extractImage(response);

            if (aiImageBytes.isPresent()) {
                return aiImageBytes.get();
            }

            throw new IllegalStateException("No image data extracted from Gemini response");

        } catch (Exception e) {
            log.error("Gemini multimodal image generation failed", e);
            throw new RuntimeException("Gemini multimodal image generation failed", e);
        }
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

        throw new IllegalStateException("No image data found in response");
    }


}

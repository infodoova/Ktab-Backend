package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.enums.LlmPurpose;

import java.util.List;
import java.util.Objects;

public record LlmRequest<T>(
        LlmPurpose purpose,
        String system,
        String user,
        List<LlmImage> images,
        Class<T> responseType,
        int maxTokens
) {
    public LlmRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(responseType, "responseType");
        if (system == null || system.isBlank() || user == null || user.isBlank()) {
            throw new IllegalArgumentException("system and user text are required");
        }
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static <T> LlmRequest<T> of(LlmPurpose purpose, String system, String user, Class<T> responseType) {
        return new LlmRequest<>(purpose, system, user, List.of(), responseType, 16000);
    }

    public LlmRequest<T> withImages(List<LlmImage> newImages) {
        return new LlmRequest<>(purpose, system, user, newImages, responseType, maxTokens);
    }
}

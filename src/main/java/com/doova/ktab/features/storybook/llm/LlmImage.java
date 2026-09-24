package com.doova.ktab.features.storybook.llm;

import java.util.Objects;
import java.util.Set;

public record LlmImage(byte[] bytes, String mediaType) {

    private static final Set<String> SUPPORTED = Set.of("image/png", "image/jpeg");

    public LlmImage {
        Objects.requireNonNull(bytes, "bytes");
        if (!SUPPORTED.contains(mediaType)) {
            throw new IllegalArgumentException("Unsupported image media type: " + mediaType);
        }
    }
}

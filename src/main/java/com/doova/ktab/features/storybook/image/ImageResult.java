package com.doova.ktab.features.storybook.image;

public record ImageResult(byte[] bytes, String mimeType, String model, long latencyMs) {
}

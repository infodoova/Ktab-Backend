package com.doova.ktab.features.storybook.image;

import java.util.Objects;

public record ReferenceImage(byte[] bytes, String mimeType) {
    public ReferenceImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
    }
}

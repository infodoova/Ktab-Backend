package com.doova.ktab.features.storybook.image;

import java.util.List;
import java.util.Objects;

public record ImageRequest(String model, String prompt, List<ReferenceImage> references) {

    /** Nano Banana Pro accepts up to 5 references; Nano Banana 2 up to 4 (checked by the caller). */
    public static final int MAX_REFERENCES = 5;

    public ImageRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(prompt, "prompt");
        references = references == null ? List.of() : List.copyOf(references);
        if (references.size() > MAX_REFERENCES) {
            throw new IllegalArgumentException("At most " + MAX_REFERENCES + " reference images");
        }
    }
}

package com.doova.ktab.features.trailer.endcard;

import java.nio.file.Path;

/** What the end card shows; {@code coverOrNull} is absent for books without a cover, {@code author} may be blank. */
public record EndCardSpec(String title, String author, Path coverOrNull, Path logo) {

    boolean hasCover() {
        return coverOrNull != null;
    }

    boolean hasAuthor() {
        return author != null && !author.isBlank();
    }
}

package com.doova.ktab.features.imagegen.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Summary of a book having generated illustrations with image count")
public record ReaderBookImageSummary(

        @Schema(description = "Book ID", example = "42")
        Long bookId,

        @Schema(description = "Book title", example = "The Desert Citadel")
        String bookTitle,

        @Schema(description = "Total number of generated images for this book", example = "7")
        long imageCount
) {
}

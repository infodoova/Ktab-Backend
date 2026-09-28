package com.doova.ktab.features.imagegen.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Group of generated images separated by their parent book")
public record BookImageGroupResponse(

        @Schema(description = "Book ID", example = "42")
        Long bookId,

        @Schema(description = "Book title", example = "The Desert Citadel")
        String bookTitle,

        @Schema(description = "Total number of images in this book group", example = "4")
        int totalImages,

        @Schema(description = "List of generated illustrations for this book")
        List<GenerateImageResponse> images
) {
}

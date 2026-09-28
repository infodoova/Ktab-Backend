package com.doova.ktab.features.imagegen.dto.response;

import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.doova.ktab.features.imagegen.enums.ImageTheme;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Detailed representation of a generated book illustration")
public record GenerateImageResponse(

        @Schema(description = "Unique image ID")
        UUID imageId,

        @Schema(description = "Associated book ID")
        Long bookId,

        @Schema(description = "Associated book title", example = "The Desert Citadel")
        String bookTitle,

        @Schema(description = "User's creative context")
        String context,

        @Schema(description = "Aesthetic theme")
        ImageTheme theme,

        @Schema(description = "Aspect ratio format")
        String aspectRatio,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Style notes")
        String styleNotes,

        @Schema(description = "Resolved Cloudflare CDN or presigned image URL")
        String imageUrl,

        @Schema(description = "Generation status")
        ImageGenerationStatus status,

        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Completion timestamp")
        Instant completedAt
) {
}

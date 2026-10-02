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

        @Schema(description = "Aesthetic theme localized in Arabic", example = "فانتازيا ملحمية")
        String theme,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Technical enum key for aesthetic theme", example = "FANTASY_ART")
        String themeKey,

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
    /**
     * Backward-compatible constructor for callers or tests passing ImageTheme enum directly.
     */
    public GenerateImageResponse(
            UUID imageId,
            Long bookId,
            String bookTitle,
            String context,
            ImageTheme theme,
            String aspectRatio,
            String styleNotes,
            String imageUrl,
            ImageGenerationStatus status,
            LocalDateTime createdAt,
            Instant completedAt
    ) {
        this(
                imageId,
                bookId,
                bookTitle,
                context,
                theme != null ? theme.getArabicName() : null,
                theme != null ? theme.name() : null,
                aspectRatio,
                styleNotes,
                imageUrl,
                status,
                createdAt,
                completedAt
        );
    }
}

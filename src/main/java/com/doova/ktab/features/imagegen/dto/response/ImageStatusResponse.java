package com.doova.ktab.features.imagegen.dto.response;

import com.doova.ktab.features.imagegen.enums.ImageGenerationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Status tracking response for an image generation job")
public record ImageStatusResponse(

        @Schema(description = "Unique identifier of the generated image record")
        UUID imageId,

        @Schema(description = "Target book ID")
        Long bookId,

        @Schema(description = "Current lifecycle status: QUEUED, PROCESSING, COMPLETED, FAILED")
        ImageGenerationStatus status,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Resolved image URL (Cloudflare CDN or presigned), present once COMPLETED")
        String imageUrl,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Error reason if generation failed")
        String failureReason,

        @Schema(description = "Timestamp when the request was accepted")
        LocalDateTime createdAt,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Timestamp when generation and Cloudflare upload completed")
        Instant completedAt
) {
}

package com.doova.ktab.features.storybook.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Update payload for an individual page's text or visual scene description")
public record EditPageRequest(
        @Schema(description = "0-based page index (0 is cover, 1..N are story pages)", example = "1")
        @NotNull(message = "pageIndex is required")
        Integer pageIndex,

        @Schema(description = "Updated Arabic story narrative text for the page", example = "استيقظ سامي مبكراً وخرج إلى الحديقة.")
        @Size(max = 1000, message = "Arabic text must not exceed 1000 characters")
        String textAr,

        @Schema(description = "Updated English visual scene prompt for image generation", example = "Sami running happily through the green garden under morning sunlight.")
        @Size(max = 2000, message = "Scene description must not exceed 2000 characters")
        String sceneEn
) {
}

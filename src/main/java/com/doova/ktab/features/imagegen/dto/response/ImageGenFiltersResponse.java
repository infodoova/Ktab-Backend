package com.doova.ktab.features.imagegen.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Available filter options (themes and aspect ratios) for the image generator")
public record ImageGenFiltersResponse(

        @Schema(description = "List of supported aesthetic themes")
        List<ThemeOption> themes,

        @Schema(description = "List of supported aspect ratio formats")
        List<AspectRatioOption> aspectRatios
) {

    @Schema(description = "Theme filter option")
    public record ThemeOption(
            @Schema(description = "Enum key to submit in GenerateImageRequest", example = "WATERCOLOR")
            String value,

            @Schema(description = "Human-readable label for UI display", example = "Watercolor")
            String displayName,

            @Schema(description = "Stylistic description", example = "delicate watercolor painting style, soft fluid gradients")
            String description
    ) {
    }

    @Schema(description = "Aspect ratio option")
    public record AspectRatioOption(
            @Schema(description = "Enum key to submit in GenerateImageRequest", example = "PORTRAIT_3_4")
            String value,

            @Schema(description = "Numeric ratio string", example = "3:4")
            String ratio,

            @Schema(description = "Localized display name in Arabic for UI display", example = "عمودي للكتب (3:4)")
            String displayName,

            @Schema(description = "Description for UI display", example = "Standard portrait book illustration format")
            String description
    ) {
    }
}

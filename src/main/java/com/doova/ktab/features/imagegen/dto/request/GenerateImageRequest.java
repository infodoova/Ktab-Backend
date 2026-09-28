package com.doova.ktab.features.imagegen.dto.request;

import com.doova.ktab.features.imagegen.enums.ImageAspectRatio;
import com.doova.ktab.features.imagegen.enums.ImageTheme;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to trigger AI image generation for a book")
public record GenerateImageRequest(

        @NotBlank(message = "{validation.imagegen.context.required}")
        @Size(min = 5, max = 4000, message = "{validation.imagegen.context.size}")
        @Schema(description = "User's creative conceptual scene description (never raw book pages)", example = "An ancient library at twilight with towering mahogany bookshelves and gentle floating lanterns")
        String context,

        @NotNull(message = "{validation.imagegen.theme.required}")
        @Schema(description = "Artistic style and aesthetic theme", example = "WATERCOLOR")
        ImageTheme theme,

        @NotNull(message = "{validation.imagegen.aspect_ratio.required}")
        @Schema(description = "Image aspect ratio / composition format", example = "PORTRAIT_3_4")
        ImageAspectRatio aspectRatio,

        @Size(max = 500)
        @Schema(description = "Optional additional artistic notes or color preferences", example = "Warm amber lighting, soft teal contrasts")
        String styleNotes
) {
}

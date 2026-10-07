package com.doova.ktab.features.storybook.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request to edit storybook title and page texts/scenes before Gate 1 approval")
public record EditStoryRequest(
        @Schema(description = "Updated Arabic title of the storybook", example = "مغامرة سامي في الحديقة السحرية")
        @Size(min = 2, max = 200, message = "Title must be between 2 and 200 characters")
        String titleAr,

        @Schema(description = "List of pages to update")
        @Valid
        List<EditPageRequest> pages
) {
}

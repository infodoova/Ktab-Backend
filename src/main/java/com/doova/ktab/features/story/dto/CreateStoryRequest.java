package com.doova.ktab.features.story.dto;

import com.doova.ktab.features.story.enums.StoryLens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStoryRequest(
        @NotBlank(message = "{validation.story.title.required}")
        @Size(max = 255, message = "{validation.story.title.size}")
        String title,

        @Size(max = 100, message = "{validation.story.genre.size}")
        String genre,

        @Min(value = 1, message = "{validation.story.max_scenes.min}")
        int maxScenes,

        @NotNull(message = "{validation.story.lens.required}")
        StoryLens lens,

        @NotBlank(message = "{validation.story.visual_style.required}")
        @Size(max = 255, message = "{validation.story.visual_style.size}")
        String visualStyle,

        @NotBlank(message = "{validation.story.visual_style_notes.required}")
        @Size(max = 1000, message = "{validation.story.visual_style_notes.size}")
        String visualStyleNotes,

        @Valid
        @NotNull(message = "{validation.story.constitution.required}")
        StoryConstitutionDto constitution
) {
}

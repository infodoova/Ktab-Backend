package com.doova.ktab.features.story.dto;

import com.doova.ktab.features.story.enums.StoryLens;
import jakarta.validation.Valid;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateStoryRequest(
        @Size(max = 255, message = "{validation.story.title.size}")
        String title,

        @Size(max = 100, message = "{validation.story.genre.size}")
        String genre,

        @Min(value = 1, message = "{validation.story.max_scenes.min}")
        Integer maxScenes,

        StoryLens lens,

        @Valid
        StoryConstitutionDto constitution
) {
}

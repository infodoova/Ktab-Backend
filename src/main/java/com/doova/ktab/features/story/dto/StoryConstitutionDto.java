package com.doova.ktab.features.story.dto;

import jakarta.validation.constraints.NotBlank;

public record StoryConstitutionDto(
        @NotBlank(message = "{validation.story.constitution.setting_time.required}")
        String settingTime,

        @NotBlank(message = "{validation.story.constitution.setting_place.required}")
        String settingPlace,

        @NotBlank(message = "{validation.story.constitution.core_theme.required}")
        String coreTheme,

        @NotBlank(message = "{validation.story.constitution.tone.required}")
        String tone,

        @NotBlank(message = "{validation.story.constitution.philosophy.required}")
        String philosophy,

        @NotBlank(message = "{validation.story.constitution.main_conflict.required}")
        String mainConflict,

        String forbiddenElements,
        String pacing
) {}

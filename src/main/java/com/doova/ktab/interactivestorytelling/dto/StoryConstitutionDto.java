package com.doova.ktab.interactivestorytelling.dto;

import jakarta.validation.constraints.NotBlank;

public record StoryConstitutionDto(
        @NotBlank String settingTime,
        @NotBlank String settingPlace,
        @NotBlank String coreTheme,
        @NotBlank String tone,
        @NotBlank String philosophy,
        @NotBlank String mainConflict,
        String forbiddenElements,
        String pacing
) {}

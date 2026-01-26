package com.doova.ktab.interactivestorytelling.dto;

import com.doova.ktab.interactivestorytelling.enums.StoryLens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStoryRequest(@NotBlank String title, String genre, int maxScenes, @NotNull StoryLens lens,
                                 @NotBlank String visualStyle, @NotBlank String visualStyleNotes,
                                 @Valid StoryConstitutionDto constitution) {
}

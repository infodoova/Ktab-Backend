package com.doova.ktab.interactivestorytelling.dto;

import com.doova.ktab.interactivestorytelling.enums.StoryLens;
import jakarta.validation.Valid;

public record UpdateStoryRequest(String title, String genre, Integer maxScenes, StoryLens lens,
                                 @Valid StoryConstitutionDto constitution) {
}

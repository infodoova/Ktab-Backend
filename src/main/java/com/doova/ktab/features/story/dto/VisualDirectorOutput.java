package com.doova.ktab.features.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VisualDirectorOutput(
        String prompt,
        String negative_prompt,
        String aspect_ratio,
        CameraSpec camera
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CameraSpec(String shot, Integer lens_mm, String angle) {}
}

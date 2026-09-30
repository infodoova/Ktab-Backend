package com.doova.ktab.features.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public record ResolutionResult(
        String storyboard,
        String script,
        ImageBrief imageBrief,
        EndingInfo ending
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EndingInfo(String id, String type, String epilogue_line_ar) {}
}

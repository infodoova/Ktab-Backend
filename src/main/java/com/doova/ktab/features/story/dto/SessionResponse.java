package com.doova.ktab.features.story.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SessionResponse(
        Long sessionId,
        String storyTitle,
        int storyScenes,
        List<TurnResponse> turns
) {
    @JsonProperty("title")
    public String getTitle() {
        return storyTitle;
    }
}

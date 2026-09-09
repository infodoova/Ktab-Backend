package com.doova.ktab.features.story.dto;

import java.util.List;

public record SessionResponse(Long sessionId, int storyScenes, List<TurnResponse> turns) {
}

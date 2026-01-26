package com.doova.ktab.interactivestorytelling.dto;

import java.util.List;

public record SessionResponse(Long sessionId, int storyScenes, List<TurnResponse> turns) {
}

package com.doova.ktab.ws.dto;

import java.util.Map;

public record TtsStreamParams(Long bookId, int start, int end, String voiceId) {
    public static TtsStreamParams fromMap(Map<String, Object> m) {
        return new TtsStreamParams(
                ((Number) m.get("bookId")).longValue(),
                ((Number) m.get("start")).intValue(),
                ((Number) m.get("end")).intValue(),
                (String) m.get("voiceId")
        );
    }
}
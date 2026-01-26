package com.doova.ktab.ocr.dto;

public record GeminiOcrResponse(String markdown, int wordCount) {

    public static GeminiOcrResponse empty() {
        return new GeminiOcrResponse("", 0);
    }

    public static GeminiOcrResponse fallback(String raw) {
        return new GeminiOcrResponse(raw.trim(), 0);
    }
}
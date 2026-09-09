package com.doova.ktab.features.tts.dto;

import java.util.List;

public record WordTiming(
        String word,
        double startSeconds,
        double endSeconds,
        double durationSeconds,
        int startChar,
        int endChar
) {}


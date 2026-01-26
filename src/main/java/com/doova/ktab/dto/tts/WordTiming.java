package com.doova.ktab.dto.tts;

import java.util.List;

public record WordTiming(
        String word,
        double startSeconds,
        double endSeconds,
        double durationSeconds,
        int startChar,
        int endChar
) {}


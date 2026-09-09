package com.doova.ktab.features.tts.dto;

import java.util.List;

public record TtsWsChunkMessage(
        String type,        // "alignment"
        int chunkIndex,
        long seq,
        List<WordTiming> words
) {}
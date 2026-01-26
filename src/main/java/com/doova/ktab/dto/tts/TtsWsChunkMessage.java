package com.doova.ktab.dto.tts;

import java.util.List;

public record TtsWsChunkMessage(
        String type,        // "alignment"
        int chunkIndex,
        long seq,
        List<WordTiming> words
) {}
package com.doova.ktab.features.tts.config;


import com.doova.ktab.features.tts.dto.ElevenLabsChunkResponse;

public sealed interface ElevenLabsEvent permits ElevenLabsEvent.Audio, ElevenLabsEvent.Alignment, ElevenLabsEvent.Done {
    record Audio(byte[] bytes) implements ElevenLabsEvent {
    }

    record Alignment(ElevenLabsChunkResponse raw) implements ElevenLabsEvent {
    }

    record Done() implements ElevenLabsEvent {
    }
}
package com.doova.ktab.config.elevenlabs;


import com.doova.ktab.dto.tts.ElevenLabsChunkResponse;

public sealed interface ElevenLabsEvent permits ElevenLabsEvent.Audio, ElevenLabsEvent.Alignment, ElevenLabsEvent.Done {
    record Audio(byte[] bytes) implements ElevenLabsEvent {
    }

    record Alignment(ElevenLabsChunkResponse raw) implements ElevenLabsEvent {
    }

    record Done() implements ElevenLabsEvent {
    }
}
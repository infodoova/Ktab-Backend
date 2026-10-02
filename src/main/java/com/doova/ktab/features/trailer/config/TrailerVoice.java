package com.doova.ktab.features.trailer.config;

/** One approved narration voice: its ElevenLabs id, a display name, and the genres/tones it suits. */
public record TrailerVoice(String id, String name, String suits) {
}

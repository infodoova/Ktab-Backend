package com.doova.ktab.features.tts.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from ElevenLabs WebSocket streaming API.
 * Handles multiple API response formats for compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElevenLabsChunkResponse(
        @JsonProperty("audio_base64")
        String audioBase64,
        
        @JsonProperty("normalized_alignment")
        Alignment alignment
) {
    /**
     * Check if this response contains audio data.
     */
    public boolean hasAudio() {
        return audioBase64 != null && !audioBase64.isBlank();
    }

    /**
     * Check if this response contains alignment data.
     */
    public boolean hasAlignment() {
        return alignment != null && alignment.characters() != null && !alignment.characters().isEmpty();
    }


    /**
     * Alignment data for word/character timing synchronization.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alignment(
            // Character list - supports multiple field names
            @JsonProperty("characters")
            @JsonAlias({"chars"})
            List<String> characters,



            // Alternative: start times in seconds (older API format)
            @JsonProperty("character_start_times_seconds")
            @JsonAlias({"charStartTimesSeconds"})
            List<Double> startTimesSeconds,

            // Alternative: end times in seconds (older API format)
            @JsonProperty("character_end_times_seconds")
            @JsonAlias({"charEndTimesSeconds"})
            List<Double> endTimesSeconds
    ) {
    }
}

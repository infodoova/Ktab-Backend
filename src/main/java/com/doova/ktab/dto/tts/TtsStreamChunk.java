package com.doova.ktab.dto.tts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single JSON chunk from the ElevenLabs HTTP stream.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TtsStreamChunk(
        @JsonProperty("audio_base64") String audioBase64,
        @JsonProperty("alignment") Alignment alignment,
        @JsonProperty("normalized_alignment") Alignment normalizedAlignment
) {

    /**
     * Checks if the chunk contains valid audio data.
     */
    @JsonIgnore
    public boolean hasAudio() {
        return audioBase64 != null && !audioBase64.isBlank();
    }

    /**
     * Checks if the chunk contains alignment/timing information.
     * We check for null and ensure the character list isn't empty.
     */
    @JsonIgnore
    public boolean hasAlignment() {
        return normalizedAlignment != null &&
                normalizedAlignment.characters() != null &&
                !normalizedAlignment.characters().isEmpty();
    }

    /**
     * Optional: Check if either raw or normalized alignment is present.
     */
    @JsonIgnore
    public boolean hasAnyAlignment() {
        return hasAlignment() || (normalizedAlignment != null && !normalizedAlignment.characters().isEmpty());
    }
}
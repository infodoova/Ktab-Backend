package com.doova.ktab.features.studio.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Character-level alignment payload for a chapter snapshot. Same shape as the
 * {@code normalized_alignment} block ElevenLabs already returns on the live WebSocket TTS
 * path (see {@code ElevenLabsChunkResponse.Alignment}) — parallel arrays, not an array of
 * objects, which is also why the archival copy in R2 uses the same layout (Phase 3.6).
 * See {@link StudioProjectResponse} for the field-name-accuracy caveat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudioCharacterAlignment(
        List<String> characters,
        @JsonProperty("character_start_times_seconds") List<Double> startTimesSeconds,
        @JsonProperty("character_end_times_seconds") List<Double> endTimesSeconds
) {
    public List<CharacterTiming> toTimings() {
        int n = characters == null ? 0 : characters.size();
        List<CharacterTiming> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new CharacterTiming(characters.get(i), startTimesSeconds.get(i), endTimesSeconds.get(i)));
        }
        return out;
    }
}

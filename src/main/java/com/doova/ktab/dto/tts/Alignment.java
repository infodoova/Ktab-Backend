package com.doova.ktab.dto.tts;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Raw alignment data from ElevenLabs API.
 * The API returns character-level timing in SECONDS.
 */
public record Alignment(
        @JsonProperty("characters") List<String> characters,
        @JsonProperty("character_start_times_seconds") List<Double> startTimes,
        @JsonProperty("character_end_times_seconds") List<Double> endTimes
) {}


package com.doova.ktab.features.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageBrief(
        String frozen_frame_en,
        List<String> characters_present,
        List<String> objects_present,
        String location_id,
        String time_of_day,
        String weather_or_air,
        String dominant_emotion,
        String key_light_source
) {}

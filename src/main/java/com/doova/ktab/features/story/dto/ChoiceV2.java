package com.doova.ktab.features.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChoiceV2(
        String id,
        String archetype,
        String text_ar,
        String risk_profile,
        Integer risk_level,
        Integer reward_level,
        Map<String, Object> effects,
        String on_success_en,
        String on_failure_en,
        String ending_vector
) {}

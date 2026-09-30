package com.doova.ktab.features.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StateUpdate(
        String summary_append_ar,
        List<String> threads_opened,
        List<String> threads_closed,
        List<String> setups_planted,
        List<String> setups_paid,
        List<String> character_changes,
        List<String> inventory_changes
) {}

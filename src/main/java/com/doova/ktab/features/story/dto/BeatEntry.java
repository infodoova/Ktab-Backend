package com.doova.ktab.features.story.dto;

import com.doova.ktab.features.story.enums.Beat;

public record BeatEntry(int scene, Beat beat, int tension) {}

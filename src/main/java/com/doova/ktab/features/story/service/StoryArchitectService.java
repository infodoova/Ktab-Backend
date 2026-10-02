package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.BeatEntry;
import com.doova.ktab.features.story.model.Story;

import java.util.List;

public interface StoryArchitectService {
    String generateStoryBible(Story story, List<BeatEntry> beatMap);
}

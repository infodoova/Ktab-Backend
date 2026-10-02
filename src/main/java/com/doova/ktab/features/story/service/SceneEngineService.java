package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.SceneEngineResult;
import com.doova.ktab.features.story.enums.Beat;
import com.doova.ktab.features.story.model.Story;

public interface SceneEngineService {

    SceneEngineResult generateScene(
            Story story,
            String storyBible,
            int sceneIndex,
            int totalScenes,
            Beat beat,
            int tension,
            String scenePlanEntryJson,
            String statsJson,
            String summarySoFarAr,
            String openThreads,
            String unpaidSetups,
            String inventory,
            String characterStatus,
            String prevRiskMapping,
            String prevChoiceTextAr,
            String prevArchetype,
            String prevRisk,
            String outcome,
            String prevSeedEn
    );
}

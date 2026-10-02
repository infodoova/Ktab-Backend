package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.ResolutionResult;
import com.doova.ktab.features.story.model.Story;

public interface ResolutionWriterService {

    ResolutionResult writeResolution(
            Story story,
            String storyBible,
            String finalStatsJson,
            String fullSummaryAr,
            String unpaidSetups,
            String climaxChoiceTextAr,
            String climaxRisk,
            String outcome,
            String endingVector
    );
}

package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.ImageBrief;
import com.doova.ktab.features.story.dto.VisualDirectorOutput;
import com.doova.ktab.features.story.enums.Beat;

public interface VisualDirectorService {

    VisualDirectorOutput directImage(
            String visualBibleJson,
            String visualStyle,
            String visualStyleNotes,
            String lockedTokens,
            ImageBrief imageBrief,
            Beat beat,
            int tension,
            int sceneIndex,
            int totalScenes
    );
}

package com.doova.ktab.features.story.dto;

import java.util.List;

public record SceneEngineResult(
        String storyboard,
        String script,
        List<ChoiceV2> choices,
        ImageBrief imageBrief,
        StateUpdate stateUpdate
) {}

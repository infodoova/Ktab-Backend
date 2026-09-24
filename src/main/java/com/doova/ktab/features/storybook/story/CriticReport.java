package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.llm.LlmCall;

import java.util.List;
import java.util.Map;

public record CriticReport(StoryPlanResponse plan, Map<Integer, List<String>> problemsByPage,
                           LlmCall<CriticResponse> llmCall) {

    public boolean allPass() {
        return problemsByPage.isEmpty();
    }

    public List<Integer> failingPages() {
        return problemsByPage.keySet().stream().sorted().toList();
    }
}

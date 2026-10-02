package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CriticResponse(
        @JsonAlias({"verdicts", "reviews", "results", "evaluations", "pageVerdicts"})
        List<PageVerdict> pages
) implements com.doova.ktab.features.storybook.llm.ValidatedLlmResponse {
    @Override
    public List<String> problems() {
        return pages == null || pages.isEmpty() ? List.of("the critic gave no verdicts") : List.of();
    }

}

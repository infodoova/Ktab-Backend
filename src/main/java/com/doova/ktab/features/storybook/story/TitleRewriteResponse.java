package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.llm.ValidatedLlmResponse;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** A corrected book title. The title is fixed on its own; a flagged title must not throw the whole story away. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TitleRewriteResponse(
        @JsonAlias({"title", "title_ar", "newTitle"})
        @JsonPropertyDescription("The corrected book title, 2 to 5 words, in the same language variety as the pages") String titleAr
) implements ValidatedLlmResponse {

    @Override
    public List<String> problems() {
        return titleAr == null || titleAr.isBlank() ? List.of("the rewritten title is empty") : List.of();
    }
}

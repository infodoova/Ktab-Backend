package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record PageVerdict(
        @JsonPropertyDescription("Page number; 0 is the title") int pageNumber,
        boolean pass,
        @JsonPropertyDescription("One short English sentence per problem, quoting the Arabic words") List<String> problems
) {
}

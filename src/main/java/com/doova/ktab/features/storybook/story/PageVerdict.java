package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageVerdict(
        @JsonAlias({"page_number", "page", "number", "index"})
        @JsonPropertyDescription("Page number; 0 is the title") int pageNumber,
        @JsonAlias({"passed", "success", "ok", "valid", "status"})
        boolean pass,
        @JsonAlias({"errors", "issues", "problem_list", "notes"})
        @JsonPropertyDescription("One short English sentence per problem, quoting the Arabic words") List<String> problems
) {
}

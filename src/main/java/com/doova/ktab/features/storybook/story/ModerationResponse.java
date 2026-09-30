package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModerationResponse(
        @JsonAlias({"allow", "isAllowed", "is_allowed", "approved", "permitted", "pass", "passed", "safe"})
        boolean allowed,
        @JsonAlias({"explanation", "message", "error", "feedback"})
        @JsonPropertyDescription("If not allowed: one short English sentence the parent can act on; otherwise null") String reason
) {
}

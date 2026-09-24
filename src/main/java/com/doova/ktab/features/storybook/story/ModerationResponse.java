package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ModerationResponse(
        boolean allowed,
        @JsonPropertyDescription("If not allowed: one short English sentence the parent can act on; otherwise null") String reason
) {
}

package com.doova.ktab.features.story.dto;

import jakarta.validation.constraints.NotBlank;

public record StartSessionRequest(
        @NotBlank(message = "{validation.session.reader_id.required}")
        String readerId
) {}

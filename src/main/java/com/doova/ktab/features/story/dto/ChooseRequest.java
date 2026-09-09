package com.doova.ktab.features.story.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChooseRequest(
        @NotNull(message = "{validation.session.choice.required}")
        @Pattern(regexp = "A|B|C|D", message = "{validation.session.choice.pattern}")
        String choiceId
) {}

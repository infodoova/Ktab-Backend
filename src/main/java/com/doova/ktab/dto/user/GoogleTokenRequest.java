package com.doova.ktab.dto.user;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequest(
        @NotBlank(message = "{validation.google.token.required}")
        String idToken
) {
}

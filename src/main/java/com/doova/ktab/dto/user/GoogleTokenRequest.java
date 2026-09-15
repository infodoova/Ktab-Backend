package com.doova.ktab.dto.user;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequest(
        @NotBlank(message = "{validation.google.token.required}")
        String idToken,

        Boolean rememberMe,
        Boolean includeRefreshToken
) {
    public GoogleTokenRequest(String idToken) {
        this(idToken, false, false);
    }
}

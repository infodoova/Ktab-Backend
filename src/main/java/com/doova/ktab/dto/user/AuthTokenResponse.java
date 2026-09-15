package com.doova.ktab.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
        String accessToken,
        String token,
        String refreshToken,
        String tokenType,
        Long expiresIn
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthTokenResponse(accessToken, accessToken, refreshToken, "Bearer", expiresIn);
    }

    public static AuthTokenResponse ofAccessTokenOnly(String accessToken) {
        return new AuthTokenResponse(accessToken, null, null, null, null);
    }
}

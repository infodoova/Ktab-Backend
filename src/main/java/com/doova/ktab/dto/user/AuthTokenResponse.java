package com.doova.ktab.dto.user;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}

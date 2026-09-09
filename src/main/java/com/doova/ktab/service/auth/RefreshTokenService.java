package com.doova.ktab.service.auth;

import com.doova.ktab.dto.user.AuthTokenResponse;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user, String deviceInfo);

    AuthTokenResponse rotateRefreshToken(String rawToken, String deviceInfo);

    void revokeToken(String rawToken);

    void revokeAllUserTokens(User user);

    void purgeExpiredTokens();
}

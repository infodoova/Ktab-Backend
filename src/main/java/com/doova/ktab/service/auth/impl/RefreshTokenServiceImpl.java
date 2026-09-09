package com.doova.ktab.service.auth.impl;

import com.doova.ktab.dto.user.AuthTokenResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.exception.UnAuthorizedException;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.RefreshTokenRepository;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JWTService jwtService;
    private final long refreshExpirationMillis;
    private final long jwtExpirationMillis;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            JWTService jwtService,
            @Value("${security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMillis,
            @Value("${security.jwt.expiration-ms:21600000}") long jwtExpirationMillis
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshExpirationMillis = refreshExpirationMillis;
        this.jwtExpirationMillis = jwtExpirationMillis;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceInfo) {
        String tokenValue = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(refreshExpirationMillis);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(expiresAt)
                .revoked(false)
                .deviceInfo(deviceInfo)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.debug("Created refresh token for user ID {}: expires at {}", user.getId(), expiresAt);
        return saved;
    }

    @Override
    @Transactional
    public AuthTokenResponse rotateRefreshToken(String rawToken, String deviceInfo) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnAuthorizedException(ApiMessageKey.SECURITY_TOKEN_MISSING);
        }

        RefreshToken token = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new UnAuthorizedException(ApiMessageKey.AUTH_REFRESH_TOKEN_INVALID));

        // Token Reuse Detection
        if (token.isRevoked()) {
            log.warn("SECURITY ALERT: Refresh token reuse detected for user ID {}. Revoking all user tokens.",
                    token.getUser().getId());
            refreshTokenRepository.revokeAllByUser(token.getUser());
            throw new UnAuthorizedException(ApiMessageKey.AUTH_REFRESH_TOKEN_INVALID);
        }

        // Expiration check
        if (token.isExpired()) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new UnAuthorizedException(ApiMessageKey.AUTH_REFRESH_TOKEN_EXPIRED);
        }

        User user = token.getUser();
        if (!Status.ACTIVE.getCode().equals(user.getActive())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new UnAuthorizedException(ApiMessageKey.SECURITY_UNAUTHORIZED);
        }

        // Token Rotation: revoke old, create new
        String newRefreshTokenValue = UUID.randomUUID().toString();
        token.setRevoked(true);
        token.setReplacedByToken(newRefreshTokenValue);
        refreshTokenRepository.save(token);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenValue)
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMillis))
                .revoked(false)
                .deviceInfo(deviceInfo != null ? deviceInfo : token.getDeviceInfo())
                .build();
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtService.generateToken(new UserPrincipal(user));
        long expiresInSeconds = jwtExpirationMillis / 1000;

        log.info("Successfully rotated refresh token for user: {}", user.getEmail());
        return AuthTokenResponse.of(newAccessToken, newRefreshTokenValue, expiresInSeconds);
    }

    @Override
    @Transactional
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(rawToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.debug("Revoked refresh token for user ID {}", token.getUser().getId());
        });
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        if (user != null) {
            refreshTokenRepository.revokeAllByUser(user);
            log.info("Revoked all refresh tokens for user ID {}", user.getId());
        }
    }

    @Override
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        log.debug("Purged expired and revoked refresh tokens from database");
    }
}

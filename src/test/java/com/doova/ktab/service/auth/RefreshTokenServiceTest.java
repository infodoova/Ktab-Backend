package com.doova.ktab.service.auth;

import com.doova.ktab.dto.user.AuthTokenResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.exception.UnAuthorizedException;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.RefreshTokenRepository;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JWTService jwtService;

    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                jwtService,
                604800000L, // 7 days
                21600000L   // 6 hours
        );

        testUser = User.builder()
                .email("reader@ktab.com")
                .firstName("Test")
                .lastName("Reader")
                .role("READER")
                .active(Status.ACTIVE.getCode())
                .build();
        testUser.setId(100L);
    }

    @Test
    @DisplayName("createRefreshToken persists a valid non-revoked token with proper expiry")
    void createRefreshToken_success() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken token = refreshTokenService.createRefreshToken(testUser, "Mozilla/5.0");

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertEquals(testUser, token.getUser());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiresAt().isAfter(Instant.now()));
        assertEquals("Mozilla/5.0", token.getDeviceInfo());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("rotateRefreshToken succeeds, revokes old token, and issues new tokens")
    void rotateRefreshToken_success() {
        String oldRawToken = "old-refresh-token-uuid";
        RefreshToken existingToken = RefreshToken.builder()
                .token(oldRawToken)
                .user(testUser)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .deviceInfo("Chrome")
                .build();

        when(refreshTokenRepository.findByToken(oldRawToken)).thenReturn(Optional.of(existingToken));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("new-access-token-jwt");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthTokenResponse response = refreshTokenService.rotateRefreshToken(oldRawToken, "Chrome-Updated");

        assertNotNull(response);
        assertEquals("new-access-token-jwt", response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotEquals(oldRawToken, response.refreshToken());

        // Verify old token was revoked and replaced
        assertTrue(existingToken.isRevoked());
        assertEquals(response.refreshToken(), existingToken.getReplacedByToken());

        // Verify save was called for both old and new tokens
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("rotateRefreshToken detects token reuse, revokes all user tokens, and throws UnAuthorizedException")
    void rotateRefreshToken_reuseDetected() {
        String reusedToken = "already-revoked-token";
        RefreshToken compromisedToken = RefreshToken.builder()
                .token(reusedToken)
                .user(testUser)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true) // already revoked!
                .build();

        when(refreshTokenRepository.findByToken(reusedToken)).thenReturn(Optional.of(compromisedToken));

        UnAuthorizedException ex = assertThrows(UnAuthorizedException.class, () ->
                refreshTokenService.rotateRefreshToken(reusedToken, "Attacker-Agent")
        );

        assertEquals(ApiMessageKey.AUTH_REFRESH_TOKEN_INVALID, ex.getMessageKey());
        verify(refreshTokenRepository, times(1)).revokeAllByUser(testUser);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("rotateRefreshToken throws exception when token is expired")
    void rotateRefreshToken_expired() {
        String expiredToken = "expired-token-uuid";
        RefreshToken token = RefreshToken.builder()
                .token(expiredToken)
                .user(testUser)
                .expiresAt(Instant.now().minusSeconds(60)) // expired
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(expiredToken)).thenReturn(Optional.of(token));

        UnAuthorizedException ex = assertThrows(UnAuthorizedException.class, () ->
                refreshTokenService.rotateRefreshToken(expiredToken, "Chrome")
        );

        assertEquals(ApiMessageKey.AUTH_REFRESH_TOKEN_EXPIRED, ex.getMessageKey());
        assertTrue(token.isRevoked());
        verify(refreshTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("rotateRefreshToken throws exception when token does not exist")
    void rotateRefreshToken_notFound() {
        when(refreshTokenRepository.findByToken("non-existent")).thenReturn(Optional.empty());

        UnAuthorizedException ex = assertThrows(UnAuthorizedException.class, () ->
                refreshTokenService.rotateRefreshToken("non-existent", "Chrome")
        );

        assertEquals(ApiMessageKey.AUTH_REFRESH_TOKEN_INVALID, ex.getMessageKey());
    }

    @Test
    @DisplayName("revokeToken marks existing token as revoked")
    void revokeToken_success() {
        String rawToken = "token-to-revoke";
        RefreshToken token = RefreshToken.builder()
                .token(rawToken)
                .user(testUser)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(token));

        refreshTokenService.revokeToken(rawToken);

        assertTrue(token.isRevoked());
        verify(refreshTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("revokeAllUserTokens delegates to repository")
    void revokeAllUserTokens_success() {
        refreshTokenService.revokeAllUserTokens(testUser);
        verify(refreshTokenRepository, times(1)).revokeAllByUser(testUser);
    }
}

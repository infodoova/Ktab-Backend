package com.doova.ktab.service.auth.impl;

import com.doova.ktab.dto.user.GoogleProfileClaims;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.service.auth.PendingGoogleTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies short-lived pending-registration tokens.
 *
 * <p>Uses the same HMAC secret as the main JWT service but includes a
 * {@code type=GOOGLE_PENDING} claim so tokens cannot be used as access tokens
 * and access tokens cannot be reused here.
 */
@Service
@Slf4j
public class PendingGoogleTokenServiceImpl implements PendingGoogleTokenService {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String TOKEN_TYPE_VALUE = "GOOGLE_PENDING";
    private static final long   TTL_MS           = 5 * 60 * 1000L; // 5 minutes

    private final SecretKey key;

    public PendingGoogleTokenServiceImpl(
            @Value("${security.jwt.secret:}") String secretKey
    ) {
        this.key = resolveKey(secretKey);
    }

    private static SecretKey resolveKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("[PendingGoogleToken] JWT_SECRET not configured — using ephemeral key.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return Keys.hmacShaKeyFor(random);
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    @Override
    public String issue(String email, String firstName, String lastName) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(Map.of(
                        TOKEN_TYPE_CLAIM, TOKEN_TYPE_VALUE,
                        "email",     email,
                        "firstName", firstName,
                        "lastName",  lastName
                ))
                .subject(email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_MS))
                .signWith(key)
                .compact();
    }

    @Override
    public GoogleProfileClaims verify(String pendingToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(pendingToken)
                    .getPayload();

            // Guard: reject if not a pending-registration token (prevents access-token substitution)
            if (!TOKEN_TYPE_VALUE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                log.warn("[PendingGoogleToken] Token type mismatch — possible token substitution attempt.");
                throw new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED);
            }

            return new GoogleProfileClaims(
                    claims.get("email",     String.class),
                    claims.get("firstName", String.class),
                    claims.get("lastName",  String.class)
            );

        } catch (ExpiredJwtException ex) {
            log.warn("[PendingGoogleToken] Pending registration token expired.");
            throw new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("[PendingGoogleToken] Invalid pending token: {}", ex.getMessage());
            throw new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED);
        }
    }
}

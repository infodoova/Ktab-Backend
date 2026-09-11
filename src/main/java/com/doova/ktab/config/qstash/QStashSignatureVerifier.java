package com.doova.ktab.config.qstash;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Verifier for incoming Upstash QStash webhook signatures.
 * Upstash-Signature header contains an HS256 signed JWT with body SHA-256 hash.
 */
@Component
@Slf4j
public class QStashSignatureVerifier {

    @Value("${qstash.current-signing-key:}")
    private String currentSigningKey;

    @Value("${qstash.next-signing-key:}")
    private String nextSigningKey;

    @Value("${qstash.verify-signature:true}")
    private boolean verifySignature;

    public boolean verify(String signature, String rawBody) {
        if (!verifySignature) {
            log.warn("QStash signature verification is disabled by configuration");
            return true;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Missing Upstash-Signature header");
            return false;
        }

        if ((currentSigningKey == null || currentSigningKey.isBlank()) &&
                (nextSigningKey == null || nextSigningKey.isBlank())) {
            log.warn("QStash signing keys are not configured; signature verification skipped for development");
            return true;
        }

        // Try verifying with currentSigningKey, then fallback to nextSigningKey
        Claims claims = null;
        if (currentSigningKey != null && !currentSigningKey.isBlank()) {
            claims = parseAndVerifyJwt(signature, currentSigningKey);
        }
        if (claims == null && nextSigningKey != null && !nextSigningKey.isBlank()) {
            claims = parseAndVerifyJwt(signature, nextSigningKey);
        }

        if (claims == null) {
            log.error("Failed to verify QStash signature with configured signing keys");
            return false;
        }

        // Verify issuer
        if (!"Upstash".equalsIgnoreCase(claims.getIssuer())) {
            log.error("Invalid QStash signature issuer: {}", claims.getIssuer());
            return false;
        }

        // Verify body hash
        String expectedBodyHash = claims.get("body", String.class);
        if (expectedBodyHash != null) {
            String actualBodyHash = computeSha256Base64Url(rawBody);
            String cleanExpected = expectedBodyHash.replace("=", "");
            String cleanActual = actualBodyHash.replace("=", "");

            if (!cleanExpected.equals(cleanActual)) {
                log.error("QStash body hash mismatch. Expected: {}, Computed: {}", cleanExpected, cleanActual);
                return false;
            }
        }

        return true;
    }

    private Claims parseAndVerifyJwt(String token, String key) {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT verification failed with key: {}", e.getMessage());
            return null;
        }
    }

    private String computeSha256Base64Url(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}

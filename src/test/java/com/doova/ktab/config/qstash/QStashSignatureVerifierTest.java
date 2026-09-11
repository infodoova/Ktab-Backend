package com.doova.ktab.config.qstash;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QStashSignatureVerifierTest {

    private QStashSignatureVerifier verifier;
    private final String currentKey = "super_secret_signing_key_for_testing_purposes_123456";
    private final String nextKey = "super_secret_next_signing_key_for_testing_purposes_789";

    @BeforeEach
    void setUp() {
        verifier = new QStashSignatureVerifier();
        ReflectionTestUtils.setField(verifier, "currentSigningKey", currentKey);
        ReflectionTestUtils.setField(verifier, "nextSigningKey", nextKey);
        ReflectionTestUtils.setField(verifier, "verifySignature", true);
    }

    private String createValidToken(String key, String body) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
        String bodyHash = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .issuer("Upstash")
                .subject("https://api.ktab.app/internal/ocr/process")
                .claim("body", bodyHash)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(secretKey)
                .compact();
    }

    @Test
    void verify_ValidSignature_ReturnsTrue() throws Exception {
        String body = "{\"bookId\":10,\"pageNumber\":1}";
        String token = createValidToken(currentKey, body);

        boolean result = verifier.verify(token, body);
        assertTrue(result);
    }

    @Test
    void verify_ValidSignatureWithNextKey_ReturnsTrue() throws Exception {
        String body = "{\"bookId\":10,\"pageNumber\":2}";
        String token = createValidToken(nextKey, body);

        boolean result = verifier.verify(token, body);
        assertTrue(result);
    }

    @Test
    void verify_TamperedBody_ReturnsFalse() throws Exception {
        String originalBody = "{\"bookId\":10,\"pageNumber\":1}";
        String tamperedBody = "{\"bookId\":10,\"pageNumber\":999}";
        String token = createValidToken(currentKey, originalBody);

        boolean result = verifier.verify(token, tamperedBody);
        assertFalse(result);
    }

    @Test
    void verify_InvalidKey_ReturnsFalse() throws Exception {
        String body = "{\"bookId\":10,\"pageNumber\":1}";
        String wrongKey = "different_secret_key_which_will_fail_verification_99999";
        String token = createValidToken(wrongKey, body);

        boolean result = verifier.verify(token, body);
        assertFalse(result);
    }

    @Test
    void verify_MissingSignature_ReturnsFalse() {
        assertFalse(verifier.verify(null, "body"));
        assertFalse(verifier.verify("", "body"));
    }

    @Test
    void verify_DisabledVerification_ReturnsTrue() {
        ReflectionTestUtils.setField(verifier, "verifySignature", false);
        assertTrue(verifier.verify("any-signature", "any-body"));
    }
}

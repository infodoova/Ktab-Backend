package com.doova.ktab.service.auth.impl;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.JWTService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JWTServiceImpl implements JWTService {

    private static final Logger log = LoggerFactory.getLogger(JWTServiceImpl.class);

    /** Placeholder values that must never be used as real keys. */
    private static final String DUMMY_MARKER = "none";

    private final SecretKey key;
    private final long expirationMillis;

    public JWTServiceImpl(
            @Value("${security.jwt.secret:}") String secretKey,
            @Value("${security.jwt.expiration-ms:43200000}") long expirationMillis
    ) {
        this.key = resolveKey(secretKey);
        this.expirationMillis = expirationMillis;
    }

    /**
     * Resolves the HMAC signing key.
     * <p>
     * If no real secret is configured (blank or placeholder), an ephemeral random
     * 256-bit key is generated. This keeps the application context loadable in
     * local/CI environments without credentials, but tokens will not survive
     * restarts. A warning is logged so developers notice the misconfiguration.
     * </p>
     */
    private static SecretKey resolveKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank() || DUMMY_MARKER.equalsIgnoreCase(secretKey.trim())) {
            log.warn("[Security] JWT_SECRET is not configured. Using an ephemeral random key — " +
                     "tokens will be invalidated on restart. Set JWT_SECRET in .env.development for local dev.");
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            return Keys.hmacShaKeyFor(randomBytes);
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    @Override
    public String generateToken(UserPrincipal userPrincipal) {
        return generateToken(Map.of(), userPrincipal);
    }

    @Override
    public String generateToken(Map<String, Object> extraClaims, UserPrincipal userPrincipal) {

        long now = System.currentTimeMillis();

        Map<String, Object> claims = new HashMap<>(extraClaims);

        User user = userPrincipal.user();

        claims.put("role", UserRole.fromCode(user.getRole()));
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("userId", user.getId());

        return Jwts.builder()
                .claims(claims)
                .subject(userPrincipal.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }


    @Override
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @Override
    public boolean validateToken(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token) && userDetails.isEnabled();
    }

    @Override
    public <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}

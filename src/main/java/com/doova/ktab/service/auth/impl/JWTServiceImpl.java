package com.doova.ktab.service.auth.impl;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.JWTService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JWTServiceImpl implements JWTService {

    private final String secretKey;
    private final long expirationMillis;

    public JWTServiceImpl(
            @Value("${security.jwt.secret}") String secretKey,
            @Value("${security.jwt.expiration-ms:43200000}") long expirationMillis
    ) {
        this.secretKey = secretKey;
        this.expirationMillis = expirationMillis;
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
                .signWith(getKey())
                .compact();
    }

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
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
        return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}

package com.doova.ktab.service.auth;

import com.doova.ktab.security.model.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.function.Function;

public interface JWTService {

    String generateToken(UserPrincipal userPrincipal);

    String generateToken(Map<String, Object> extraClaims, UserPrincipal userPrincipal);

    String extractEmail(String token);

    boolean validateToken(String token, UserDetails userDetails);

    <T> T extractClaim(String token, Function<Claims, T> claimResolver);
}

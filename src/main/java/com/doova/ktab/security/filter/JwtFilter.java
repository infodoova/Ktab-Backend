package com.doova.ktab.security.filter;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.MyUserDetailsService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String ACCESS_TOKEN_COOKIE = "ACCESS_TOKEN";
    private static final String LEGACY_JWT_COOKIE = "JWT_TOKEN";

    private final JWTService jwtService;
    private final MyUserDetailsService userDetailsService;
    private final FilterResponseWriter responseWriter;

    // -------------------------------------------------------------------------
    // FILTER SKIP RULES
    // -------------------------------------------------------------------------
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        // Public auth endpoints (except refresh)
        if (path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/refresh-token")) {
            return true;
        }

        // Public book covers endpoint
        if (path.equals("/api/v1/reader/covers")) {
            return true;
        }

        // WebSocket endpoints - auth handled inside the handler
        if (path.startsWith("/ws/")) {
            return true;
        }

        // Swagger & infra
        return path.startsWith("/swagger") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator");
    }

    // -------------------------------------------------------------------------
    // FILTER CORE
    // -------------------------------------------------------------------------
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Already authenticated → continue
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractToken(request);

            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            authenticateUser(request, token);

        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", request.getRequestURI());
            responseWriter.writeError(response, HttpStatus.UNAUTHORIZED, ApiMessageKey.SECURITY_TOKEN_INVALID);
            return;

        } catch (UsernameNotFoundException ex) {
            log.warn("User not found for JWT");
            responseWriter.writeError(response, HttpStatus.UNAUTHORIZED, ApiMessageKey.SECURITY_AUTHENTICATION_FAILED);
            return;

        } catch (Exception ex) {
            log.error("JWT processing error", ex);
            responseWriter.writeError(response, HttpStatus.UNAUTHORIZED, ApiMessageKey.SECURITY_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // TOKEN EXTRACTION
    // -------------------------------------------------------------------------
    private String extractToken(HttpServletRequest request) {

        // 1️⃣ Authorization header
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // 2️⃣ Cookie fallback
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> ACCESS_TOKEN_COOKIE.equals(c.getName()) || LEGACY_JWT_COOKIE.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // AUTHENTICATION
    // -------------------------------------------------------------------------
    private void authenticateUser(HttpServletRequest request, String token) {

        String email = jwtService.extractEmail(token);
        if (email == null) {
            throw new IllegalArgumentException("Invalid JWT payload");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (!jwtService.validateToken(token, userDetails)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("JWT authenticated user: {}", email);
    }
}

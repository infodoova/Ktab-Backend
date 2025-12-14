package com.doova.ktab.security.filter;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Custom JWT Authentication Filter for Spring Security.
 * It processes a request to extract a JWT from the Authorization header or a cookie,
 * validates it, and sets the authenticated user in the SecurityContext.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_COOKIE_NAME = "JWT_TOKEN"; // Consider externalizing

    private final JWTService jwtService;
    private final MyUserDetailsService userDetailsService;

    // Using a dedicated ResponseWriter class is a common 'pro' refactoring
    private final FilterResponseWriter responseWriter;

    // --- Filter Execution Strategy ---

    /**
     * Defines which requests should be excluded from this filter's execution.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        // Allow all /api/v1/auth/* EXCEPT refresh-token
        if (path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/refresh-token")) {
            return true;
        }

        // Standard exclusions for development/management
        return path.startsWith("/swagger") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator");
    }

    /**
     * Core filter logic to process the request for JWT authentication.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. Skip if authentication is already set (e.g., by another filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. Extract and validate token
            String token = extractToken(request);

            if (token != null) {
                authenticateUser(request, token);
            }

        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired for request: {}", request.getRequestURI());
            responseWriter.writeError(response, HttpStatus.UNAUTHORIZED, "Token expired");
            return; // Stop the chain
        } catch (UsernameNotFoundException ex) {
            log.warn("User not found for JWT: {}", ex.getMessage());
            responseWriter.writeError(response, HttpStatus.NOT_FOUND, "Email Not Found");
            return; // Stop the chain
        } catch (Exception ex) {
            // Catch all other validation/processing issues
            log.error("JWT processing failed for request: {}: {}", request.getRequestURI(), ex.getMessage());
            // We log the error but allow the chain to continue unauthenticated (403/401 will be handled by ExceptionHandler)
        }

        filterChain.doFilter(request, response);
    }

    // --- Token Extraction ---

    /**
     * Tries to extract the JWT token from the Authorization header,
     * then falls back to an HttpOnly cookie.
     *
     * @return The token string or null.
     */
    private String extractToken(HttpServletRequest request) {
        // 1) Authorization header (Preferred)
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // 2) Cookie (Fallback)
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies()).filter(cookie -> JWT_COOKIE_NAME.equals(cookie.getName())).findFirst().map(Cookie::getValue).orElse(null);
        }

        return null;
    }

    // --- Authentication Logic ---

    /**
     * Validates the token and sets the authentication in the SecurityContext.
     *
     * @param request The current request.
     * @param token   The extracted JWT.
     * @throws UsernameNotFoundException if the user does not exist.
     */
    private void authenticateUser(HttpServletRequest request, String token) {
        String email = jwtService.extractEmail(token);

        if (email != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (jwtService.validateToken(token, userDetails)) {
                // Create Authentication object
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, // Credentials (token) are not stored in context
                        userDetails.getAuthorities());

                // Set request details for audit logging and other features
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set the Authentication object in the SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("User authenticated successfully: {}", email);
            } else {
                log.debug("JWT token validation failed for email: {}", email);
            }
        }
    }
}
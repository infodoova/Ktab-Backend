package com.doova.ktab.security.filter;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.ratelimit.RateLimitTier;
import com.doova.ktab.service.ratelimit.RateLimitResult;
import com.doova.ktab.service.ratelimit.RateLimitService;
import com.doova.ktab.utils.web.ClientIpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that enforces token-bucket rate limits on incoming HTTP requests.
 * Applies different rate limit tiers (AUTH, AI, GENERAL) and decorates responses
 * with standard RFC rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RESET = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimitService rateLimitService;
    private final FilterResponseWriter responseWriter;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        RateLimitTier tier = resolveTier(request);

        if (tier == RateLimitTier.SKIP || !rateLimitService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = ClientIpUtils.getClientIp(request);
        RateLimitResult result = rateLimitService.tryConsume(clientIp, tier);

        if (result.limit() > 0) {
            response.setHeader(HEADER_LIMIT, String.valueOf(result.limit()));
        }

        if (result.allowed()) {
            response.setHeader(HEADER_REMAINING, String.valueOf(result.remainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        // 429 Too Many Requests
        response.setHeader(HEADER_REMAINING, "0");
        response.setHeader(HEADER_RESET, String.valueOf(result.secondsToWaitForRefill()));
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.secondsToWaitForRefill()));

        log.warn("Rate limit violation: IP {} throttled on path {} (tier: {})", clientIp, request.getRequestURI(), tier);
        responseWriter.writeError(response, HttpStatus.TOO_MANY_REQUESTS, ApiMessageKey.RATE_LIMIT_EXCEEDED);
    }

    private RateLimitTier resolveTier(HttpServletRequest request) {
        // Exempt preflight CORS requests from rate limiting
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return RateLimitTier.SKIP;
        }

        String path = request.getServletPath();

        // Exempt / Infrastructure paths
        if (path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.startsWith("/ws/")
                || path.startsWith("/webjars")
                || path.equals("/favicon.ico")
                || path.equals("/error")) {
            return RateLimitTier.SKIP;
        }

        // Authentication & Credential-sensitive endpoints
        if (path.startsWith("/api/v1/auth/")) {
            return RateLimitTier.AUTH;
        }

        // Heavy / Expensive AI & Story endpoints
        if (path.startsWith("/api/v1/ai/") || path.startsWith("/api/v1/story/")) {
            return RateLimitTier.AI;
        }

        // General public or protected API endpoints
        if (path.startsWith("/api/")) {
            return RateLimitTier.GENERAL;
        }

        return RateLimitTier.SKIP;
    }
}

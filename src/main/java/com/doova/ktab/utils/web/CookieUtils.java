package com.doova.ktab.utils.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";

    private static final Set<String> VALID_SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

    private final boolean secure;
    private final String sameSite;
    private final long maxAgeSeconds;
    private final long refreshMaxAgeSeconds;

    public CookieUtils(
            @Value("${app.cookie.secure:false}") boolean secure,
            @Value("${app.cookie.same-site:Lax}") String sameSite,
            @Value("${app.cookie.max-age-seconds:21600}") long maxAgeSeconds,
            @Value("${app.cookie.refresh-max-age-seconds:604800}") long refreshMaxAgeSeconds
    ) {
        this.secure = secure;
        this.sameSite = normalizeSameSite(sameSite);
        this.maxAgeSeconds = validateMaxAge(maxAgeSeconds, "maxAgeSeconds");
        this.refreshMaxAgeSeconds = validateMaxAge(refreshMaxAgeSeconds, "refreshMaxAgeSeconds");

        if ("None".equals(this.sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires secure cookies");
        }
    }

    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        setAccessTokenCookie(response, token, maxAgeSeconds);
    }

    public void setAccessTokenCookie(HttpServletResponse response, String token, long maxAge) {
        addTokenCookie(response, ACCESS_TOKEN_COOKIE_NAME, token, maxAge);
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        setRefreshTokenCookie(response, token, refreshMaxAgeSeconds);
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAge) {
        addTokenCookie(response, REFRESH_TOKEN_COOKIE_NAME, token, maxAge);
    }

    public void clearAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE_NAME);
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        clearCookie(response, REFRESH_TOKEN_COOKIE_NAME);
    }

    public void clearAllAuthCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
    }

    public Optional<String> extractCookieValue(HttpServletRequest request, String cookieName) {
        if (request == null || cookieName == null || cookieName.isBlank() || request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private void addTokenCookie(
            HttpServletResponse response,
            String cookieName,
            String token,
            long maxAge
    ) {
        Objects.requireNonNull(response, "response must not be null");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }

        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofSeconds(validateMaxAge(maxAge, "maxAge")))
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String cookieName) {
        Objects.requireNonNull(response, "response must not be null");

        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static long validateMaxAge(long maxAge, String fieldName) {
        if (maxAge < 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 0");
        }
        return maxAge;
    }

    private static String normalizeSameSite(String sameSite) {
        if (sameSite == null || sameSite.isBlank()) {
            return "Lax";
        }

        String normalized = sameSite.substring(0, 1).toUpperCase(Locale.ROOT)
                + sameSite.substring(1).toLowerCase(Locale.ROOT);

        if (!VALID_SAME_SITE_VALUES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid SameSite value: " + sameSite);
        }

        return normalized;
    }
}

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
import java.util.Optional;

@Component
public class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";

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
        this.sameSite = sameSite;
        this.maxAgeSeconds = maxAgeSeconds;
        this.refreshMaxAgeSeconds = refreshMaxAgeSeconds;
    }

    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        setAccessTokenCookie(response, token, this.maxAgeSeconds);
    }

    public void setAccessTokenCookie(HttpServletResponse response, String token, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(this.secure)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAge))
                .sameSite(this.sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        setRefreshTokenCookie(response, token, this.refreshMaxAgeSeconds);
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(this.secure)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAge))
                .sameSite(this.sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(this.secure)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(this.sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(this.secure)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(this.sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAllAuthCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
    }

    public Optional<String> extractCookieValue(HttpServletRequest request, String cookieName) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}

package com.doova.ktab.controller.v1.auth;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.PendingGoogleTokenService;
import com.doova.ktab.service.auth.RefreshTokenService;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.web.CookieUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * "Sign in with Google" in redirect mode, for browsers where the popup flow cannot hand the result back
 * (iPhone and iPad). The whole page goes to Google, and Google posts the signed ID token straight here.
 * The popup flow at POST /auth/google is unchanged.
 *
 * <p>Google's usual CSRF cookie belongs to the website's domain and is not sent to this one, so a nonce ties the
 * request to the browser instead: the page asks for one ({@code GET /nonce}), it is put in a cookie here and in
 * the ID token Google signs, and the two must match when Google posts back.
 */
@Slf4j
@ApiVersion(1)
@RestController
@RequestMapping(path = "/auth/google")
@Tag(name = "Authentication API")
public class GoogleRedirectAuthController {

    static final long NONCE_MAX_AGE_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleOAuth2Service googleOAuth2Service;
    private final PendingGoogleTokenService pendingGoogleTokenService;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtils cookieUtils;
    private final MessageSource messageSource;
    private final String frontendUrl;

    public GoogleRedirectAuthController(
            GoogleOAuth2Service googleOAuth2Service,
            PendingGoogleTokenService pendingGoogleTokenService,
            JWTService jwtService,
            RefreshTokenService refreshTokenService,
            CookieUtils cookieUtils,
            MessageSource messageSource,
            @Value("${ktab.google.redirect.frontend-url:${ktab.app.frontend-url}}") String frontendUrl
    ) {
        this.googleOAuth2Service = googleOAuth2Service;
        this.pendingGoogleTokenService = pendingGoogleTokenService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.cookieUtils = cookieUtils;
        this.messageSource = messageSource;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Operation(summary = "Start a Google redirect sign-in — returns a nonce and stores the same value in a short-lived cookie")
    @GetMapping(path = "/nonce")
    public ResponseEntity<ApiResponse<Map<String, String>>> nonce(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        cookieUtils.setGoogleNonceCookie(response, nonce, NONCE_MAX_AGE_SECONDS);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseUtils.success(Map.of("nonce", nonce), ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Receives Google's form post, signs the user in and redirects back to the website")
    @PostMapping(path = "/redirect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> redirect(
            @RequestParam(name = "credential", required = false) String credential,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String cookieNonce = cookieUtils.extractCookieValue(request, CookieUtils.GOOGLE_NONCE_COOKIE_NAME).orElse(null);
        // One use only, whatever the outcome.
        cookieUtils.clearGoogleNonceCookie(response);

        if (credential == null || credential.isBlank() || cookieNonce == null) {
            log.warn("GOOGLE_REDIRECT_REJECTED reason={}", credential == null || credential.isBlank() ? "no-credential" : "no-nonce-cookie");
            return to(loginUrl("error", null));
        }

        GoogleIdToken.Payload payload;
        try {
            payload = googleOAuth2Service.verifyGoogleToken(credential);
        } catch (RuntimeException e) {
            log.warn("GOOGLE_REDIRECT_REJECTED reason=invalid-token");
            return to(loginUrl("error", null));
        }

        if (!sameValue(cookieNonce, payload.getNonce())) {
            log.warn("GOOGLE_REDIRECT_REJECTED reason=nonce-mismatch");
            return to(loginUrl("error", null));
        }

        String email = payload.getEmail();
        UserPrincipal principal = googleOAuth2Service.findExistingUser(email);
        if (principal != null) {
            cookieUtils.setAccessTokenCookie(response, jwtService.generateToken(principal));
            RefreshToken rt = refreshTokenService.createRefreshToken(principal.user(), request.getHeader("User-Agent"));
            cookieUtils.setRefreshTokenCookie(response, rt.getToken());
            return to(loginUrl("success", null));
        }

        // New user: the website asks which kind of account they want, then calls /auth/google/complete.
        String pendingToken = pendingGoogleTokenService.issue(email, firstName(payload), lastName(payload));
        return to(loginUrl("pending", pendingToken));
    }

    /** The token goes in the fragment so it is never sent to a server or written to an access log. */
    private String loginUrl(String result, String pendingToken) {
        String url = frontendUrl + "/login?google=" + result;
        return pendingToken == null ? url : url + "#pending=" + URLEncoder.encode(pendingToken, StandardCharsets.UTF_8);
    }

    private static ResponseEntity<Void> to(String url) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(url))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private static boolean sameValue(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String firstName(GoogleIdToken.Payload payload) {
        String firstName = (String) payload.get("given_name");
        if (firstName == null || firstName.isBlank()) {
            firstName = (String) payload.get("name");
        }
        return (firstName != null && !firstName.isBlank()) ? firstName : "GoogleUser";
    }

    private static String lastName(GoogleIdToken.Payload payload) {
        String lastName = (String) payload.get("family_name");
        return lastName != null ? lastName : "";
    }
}

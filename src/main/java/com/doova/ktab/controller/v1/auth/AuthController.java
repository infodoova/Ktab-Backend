package com.doova.ktab.controller.v1.auth;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.user.*;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.PendingGoogleTokenService;
import com.doova.ktab.service.auth.RefreshTokenService;
import com.doova.ktab.service.user.UserService;
import com.doova.ktab.utils.web.CookieUtils;
import com.doova.ktab.utils.response.ResponseUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/auth", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "Endpoints for user registration, login, verification and password management.")
public class AuthController {

    private final UserService service;
    private final MessageSource messageSource;
    private final CookieUtils cookieUtils;
    private final GoogleOAuth2Service googleOAuth2Service;
    private final PendingGoogleTokenService pendingGoogleTokenService;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;

    // ============================
    // REGISTER
    // ============================

    @Operation(summary = "Register a new user")
    @PostMapping(path = "/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<UserResponseDto>> register(@Valid @RequestBody UserRegisterRequest req) {
        User user = service.register(req);
        return ResponseUtils.success(UserResponseDto.from(user), ApiMessageKey.AUTH_REGISTER_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    // ============================
    // LOGIN
    // ============================

    @Operation(summary = "Login — sets ACCESS_TOKEN and REFRESH_TOKEN as HttpOnly cookies")
    @PostMapping(path = "/login", consumes = "application/json")
    public ResponseEntity<ApiResponse<UserResponseDto>> login(
            @Valid @RequestBody UserLoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UserPrincipal principal = service.authenticate(req);

        String accessToken = jwtService.generateToken(principal);
        cookieUtils.setAccessTokenCookie(response, accessToken);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(principal.user(), request.getHeader("User-Agent"));
        cookieUtils.setRefreshTokenCookie(response, refreshToken.getToken());

        return ResponseUtils.success(UserResponseDto.from(principal.user()), ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // GOOGLE LOGIN (step 1)
    // ============================

    @Operation(summary = "Google OAuth2 login — returns session for existing users, or a pendingToken for new users to complete registration")
    @PostMapping(path = "/google", consumes = "application/json")
    public ResponseEntity<?> googleLogin(
            @Valid @RequestBody GoogleTokenRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // Verify Google ID token — throws if invalid
        GoogleIdToken.Payload payload = googleOAuth2Service.verifyGoogleToken(req.idToken());
        String email     = payload.getEmail();
        String firstName = resolveFirstName(payload);
        String lastName  = resolveLastName(payload);

        // Fast path: existing user → issue session immediately
        UserPrincipal principal = googleOAuth2Service.findExistingUser(email);
        if (principal != null) {
            cookieUtils.setAccessTokenCookie(response, jwtService.generateToken(principal));
            RefreshToken rt = refreshTokenService.createRefreshToken(principal.user(), request.getHeader("User-Agent"));
            cookieUtils.setRefreshTokenCookie(response, rt.getToken());
            return ResponseUtils.success(UserResponseDto.from(principal.user()), ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
        }

        // New user → issue a short-lived pending token and ask frontend to pick a role
        String pendingToken = pendingGoogleTokenService.issue(email, firstName, lastName);
        GooglePendingResponse pending = new GooglePendingResponse(
                GooglePendingResponse.STATUS, pendingToken, email, firstName, lastName
        );
        return ResponseUtils.success(pending, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // GOOGLE COMPLETE (step 2)
    // ============================

    @Operation(summary = "Complete Google registration — creates account with chosen role and issues session cookies")
    @PostMapping(path = "/google/complete", consumes = "application/json")
    public ResponseEntity<ApiResponse<UserResponseDto>> completeGoogleRegistration(
            @Valid @RequestBody GoogleCompleteRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // Enforce allowed self-registration roles
        if (req.role() != UserRole.READER && req.role() != UserRole.AUTHOR) {
            throw new com.doova.ktab.exception.BadRequestException(ApiMessageKey.AUTH_ROLE_REGISTRATION_FORBIDDEN);
        }

        // Verify pending token — throws if expired or tampered
        GoogleProfileClaims claims = pendingGoogleTokenService.verify(req.pendingToken());

        // Create user with chosen role
        UserPrincipal principal = googleOAuth2Service.createUser(
                claims.email(), claims.firstName(), claims.lastName(), req.role()
        );

        // Issue session
        cookieUtils.setAccessTokenCookie(response, jwtService.generateToken(principal));
        RefreshToken rt = refreshTokenService.createRefreshToken(principal.user(), request.getHeader("User-Agent"));
        cookieUtils.setRefreshTokenCookie(response, rt.getToken());

        return ResponseUtils.success(UserResponseDto.from(principal.user()), ApiMessageKey.AUTH_REGISTER_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    // ============================
    // VERIFY EMAIL
    // ============================

    @Operation(summary = "Verify email")
    @PostMapping(path = "/verify", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody VerifyCodeRequest req) {
        service.verifyEmail(req);
        return ResponseUtils.success(null, ApiMessageKey.AUTH_EMAIL_VERIFIED.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // RESEND VERIFICATION CODE
    // ============================

    @Operation(summary = "Resend verification code")
    @PostMapping(path = "/send-re-verify", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> resendVerificationCode(@Valid @RequestBody ResendVerificationCodeRequest req) {
        service.sendReVerifyAccountCode(req);
        return ResponseUtils.success(null, ApiMessageKey.AUTH_RESET_CODE_SENT.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // SEND RESET PASSWORD CODE
    // ============================

    @Operation(summary = "Send reset password code")
    @PostMapping(path = "/send-reset", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> sendReset(@Valid @RequestBody SendResetPasswordRequest req) {
        service.sendResetCode(req);
        return ResponseUtils.success(null, ApiMessageKey.AUTH_RESET_CODE_SENT.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // RESET PASSWORD
    // ============================

    @Operation(summary = "Reset password")
    @PostMapping(path = "/reset-password", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        service.resetPassword(req);
        return ResponseUtils.success(null, ApiMessageKey.AUTH_PASSWORD_RESET_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // REFRESH TOKEN
    // ============================

    @Operation(summary = "Rotate refresh token — reads from HttpOnly cookie or request body")
    @PostMapping(path = "/refresh-token")
    public ResponseEntity<ApiResponse<Void>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = resolveRefreshToken(req, request);
        AuthTokenResponse tokenResponse = refreshTokenService.rotateRefreshToken(token, request.getHeader("User-Agent"));

        cookieUtils.setAccessTokenCookie(response, tokenResponse.accessToken());
        cookieUtils.setRefreshTokenCookie(response, tokenResponse.refreshToken());

        return ResponseUtils.success(null, ApiMessageKey.AUTH_TOKEN_REFRESH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // LOGOUT
    // ============================

    @Operation(summary = "Logout — revokes refresh token and clears auth cookies")
    @PostMapping(path = "/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = resolveRefreshToken(req, request);
        if (token != null && !token.isBlank()) {
            refreshTokenService.revokeToken(token);
        }
        cookieUtils.clearAllAuthCookies(response);
        return ResponseUtils.success(null, ApiMessageKey.AUTH_LOGOUT_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // HELPERS
    // ============================

    /**
     * Resolves the refresh token from, in priority order:
     * 1. JSON request body
     * 2. HttpOnly REFRESH_TOKEN cookie (primary path for browser clients)
     * 3. X-Refresh-Token header (mobile / non-cookie clients)
     */
    private String resolveRefreshToken(RefreshTokenRequest req, HttpServletRequest request) {
        if (req != null && req.refreshToken() != null && !req.refreshToken().isBlank()) {
            return req.refreshToken();
        }
        return cookieUtils.extractCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME)
                .orElse(request.getHeader("X-Refresh-Token"));
    }

    private String resolveFirstName(com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload) {
        String firstName = (String) payload.get("given_name");
        if (firstName == null || firstName.isBlank()) {
            firstName = (String) payload.get("name");
        }
        return (firstName != null && !firstName.isBlank()) ? firstName : "GoogleUser";
    }

    private String resolveLastName(com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload) {
        String lastName = (String) payload.get("family_name");
        return lastName != null ? lastName : "";
    }
}
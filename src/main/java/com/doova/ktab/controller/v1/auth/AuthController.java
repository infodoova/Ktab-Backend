package com.doova.ktab.controller.v1.auth;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.user.*;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.RefreshTokenService;
import com.doova.ktab.service.user.UserService;
import com.doova.ktab.utils.web.CookieUtils;
import com.doova.ktab.utils.response.ResponseUtils;
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

    @Operation(summary = "Login")
    @PostMapping(path = "/login", consumes = "application/json")
    public ResponseEntity<ApiResponse<Object>> login(
            @Valid @RequestBody UserLoginRequest req,
            @RequestParam(name = "includeRefreshToken", required = false) Boolean includeRefreshTokenParam,
            @RequestParam(name = "rememberMe", required = false) Boolean rememberMeParam,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UserPrincipal principal = service.authenticate(req);
        String accessToken = jwtService.generateToken(principal);

        cookieUtils.setAccessTokenCookie(response, accessToken);

        boolean includeRefreshToken = isRefreshTokenRequested(
                includeRefreshTokenParam,
                rememberMeParam,
                req.includeRefreshToken(),
                req.rememberMe(),
                request
        );

        if (includeRefreshToken) {
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(principal.user(), request.getHeader("User-Agent"));
            cookieUtils.setRefreshTokenCookie(response, refreshToken.getToken());
            AuthTokenResponse data = AuthTokenResponse.of(accessToken, refreshToken.getToken(), 21600);
            return ResponseUtils.success(data, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
        }

        return ResponseUtils.success(accessToken, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // GOOGLE LOGIN
    // ============================

    @Operation(summary = "Google OAuth2 login")
    @PostMapping(path = "/google", consumes = "application/json")
    public ResponseEntity<ApiResponse<Object>> googleLogin(
            @Valid @RequestBody GoogleTokenRequest req,
            @RequestParam(name = "includeRefreshToken", required = false) Boolean includeRefreshTokenParam,
            @RequestParam(name = "rememberMe", required = false) Boolean rememberMeParam,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UserPrincipal principal = googleOAuth2Service.verifyAndAuthenticate(req.idToken());
        String accessToken = jwtService.generateToken(principal);

        cookieUtils.setAccessTokenCookie(response, accessToken);

        boolean includeRefreshToken = isRefreshTokenRequested(
                includeRefreshTokenParam,
                rememberMeParam,
                req.includeRefreshToken(),
                req.rememberMe(),
                request
        );

        if (includeRefreshToken) {
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(principal.user(), request.getHeader("User-Agent"));
            cookieUtils.setRefreshTokenCookie(response, refreshToken.getToken());
            AuthTokenResponse data = AuthTokenResponse.of(accessToken, refreshToken.getToken(), 21600);
            return ResponseUtils.success(data, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
        }

        return ResponseUtils.success(accessToken, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    private boolean isRefreshTokenRequested(
            Boolean paramIncludeRefreshToken,
            Boolean paramRememberMe,
            Boolean bodyIncludeRefreshToken,
            Boolean bodyRememberMe,
            HttpServletRequest request
    ) {
        return Boolean.TRUE.equals(paramIncludeRefreshToken)
                || Boolean.TRUE.equals(paramRememberMe)
                || Boolean.TRUE.equals(bodyIncludeRefreshToken)
                || Boolean.TRUE.equals(bodyRememberMe)
                || "true".equalsIgnoreCase(request.getHeader("X-Include-Refresh-Token"));
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

    @Operation(summary = "Refresh token")
    @PostMapping(path = "/refresh-token")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = resolveRefreshToken(req, request);

        AuthTokenResponse tokenResponse = refreshTokenService.rotateRefreshToken(token, request.getHeader("User-Agent"));

        cookieUtils.setAccessTokenCookie(response, tokenResponse.accessToken());
        cookieUtils.setRefreshTokenCookie(response, tokenResponse.refreshToken());

        return ResponseUtils.success(tokenResponse, ApiMessageKey.AUTH_TOKEN_REFRESH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // LOGOUT
    // ============================

    @Operation(summary = "Logout")
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

    private String resolveRefreshToken(RefreshTokenRequest req, HttpServletRequest request) {
        if (req != null && req.refreshToken() != null && !req.refreshToken().isBlank()) {
            return req.refreshToken();
        }
        return cookieUtils.extractCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME)
                .orElse(request.getHeader("X-Refresh-Token"));
    }
}

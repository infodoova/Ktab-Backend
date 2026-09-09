package com.doova.ktab.controller.v1.auth;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.user.*;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.user.UserService;
import com.doova.ktab.utils.web.CookieUtils;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    // ============================
    // REGISTER
    // ============================

    @Operation(summary = "Register a new user")
    @PostMapping(path = "/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody UserRegisterRequest req) {
        User user = service.register(req);

        return ResponseUtils.success(user, ApiMessageKey.AUTH_REGISTER_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    // ============================
    // LOGIN
    // ============================

    @Operation(summary = "Login")
    @PostMapping(path = "/login", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody UserLoginRequest req, HttpServletResponse response) {
        String token = service.verify(req);
        cookieUtils.setAccessTokenCookie(response, token);

        return ResponseUtils.success(null, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // GOOGLE LOGIN
    // ============================

    @Operation(summary = "Google OAuth2 login")
    @PostMapping(path = "/google", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> googleLogin(@Valid @RequestBody GoogleTokenRequest req, HttpServletResponse response) {
        UserPrincipal principal = googleOAuth2Service.verifyAndAuthenticate(req.idToken());
        String token = jwtService.generateToken(principal);
        cookieUtils.setAccessTokenCookie(response, token);

        return ResponseUtils.success(null, ApiMessageKey.AUTH_LOGIN_SUCCESS.getMessage(messageSource), HttpStatus.OK);
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
    public ResponseEntity<ApiResponse<Void>> refreshToken(@CurrentUser User user, HttpServletResponse response) {
        String token = service.refreshToken(user.getEmail());
        cookieUtils.setAccessTokenCookie(response, token);

        return ResponseUtils.success(null, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================
    // LOGOUT
    // ============================

    @Operation(summary = "Logout")
    @PostMapping(path = "/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        cookieUtils.clearAccessTokenCookie(response);

        return ResponseUtils.success(null, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}

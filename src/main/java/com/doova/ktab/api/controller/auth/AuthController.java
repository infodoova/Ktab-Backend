package com.doova.ktab.api.controller.auth;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.api.dto.request.*;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.user.UserService;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = "application/json")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "Endpoints for user registration, login, verification and password management.")
public class AuthController {

    private final UserService service;

    // ============================================================================================
    // REGISTER
    // ============================================================================================

    @Operation(summary = "Register a new user", description = "Creates a new user account and sends an email verification code.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User successfully created", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or user already exists", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody UserRegisterRequest req) {
        User createdUser = service.register(req);
        return ResponseUtils.created(createdUser);
    }

    // ============================================================================================
    // LOGIN
    // ============================================================================================

    @Operation(summary = "Login", description = "Authenticates the user and returns a JWT token.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful, JWT token returned", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/login", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody UserLoginRequest req) {
        String token = service.verify(req);
        return ResponseUtils.response(token, "Login successful");
    }

    // ============================================================================================
    // VERIFY EMAIL
    // ============================================================================================

    @Operation(summary = "Verify email", description = "Validates an email verification code and activates the account.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification successful", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired verification code", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/verify", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> verify(@RequestBody VerifyCodeRequest req) {
        boolean ok = service.verifyEmail(req);

        if (ok) {
            return ResponseUtils.response("Verification successful", "Account verified!");
        } else {
            throw ResponseUtils.errorResponse("Invalid or expired code", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    // ============================================================================================
    // RESEND VERIFICATION CODE
    // ============================================================================================

    @Operation(summary = "Resend email verification code", description = "Resends a new email verification code if the account is not yet verified.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification code resent successfully", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Account already verified or invalid email", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/send-re-verify", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> resendVerificationCode(@Valid @RequestBody ResendVerificationCodeRequest req) {
        service.sendReVerifyAccountCode(req);
        return ResponseUtils.response("Verification code resent", "A new verification code has been sent to your email");
    }

    // ============================================================================================
    // SEND RESET PASSWORD CODE
    // ============================================================================================

    @Operation(summary = "Send reset password code", description = "Sends a password reset verification code to the user's email.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reset code sent", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/send-reset", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> sendReset(@RequestBody SendResetPasswordRequest req) {
        service.sendResetCode(req);
        return ResponseUtils.response("Reset code dispatched", "Reset code sent!");
    }

    // ============================================================================================
    // RESET PASSWORD
    // ============================================================================================

    @Operation(summary = "Reset password", description = "Resets the password using a valid verification code.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password successfully updated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired reset code", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/reset-password", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest req) {
        boolean ok = service.resetPassword(req);

        if (ok) {
            return ResponseUtils.response("Password change successful", "Password updated!");
        } else {
            throw ResponseUtils.errorResponse("Invalid or expired reset code", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    // ============================================================================================
    // REFRESH TOKEN
    // ============================================================================================

    @Operation(summary = "Refresh JWT token", description = "Generates a new JWT token for a valid user email.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email or user does not exist", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping(path = "/refresh-token", consumes = "application/json")
    public ResponseEntity<ApiResponse<String>> refreshToken(@CurrentUser User user) {

        String newToken = service.refreshToken(user.getEmail());

        return ResponseUtils.response(newToken, "Token refreshed successfully");
    }


}

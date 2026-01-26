package com.doova.ktab.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,

        @NotBlank(message = "Verification code is required") String code,

        @NotBlank(message = "New password is required") @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {
}

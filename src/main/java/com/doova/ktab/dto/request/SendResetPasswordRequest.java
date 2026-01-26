package com.doova.ktab.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendResetPasswordRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email) {
}

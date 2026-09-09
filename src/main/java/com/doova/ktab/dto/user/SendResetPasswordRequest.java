package com.doova.ktab.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendResetPasswordRequest(
        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email) {
}

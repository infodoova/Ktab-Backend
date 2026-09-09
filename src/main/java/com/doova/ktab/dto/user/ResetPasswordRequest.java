package com.doova.ktab.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

        @NotBlank(message = "{validation.code.required}") String code,

        @NotBlank(message = "{validation.password.required}") @Size(min = 8, message = "{validation.password.min_8}") String newPassword) {
}

package com.doova.ktab.dto.user;

import com.doova.ktab.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(

        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

        @NotBlank(message = "{validation.code.required}") String code,

        @ValidPassword
        String newPassword) {
}

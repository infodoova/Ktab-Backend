package com.doova.ktab.dto.user;

import com.doova.ktab.validation.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

                @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,

                @NotBlank(message = "{validation.code.required}") String code,

                @NotBlank(message = "{validation.password.required}") @Pattern(regexp = ValidationConstants.PASSWORD_REGEX, message = "{validation.password.complexity}") String newPassword) {
}

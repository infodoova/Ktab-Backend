package com.doova.ktab.dto.user;

import com.doova.ktab.annotation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserRegisterRequest(
        @NotBlank(message = "{validation.first_name.required}")
        String firstName,

        String middleName,

        @NotBlank(message = "{validation.last_name.required}")
        String lastName,

        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,

        @ValidPassword
        String password,

        @NotBlank(message = "{validation.role.required}")
        @Pattern(regexp = "(?i)AUTHOR|READER|10|20", message = "{auth.role.registration.forbidden}")
        String role
) {
}

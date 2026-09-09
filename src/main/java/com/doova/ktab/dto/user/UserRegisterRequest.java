package com.doova.ktab.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
        @NotBlank(message = "{validation.first_name.required}")
        String firstName,

        String middleName,

        @NotBlank(message = "{validation.last_name.required}")
        String lastName,

        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 6, message = "{validation.password.min_size}")
        String password,

        @NotBlank(message = "{validation.role.required}")
        @Pattern(regexp = "(?i)AUTHOR|READER|10|20", message = "{auth.role.registration.forbidden}")
        String role
) {
}

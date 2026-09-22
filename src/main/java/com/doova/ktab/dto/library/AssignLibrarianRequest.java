package com.doova.ktab.dto.library;

import com.doova.ktab.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssignLibrarianRequest(
        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,

        @NotBlank(message = "{validation.first_name.required}")
        String firstName,

        String middleName,

        @NotBlank(message = "{validation.last_name.required}")
        String lastName,

        @ValidPassword
        String password,

        String role
) {}

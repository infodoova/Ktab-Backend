package com.doova.ktab.dto.library;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignLibrarianRequest(
        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,

        @NotBlank(message = "{validation.first_name.required}")
        String firstName,

        String middleName,

        @NotBlank(message = "{validation.last_name.required}")
        String lastName,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 6, message = "{validation.password.min_size}")
        String password,

        String role
) {}

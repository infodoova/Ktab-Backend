package com.doova.ktab.dto.library;

import com.doova.ktab.annotation.ValidPassword;
import jakarta.validation.constraints.Email;

public record UpdateLibraryAdminRequest(
        @Email(message = "{validation.email.invalid}")
        String email,

        String firstName,
        String middleName,
        String lastName,

        @ValidPassword(required = false)
        String password
) {}

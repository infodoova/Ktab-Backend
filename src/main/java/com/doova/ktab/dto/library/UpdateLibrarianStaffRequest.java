package com.doova.ktab.dto.library;

import com.doova.ktab.annotation.ValidPassword;
import jakarta.validation.constraints.Email;

public record UpdateLibrarianStaffRequest(
        @Email(message = "{validation.email.invalid}")
        String email,

        String firstName,
        String middleName,
        String lastName,

        /**
         * Optional — when null or blank the existing password is left unchanged.
         */
        @ValidPassword(required = false)
        String password,

        /**
         * Optional — LIBRARIAN or ADMIN_LIBRARIAN.
         */
        String role
) {}

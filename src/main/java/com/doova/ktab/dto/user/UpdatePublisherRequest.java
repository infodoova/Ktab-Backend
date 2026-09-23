package com.doova.ktab.dto.user;

import com.doova.ktab.annotation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdatePublisherRequest(
        @Email(message = "{validation.email.invalid}")
        @NotBlank(message = "{validation.email.required}")
        String email,

        @NotBlank(message = "{validation.first_name.required}")
        String firstName,

        String middleName,

        @NotBlank(message = "{validation.last_name.required}")
        String lastName,

        /**
         * Optional — when null the existing password is left unchanged.
         */
        @ValidPassword(required = false)
        String password
) {}

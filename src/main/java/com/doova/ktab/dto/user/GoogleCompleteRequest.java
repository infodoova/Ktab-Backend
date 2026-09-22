package com.doova.ktab.dto.user;

import com.doova.ktab.enums.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sent by the frontend to finalise a Google registration after the user selects a role.
 */
public record GoogleCompleteRequest(

        @NotBlank(message = "{validation.google.pending.token.required}")
        String pendingToken,

        @NotNull(message = "{validation.role.required}")
        UserRole role
) {}
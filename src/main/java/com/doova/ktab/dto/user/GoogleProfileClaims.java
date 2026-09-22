package com.doova.ktab.dto.user;

/** Verified Google profile data extracted from a pending registration token. */
public record GoogleProfileClaims(
        String email,
        String firstName,
        String lastName
) {}
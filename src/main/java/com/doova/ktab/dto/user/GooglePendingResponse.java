package com.doova.ktab.dto.user;

/**
 * Returned by POST /auth/google when the Google account has no existing Ktab account.
 * The frontend must present a role-picker and POST to /auth/google/complete.
 */
public record GooglePendingResponse(
        /** Always "PENDING_REGISTRATION" — frontend uses this to detect the two-step flow. */
        String status,

        /** Short-lived signed JWT (5 min) containing the verified Google profile.
         *  Must be forwarded to /auth/google/complete. */
        String pendingToken,

        String email,
        String firstName,
        String lastName
) {
    public static final String STATUS = "PENDING_REGISTRATION";
}
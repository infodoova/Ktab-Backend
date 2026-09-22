package com.doova.ktab.service.auth;

import com.doova.ktab.dto.user.GoogleProfileClaims;

/**
 * Issues and verifies short-lived tokens used during the two-step Google registration flow.
 * The token bridges the gap between Google ID-token verification and role selection.
 */
public interface PendingGoogleTokenService {

    /**
     * Issues a signed 5-minute JWT containing the verified Google profile.
     * This token is returned to the frontend to be forwarded to /auth/google/complete.
     */
    String issue(String email, String firstName, String lastName);

    /**
     * Verifies the token's signature and expiry, then returns the embedded profile claims.
     *
     * @throws com.doova.ktab.exception.BadRequestException if the token is expired, tampered, or invalid
     */
    GoogleProfileClaims verify(String pendingToken);
}

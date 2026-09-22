package com.doova.ktab.service.auth;

import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.security.model.UserPrincipal;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

public interface GoogleOAuth2Service {

    /**
     * Verifies a Google ID token cryptographically.
     * Does NOT touch the database — use for step 1 of the two-step flow.
     *
     * @return the verified payload from Google
     * @throws com.doova.ktab.exception.BadRequestException if the token is invalid
     */
    GoogleIdToken.Payload verifyGoogleToken(String idTokenString);

    /**
     * Finds an existing user by email and returns their principal.
     *
     * @return the user principal, or null if no user exists with that email
     */
    UserPrincipal findExistingUser(String email);

    /**
     * Creates a new user from a verified Google profile with the given role.
     * Only READER and AUTHOR are permitted — enforced at the controller layer.
     */
    UserPrincipal createUser(String email, String firstName, String lastName, UserRole role);
}

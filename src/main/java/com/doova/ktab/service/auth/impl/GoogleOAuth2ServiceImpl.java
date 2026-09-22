package com.doova.ktab.service.auth.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
@Slf4j
public class GoogleOAuth2ServiceImpl implements GoogleOAuth2Service {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuth2ServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${google.oauth2.client-id:}") String clientId
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;

        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        );
        if (clientId != null && !clientId.isBlank()) {
            builder.setAudience(Collections.singletonList(clientId));
        }
        this.verifier = builder.build();
    }

    // -------------------------------------------------------------------------
    // STEP 1 — Verify Google token (no DB access)
    // -------------------------------------------------------------------------

    @Override
    public GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Invalid Google ID token signature or claims");
                throw new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());

            if (email == null || email.isBlank() || !emailVerified) {
                log.warn("Google account email unverified or missing: {}", email);
                throw new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED);
            }

            return payload;

        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to verify Google ID token", ex);
            throw new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED);
        }
    }

    // -------------------------------------------------------------------------
    // STEP 1b — Find existing user (fast-path login, no registration)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public UserPrincipal findExistingUser(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    // Re-activate silently deactivated accounts
                    if (!Status.ACTIVE.getCode().equals(user.getActive())) {
                        user.setActive(Status.ACTIVE.getCode());
                        userRepository.save(user);
                    }
                    return new UserPrincipal(user);
                })
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // STEP 2 — Create new user after role selection
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public UserPrincipal createUser(String email, String firstName, String lastName, UserRole role) {
        // Idempotent: if somehow the user already exists (race), just return them
        return userRepository.findByEmail(email)
                .map(existing -> {
                    log.info("Google complete: user already exists for email {}, skipping creation", email);
                    return new UserPrincipal(existing);
                })
                .orElseGet(() -> {
                    log.info("Creating new user from Google OAuth2 registration: {} role={}", email, role);
                    User newUser = User.builder()
                            .email(email)
                            .firstName(firstName != null && !firstName.isBlank() ? firstName : "GoogleUser")
                            .lastName(lastName != null ? lastName : "")
                            .role(role.getCode())
                            .active(Status.ACTIVE.getCode())
                            .build();
                    newUser.setPasswordAndDigest(UUID.randomUUID().toString(), passwordEncoder);
                    return new UserPrincipal(userRepository.save(newUser));
                });
    }
}

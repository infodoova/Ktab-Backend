package com.doova.ktab.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Google posts the redirect sign-in result from its own page, so the request carries Origin
 * "https://accounts.google.com" or the literal "null". Those must get past the CORS filter on this one path only.
 */
class WebSecurityConfigGoogleCorsTest {

    private static MockHttpServletResponse process(String method, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/auth/google/redirect");
        request.setServerName("api.ktab.app");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration config = WebSecurityConfig.googleRedirectCors();
        new DefaultCorsProcessor().processRequest(config, request, response);
        return response;
    }

    @Test
    @DisplayName("googleRedirect_originNull_isNotRejected")
    void originNull_isAllowed() throws Exception {
        assertThat(process("POST", "null").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("googleRedirect_originAccountsGoogle_isNotRejected")
    void originAccountsGoogle_isAllowed() throws Exception {
        assertThat(process("POST", "https://accounts.google.com").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("googleRedirect_otherOrigin_isStillRejected")
    void otherOrigin_isRejected() throws Exception {
        assertThat(process("POST", "https://evil.example").getStatus()).isEqualTo(403);
        assertThat(process("POST", "https://accounts.google.com.evil.example").getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("googleRedirect_nonPostMethod_isStillRejected")
    void nonPost_isRejected() throws Exception {
        assertThat(process("GET", "https://accounts.google.com").getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("googleRedirect_neverAllowsCredentialedCrossOriginReads")
    void noCredentials() {
        assertThat(WebSecurityConfig.googleRedirectCors().getAllowCredentials()).isFalse();
    }

    @Test
    @DisplayName("isGoogleRedirect_matchesOnlyTheRedirectEndpoint")
    void pathMatch() {
        assertThat(WebSecurityConfig.isGoogleRedirect("/api/v1/auth/google/redirect")).isTrue();
        assertThat(WebSecurityConfig.isGoogleRedirect("/api/v1/auth/google")).isFalse();
        assertThat(WebSecurityConfig.isGoogleRedirect("/api/v1/auth/google/nonce")).isFalse();
        assertThat(WebSecurityConfig.isGoogleRedirect("/api/v1/auth/google/complete")).isFalse();
        assertThat(WebSecurityConfig.isGoogleRedirect(null)).isFalse();
    }
}

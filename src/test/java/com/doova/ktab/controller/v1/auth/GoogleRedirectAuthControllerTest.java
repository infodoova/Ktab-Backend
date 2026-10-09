package com.doova.ktab.controller.v1.auth;

import com.doova.ktab.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.PendingGoogleTokenService;
import com.doova.ktab.service.auth.RefreshTokenService;
import com.doova.ktab.utils.web.CookieUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real controller over MockMvc with the real {@link CookieUtils} (secure, as in production), so the
 * form post, the cookie attributes and the redirect are all checked together. Google's side is mocked.
 */
@ExtendWith(MockitoExtension.class)
class GoogleRedirectAuthControllerTest {

    private static final String FRONTEND = "https://ktab.app";
    private static final String NONCE = "nonce-from-the-cookie";

    @Mock private GoogleOAuth2Service googleOAuth2Service;
    @Mock private PendingGoogleTokenService pendingGoogleTokenService;
    @Mock private JWTService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private MessageSource messageSource;

    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        CookieUtils cookies = new CookieUtils(true, "Lax", 900, 86400);
        // The trailing slash must not leak into the redirect address.
        GoogleRedirectAuthController controller = new GoogleRedirectAuthController(
                googleOAuth2Service, pendingGoogleTokenService, jwtService, refreshTokenService, cookies, messageSource, new ObjectMapper(), FRONTEND + "/");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");

        user = User.builder().email("ali@ktab.app").firstName("Ali").lastName("Hashem").role("READER").build();
        user.setId(7L);
    }

    private static GoogleIdToken.Payload payload(String email, String nonce) {
        GoogleIdToken.Payload p = new GoogleIdToken.Payload();
        p.setEmail(email);
        p.setNonce(nonce);
        p.set("given_name", "Ali");
        p.set("family_name", "Hashem");
        return p;
    }

    private MvcResult postCredential(String credential, Cookie... cookies) throws Exception {
        var request = post("/auth/google/redirect")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("g_csrf_token", "ignored");
        if (credential != null) {
            request.param("credential", credential);
        }
        for (Cookie c : cookies) {
            request.cookie(c);
        }
        return mvc.perform(request).andReturn();
    }

    private static String location(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private static List<String> setCookies(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    @DisplayName("nonce_returnsTheValueAndStoresTheSameOneInACookieThatSurvivesTheCrossSitePost")
    void nonce_returnsTheValueAndStoresTheSameOneInACookie() throws Exception {
        MvcResult result = mvc.perform(get("/auth/google/nonce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nonce").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String nonce = body.replaceAll(".*\"nonce\":\"([^\"]+)\".*", "$1");
        String cookie = setCookies(result).get(0);

        assertThat(cookie).startsWith("GOOGLE_NONCE=" + nonce);
        // Google posts back from another site: only SameSite=None gets the cookie sent.
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=None", "Max-Age=600");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(nonce.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("nonce_twoCalls_giveDifferentValues")
    void nonce_twoCalls_giveDifferentValues() throws Exception {
        String a = mvc.perform(get("/auth/google/nonce")).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(get("/auth/google/nonce")).andReturn().getResponse().getContentAsString();
        assertThat(a.replaceAll(".*\"nonce\":\"([^\"]+)\".*", "$1")).isNotEqualTo(b.replaceAll(".*\"nonce\":\"([^\"]+)\".*", "$1"));
    }

    @Test
    @DisplayName("redirect_existingUser_setsTheSessionCookiesAndSendsThemToTheWebsite")
    void redirect_existingUser_signsInAndRedirects() throws Exception {
        UserPrincipal principal = new UserPrincipal(user);
        when(googleOAuth2Service.verifyGoogleToken("google-token")).thenReturn(payload("ali@ktab.app", NONCE));
        when(googleOAuth2Service.findExistingUser("ali@ktab.app")).thenReturn(principal);
        when(jwtService.generateToken(principal)).thenReturn("access-jwt");
        when(refreshTokenService.createRefreshToken(user, "iPhone")).thenReturn(RefreshToken.builder().token("refresh-1").user(user).build());

        var request = post("/auth/google/redirect")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("User-Agent", "iPhone")
                .param("credential", "google-token")
                .cookie(new Cookie("GOOGLE_NONCE", NONCE));
        MvcResult result = mvc.perform(request).andExpect(status().isSeeOther()).andReturn();

        // The profile travels in the fragment, in the same shape the login endpoint returns.
        String location = location(result);
        assertThat(location).startsWith(FRONTEND + "/login?google=success#user=");
        String json = new String(java.util.Base64.getUrlDecoder().decode(location.substring(location.indexOf("#user=") + 6)), java.nio.charset.StandardCharsets.UTF_8);
        var profile = new ObjectMapper().readTree(json);
        assertThat(profile.get("id").asLong()).isEqualTo(7L);
        assertThat(profile.get("email").asText()).isEqualTo("ali@ktab.app");
        assertThat(profile.get("role").asText()).isEqualTo("READER");
        assertThat(profile.get("fullName").asText()).isEqualTo(user.getFullName());
        assertThat(location).doesNotContain("=="); // URL-safe Base64 without padding
        List<String> cookies = setCookies(result);
        assertThat(cookies).anyMatch(c -> c.startsWith("ACCESS_TOKEN=access-jwt"));
        assertThat(cookies).anyMatch(c -> c.startsWith("REFRESH_TOKEN=refresh-1"));
        // The nonce is single-use.
        assertThat(cookies).anyMatch(c -> c.startsWith("GOOGLE_NONCE=;") && c.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("redirect_newUser_sendsAPendingTokenInTheFragmentAndNoSession")
    void redirect_newUser_sendsPendingToken() throws Exception {
        when(googleOAuth2Service.verifyGoogleToken("google-token")).thenReturn(payload("new@ktab.app", NONCE));
        when(googleOAuth2Service.findExistingUser("new@ktab.app")).thenReturn(null);
        when(pendingGoogleTokenService.issue("new@ktab.app", "Ali", "Hashem")).thenReturn("pending.jwt+value");

        MvcResult result = postCredential("google-token", new Cookie("GOOGLE_NONCE", NONCE));

        assertThat(result.getResponse().getStatus()).isEqualTo(303);
        assertThat(location(result)).isEqualTo(FRONTEND + "/login?google=pending#pending=pending.jwt%2Bvalue");
        assertThat(setCookies(result)).noneMatch(c -> c.startsWith("ACCESS_TOKEN=") || c.startsWith("REFRESH_TOKEN="));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("redirect_nonceMismatch_isRejectedWithoutASession")
    void redirect_nonceMismatch_isRejected() throws Exception {
        when(googleOAuth2Service.verifyGoogleToken("google-token")).thenReturn(payload("ali@ktab.app", "someone-elses-nonce"));

        MvcResult result = postCredential("google-token", new Cookie("GOOGLE_NONCE", NONCE));

        assertThat(location(result)).isEqualTo(FRONTEND + "/login?google=error");
        assertThat(setCookies(result)).noneMatch(c -> c.startsWith("ACCESS_TOKEN="));
        verify(googleOAuth2Service, never()).findExistingUser(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("redirect_tokenWithoutANonce_isRejected")
    void redirect_tokenWithoutNonce_isRejected() throws Exception {
        when(googleOAuth2Service.verifyGoogleToken("google-token")).thenReturn(payload("ali@ktab.app", null));

        MvcResult result = postCredential("google-token", new Cookie("GOOGLE_NONCE", NONCE));

        assertThat(location(result)).isEqualTo(FRONTEND + "/login?google=error");
        verify(googleOAuth2Service, never()).findExistingUser(any());
    }

    @Test
    @DisplayName("redirect_noNonceCookie_isRejectedBeforeGoogleIsEvenAsked")
    void redirect_noNonceCookie_isRejected() throws Exception {
        MvcResult result = postCredential("google-token");

        assertThat(location(result)).isEqualTo(FRONTEND + "/login?google=error");
        verify(googleOAuth2Service, never()).verifyGoogleToken(any());
    }

    @Test
    @DisplayName("redirect_missingCredential_isRejected")
    void redirect_missingCredential_isRejected() throws Exception {
        MvcResult result = postCredential(null, new Cookie("GOOGLE_NONCE", NONCE));

        assertThat(location(result)).isEqualTo(FRONTEND + "/login?google=error");
        verify(googleOAuth2Service, never()).verifyGoogleToken(any());
    }

    @Test
    @DisplayName("redirect_invalidGoogleToken_isRejectedAndTheNonceIsStillBurned")
    void redirect_invalidGoogleToken_isRejected() throws Exception {
        when(googleOAuth2Service.verifyGoogleToken("forged")).thenThrow(new BadRequestException(ApiMessageKey.AUTH_GOOGLE_LOGIN_FAILED));

        MvcResult result = postCredential("forged", new Cookie("GOOGLE_NONCE", NONCE));

        assertThat(location(result)).isEqualTo(FRONTEND + "/login?google=error");
        assertThat(setCookies(result)).anyMatch(c -> c.startsWith("GOOGLE_NONCE=;") && c.contains("Max-Age=0"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("redirect_jsonBody_isNotAcceptedByThisEndpoint")
    void redirect_jsonBody_isNotAccepted() throws Exception {
        mvc.perform(post("/auth/google/redirect").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"x\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}

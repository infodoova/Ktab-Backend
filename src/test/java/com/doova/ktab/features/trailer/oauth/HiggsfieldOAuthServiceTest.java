package com.doova.ktab.features.trailer.oauth;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.model.TrailerOAuthState;
import com.doova.ktab.features.trailer.repository.TrailerOAuthStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HiggsfieldOAuthServiceTest {

    static final String METADATA = """
            {"issuer":"https://mcp.higgsfield.ai","authorization_endpoint":"https://mcp.higgsfield.ai/oauth2/authorize",
             "token_endpoint":"https://mcp.higgsfield.ai/oauth2/token","registration_endpoint":"https://mcp.higgsfield.ai/oauth2/register"}
            """;

    final TrailerOAuthStateRepository states = mock(TrailerOAuthStateRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    MockRestServiceServer server;
    HiggsfieldOAuthService service;

    @BeforeEach
    void setUp() {
        TrailerProperties p = new TrailerProperties();
        p.setHiggsfieldCallbackUrl("https://ktab.example/api/v1/public/trailer-agent/higgsfield/callback");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new HiggsfieldOAuthService(builder, states, gateway, p);
        when(states.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void beginRegistersAPublicClientAndReturnsAPkceAuthorizeUrl() {
        server.expect(requestTo("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server"))
                .andRespond(withSuccess(METADATA, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mcp.higgsfield.ai/oauth2/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
                .andExpect(jsonPath("$.redirect_uris[0]").value("https://ktab.example/api/v1/public/trailer-agent/higgsfield/callback"))
                .andRespond(withSuccess("{\"client_id\":\"cid-1\"}", MediaType.APPLICATION_JSON));

        String url = service.begin(9L);

        assertThat(url).startsWith("https://mcp.higgsfield.ai/oauth2/authorize?")
                .contains("client_id=cid-1").contains("code_challenge_method=S256").contains("response_type=code")
                .contains("offline_access").contains("resource=");
        ArgumentCaptor<TrailerOAuthState> saved = ArgumentCaptor.forClass(TrailerOAuthState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().getClientId()).isEqualTo("cid-1");
        assertThat(saved.getValue().getAdminUserId()).isEqualTo(9L);
    }

    @Test
    void completeExchangesTheCodeAndStoresTokensInTheVault() {
        TrailerOAuthState st = new TrailerOAuthState();
        st.setState("s1");
        st.setCodeVerifier("v1");
        st.setClientId("cid-1");
        st.setRedirectUri("https://ktab.example/cb");
        st.setAdminUserId(9L);
        st.setCreatedAt(java.time.LocalDateTime.now());
        when(states.findByStateAndUsedFalse("s1")).thenReturn(Optional.of(st));
        server.expect(requestTo("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server"))
                .andRespond(withSuccess(METADATA, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mcp.higgsfield.ai/oauth2/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=v1")))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        service.complete("code-1", "s1");

        ArgumentCaptor<TrailerAgentGateway.HiggsfieldTokens> tokens = ArgumentCaptor.forClass(TrailerAgentGateway.HiggsfieldTokens.class);
        verify(gateway).upsertHiggsfieldCredential(tokens.capture());
        assertThat(tokens.getValue().refreshToken()).isEqualTo("rt");
        assertThat(tokens.getValue().tokenEndpoint()).isEqualTo("https://mcp.higgsfield.ai/oauth2/token");
        assertThat(st.isUsed()).isTrue();
    }

    @Test
    void unknownOrReusedStateIsRejected() {
        when(states.findByStateAndUsedFalse("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete("c", "nope")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aTokenWithoutARefreshTokenIsRejectedBecauseTheVaultCouldNotKeepItAlive() {
        TrailerOAuthState st = new TrailerOAuthState();
        st.setState("s2");
        st.setCodeVerifier("v");
        st.setClientId("c");
        st.setRedirectUri("r");
        st.setAdminUserId(9L);
        st.setCreatedAt(java.time.LocalDateTime.now());
        when(states.findByStateAndUsedFalse("s2")).thenReturn(Optional.of(st));
        server.expect(requestTo("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server"))
                .andRespond(withSuccess(METADATA, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mcp.higgsfield.ai/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.complete("c", "s2")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(gateway);
    }
}

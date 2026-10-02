package com.doova.ktab.features.trailer.oauth;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.model.TrailerOAuthState;
import com.doova.ktab.features.trailer.repository.TrailerOAuthStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * D8: admin connects Ktab's Higgsfield account once; tokens go straight to the Anthropic vault as mcp_oauth with a refresh
 * block, after which Anthropic refreshes them. Endpoints are discovered from Higgsfield's RFC 8414 metadata at runtime.
 */
@Service
public class HiggsfieldOAuthService {

    static final String SCOPE = "openid email offline_access";

    private final RestClient http;
    private final TrailerOAuthStateRepository states;
    private final TrailerAgentGateway gateway;
    private final TrailerProperties properties;

    @Autowired
    public HiggsfieldOAuthService(TrailerOAuthStateRepository states, TrailerAgentGateway gateway,
                                  TrailerProperties properties) {
        this(RestClient.builder(), states, gateway, properties);
    }

    HiggsfieldOAuthService(RestClient.Builder builder, TrailerOAuthStateRepository states, TrailerAgentGateway gateway,
                           TrailerProperties properties) {
        this.http = builder.build();
        this.states = states;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Transactional
    public String begin(Long adminUserId) {
        JsonNode meta = metadata();
        String redirect = properties.getHiggsfieldCallbackUrl();
        JsonNode reg = http.post().uri(meta.path("registration_endpoint").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "client_name", "Ktab trailer agent",
                        "redirect_uris", List.of(redirect),
                        "grant_types", List.of("authorization_code", "refresh_token"),
                        "response_types", List.of("code"),
                        "token_endpoint_auth_method", "none",
                        "scope", SCOPE))
                .retrieve().body(JsonNode.class);
        String clientId = reg == null ? null : reg.path("client_id").asText(null);
        if (clientId == null) {
            throw new IllegalStateException("Higgsfield did not return a client_id on registration");
        }

        TrailerOAuthState st = new TrailerOAuthState();
        st.setState(Pkce.state());
        st.setCodeVerifier(Pkce.verifier());
        st.setClientId(clientId);
        st.setRedirectUri(redirect);
        st.setAdminUserId(adminUserId);
        states.save(st);

        return UriComponentsBuilder.fromUriString(meta.path("authorization_endpoint").asText())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirect)
                .queryParam("scope", SCOPE)
                .queryParam("state", st.getState())
                .queryParam("code_challenge", Pkce.challenge(st.getCodeVerifier()))
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", properties.getHiggsfieldMcpUrl())
                .encode().build().toUriString();
    }

    @Transactional
    public void complete(String code, String state) {
        TrailerOAuthState st = states.findByStateAndUsedFalse(state)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.TRAILER_OAUTH_INVALID));
        if (st.getCreatedAt() == null || st.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
            throw new BadRequestException(ApiMessageKey.TRAILER_OAUTH_INVALID);
        }
        st.setUsed(true); // single use, even if the exchange below fails

        String tokenEndpoint = metadata().path("token_endpoint").asText();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", st.getRedirectUri());
        form.add("client_id", st.getClientId());
        form.add("code_verifier", st.getCodeVerifier());
        form.add("resource", properties.getHiggsfieldMcpUrl());
        JsonNode token = http.post().uri(tokenEndpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);

        String refresh = token == null ? null : token.path("refresh_token").asText(null);
        if (refresh == null) {
            // Without a refresh token the vault credential dies at expiry; make the admin retry with offline_access.
            throw new BadRequestException(ApiMessageKey.TRAILER_OAUTH_INVALID);
        }
        gateway.upsertHiggsfieldCredential(new TrailerAgentGateway.HiggsfieldTokens(
                token.path("access_token").asText(), refresh,
                OffsetDateTime.now().plusSeconds(token.path("expires_in").asLong(3600)),
                st.getClientId(), tokenEndpoint));
    }

    private JsonNode metadata() {
        String base = UriComponentsBuilder.fromUriString(properties.getHiggsfieldMcpUrl()).replacePath(null).build().toUriString();
        JsonNode meta = http.get().uri(base + "/.well-known/oauth-authorization-server").retrieve().body(JsonNode.class);
        if (meta == null || !meta.hasNonNull("token_endpoint")) {
            throw new IllegalStateException("Higgsfield OAuth metadata unavailable");
        }
        return meta;
    }
}

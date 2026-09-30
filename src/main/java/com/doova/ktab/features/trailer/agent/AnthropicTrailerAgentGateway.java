package com.doova.ktab.features.trailer.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.UnwrapWebhookParams;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.models.beta.files.FileListParams;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileUploadParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsAgentParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsFileResourceParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsSession;
import com.anthropic.models.beta.sessions.SessionCreateParams;
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsSessionEvent;
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserInterruptEventParams;
import com.anthropic.models.beta.sessions.events.EventSendParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsMcpOAuthCreateParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsMcpOAuthRefreshParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsTokenEndpointAuthNoneParam;
import com.anthropic.models.beta.vaults.credentials.CredentialCreateParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsCredential;
import com.anthropic.models.beta.vaults.credentials.CredentialArchiveParams;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only class that talks to the Anthropic SDK for trailers. Not an AnthropicClient bean on purpose: storybook injects
 * AnthropicClient by type, and a second bean would make that injection ambiguous.
 * Type names verified with javap against anthropic-java-core 2.34.0; see Global Constraints for the raw-JSON exceptions.
 */
@Component
@Slf4j
public class AnthropicTrailerAgentGateway implements TrailerAgentGateway {

    static final String MANAGED_AGENTS_BETA = "managed-agents-2026-04-01";

    private final AnthropicClient client;
    private final TrailerProperties properties;
    private final ObjectMapper sdkJson = ObjectMappers.jsonMapper();

    public AnthropicTrailerAgentGateway(TrailerProperties properties) {
        this.properties = properties;
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
        if (properties.getAnthropicApiKey() != null && !properties.getAnthropicApiKey().isBlank()) {
            builder.apiKey(properties.getAnthropicApiKey());
        } else {
            builder.apiKey("missing-anthropic-api-key"); // lets the app start with the feature off
        }
        builder.timeout(java.time.Duration.ofMinutes(10));
        builder.maxRetries(3);
        this.client = builder.build();
    }

    @Override
    public String uploadBook(Path pdf) {
        FileMetadata file = client.beta().files().upload(FileUploadParams.builder().file(pdf).build());
        return file.id();
    }

    @Override
    public String uploadFile(Path image) {
        FileMetadata file = client.beta().files().upload(FileUploadParams.builder().file(image).build());
        return file.id();
    }

    @Override
    public String startSession(long trailerId, String fileId, List<SessionFile> files, String taskDescription, String rubric) {
        // D3: outcome kickoff in initial_events so create + kickoff are one atomic call.
        // D4: budget. Neither has a typed setter in anthropic-java 2.34.0, so both go as raw body properties.
        JsonValue initialEvents = JsonValue.from(List.of(Map.of(
                "type", "user.define_outcome",
                "description", taskDescription,
                "rubric", Map.of("type", "text", "content", rubric),
                "max_iterations", properties.getMaxOutcomeIterations())));
        JsonValue budget = JsonValue.from(Map.of("type", "limit",
                "max_list_cost", Map.of("amount", Long.toString(properties.getBudgetCents()), "currency", "USD")));

        SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                .agent(BetaManagedAgentsAgentParams.builder()
                        .type(BetaManagedAgentsAgentParams.Type.AGENT)
                        .id(properties.getAgentId())
                        .version(properties.getAgentVersion())
                        .build())
                .environmentId(properties.getEnvironmentId())
                .addVaultId(properties.getVaultId())
                .addResource(BetaManagedAgentsFileResourceParams.builder()
                        .type(BetaManagedAgentsFileResourceParams.Type.FILE)
                        .fileId(fileId)
                        .mountPath("/workspace/book.pdf")
                        .build())
                .title("ktab-trailer-" + trailerId)
                .metadata(SessionCreateParams.Metadata.builder()
                        .putAdditionalProperty("ktab_trailer_id", JsonValue.from(Long.toString(trailerId)))
                        .build())
                .putAdditionalBodyProperty("initial_events", initialEvents)
                .putAdditionalBodyProperty("budget", budget);

        for (SessionFile f : files) {
            sessionBuilder.addResource(BetaManagedAgentsFileResourceParams.builder()
                    .type(BetaManagedAgentsFileResourceParams.Type.FILE)
                    .fileId(f.fileId())
                    .mountPath(f.mountPath())
                    .build());
        }

        BetaManagedAgentsSession session = client.beta().sessions().create(sessionBuilder.build());
        log.info("trailer {} session {} started: {}", trailerId, session.id(), traceUrl(session.id()));
        return session.id();
    }

    @Override
    public SessionSnapshot snapshot(String sessionId) {
        BetaManagedAgentsSession session = client.beta().sessions().retrieve(sessionId);
        List<JsonNode> events = new ArrayList<>();
        for (BetaManagedAgentsSessionEvent event : client.beta().sessions().events().list(sessionId).autoPager()) {
            events.add(sdkJson.valueToTree(event));
        }
        List<SessionEvents.OutcomeResult> outcomes = session.outcomeEvaluations().stream()
                .map(o -> new SessionEvents.OutcomeResult(o.result(), o.explanation().orElse(null)))
                .toList();
        return SessionEvents.interpret(session.status().asString(), events, outcomes,
                properties.getHiggsfieldGenerationMarker(),
                java.util.regex.Pattern.compile(properties.getHiggsfieldJobPattern()));
    }

    @Override
    public void interrupt(String sessionId) {
        client.beta().sessions().events().send(sessionId, EventSendParams.builder()
                .addEvent(BetaManagedAgentsUserInterruptEventParams.builder()
                        .type(BetaManagedAgentsUserInterruptEventParams.Type.USER_INTERRUPT)
                        .build())
                .build());
    }

    @Override
    public void sendMessage(String sessionId, String text) {
        try {
            client.beta().sessions().events().send(sessionId, EventSendParams.builder()
                    .putAdditionalBodyProperty("events", JsonValue.from(List.of(Map.of(
                            "type", "user.message",
                            "content", List.of(Map.of("type", "text", "text", text))))))
                    .build());
        } catch (RuntimeException e) {
            log.warn("sendMessage to session {} failed: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public List<OutputFile> outputs(String sessionId) {
        List<OutputFile> files = new ArrayList<>();
        for (FileMetadata f : client.beta().files().list(FileListParams.builder()
                .scopeId(sessionId)
                .addBeta(MANAGED_AGENTS_BETA) // scope_id needs the Managed Agents beta header (environments doc)
                .build()).autoPager()) {
            files.add(new OutputFile(f.id(), f.filename(), f.sizeBytes()));
        }
        return files;
    }

    @Override
    public void download(String fileId, Path target) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.deleteIfExists(target);
                try (HttpResponse response = client.beta().files().download(fileId);
                     InputStream body = response.body()) {
                    Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) { }
                if (attempt == maxAttempts) {
                    throw new UncheckedIOException(e);
                }
                log.warn("Download of file {} failed on attempt {}/{}: {}. Retrying in {}s...",
                        fileId, attempt, maxAttempts, e.getMessage(), attempt * 2);
                try {
                    Thread.sleep(attempt * 2000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    @Override
    public void archive(String sessionId) {
        try {
            client.beta().sessions().archive(sessionId);
        } catch (RuntimeException e) {
            // SSE idle can precede the queryable status (client-patterns); the next reconcile retries.
            log.info("archive of {} deferred: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public void upsertHiggsfieldCredential(HiggsfieldTokens t) {
        // Keys (mcp_server_url, client_id, token_endpoint) are immutable, so archive any existing Higgsfield credential
        // and create a fresh one; a vault allows one active credential per mcp_server_url.
        for (BetaManagedAgentsCredential c : client.beta().vaults().credentials().list(properties.getVaultId()).autoPager()) {
            JsonNode auth = sdkJson.valueToTree(c).path("auth");
            if (properties.getHiggsfieldMcpUrl().equals(auth.path("mcp_server_url").asText())
                    && c.archivedAt().isEmpty()) {
                client.beta().vaults().credentials().archive(c.id(), CredentialArchiveParams.builder()
                        .vaultId(properties.getVaultId()).build());
            }
        }
        client.beta().vaults().credentials().create(properties.getVaultId(), CredentialCreateParams.builder()
                .displayName("Higgsfield MCP (Ktab)")
                .auth(BetaManagedAgentsMcpOAuthCreateParams.builder()
                        .type(BetaManagedAgentsMcpOAuthCreateParams.Type.MCP_OAUTH)
                        .mcpServerUrl(properties.getHiggsfieldMcpUrl())
                        .accessToken(t.accessToken())
                        .expiresAt(t.expiresAt())
                        .refresh(BetaManagedAgentsMcpOAuthRefreshParams.builder()
                                .tokenEndpoint(t.tokenEndpoint())
                                .clientId(t.clientId())
                                .refreshToken(t.refreshToken())
                                .resource(properties.getHiggsfieldMcpUrl())
                                .tokenEndpointAuth(BetaManagedAgentsTokenEndpointAuthNoneParam.builder()
                                         .type(BetaManagedAgentsTokenEndpointAuthNoneParam.Type.NONE)
                                        .build())
                                .build())
                        .build())
                .build());
    }

    @Override
    public Optional<WebhookNotice> verifyWebhook(String body, Map<String, String> headers) {
        Headers.Builder h = Headers.builder();
        headers.forEach(h::put);
        try {
            var event = client.beta().webhooks().unwrap(UnwrapWebhookParams.builder()
                    .body(body)
                    .headers(h.build())
                    .secret(properties.getWebhookSigningKey())
                    .build());
            JsonNode data = sdkJson.readTree(body).path("data"); // body is now verified
            return Optional.of(new WebhookNotice(event.id(), data.path("type").asText(), data.path("id").asText()));
        } catch (RuntimeException | IOException e) {
            log.warn("trailer webhook rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String traceUrl(String sessionId) {
        return "https://platform.claude.com/workspaces/" + properties.getWorkspace() + "/sessions/" + sessionId;
    }
}

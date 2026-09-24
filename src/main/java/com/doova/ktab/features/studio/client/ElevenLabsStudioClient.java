package com.doova.ktab.features.studio.client;

import com.doova.ktab.features.studio.client.dto.*;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the ElevenLabs Studio API (REST, not the WebSocket TTS path handled by
 * {@link com.doova.ktab.features.tts.config.ElevenLabsClient}).
 *
 * <p><strong>Error handling contract (docs/ocr_engine_v3.md, Phase 4.4):</strong>
 * <ul>
 *   <li>Network / IO failures and HTTP 429 / 5xx &rarr; {@link StudioApiException.Retryable}</li>
 *   <li>Any other 4xx (auth, bad request, not found) &rarr; {@link StudioApiException.Fatal}</li>
 * </ul>
 * The caller is responsible for retry/backoff; this class does not loop internally.
 *
 * <p><strong>Retry-After:</strong> When the server returns a {@code Retry-After} header,
 * {@link StudioApiException.Retryable} is thrown as a {@link RetryableWithDelay} subclass
 * so callers can honour it without reparsing the header themselves.
 *
 * <p><strong>Field-name accuracy note:</strong> DTO field names were written against the
 * ElevenLabs Studio API spec as it stood when the pipeline was designed. Verify against
 * the <a href="https://elevenlabs.io/docs/api-reference/studio">live reference</a> before
 * deploying to production. All DTOs carry {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * so additive API changes are safe.
 */
@Component
@Slf4j
public class ElevenLabsStudioClient {

    private static final String BASE_PROJECTS_PATH = "/v1/studio/projects";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final StudioProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    @Autowired
    public ElevenLabsStudioClient(StudioProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build());
    }

    ElevenLabsStudioClient(StudioProperties props, ObjectMapper objectMapper, HttpClient http) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = http;
    }

    // =========================================================================
    // Projects
    // =========================================================================

    /**
     * Create a new Studio project for the book.
     *
     * @param title            human-readable title
     * @param fromUrl          publicly accessible PDF URL that Studio will parse
     * @param modelId          override; falls back to {@code ktab.studio.default-model-id} when null
     * @param titleVoiceId     override; falls back to {@code ktab.studio.default-title-voice-id}
     * @param paragraphVoiceId override; falls back to {@code ktab.studio.default-paragraph-voice-id}
     */
    public StudioProjectResponse createProject(
            String title,
            String fromUrl,
            String modelId,
            String titleVoiceId,
            String paragraphVoiceId
    ) {
        Map<String, Object> body = Map.of(
                "name", title,
                "from_url", fromUrl,
                "default_model_id", coalesce(modelId, props.getDefaultModelId()),
                "default_title_voice_id", coalesce(titleVoiceId, props.getDefaultTitleVoiceId()),
                "default_paragraph_voice_id", coalesce(paragraphVoiceId, props.getDefaultParagraphVoiceId()),
                "quality_preset", props.getQualityPreset()
        );

        HttpRequest request = buildJson(post(BASE_PROJECTS_PATH), body);
        log.debug("studio.createProject title={}", title);
        return execute(request, StudioProjectResponse.class);
    }

    /**
     * Fetch project metadata (does not include chapter content).
     */
    public StudioProjectResponse getProject(String externalProjectId) {
        HttpRequest request = buildGet(BASE_PROJECTS_PATH + "/" + externalProjectId);
        log.debug("studio.getProject projectId={}", externalProjectId);
        return execute(request, StudioProjectResponse.class);
    }

    /**
     * List all projects visible under the API key — used by the hourly orphan reconciler
     * (docs/ocr_engine_v3.md, Phase 4.5) to left-join against {@code tbl_studio_projects}.
     */
    public List<StudioProjectResponse> listProjects() {
        HttpRequest request = buildGet(BASE_PROJECTS_PATH);
        log.debug("studio.listProjects");

        JsonNode root = execute(request, JsonNode.class);
        return parseList(root, "projects", new TypeReference<List<StudioProjectResponse>>() {});
    }

    /**
     * Delete the Studio project at ElevenLabs. Terminal — do not call until all audio has
     * been verified and written to {@code tbl_book_audio_chapters}. See Phase 4 cleanupStep.
     */
    public void deleteProject(String externalProjectId) {
        HttpRequest request = buildDelete(BASE_PROJECTS_PATH + "/" + externalProjectId);
        log.info("studio.deleteProject projectId={}", externalProjectId);
        executeVoid(request);
    }

    // =========================================================================
    // Chapters — Tier 1 status sync (Phase 3.7)
    // =========================================================================

    /**
     * List all chapter summaries for the project. Used for Tier 1 (status) polling —
     * touches only conversion progress, error text and timestamps; never fetches content.
     *
     * @return list ordered as returned by the API
     */
    public List<StudioChapterSummary> listChapters(String externalProjectId) {
        HttpRequest request = buildGet(BASE_PROJECTS_PATH + "/" + externalProjectId + "/chapters");
        log.debug("studio.listChapters projectId={}", externalProjectId);

        // API wraps the array in an envelope: { "chapters": [...] }
        JsonNode root = execute(request, JsonNode.class);
        return parseList(root, "chapters", new TypeReference<List<StudioChapterSummary>>() {});
    }

    // =========================================================================
    // Chapters — Tier 2 content sync / push path (Phase 3.7, 4.1)
    // =========================================================================

    /**
     * Fetch the full chapter content for a Tier 2 sync. Only called after checking
     * {@code col_content_hash} to avoid redundant rewrites of the section subtree.
     */
    public StudioChapterDetail getChapter(String externalProjectId, String externalChapterId) {
        String path = BASE_PROJECTS_PATH + "/" + externalProjectId
                + "/chapters/" + externalChapterId;
        HttpRequest request = buildGet(path);
        log.debug("studio.getChapter projectId={} chapterId={}", externalProjectId, externalChapterId);
        return execute(request, StudioChapterDetail.class);
    }

    /**
     * Create a chapter inside an existing project with our own content (OCR push path —
     * Phase 4.1). The caller writes {@code col_external_chapter_id} onto the section row
     * in the same transaction after a successful response.
     *
     * @param name    chapter display name
     * @param content HTML/Markdown; headings become {@code h1/h2/h3}, paragraphs become {@code p}
     */
    public StudioChapterDetail createChapter(
            String externalProjectId,
            String name,
            String content
    ) {
        Map<String, Object> body = Map.of(
                "name", name,
                "content", content
        );
        String path = BASE_PROJECTS_PATH + "/" + externalProjectId + "/chapters/";
        HttpRequest request = buildJson(post(path), body);
        log.debug("studio.createChapter projectId={} name={}", externalProjectId, name);
        return execute(request, StudioChapterDetail.class);
    }

    // =========================================================================
    // Conversion
    // =========================================================================

    /**
     * Fire TTS conversion for the whole project. One call per book; subsequent status
     * polling is done via {@link #listChapters}. Returns immediately — conversion is
     * asynchronous on ElevenLabs' side (Phase 4 convertStep).
     */
    public void convertProject(String externalProjectId) {
        String path = BASE_PROJECTS_PATH + "/" + externalProjectId + "/convert";
        HttpRequest request = buildJson(post(path), Map.of());
        log.info("studio.convertProject projectId={}", externalProjectId);
        executeVoid(request);
    }

    // =========================================================================
    // Snapshots & audio download (Phase 4.3)
    // =========================================================================

    /**
     * List snapshots for a chapter. ElevenLabs retains only the latest conversion output;
     * pick the first element with {@code can_be_downloaded = true}.
     */
    public List<StudioSnapshotSummary> listSnapshots(
            String externalProjectId,
            String externalChapterId
    ) {
        String path = BASE_PROJECTS_PATH + "/" + externalProjectId
                + "/chapters/" + externalChapterId + "/snapshots";
        HttpRequest request = buildGet(path);
        log.debug("studio.listSnapshots projectId={} chapterId={}", externalProjectId, externalChapterId);

        JsonNode root = execute(request, JsonNode.class);
        return parseList(root, "snapshots", new TypeReference<List<StudioSnapshotSummary>>() {});
    }

    /**
     * Open an {@link InputStream} over the MP3 audio for a chapter snapshot. The caller
     * <em>must</em> close the stream. Pass directly to an S3 multipart upload to avoid
     * buffering to local disk (Phase 4.3).
     */
    public InputStream streamAudio(
            String externalProjectId,
            String externalChapterId,
            String snapshotId
    ) {
        String path = BASE_PROJECTS_PATH + "/" + externalProjectId
                + "/chapters/" + externalChapterId
                + "/snapshots/" + snapshotId + "/stream";
        HttpRequest request = buildGet(path);
        log.debug("studio.streamAudio projectId={} chapterId={} snapshotId={}",
                externalProjectId, externalChapterId, snapshotId);
        return executeStream(request);
    }

    /**
     * Fetch character-level alignment data for a snapshot. Returns parallel arrays
     * (characters / startTimesSeconds / endTimesSeconds); use
     * {@link StudioCharacterAlignment#toTimings()} to convert to indexed records. See
     * Phase 3.6 for the R2 timing-index format.
     */
    public StudioCharacterAlignment getAlignment(
            String externalProjectId,
            String externalChapterId,
            String snapshotId
    ) {
        String path = BASE_PROJECTS_PATH + "/" + externalProjectId
                + "/chapters/" + externalChapterId
                + "/snapshots/" + snapshotId + "/alignment";
        HttpRequest request = buildGet(path);
        log.debug("studio.getAlignment projectId={} chapterId={} snapshotId={}",
                externalProjectId, externalChapterId, snapshotId);
        return execute(request, StudioCharacterAlignment.class);
    }

    // =========================================================================
    // HTTP execution helpers
    // =========================================================================

    private <T> T execute(HttpRequest request, Class<T> type) {
        HttpResponse<String> response = sendForString(request);
        checkStatus(response);
        return parseBody(response.body(), type);
    }

    private void executeVoid(HttpRequest request) {
        HttpResponse<String> response = sendForString(request);
        checkStatus(response);
    }

    private InputStream executeStream(HttpRequest request) {
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new StudioApiException.Retryable(
                    "Network failure streaming audio: " + e.getMessage(), -1, e);
        }
        checkStatus(response);
        return response.body();
    }

    private HttpResponse<String> sendForString(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new StudioApiException.Retryable(
                    "Network failure: " + e.getMessage(), -1, e);
        }
    }

    /**
     * Translates an HTTP status into the right exception subtype.
     * 2xx passes through silently. 429 / 5xx are retryable; other 4xx are fatal.
     * A {@code Retry-After} header on a retryable response is surfaced as
     * {@link RetryableWithDelay}.
     */
    private <T> void checkStatus(HttpResponse<T> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;

        String body = response.body() instanceof String s ? s : "(binary)";
        String msg = "ElevenLabs Studio API error status=" + status
                + " uri=" + response.uri()
                + " body=" + abbreviate(body, 200);

        if (status == 429 || status >= 500) {
            long retryAfterMs = parseRetryAfterMs(response);
            if (retryAfterMs > 0) {
                throw new RetryableWithDelay(msg, status, retryAfterMs);
            }
            throw new StudioApiException.Retryable(msg, status);
        }

        throw new StudioApiException.Fatal(msg, status);
    }

    private long parseRetryAfterMs(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").map(val -> {
            try {
                return Long.parseLong(val.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }).orElse(0L);
    }

    private <T> T parseBody(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new StudioApiException.Fatal(
                    "Failed to deserialise response into " + type.getSimpleName()
                            + ": " + e.getMessage(), -1);
        }
    }

    private <T> List<T> parseList(JsonNode root, String field, TypeReference<List<T>> ref) {
        JsonNode node = root.path(field);
        try {
            return objectMapper.convertValue(node, ref);
        } catch (IllegalArgumentException e) {
            throw new StudioApiException.Fatal(
                    "Failed to parse '" + field + "' list: " + e.getMessage(), -1);
        }
    }

    // =========================================================================
    // Request builders
    // =========================================================================

    private HttpRequest buildGet(String path) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(uri(path))
                .header("xi-api-key", props.getApiKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    private HttpRequest buildDelete(String path) {
        return HttpRequest.newBuilder()
                .DELETE()
                .uri(uri(path))
                .header("xi-api-key", props.getApiKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /** Returns a POST builder — complete via {@link #buildJson}. */
    private HttpRequest.Builder post(String path) {
        return HttpRequest.newBuilder()
                .uri(uri(path))
                .header("xi-api-key", props.getApiKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60));
    }

    private HttpRequest buildJson(HttpRequest.Builder builder, Map<String, Object> body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                    .build();
        } catch (Exception e) {
            throw new StudioApiException.Fatal(
                    "Failed to serialise request body: " + e.getMessage(), -1);
        }
    }

    private URI uri(String path) {
        return URI.create(props.getBaseUrl() + path);
    }

    private static String coalesce(String first, String second) {
        return (first != null && !first.isBlank()) ? first : second;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\u2026";
    }

    // =========================================================================
    // RetryableWithDelay subtype
    // =========================================================================

    /**
     * Retryable exception that carries the server-requested delay from a {@code Retry-After}
     * header. Callers can {@code instanceof}-check for this subtype and honour the hint:
     * <pre>{@code
     * } catch (StudioApiException.Retryable e) {
     *     long wait = e instanceof RetryableWithDelay r ? r.retryAfterMs() : defaultBackoffMs;
     *     Thread.sleep(wait);
     * }</pre>
     */
    public static final class RetryableWithDelay extends StudioApiException.Retryable {

        private final long retryAfterMs;

        public RetryableWithDelay(String message, int statusCode, long retryAfterMs) {
            super(message, statusCode);
            this.retryAfterMs = retryAfterMs;
        }

        /** How long the caller should wait before the next attempt, in milliseconds. */
        public long retryAfterMs() {
            return retryAfterMs;
        }
    }
}


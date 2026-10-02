package com.doova.ktab.features.trailer.agent;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything Ktab does against Claude Managed Agents. The pipeline and tests depend on this, not on the SDK. */
public interface TrailerAgentGateway {

    record OutputFile(String id, String filename, long sizeBytes) {
    }

    /** A file uploaded to the Files API that the session mounts read-only at {@code mountPath}. */
    record SessionFile(String fileId, String mountPath) {
    }

    record HiggsfieldTokens(String accessToken, String refreshToken, OffsetDateTime expiresAt, String clientId,
                            String tokenEndpoint) {
    }

    record WebhookNotice(String eventId, String type, String resourceId) {
    }

    String uploadBook(Path pdf);

    /** Uploads any file (cover, end-card layer) and returns its file id. */
    String uploadFile(Path file);

    String startSession(long trailerId, String bookFileId, List<SessionFile> files, String taskDescription, String rubric);

    default String startSession(long trailerId, String bookFileId, String taskDescription, String rubric) {
        return startSession(trailerId, bookFileId, List.of(), taskDescription, rubric);
    }

    SessionSnapshot snapshot(String sessionId);

    void interrupt(String sessionId);

    void sendMessage(String sessionId, String text);

    List<OutputFile> outputs(String sessionId);

    void download(String fileId, Path target);

    void archive(String sessionId);

    void upsertHiggsfieldCredential(HiggsfieldTokens tokens);

    /** Verifies the HMAC signature; empty when the signature is invalid. */
    Optional<WebhookNotice> verifyWebhook(String body, Map<String, String> headers);

    String traceUrl(String sessionId);
}

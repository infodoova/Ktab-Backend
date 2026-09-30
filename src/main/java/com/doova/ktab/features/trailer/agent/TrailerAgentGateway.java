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

    record HiggsfieldTokens(String accessToken, String refreshToken, OffsetDateTime expiresAt, String clientId,
                            String tokenEndpoint) {
    }

    record WebhookNotice(String eventId, String type, String resourceId) {
    }

    String uploadBook(Path pdf);

    String uploadCover(Path image);

    String startSession(long trailerId, String bookFileId, String coverFileId, String taskDescription, String rubric);

    default String startSession(long trailerId, String bookFileId, String taskDescription, String rubric) {
        return startSession(trailerId, bookFileId, null, taskDescription, rubric);
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

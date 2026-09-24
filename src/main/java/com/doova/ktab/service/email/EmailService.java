package com.doova.ktab.service.email;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.dto.mail.EmailResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

    /**
     * Asynchronously sends an email on the dedicated bounded mailTaskExecutor.
     * Preserves MDC correlationId.
     *
     * @param request the fluent email request
     * @return CompletableFuture resolving to EmailResult
     */
    CompletableFuture<EmailResult> sendAsync(EmailRequest request);

    /**
     * Synchronously sends an email on the caller's thread.
     *
     * @param request the fluent email request
     * @return EmailResult outcome
     */
    EmailResult sendSync(EmailRequest request);

    /**
     * Default fire-and-forget async email dispatch.
     *
     * @param request the fluent email request
     */
    default void send(EmailRequest request) {
        sendAsync(request);
    }

    /**
     * Sends the email strictly after the current database transaction commits.
     * If no transaction is active, dispatches immediately.
     *
     * @param request the fluent email request
     */
    void sendAfterCommit(EmailRequest request);

    // =========================================================================
    // Legacy / Backward-Compatible Convenience Signatures
    // =========================================================================

    /**
     * Sends a templated HTML email asynchronously (legacy signature).
     */
    void sendHtml(String to, String subject, String templateName, Map<String, Object> variables);

    /**
     * Sends a simple text email asynchronously (legacy signature).
     */
    void sendText(String to, String subject, String body);
}

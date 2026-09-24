package com.doova.ktab.dto.mail;

import lombok.Builder;

import java.time.Instant;

@Builder
public record EmailResult(
        boolean success,
        EmailDeliveryStatus status,
        String messageId,
        Instant sentAt,
        String errorMessage,
        int recipientCount
) {
    public static EmailResult success(String messageId, int recipientCount) {
        return EmailResult.builder()
                .success(true)
                .status(EmailDeliveryStatus.SENT)
                .messageId(messageId)
                .sentAt(Instant.now())
                .recipientCount(recipientCount)
                .build();
    }

    public static EmailResult failed(String errorMessage, int recipientCount) {
        return EmailResult.builder()
                .success(false)
                .status(EmailDeliveryStatus.FAILED)
                .sentAt(Instant.now())
                .errorMessage(errorMessage)
                .recipientCount(recipientCount)
                .build();
    }

    public static EmailResult queued(int recipientCount) {
        return EmailResult.builder()
                .success(true)
                .status(EmailDeliveryStatus.QUEUED)
                .sentAt(Instant.now())
                .recipientCount(recipientCount)
                .build();
    }

    public static EmailResult skipped(String reason) {
        return EmailResult.builder()
                .success(true)
                .status(EmailDeliveryStatus.SKIPPED)
                .sentAt(Instant.now())
                .errorMessage(reason)
                .recipientCount(0)
                .build();
    }
}

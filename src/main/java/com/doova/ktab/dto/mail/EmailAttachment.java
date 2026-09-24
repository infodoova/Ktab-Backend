package com.doova.ktab.dto.mail;

import lombok.Builder;

import java.util.Objects;

@Builder
public record EmailAttachment(
        String filename,
        byte[] data,
        String contentType,
        boolean inline,
        String contentId
) {
    public EmailAttachment {
        Objects.requireNonNull(filename, "Attachment filename must not be null");
        Objects.requireNonNull(data, "Attachment data must not be null");
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
    }

    public static EmailAttachment of(String filename, byte[] data, String contentType) {
        return EmailAttachment.builder()
                .filename(filename)
                .data(data)
                .contentType(contentType)
                .inline(false)
                .build();
    }

    public static EmailAttachment inline(String filename, byte[] data, String contentType, String contentId) {
        return EmailAttachment.builder()
                .filename(filename)
                .data(data)
                .contentType(contentType)
                .inline(true)
                .contentId(contentId)
                .build();
    }
}

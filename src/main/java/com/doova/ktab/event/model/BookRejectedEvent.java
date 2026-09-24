package com.doova.ktab.event.model;

import java.time.Instant;
import java.util.Objects;

public record BookRejectedEvent(
        Long bookId,
        String bookTitle,
        String recipientEmail,
        String recipientName,
        String reviewNote,
        String reviewerName,
        Instant occurredAt
) {
    public BookRejectedEvent {
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(bookTitle, "bookTitle must not be null");
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public BookRejectedEvent(Long bookId, String bookTitle, String recipientEmail, String recipientName, String reviewNote, String reviewerName) {
        this(bookId, bookTitle, recipientEmail, recipientName, reviewNote, reviewerName, Instant.now());
    }
}

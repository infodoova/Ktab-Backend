package com.doova.ktab.event.model;

import java.time.Instant;
import java.util.Objects;

public record BookPublishedEvent(
        Long bookId,
        String pdfKey,
        Instant occurredAt
) {
    public BookPublishedEvent {
        Objects.requireNonNull(bookId, "bookId must not be null");
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public BookPublishedEvent(Long bookId, String pdfKey) {
        this(bookId, pdfKey, Instant.now());
    }
}

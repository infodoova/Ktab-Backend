package com.doova.ktab.event.model;

import com.doova.ktab.dto.mail.EmailRequest;

import java.time.Instant;
import java.util.Objects;

public record SendEmailEvent(
        EmailRequest request,
        Instant occurredAt
) {
    public SendEmailEvent {
        Objects.requireNonNull(request, "EmailRequest must not be null");
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public SendEmailEvent(EmailRequest request) {
        this(request, Instant.now());
    }
}

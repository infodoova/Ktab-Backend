package com.doova.ktab.features.trailer.web;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;

import java.time.Instant;

public record TrailerView(Long id, Long bookId, TrailerStatus status, String outcomeResult, String error,
                          Integer higgsfieldGenerations, Instant startedAt, Instant finishedAt,
                          boolean notifyByEmail) {

    /** Factory used on create — notifyByEmail = true. */
    public static TrailerView of(BookTrailer t, boolean notifyByEmail) {
        return new TrailerView(
                t.getId(), t.getBookId(), t.getStatus(), t.getOutcomeResult(), t.getError(),
                t.getHiggsfieldGenerations(), t.getStartedAt(), t.getFinishedAt(),
                notifyByEmail);
    }

    /** Factory used for reads (list / get / review) — notifyByEmail = false. */
    public static TrailerView of(BookTrailer t) {
        return of(t, false);
    }
}


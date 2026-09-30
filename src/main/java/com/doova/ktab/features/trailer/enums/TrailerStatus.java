package com.doova.ktab.features.trailer.enums;

import java.util.EnumSet;
import java.util.Set;

public enum TrailerStatus {
    QUEUED, RUNNING, HARVESTING, READY, NEEDS_REVIEW, FAILED, CANCELLED;

    /** Must match uq_book_trailer_active in V21__book_trailers.sql. */
    public static final Set<TrailerStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING, HARVESTING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}

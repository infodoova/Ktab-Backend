package com.doova.ktab.service.book;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the book editorial lifecycle
 * (DRAFT -> UNDER_REVIEW -> PUBLISHED, with rejection back to DRAFT).
 * Every status write path (author submit/withdraw, publisher approve/reject,
 * librarian direct publish of institutional books) must route through
 * {@link #assertAllowed(BookStatus, BookStatus)}.
 * <p>
 * DRAFT -> PUBLISHED is also permitted directly to support institutional
 * (library-sourced) books, which are managed by trusted librarian staff and
 * do not go through the author review queue. Author-authored books are kept
 * on the review path by application-level checks (authors are never allowed
 * to assign a non-DRAFT status themselves), not by this transition graph.
 */
public final class BookStatusTransition {

    private static final Map<BookStatus, Set<BookStatus>> ALLOWED = new EnumMap<>(BookStatus.class);

    static {
        ALLOWED.put(BookStatus.DRAFT, EnumSet.of(BookStatus.UNDER_REVIEW, BookStatus.PUBLISHED));
        ALLOWED.put(BookStatus.UNDER_REVIEW, EnumSet.of(BookStatus.DRAFT, BookStatus.PUBLISHED));
        ALLOWED.put(BookStatus.PUBLISHED, EnumSet.noneOf(BookStatus.class));
    }

    private BookStatusTransition() {}

    public static void assertAllowed(BookStatus from, BookStatus to) {
        if (from == to) {
            throw new BadRequestException(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION);
        }
        Set<BookStatus> allowedTargets = ALLOWED.get(from);
        if (allowedTargets == null || !allowedTargets.contains(to)) {
            throw new BadRequestException(ApiMessageKey.BOOK_INVALID_STATUS_TRANSITION);
        }
    }
}

package com.doova.ktab.enums.status;

/**
 * Editorial lifecycle of a book.
 * <p>
 * DRAFT -> UNDER_REVIEW -> PUBLISHED, with a rejection path back to DRAFT.
 * Allowed transitions are enforced by
 * {@link com.doova.ktab.service.book.BookStatusTransition}.
 */
public enum BookStatus {
    DRAFT, UNDER_REVIEW, PUBLISHED;
}

package com.doova.ktab.service.book;

import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;

/**
 * Centralizes the side effects of publishing a book (status transition, review
 * stamping, and the {@code BookPublishedEvent}) so every path that can
 * reach PUBLISHED - publisher approval, librarian direct publish - behaves identically.
 */
public interface BookPublicationService {

    /**
     * Transitions {@code book} to PUBLISHED, stamps the reviewer/decision metadata,
     * persists it, and fires {@code BookPublishedEvent} to kick off text extraction and ingestion processing.
     *
     * @param book       the book to publish (must be in a state allowed to reach PUBLISHED)
     * @param decidedBy  the user responsible for the publish decision (publisher, librarian, or admin)
     * @param reviewNote optional note to record alongside the decision
     * @return the persisted, published book
     */
    Book publish(Book book, User decidedBy, String reviewNote);
}

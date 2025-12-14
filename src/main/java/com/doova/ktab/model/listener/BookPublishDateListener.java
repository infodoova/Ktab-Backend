package com.doova.ktab.model.listener;

import com.doova.ktab.enums.BookStatus;
import com.doova.ktab.model.book.Book;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

import java.time.Instant;

public class BookPublishDateListener {

    @PrePersist
    public void onCreate(Book book) {
        setPublishDateIfPublished(book);
    }

    @PreUpdate
    public void onUpdate(Book book) {
        setPublishDateIfPublished(book);
    }

    @PreRemove
    public void onDelete(Book book) {
        // ⚠️ NOTE:
        // Changes made here are NOT guaranteed to persist
        // because the entity will be deleted immediately after.
        // This hook is useful for logging/auditing only.
        setPublishDateIfPublished(book);
    }

    private void setPublishDateIfPublished(Book book) {
        if (book.getStatus() == BookStatus.PUBLISHED && book.getPublishDate() == null) {
            book.setPublishDate(Instant.now());
        }
    }
}

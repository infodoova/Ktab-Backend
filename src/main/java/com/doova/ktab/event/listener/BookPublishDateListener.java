package com.doova.ktab.event.listener;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.model.book.Book;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class BookPublishDateListener {

    private static final Logger log = LoggerFactory.getLogger(BookPublishDateListener.class);

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
        log.debug("Book with ID {} is being removed", book.getId());
    }

    private void setPublishDateIfPublished(Book book) {
        if (book.getStatus() == BookStatus.PUBLISHED && book.getPublishDate() == null) {
            book.setPublishDate(Instant.now());
        }
    }
}

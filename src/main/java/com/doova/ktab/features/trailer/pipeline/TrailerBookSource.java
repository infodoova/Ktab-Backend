package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/** Reads lazy book relations inside a transaction (the worker thread has none of its own). */
@Component
@RequiredArgsConstructor
public class TrailerBookSource {

    public record BookFacts(String title, String author, String language, String pdfKey, String coverKey) {
        public BookFacts(String title, String author, String language, String pdfKey) {
            this(title, author, language, pdfKey, null);
        }
    }

    private final BookRepository books;
    private final AttachmentService attachments;
    private final TrailerStore store;

    @Transactional(readOnly = true)
    public BookFacts facts(Long bookId) {
        Book book = books.findById(bookId).orElseThrow();
        String pdfKey = attachments.getAttachment(bookId, Book.class.getSimpleName(), "PDF_SOURCE")
                .map(Attachment::getStoragePath).orElse(null);
        String coverKey = attachments.getAttachment(bookId, Book.class.getSimpleName(), "COVER_IMAGE")
                .map(Attachment::getStoragePath).orElse(null);
        return new BookFacts(book.getTitle(), authorName(book), book.getLanguage(), pdfKey, coverKey);
    }

    public Path downloadPdf(String key, Path target) {
        return store.downloadTo(key, target);
    }

    public Path downloadCover(String key, Path target) {
        return store.downloadTo(key, target);
    }

    private static String authorName(Book book) {
        if (book.getCustomAuthorName() != null && !book.getCustomAuthorName().isBlank()) {
            return book.getCustomAuthorName();
        }
        User author = book.getAuthor();
        return author == null ? "" : (author.getFirstName() + " " + (author.getLastName() == null ? "" : author.getLastName())).strip();
    }
}

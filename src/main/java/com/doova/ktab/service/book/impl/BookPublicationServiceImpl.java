package com.doova.ktab.service.book.impl;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookPublicationService;
import com.doova.ktab.service.book.BookStatusTransition;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookPublicationServiceImpl implements BookPublicationService {

    private final BookRepository bookRepository;
    private final AttachmentService attachmentService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Book publish(Book book, User decidedBy, String reviewNote) {
        BookStatusTransition.assertAllowed(book.getStatus(), BookStatus.PUBLISHED);

        book.setStatus(BookStatus.PUBLISHED);
        book.setReviewedBy(decidedBy);
        book.setReviewedAt(Instant.now());
        book.setReviewNote(reviewNote);

        Book savedBook = bookRepository.save(book);

        String pdfKey = getPdfKey(savedBook.getId());
        eventPublisher.publishEvent(new BookPublishedEvent(savedBook.getId(), pdfKey));

        log.info("Book {} published by user {}", savedBook.getId(), decidedBy != null ? decidedBy.getEmail() : "system");

        return savedBook;
    }

    private String getPdfKey(Long bookId) {
        return attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE")
                .map(Attachment::getStoragePath)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.BOOK_OCR_PDF_MISSING));
    }
}

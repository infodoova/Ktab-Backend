package com.doova.ktab.service.publisher.impl;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.dto.book.PublisherReviewSearchRequest;
import com.doova.ktab.dto.publisher.ReviewDecisionRequest;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookPublicationService;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.book.BookStatusTransition;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.service.publisher.PublisherReviewService;
import com.doova.ktab.specification.BookSpecification;
import com.doova.ktab.utils.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherReviewServiceImpl implements PublisherReviewService {

    private final BookRepository bookRepository;
    private final BookResponseBuilderService responseBuilder;
    private final BookPublicationService bookPublicationService;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getReviewQueue(int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = (size <= 0 || size > 100) ? 10 : size;
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.ASC, "submittedAt"));
        Page<Book> books = bookRepository.findAllByStatusAndBookSource(BookStatus.UNDER_REVIEW, BookSource.AUTHOR, pageable);
        return PageResponse.fromPage(books.map(b -> responseBuilder.build(b, true)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> searchReviewQueue(PublisherReviewSearchRequest req) {
        PublisherReviewSearchRequest effectiveReq = req != null ? req : new PublisherReviewSearchRequest(
                null, null, null, null, null, null, null, null, 0, 10, null, null
        );
        Pageable pageable = PageRequest.of(
                effectiveReq.page(),
                effectiveReq.size(),
                Sort.by(effectiveReq.sortDirection(), effectiveReq.sortBy())
        );
        Page<Book> books = bookRepository.findAll(BookSpecification.forReviewQueue(effectiveReq), pageable);
        return PageResponse.fromPage(books.map(b -> responseBuilder.build(b, true)));
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponseDto getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.PUBLISHER_BOOK_NOT_FOUND));
        return responseBuilder.build(book, true);
    }

    @Override
    @Transactional(readOnly = true)
    public BookSourceFileResponseDto getSourceFileForPublisher(Long id, User publisher) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("SECURITY_ALERT: Unauthorized attempt by User ID {} ({}) to access non-existent Book ID {}",
                            publisher.getId(), publisher.getEmail(), id);
                    return new ResourceNotFoundException(ApiMessageKey.PUBLISHER_BOOK_NOT_FOUND);
                });

        Attachment pdf = attachmentService.getAttachment(book.getId(), "Book", "PDF_SOURCE")
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.BOOK_OCR_PDF_MISSING));

        Duration ttl = Duration.ofMinutes(3);
        String downloadUrl = fileStorageService.getPreSignedDownloadUrl(pdf.getStoragePath(), ttl, pdf.getFileName());

        log.info("SECURITY_AUDIT: Publisher User ID {} ({}) generated ephemeral download URL for Book ID {} ({})",
                publisher.getId(), publisher.getEmail(), book.getId(), pdf.getFileName());

        return new BookSourceFileResponseDto(
                book.getId(),
                pdf.getFileName(),
                downloadUrl,
                Instant.now().plus(ttl)
        );
    }

    @Override
    @Transactional
    public BookResponseDto approveBook(Long id, ReviewDecisionRequest req, User publisher) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.PUBLISHER_BOOK_NOT_FOUND));

        BookStatusTransition.assertAllowed(book.getStatus(), BookStatus.PUBLISHED);

        String note = (req != null && req.note() != null && !req.note().isBlank()) ? req.note().trim() : null;
        Book publishedBook = bookPublicationService.publish(book, publisher, note);

        log.info("Publisher {} approved book {}", publisher.getEmail(), publishedBook.getId());
        return responseBuilder.build(publishedBook, true);
    }

    @Override
    @Transactional
    public BookResponseDto rejectBook(Long id, ReviewDecisionRequest req, User publisher) {
        if (req == null || req.note() == null || req.note().isBlank()) {
            throw new BadRequestException(ApiMessageKey.PUBLISHER_REVIEW_NOTE_REQUIRED);
        }

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.PUBLISHER_BOOK_NOT_FOUND));

        BookStatusTransition.assertAllowed(book.getStatus(), BookStatus.DRAFT);

        book.setStatus(BookStatus.DRAFT);
        book.setReviewedBy(publisher);
        book.setReviewedAt(Instant.now());
        book.setReviewNote(req.note().trim());

        Book saved = bookRepository.save(book);

        User recipient = saved.getUploader() != null ? saved.getUploader() : saved.getAuthor();
        String recipientEmail = recipient != null ? recipient.getEmail() : null;
        String recipientName = recipient != null ? (recipient.getFirstName() != null ? recipient.getFirstName() : "Author") : "Author";
        String reviewerName = (publisher != null && publisher.getFirstName() != null) ? publisher.getFirstName() : "Editorial Team";

        eventPublisher.publishEvent(new com.doova.ktab.event.model.BookRejectedEvent(
                saved.getId(),
                saved.getTitle(),
                recipientEmail,
                recipientName,
                saved.getReviewNote(),
                reviewerName
        ));

        log.info("Publisher {} rejected book {} with note", publisher.getEmail(), saved.getId());
        return responseBuilder.build(saved, true);
    }
}

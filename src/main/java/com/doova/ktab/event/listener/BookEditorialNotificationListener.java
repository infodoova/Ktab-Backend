package com.doova.ktab.event.listener;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.event.model.BookRejectedEvent;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookEditorialNotificationListener {

    private final EmailService emailService;
    private final BookRepository bookRepository;

    @Value("${ktab.app.frontend-url:https://ktab-rho.vercel.app}")
    private String frontendUrl;

    /**
     * Sends an email notification to the author/uploader when their book is rejected with an editorial note.
     * Executes asynchronously after the review decision transaction commits.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookRejected(BookRejectedEvent event) {
        if (event.recipientEmail() == null || event.recipientEmail().isBlank()) {
            log.warn("Cannot send rejection email for bookId={}: recipient email is null or blank", event.bookId());
            return;
        }

        log.info("Sending book rejection email to '{}' for bookId={}", event.recipientEmail(), event.bookId());

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(event.recipientEmail())
                    .subject("المراجعة التحريرية لكِتاب: تحديث بخصوص «" + event.bookTitle() + "»")
                    .template("book-rejected")
                    .variable("NAME", event.recipientName() != null ? event.recipientName() : "عزيزنا المؤلف")
                    .variable("BOOK_TITLE", event.bookTitle())
                    .variable("REVIEW_NOTE", event.reviewNote() != null ? event.reviewNote() : "")
                    .variable("REVIEWER_NAME", event.reviewerName() != null ? event.reviewerName() : "فريق التحرير")
                    .variable("STUDIO_URL", frontendUrl + "/studio/books/" + event.bookId())
                    .variable("SUPPORT_EMAIL", "support@ktab.app")
                    .build();

            emailService.sendSync(request);
        } catch (Exception e) {
            log.error("Failed to send book rejection notification for bookId={}: {}", event.bookId(), e.getMessage(), e);
        }
    }

    /**
     * Sends an email notification to the author/uploader when their book is approved and published.
     * Executes asynchronously after the publication transaction commits.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookPublished(BookPublishedEvent event) {
        log.debug("Received BookPublishedEvent for bookId={}, resolving author notification", event.bookId());

        Optional<Book> bookOpt = bookRepository.findById(event.bookId());
        if (bookOpt.isEmpty()) {
            log.warn("Cannot send publication email: bookId={} not found", event.bookId());
            return;
        }

        Book book = bookOpt.get();
        User recipient = book.getUploader() != null ? book.getUploader() : book.getAuthor();

        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.info("No uploader or author email found for published bookId={}, skipping email notification", event.bookId());
            return;
        }

        String recipientEmail = recipient.getEmail();
        String recipientName = recipient.getFirstName() != null ? recipient.getFirstName() : "عزيزنا المؤلف";

        log.info("Sending book published celebration email to '{}' for bookId={}", recipientEmail, event.bookId());

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(recipientEmail)
                    .subject("تهانينا! تم نشر كتابك «" + book.getTitle() + "» على كِتاب")
                    .template("book-published")
                    .variable("NAME", recipientName)
                    .variable("BOOK_TITLE", book.getTitle())
                    .variable("BOOK_ID", book.getId())
                    .variable("PUBLISH_DATE", book.getPublishDate() != null ? book.getPublishDate().toString() : "اليوم")
                    .variable("REVIEW_NOTE", book.getReviewNote() != null ? book.getReviewNote() : "")
                    .variable("BOOK_URL", frontendUrl + "/books/" + book.getId())
                    .build();

            emailService.sendSync(request);
        } catch (Exception e) {
            log.error("Failed to send book published notification for bookId={}: {}", event.bookId(), e.getMessage(), e);
        }
    }
}

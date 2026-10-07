package com.doova.ktab.features.storybook.notification;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.features.storybook.event.StorybookCharacterReadyEvent;
import com.doova.ktab.features.storybook.event.StorybookCompletedEvent;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.event.StorybookStoryReadyEvent;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Event-driven notification listener for personalized storybooks.
 * Dispatches transactional emails informing parents about the creative journey,
 * human-in-the-loop review checkpoints (story text & character look sheet),
 * and final storybook completion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorybookNotificationListener {

    private final EmailService emailService;
    private final StorybookRepository storybookRepository;

    @Value("${ktab.app.frontend-url:https://ktab-rho.vercel.app}")
    private String frontendUrl;

    /**
     * Sent immediately after storybook creation.
     * Explains the creative journey, what happens next, and privacy/photo protection.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStorybookCreated(StorybookCreatedEvent event) {
        log.debug("Handling StorybookCreatedEvent for bookId={}", event.storybookId());
        Optional<Storybook> bookOpt = storybookRepository.findByIdWithOwner(event.storybookId());
        if (bookOpt.isEmpty()) {
            log.warn("Cannot send storybook creation email: bookId={} not found", event.storybookId());
            return;
        }

        Storybook book = bookOpt.get();
        User owner = book.getOwner();
        if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) {
            log.warn("Cannot send creation email for bookId={}: owner or email is missing", event.storybookId());
            return;
        }

        String recipientEmail = owner.getEmail();
        String recipientName = owner.getFullName();
        String childName = resolveChildName(book);
        String bookUrl = frontendUrl + "/storybook/books/" + event.storybookId();

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(recipientEmail)
                    .subject("بدأت رحلة كتاب " + childName + ": ماذا سيحدث الآن؟")
                    .template("storybook-created")
                    .variable("NAME", recipientName)
                    .variable("CHILD_NAME", childName)
                    .variable("BOOK_ID", String.valueOf(event.storybookId()))
                    .variable("PAGE_COUNT", String.valueOf(book.getPageCount()))
                    .variable("BOOK_URL", bookUrl)
                    .variable("SUPPORT_EMAIL", "support@ktab.app")
                    .build();

            emailService.sendSync(request);
            log.info("Storybook creation onboarding email sent to '{}' for bookId={}", recipientEmail, event.storybookId());
        } catch (Exception e) {
            log.error("Failed to send storybook creation email for bookId={}: {}", event.storybookId(), e.getMessage(), e);
        }
    }

    /**
     * Human-in-the-Loop #1:
     * Sent when the Arabic story script is ready for review (STORY_READY).
     * Invites the parent to review and approve the story pages.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStoryReady(StorybookStoryReadyEvent event) {
        log.debug("Handling StorybookStoryReadyEvent for bookId={}", event.storybookId());
        Optional<Storybook> bookOpt = storybookRepository.findByIdWithOwner(event.storybookId());
        if (bookOpt.isEmpty()) {
            log.warn("Cannot send story ready email: bookId={} not found", event.storybookId());
            return;
        }

        Storybook book = bookOpt.get();
        User owner = book.getOwner();
        if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) {
            log.warn("Cannot send story ready email for bookId={}: owner or email is missing", event.storybookId());
            return;
        }

        String recipientEmail = owner.getEmail();
        String recipientName = owner.getFullName();
        String childName = resolveChildName(book);
        String bookTitle = book.getTitleAr() != null && !book.getTitleAr().isBlank()
                ? book.getTitleAr()
                : ("مغامرة " + childName);
        String bookUrl = frontendUrl + "/storybook/books/" + event.storybookId();

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(recipientEmail)
                    .subject("قصة «" + bookTitle + "» جاهزة لمراجعتك واعتمادك")
                    .template("storybook-story-ready")
                    .variable("NAME", recipientName)
                    .variable("CHILD_NAME", childName)
                    .variable("BOOK_TITLE", bookTitle)
                    .variable("BOOK_URL", bookUrl)
                    .variable("SUPPORT_EMAIL", "support@ktab.app")
                    .build();

            emailService.sendSync(request);
            log.info("Story ready approval email sent to '{}' for bookId={}", recipientEmail, event.storybookId());
        } catch (Exception e) {
            log.error("Failed to send story ready email for bookId={}: {}", event.storybookId(), e.getMessage(), e);
        }
    }

    /**
     * Human-in-the-Loop #2:
     * Sent when the watercolor character design sheet is generated (CHARACTER_READY).
     * Invites parent to review the look sheet and approve it before page illustrations begin.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCharacterReady(StorybookCharacterReadyEvent event) {
        log.debug("Handling StorybookCharacterReadyEvent for bookId={}", event.storybookId());
        Optional<Storybook> bookOpt = storybookRepository.findByIdWithOwner(event.storybookId());
        if (bookOpt.isEmpty()) {
            log.warn("Cannot send character ready email: bookId={} not found", event.storybookId());
            return;
        }

        Storybook book = bookOpt.get();
        User owner = book.getOwner();
        if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) {
            log.warn("Cannot send character ready email for bookId={}: owner or email is missing", event.storybookId());
            return;
        }

        String recipientEmail = owner.getEmail();
        String recipientName = owner.getFullName();
        String childName = resolveChildName(book);
        String bookTitle = book.getTitleAr() != null && !book.getTitleAr().isBlank()
                ? book.getTitleAr()
                : ("مغامرة " + childName);
        String bookUrl = frontendUrl + "/storybook/books/" + event.storybookId();

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(recipientEmail)
                    .subject("لوحة رسم شخصية " + childName + " جاهزة للاعتماد")
                    .template("storybook-character-ready")
                    .variable("NAME", recipientName)
                    .variable("CHILD_NAME", childName)
                    .variable("BOOK_TITLE", bookTitle)
                    .variable("BOOK_URL", bookUrl)
                    .variable("SUPPORT_EMAIL", "support@ktab.app")
                    .build();

            emailService.sendSync(request);
            log.info("Character ready approval email sent to '{}' for bookId={}", recipientEmail, event.storybookId());
        } catch (Exception e) {
            log.error("Failed to send character ready email for bookId={}: {}", event.storybookId(), e.getMessage(), e);
        }
    }

    /**
     * Sent when all page illustrations and the final 21x21 cm PDF are compiled (COMPLETED / READY).
     * Informs the parent that the book is ready to read online and download for printing.
     */
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStorybookCompleted(StorybookCompletedEvent event) {
        log.debug("Handling StorybookCompletedEvent for bookId={}", event.storybookId());
        Optional<Storybook> bookOpt = storybookRepository.findByIdWithOwner(event.storybookId());
        if (bookOpt.isEmpty()) {
            log.warn("Cannot send completion email: bookId={} not found", event.storybookId());
            return;
        }

        Storybook book = bookOpt.get();
        User owner = book.getOwner();
        if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) {
            log.warn("Cannot send completion email for bookId={}: owner or email is missing", event.storybookId());
            return;
        }

        String recipientEmail = owner.getEmail();
        String recipientName = owner.getFullName();
        String childName = resolveChildName(book);
        String bookTitle = book.getTitleAr() != null && !book.getTitleAr().isBlank()
                ? book.getTitleAr()
                : ("مغامرة " + childName);
        String bookUrl = frontendUrl + "/storybook/books/" + event.storybookId();
        String readerUrl = frontendUrl + "/storybook/books/" + event.storybookId() + "/reader";
        String downloadUrl = frontendUrl + "/storybook/books/" + event.storybookId() + "/download";

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(recipientEmail)
                    .subject("تهانينا! كتاب «" + bookTitle + "» مكتمل وجاهز للقراءة الآن")
                    .template("storybook-completed")
                    .variable("NAME", recipientName)
                    .variable("CHILD_NAME", childName)
                    .variable("BOOK_TITLE", bookTitle)
                    .variable("READER_URL", readerUrl)
                    .variable("DOWNLOAD_URL", downloadUrl)
                    .variable("BOOK_URL", bookUrl)
                    .variable("SUPPORT_EMAIL", "support@ktab.app")
                    .build();

            emailService.sendSync(request);
            log.info("Storybook completion email sent to '{}' for bookId={}", recipientEmail, event.storybookId());
        } catch (Exception e) {
            log.error("Failed to send storybook completion email for bookId={}: {}", event.storybookId(), e.getMessage(), e);
        }
    }

    private String resolveChildName(Storybook book) {
        if (book.getInputs() != null && book.getInputs().childNameAr() != null && !book.getInputs().childNameAr().isBlank()) {
            return book.getInputs().childNameAr();
        }
        if (book.getChildProfile() != null && book.getChildProfile().getNameAr() != null && !book.getChildProfile().getNameAr().isBlank()) {
            return book.getChildProfile().getNameAr();
        }
        return "طفلكم";
    }
}

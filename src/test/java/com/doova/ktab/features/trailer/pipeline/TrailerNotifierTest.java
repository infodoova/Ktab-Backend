package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrailerNotifierTest {

    private final EmailService emailService = mock(EmailService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookRepository books = mock(BookRepository.class);

    private TrailerNotifier notifier;
    private BookTrailer trailer;
    private User user;
    private Book book;

    @BeforeEach
    void setUp() {
        notifier = new TrailerNotifier(emailService, users, books);
        ReflectionTestUtils.setField(notifier, "frontendUrl", "https://ktab-rho.vercel.app");

        trailer = new BookTrailer();
        trailer.setId(100L);
        trailer.setBookId(42L);
        trailer.setRequestedById(7L);

        user = new User();
        user.setId(7L);
        user.setFirstName("Amira");
        user.setEmail("amira@example.com");

        book = new Book();
        book.setId(42L);
        book.setTitle("Sands of Destiny");

        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(books.findById(42L)).thenReturn(Optional.of(book));
    }

    @Test
    void notifyCompletion_trailerStatusReady_dispatchesEmailWithReadyUrl() {
        trailer.setStatus(TrailerStatus.READY);

        notifier.notifyCompletion(trailer);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).send(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.to()).contains("amira@example.com");
        assertThat(req.subject()).contains("إعلانك الترويجي السينمائي جاهز!").contains("Sands of Destiny");
        assertThat(req.templateName()).isEqualTo("trailer-ready");
        assertThat(req.templateVariables()).containsEntry("NAME", "Amira");
        assertThat(req.templateVariables()).containsEntry("BOOK_TITLE", "Sands of Destiny");
        assertThat(req.templateVariables()).containsEntry("STATUS", "READY");
        assertThat(req.templateVariables().get("TRAILER_URL")).isEqualTo("https://ktab-rho.vercel.app/books/42/trailer");
        assertThat(req.textBody()).contains("https://ktab-rho.vercel.app/books/42/trailer");
    }

    @Test
    void notifyCompletion_trailerStatusNeedsReview_dispatchesEmailWithReviewUrl() {
        trailer.setStatus(TrailerStatus.NEEDS_REVIEW);

        notifier.notifyCompletion(trailer);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).send(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.subject()).contains("إعلانك الترويجي جاهز للمراجعة التحريرية");
        assertThat(req.templateVariables()).containsEntry("STATUS", "NEEDS_REVIEW");
        assertThat(req.templateVariables().get("TRAILER_URL")).isEqualTo("https://ktab-rho.vercel.app/studio/books/42");
    }

    @Test
    void notifyCompletion_trailerStatusFailed_dispatchesEmailWithErrorNote() {
        trailer.setStatus(TrailerStatus.FAILED);
        trailer.setError("Audio sync timeout exceeded");

        notifier.notifyCompletion(trailer);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).send(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.subject()).contains("تحديث بخصوص إعلان كتابك");
        assertThat(req.templateVariables()).containsEntry("STATUS", "FAILED");
        assertThat(req.templateVariables()).containsEntry("ERROR_NOTE", "Audio sync timeout exceeded");
        assertThat(req.textBody()).contains("Audio sync timeout exceeded");
    }

    @Test
    void notifyCompletion_requestedByIdIsNull_skipsDispatch() {
        trailer.setRequestedById(null);

        notifier.notifyCompletion(trailer);

        verifyNoInteractions(emailService);
    }

    @Test
    void notifyCompletion_userNotFound_skipsDispatch() {
        trailer.setStatus(TrailerStatus.READY);
        when(users.findById(7L)).thenReturn(Optional.empty());

        notifier.notifyCompletion(trailer);

        verifyNoInteractions(emailService);
    }

    @Test
    void notifyCompletion_userEmailIsBlank_skipsDispatch() {
        trailer.setStatus(TrailerStatus.READY);
        user.setEmail("   ");

        notifier.notifyCompletion(trailer);

        verifyNoInteractions(emailService);
    }

    @Test
    void notifyCompletion_emailServiceThrowsException_catchesAndDoesNotRethrow() {
        trailer.setStatus(TrailerStatus.READY);
        doThrow(new RuntimeException("SMTP connection timeout")).when(emailService).send(any());

        assertThatCode(() -> notifier.notifyCompletion(trailer)).doesNotThrowAnyException();
    }
}

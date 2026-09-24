package com.doova.ktab.event.listener;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.event.model.BookRejectedEvent;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookEditorialNotificationListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookEditorialNotificationListener listener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "frontendUrl", "https://ktab-rho.vercel.app");
    }

    @Test
    @DisplayName("onBookRejected: dispatches editorial feedback email to author/uploader")
    void onBookRejected_sendsEmailWithReviewNoteAndTitle() {
        BookRejectedEvent event = new BookRejectedEvent(
                10L,
                "The Great Novel",
                "sarah@example.com",
                "Sarah",
                "Please format chapter 2 headings.",
                "Chief Editor",
                Instant.now()
        );

        listener.onBookRejected(event);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest request = captor.getValue();
        assertThat(request.to()).containsExactly("sarah@example.com");
        assertThat(request.subject()).contains("The Great Novel");
        assertThat(request.templateName()).isEqualTo("book-rejected");
        assertThat(request.templateVariables())
                .containsEntry("NAME", "Sarah")
                .containsEntry("BOOK_TITLE", "The Great Novel")
                .containsEntry("REVIEW_NOTE", "Please format chapter 2 headings.")
                .containsEntry("REVIEWER_NAME", "Chief Editor")
                .containsEntry("STUDIO_URL", "https://ktab-rho.vercel.app/studio/books/10");
    }

    @Test
    @DisplayName("onBookRejected: skips dispatch when recipient email is null or blank")
    void onBookRejected_whenEmailBlank_skipsDispatch() {
        BookRejectedEvent event = new BookRejectedEvent(
                10L,
                "The Great Novel",
                null,
                "Sarah",
                "Note",
                "Editor",
                Instant.now()
        );

        listener.onBookRejected(event);

        verify(emailService, never()).sendSync(any());
    }

    @Test
    @DisplayName("onBookPublished: resolves uploader and dispatches celebration email")
    void onBookPublished_resolvesUploaderAndSendsCongratulationsEmail() {
        User uploader = new User();
        uploader.setEmail("uploader@example.com");
        uploader.setFirstName("Alex");

        Book book = new Book();
        book.setId(20L);
        book.setTitle("Mastering Spring Boot");
        book.setUploader(uploader);
        book.setPublishDate(Instant.now());
        book.setReviewNote("Outstanding manuscript!");

        when(bookRepository.findById(20L)).thenReturn(Optional.of(book));

        BookPublishedEvent event = new BookPublishedEvent(20L, "s3://keys/book.pdf");
        listener.onBookPublished(event);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest request = captor.getValue();
        assertThat(request.to()).containsExactly("uploader@example.com");
        assertThat(request.subject()).contains("Mastering Spring Boot");
        assertThat(request.templateName()).isEqualTo("book-published");
        assertThat(request.templateVariables())
                .containsEntry("NAME", "Alex")
                .containsEntry("BOOK_TITLE", "Mastering Spring Boot")
                .containsEntry("BOOK_ID", 20L)
                .containsEntry("REVIEW_NOTE", "Outstanding manuscript!")
                .containsEntry("BOOK_URL", "https://ktab-rho.vercel.app/books/20");
    }

    @Test
    @DisplayName("onBookPublished: resolves author if uploader is null")
    void onBookPublished_resolvesAuthorIfUploaderNull() {
        User author = new User();
        author.setEmail("author@example.com");
        author.setFirstName("Fatima");

        Book book = new Book();
        book.setId(21L);
        book.setTitle("Arabic Poetry Collection");
        book.setAuthor(author);
        book.setUploader(null);

        when(bookRepository.findById(21L)).thenReturn(Optional.of(book));

        BookPublishedEvent event = new BookPublishedEvent(21L, "s3://keys/poetry.pdf");
        listener.onBookPublished(event);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest request = captor.getValue();
        assertThat(request.to()).containsExactly("author@example.com");
        assertThat(request.templateVariables()).containsEntry("NAME", "Fatima");
    }

    @Test
    @DisplayName("onBookPublished: skips dispatch when neither uploader nor author has email")
    void onBookPublished_whenNoEmail_skipsDispatch() {
        Book book = new Book();
        book.setId(22L);
        book.setTitle("Anonymous Folklore");

        when(bookRepository.findById(22L)).thenReturn(Optional.of(book));

        BookPublishedEvent event = new BookPublishedEvent(22L, "s3://keys/folklore.pdf");
        listener.onBookPublished(event);

        verify(emailService, never()).sendSync(any());
    }
}

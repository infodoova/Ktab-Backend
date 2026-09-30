package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.pipeline.TrailerNotifier;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerServiceTest {

    final BookRepository books = mock(BookRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerNotifier notifier = mock(TrailerNotifier.class);
    final TrailerService service = new TrailerService(new TrailerAccess(books, users), trailers, gateway, storage, props, notifier);


    static User user(UserRole role, long id) {
        User u = new User();
        u.setId(id);
        u.setRole(role.getCode());
        return u;
    }

    static Book book(BookStatus status) {
        Book b = new Book();
        b.setId(3L);
        b.setStatus(status);
        return b;
    }

    @Test
    void authorOnlyTheirOwnBook() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(ResourceNotFoundException.class);

        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.DRAFT)));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(author, 3L).status()).isEqualTo(TrailerStatus.QUEUED);
    }

    @Test
    void librarianIsScopedToTheirOrganization() {
        LibraryOrganization org = new LibraryOrganization();
        org.setId(50L);
        User managed = user(UserRole.LIBRARIAN, 2);
        managed.setLibraryOrganization(org);
        when(users.findById(2L)).thenReturn(Optional.of(managed));
        when(books.findByIdAndLibraryOrganizationId(3L, 50L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(user(UserRole.LIBRARIAN, 2), 3L).status()).isEqualTo(TrailerStatus.QUEUED);
    }

    @Test
    void secondActiveTrailerIsAConflictAndTheMonthlyLimitApplies() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.existsByBookIdAndStatusIn(eq(3L), anyCollection())).thenReturn(true);
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(TrailerConflictException.class);

        when(trailers.existsByBookIdAndStatusIn(eq(3L), anyCollection())).thenReturn(false);
        when(trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(eq(3L), any(), anyCollection())).thenReturn(3L);
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelInterruptsTheSession() {
        User admin = user(UserRole.ADMIN, 9);
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.RUNNING);
        t.setSessionId("sesn_1");
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));

        service.cancel(admin, 7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.CANCELLED);
        verify(gateway).interrupt("sesn_1");
    }

    @Test
    void onlyAdminsApproveTrailersNeedingReview() {
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.NEEDS_REVIEW);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.review(user(UserRole.AUTHOR, 1), 7L, true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        service.review(user(UserRole.ADMIN, 9), 7L, true);
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.READY);
    }
}

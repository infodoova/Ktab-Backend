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

    private BookTrailer trailerIn(TrailerStatus status) {
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(status);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void anAuthorCanCancelATrailerOnlyWhileItIsQueued() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        BookTrailer queued = trailerIn(TrailerStatus.QUEUED);

        service.cancel(author, 7L);

        assertThat(queued.getStatus()).isEqualTo(TrailerStatus.CANCELLED);
    }

    @Test
    void anAuthorCannotCancelOnceTheTrailerHasStarted() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        for (TrailerStatus started : new TrailerStatus[]{TrailerStatus.RUNNING, TrailerStatus.HARVESTING}) {
            BookTrailer t = trailerIn(started);

            assertThatThrownBy(() -> service.cancel(author, 7L)).isInstanceOf(TrailerConflictException.class);
            assertThat(t.getStatus()).isEqualTo(started);
        }
        verify(gateway, never()).interrupt(any());
    }

    @Test
    void aTrailerThatIsNotActiveCannotBeCancelledAtAll() {
        User admin = user(UserRole.ADMIN, 9);
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        trailerIn(TrailerStatus.READY);

        assertThatThrownBy(() -> service.cancel(admin, 7L)).isInstanceOf(TrailerConflictException.class);
    }

    private BookTrailer needingReview() {
        BookTrailer t = trailerIn(TrailerStatus.NEEDS_REVIEW);
        t.setVideoKey("v.mp4");
        return t;
    }

    private User adminLibrarianOfOrganization(long userId, long orgId) {
        LibraryOrganization org = new LibraryOrganization();
        org.setId(orgId);
        User managed = user(UserRole.ADMIN_LIBRARIAN, userId);
        managed.setLibraryOrganization(org);
        when(users.findById(userId)).thenReturn(Optional.of(managed));
        return managed;
    }

    @Test
    void theBooksAuthorApprovesAndRejectsTheirOwnTrailer() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        BookTrailer t = needingReview();

        assertThat(service.review(author, 7L, true).status()).isEqualTo(TrailerStatus.READY);

        t.setStatus(TrailerStatus.NEEDS_REVIEW);
        assertThat(service.review(author, 7L, false).status()).isEqualTo(TrailerStatus.FAILED);
        verify(notifier, times(2)).notifyCompletion(t);
    }

    @Test
    void anAuthorCannotReviewATrailerOfSomeoneElsesBook() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.empty());
        BookTrailer t = needingReview();

        assertThatThrownBy(() -> service.review(author, 7L, true))
                .isInstanceOf(com.doova.ktab.exception.ResourceNotFoundException.class);
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
    }

    @Test
    void anAdminLibrarianReviewsTrailersOfTheirOrganizationsBooksOnly() {
        User adminLibrarian = adminLibrarianOfOrganization(2, 50);
        when(books.findByIdAndLibraryOrganizationId(3L, 50L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        BookTrailer t = needingReview();

        assertThat(service.review(adminLibrarian, 7L, true).status()).isEqualTo(TrailerStatus.READY);

        t.setStatus(TrailerStatus.NEEDS_REVIEW);
        when(books.findByIdAndLibraryOrganizationId(3L, 50L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.review(adminLibrarian, 7L, true))
                .isInstanceOf(com.doova.ktab.exception.ResourceNotFoundException.class);
    }

    @Test
    void anAdminReviewsAnyTrailer() {
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        BookTrailer t = needingReview();

        assertThat(service.review(user(UserRole.ADMIN, 9), 7L, true).status()).isEqualTo(TrailerStatus.READY);
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.READY);
    }

    @Test
    void aPlainLibrarianCannotDecideOnATrailerEvenInTheirOrganization() {
        User librarian = user(UserRole.LIBRARIAN, 2);
        BookTrailer t = needingReview();

        assertThatThrownBy(() -> service.review(librarian, 7L, true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
        verifyNoInteractions(notifier);
    }

    @Test
    void aTrailerThatIsNotWaitingForReviewCannotBeReviewed() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        trailerIn(TrailerStatus.READY);

        assertThatThrownBy(() -> service.review(author, 7L, false)).isInstanceOf(TrailerConflictException.class);
    }

    @Test
    void theReviewersCanPreviewATrailerThatIsWaitingButOtherRolesCannot() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        User librarian = user(UserRole.LIBRARIAN, 2);
        LibraryOrganization org = new LibraryOrganization();
        org.setId(50L);
        librarian.setLibraryOrganization(org);
        when(users.findById(2L)).thenReturn(Optional.of(librarian));
        when(books.findByIdAndLibraryOrganizationId(3L, 50L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        needingReview();
        when(storage.getPreSignedDownloadUrl(anyString(), any(), anyString())).thenReturn("https://signed");

        assertThat(service.downloadUrls(author, 7L)).containsKey("video");
        assertThatThrownBy(() -> service.downloadUrls(librarian, 7L)).isInstanceOf(TrailerConflictException.class);
    }
}

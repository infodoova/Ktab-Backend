package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReaderTrailerServiceTest {

    private final BookRepository books = mock(BookRepository.class);
    private final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ReaderTrailerService service = new ReaderTrailerService(books, trailers, storage);

    private final Book book = new Book();
    private final BookTrailer trailer = new BookTrailer();

    @BeforeEach
    void setUp() {
        book.setStatus(BookStatus.PUBLISHED);
        trailer.setId(7L);
        trailer.setBookId(5L);
        trailer.setStatus(TrailerStatus.READY);
        trailer.setVideoKey("v.mp4");
        when(books.findById(5L)).thenReturn(Optional.of(book));
        when(trailers.findFirstByBookIdAndStatusOrderByIdDesc(5L, TrailerStatus.READY)).thenReturn(Optional.of(trailer));
        when(storage.getPreSignedDownloadUrl(anyString(), eq(Duration.ofMinutes(10)), anyString()))
                .thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
    }

    @Test
    void aPublishedBooksFinishedTrailerIsReturnedWithShortLivedLinks() {
        ReaderTrailerView view = service.latest(5L);

        assertThat(view.trailerId()).isEqualTo(7L);
        assertThat(view.video()).isEqualTo("https://signed/v.mp4");
        assertThat(view.videoClean()).isNull();
        assertThat(view.captions()).isNull();
        assertThat(view.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void theOptionalCleanVideoAndSubtitlesAreIncludedWhenTheyExist() {
        trailer.setCleanVideoKey("clean.mp4");
        trailer.setCaptionsKey("ar.srt");

        ReaderTrailerView view = service.latest(5L);

        assertThat(view.videoClean()).isEqualTo("https://signed/clean.mp4");
        assertThat(view.captions()).isEqualTo("https://signed/ar.srt");
    }

    @Test
    void aBookThatIsNotPublishedHasNoTrailerForReaders() {
        book.setStatus(BookStatus.DRAFT);

        assertThatThrownBy(() -> service.latest(5L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trailers, storage);
    }

    @Test
    void anUnknownBookIsNotFound() {
        when(books.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBookWithNoFinishedTrailerIsNotFound() {
        when(trailers.findFirstByBookIdAndStatusOrderByIdDesc(5L, TrailerStatus.READY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aReadyTrailerWithoutAVideoFileIsNotFound() {
        trailer.setVideoKey(null);

        assertThatThrownBy(() -> service.latest(5L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }
}

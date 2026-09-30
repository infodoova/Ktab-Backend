package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerLauncherTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerBookSource books = mock(TrailerBookSource.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerLauncher launcher = new TrailerLauncher(trailers, gateway, books, props);

    @Test
    void uploadsTheBookStartsTheSessionAndRecordsIt() throws Exception {
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.QUEUED);
        props.setVoiceId("voice-1");
        props.setAgentVersion(4);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(books.facts(3L)).thenReturn(new TrailerBookSource.BookFacts("عنوان", "مؤلف", "ar", "books/3/source.pdf"));
        when(books.downloadPdf(eq("books/3/source.pdf"), any())).thenAnswer(i -> i.getArgument(1));
        when(gateway.uploadBook(any())).thenReturn("file_1");
        when(gateway.startSession(eq(7L), eq("file_1"), isNull(), contains("voice-1"), anyString())).thenReturn("sesn_1");

        launcher.launch(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getSessionId()).isEqualTo("sesn_1");
        assertThat(t.getBookFileId()).isEqualTo("file_1");
        assertThat(t.getCoverFileId()).isNull();
        assertThat(t.getAgentVersion()).isEqualTo(4);
    }

    @Test
    void uploadsTheBookAndCoverWhenPresent() {
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.QUEUED);
        props.setVoiceId("voice-1");
        props.setAgentVersion(4);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(books.facts(3L)).thenReturn(new TrailerBookSource.BookFacts("عنوان", "مؤلف", "ar", "books/3/source.pdf", "books/3/cover.jpg"));
        when(books.downloadPdf(eq("books/3/source.pdf"), any())).thenAnswer(i -> i.getArgument(1));
        when(books.downloadCover(eq("books/3/cover.jpg"), any())).thenAnswer(i -> i.getArgument(1));
        when(gateway.uploadBook(any())).thenReturn("file_book_1");
        when(gateway.uploadCover(any())).thenReturn("file_cover_1");
        when(gateway.startSession(eq(7L), eq("file_book_1"), eq("file_cover_1"), contains("Cover image: /workspace/cover.jpg"), anyString()))
                .thenReturn("sesn_1");

        launcher.launch(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getSessionId()).isEqualTo("sesn_1");
        assertThat(t.getBookFileId()).isEqualTo("file_book_1");
        assertThat(t.getCoverFileId()).isEqualTo("file_cover_1");
        verify(gateway).uploadBook(any());
        verify(gateway).uploadCover(any());
    }

    @Test
    void aBookWithoutAPdfFailsImmediately() {
        BookTrailer t = new BookTrailer();
        t.setId(8L);
        t.setBookId(4L);
        t.setStatus(TrailerStatus.QUEUED);
        when(trailers.findById(8L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(books.facts(4L)).thenReturn(new TrailerBookSource.BookFacts("t", "a", "ar", null));

        launcher.launch(8L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        verifyNoInteractions(gateway);
    }
}

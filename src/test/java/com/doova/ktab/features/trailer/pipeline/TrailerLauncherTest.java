package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.endcard.EndCardRenderer;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerLauncherTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerBookSource books = mock(TrailerBookSource.class);
    final EndCardRenderer endCardRenderer = mock(EndCardRenderer.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerLauncher launcher = new TrailerLauncher(trailers, gateway, books, props, endCardRenderer);

    private static final List<Path> ALL_LAYERS = List.of(Path.of("scrim.png"), Path.of("cover.png"),
            Path.of("title.png"), Path.of("subtitle.png"), Path.of("rule.png"), Path.of("author.png"),
            Path.of("logo.png"), Path.of("layout.json"));

    private BookTrailer queued(long id, long bookId) {
        BookTrailer t = new BookTrailer();
        t.setId(id);
        t.setBookId(bookId);
        t.setStatus(TrailerStatus.QUEUED);
        props.setVoices("voice-1|Sami|politics, history;voice-2|Rawi|documentary");
        props.setAgentVersion(4);
        when(trailers.findById(id)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        return t;
    }

    private void bookWithCover() {
        when(books.facts(3L)).thenReturn(new TrailerBookSource.BookFacts("عنوان", "مؤلف", "ar", "books/3/source.pdf", "books/3/cover.jpg"));
        when(books.downloadPdf(eq("books/3/source.pdf"), any())).thenAnswer(i -> i.getArgument(1));
        when(books.downloadCover(eq("books/3/cover.jpg"), any())).thenAnswer(i -> i.getArgument(1));
    }

    @Test
    void uploadsTheBookStartsTheSessionAndRecordsIt() {
        BookTrailer t = queued(7L, 3L);
        when(books.facts(3L)).thenReturn(new TrailerBookSource.BookFacts("عنوان", "مؤلف", "ar", "books/3/source.pdf"));
        when(books.downloadPdf(eq("books/3/source.pdf"), any())).thenAnswer(i -> i.getArgument(1));
        when(gateway.uploadBook(any())).thenReturn("file_1");
        when(gateway.startSession(eq(7L), eq("file_1"), anyList(), contains("voice-1 — Sami"), anyString())).thenReturn("sesn_1");

        launcher.launch(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getSessionId()).isEqualTo("sesn_1");
        assertThat(t.getBookFileId()).isEqualTo("file_1");
        assertThat(t.getCoverFileId()).isNull();
        assertThat(t.getAgentVersion()).isEqualTo(4);
        verify(books, never()).downloadCover(any(), any());
        verify(endCardRenderer).render(argThat(spec -> spec.coverOrNull() == null && "عنوان".equals(spec.title())), any());
    }

    @Test
    void uploadsTheBookAndCoverWhenPresent() {
        BookTrailer t = queued(7L, 3L);
        bookWithCover();
        when(gateway.uploadBook(any())).thenReturn("file_book_1");
        when(gateway.uploadFile(any())).thenReturn("file_cover_1");
        when(gateway.startSession(eq(7L), eq("file_book_1"), anyList(), contains("Cover image: /workspace/cover.jpg"), anyString()))
                .thenReturn("sesn_1");

        launcher.launch(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getSessionId()).isEqualTo("sesn_1");
        assertThat(t.getBookFileId()).isEqualTo("file_book_1");
        assertThat(t.getCoverFileId()).isEqualTo("file_cover_1");
        verify(gateway).uploadBook(any());
        verify(gateway).uploadFile(any());
    }

    @Test
    void mountsEndCardLayersAndCoverAtFixedPaths() {
        queued(7L, 3L);
        bookWithCover();
        when(gateway.uploadBook(any())).thenReturn("file_book_1");
        when(gateway.uploadFile(any())).thenReturn("file_x");
        when(endCardRenderer.render(any(), any())).thenReturn(ALL_LAYERS);
        when(gateway.startSession(anyLong(), anyString(), anyList(), anyString(), anyString())).thenReturn("sesn_1");

        launcher.launch(7L);

        verify(gateway).startSession(eq(7L), eq("file_book_1"), argThat(files -> files.stream()
                        .map(TrailerAgentGateway.SessionFile::mountPath).toList()
                        .containsAll(List.of("/workspace/cover.jpg", "/workspace/endcard/scrim.png",
                                "/workspace/endcard/cover.png", "/workspace/endcard/title.png",
                                "/workspace/endcard/subtitle.png", "/workspace/endcard/rule.png", "/workspace/endcard/author.png",
                                "/workspace/endcard/logo.png", "/workspace/endcard/layout.json"))),
                contains("/workspace/endcard/"), anyString());
    }

    @Test
    void retryStillRendersTheCoverOnTheEndCard() {
        BookTrailer t = queued(7L, 3L);
        t.setBookFileId("file_book_1");
        t.setCoverFileId("file_cover_1"); // uploaded by an earlier, crashed launch
        bookWithCover();
        when(gateway.uploadFile(any())).thenReturn("file_x");
        when(endCardRenderer.render(any(), any())).thenReturn(ALL_LAYERS);
        when(gateway.startSession(anyLong(), anyString(), anyList(), anyString(), anyString())).thenReturn("sesn_1");

        launcher.launch(7L);

        verify(books).downloadCover(eq("books/3/cover.jpg"), any()); // still downloaded, for the end card
        verify(gateway, never()).uploadFile(argThat((Path p) -> p.endsWith("cover.jpg"))); // not re-uploaded
        verify(endCardRenderer).render(argThat(spec -> spec.coverOrNull() != null), any());
        verify(gateway, never()).uploadBook(any());
        // the persisted cover id is still mounted at /workspace/cover.jpg
        verify(gateway).startSession(eq(7L), eq("file_book_1"), argThat(files -> files.contains(
                new TrailerAgentGateway.SessionFile("file_cover_1", "/workspace/cover.jpg"))), anyString(), anyString());
    }

    @Test
    void withoutConfiguredVoicesTheTrailerFailsBeforeAnythingIsUploaded() {
        BookTrailer t = queued(9L, 5L);
        props.setVoices("");
        when(books.facts(5L)).thenReturn(new TrailerBookSource.BookFacts("t", "a", "ar", "books/5/source.pdf"));

        launcher.launch(9L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("KTAB_TRAILER_VOICES");
        verifyNoInteractions(gateway);
        verifyNoInteractions(endCardRenderer);
    }

    @Test
    void aBookWithoutAPdfFailsImmediately() {
        BookTrailer t = queued(8L, 4L);
        when(books.facts(4L)).thenReturn(new TrailerBookSource.BookFacts("t", "a", "ar", null));

        launcher.launch(8L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        verifyNoInteractions(gateway);
        verifyNoInteractions(endCardRenderer);
    }
}

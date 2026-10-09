package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookAboutAudioResponse;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.BookAboutAudio;
import com.doova.ktab.repository.book.BookAboutAudioRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class BookAboutAudioServiceImplTest {

    private final BookAboutAudioRepository repository = mock(BookAboutAudioRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private BookAboutAudioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookAboutAudioServiceImpl(repository, bookRepository, storage);
        when(bookRepository.existsById(110L)).thenReturn(true);
        when(storage.storeBytes(any(), anyString(), anyString(), anyString())).thenReturn("books/110/about-audio/new.mp3");
        when(storage.getFileUrl(anyString(), eq(UrlStrategy.SIGNED))).thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
        when(repository.findById(110L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(BookAboutAudio.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static byte[] mp3() {
        byte[] b = new byte[2000];
        b[0] = 'I';
        b[1] = 'D';
        b[2] = '3';
        return b;
    }

    private static byte[] withHeader(int... header) {
        byte[] b = new byte[2000];
        for (int i = 0; i < header.length; i++) b[i] = (byte) header[i];
        return b;
    }

    // ---------------------------------------------------------------- recognising the file

    @Test
    void theAcceptedFormatsAreRecognisedFromTheirFirstBytes() {
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(mp3())).contains(BookAboutAudioServiceImpl.AudioFormat.MP3);
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(withHeader(0xFF, 0xFB, 0x90, 0x00))).contains(BookAboutAudioServiceImpl.AudioFormat.MP3);
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(withHeader(0xFF, 0xF1, 0x50, 0x80))).contains(BookAboutAudioServiceImpl.AudioFormat.AAC);
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(withHeader(0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ')))
                .contains(BookAboutAudioServiceImpl.AudioFormat.M4A);
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(withHeader('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E')))
                .contains(BookAboutAudioServiceImpl.AudioFormat.WAV);
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(withHeader('O', 'g', 'g', 'S'))).contains(BookAboutAudioServiceImpl.AudioFormat.OGG);
    }

    @Test
    void somethingThatIsNotAudioIsRefused() {
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect("<html>not audio</html>".getBytes())).isEmpty();
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(withHeader('%', 'P', 'D', 'F'))).isEmpty();
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(new byte[0])).isEmpty();
        assertThat(BookAboutAudioServiceImpl.AudioFormat.detect(new byte[1])).isEmpty();
    }

    // ---------------------------------------------------------------- saving

    @Test
    void anAudioIsStoredInTheCloudAndItsPathAndDescriptionAreSaved() {
        BookAboutAudioResponse response = service.save(110L, mp3(), "intro.mp3", "  A short introduction.  ", 40);

        verify(storage).storeBytes(any(), eq("audio/mpeg"), eq("books/110/about-audio"), eq("mp3"));
        var saved = ArgumentCaptor.forClass(BookAboutAudio.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBookId()).isEqualTo(110L);
        assertThat(saved.getValue().getStoragePath()).isEqualTo("books/110/about-audio/new.mp3");
        assertThat(saved.getValue().getFileName()).isEqualTo("intro.mp3");
        assertThat(saved.getValue().getMimeType()).isEqualTo("audio/mpeg");
        assertThat(saved.getValue().getFileSize()).isEqualTo(2000);
        assertThat(saved.getValue().getDescription()).isEqualTo("A short introduction.");
        assertThat(saved.getValue().getDurationSeconds()).isEqualTo(40);

        assertThat(response.getUrl()).isEqualTo("https://signed/books/110/about-audio/new.mp3");
        assertThat(response.getDescription()).isEqualTo("A short introduction.");
        assertThat(response.getDurationSeconds()).isEqualTo(40);
        assertThat(response.getMimeType()).isEqualTo("audio/mpeg");
    }

    @Test
    void theTypeComesFromTheFileNotFromTheNameItWasGiven() {
        service.save(110L, mp3(), "malicious.pdf", null, null);

        verify(storage).storeBytes(any(), eq("audio/mpeg"), anyString(), eq("mp3"));
    }

    @Test
    void theFileNameKeepsNoFolders() {
        service.save(110L, mp3(), "C:\\Users\\me\\Desktop\\intro.mp3", null, null);

        var saved = ArgumentCaptor.forClass(BookAboutAudio.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFileName()).isEqualTo("intro.mp3");
    }

    @Test
    void aMissingNameGetsAGenericOne() {
        service.save(110L, mp3(), null, null, null);

        var saved = ArgumentCaptor.forClass(BookAboutAudio.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFileName()).isEqualTo("about-audio.mp3");
        assertThat(saved.getValue().getDescription()).isNull();
    }

    @Test
    void savingAgainReplacesTheAudioAndDeletesTheOldFile() {
        BookAboutAudio old = new BookAboutAudio();
        old.setBookId(110L);
        old.setStoragePath("books/110/about-audio/old.mp3");
        when(repository.findById(110L)).thenReturn(Optional.of(old));

        service.save(110L, mp3(), "new.mp3", "New description", null);

        assertThat(old.getStoragePath()).isEqualTo("books/110/about-audio/new.mp3");
        assertThat(old.getDescription()).isEqualTo("New description");
        verify(storage).deleteFile("books/110/about-audio/old.mp3");
        verify(storage, never()).deleteFile("books/110/about-audio/new.mp3");
    }

    @Test
    void ifTheDatabaseSaveFailsTheNewFileIsNotLeftBehindAndTheOldOneIsKept() {
        BookAboutAudio old = new BookAboutAudio();
        old.setBookId(110L);
        old.setStoragePath("books/110/about-audio/old.mp3");
        when(repository.findById(110L)).thenReturn(Optional.of(old));
        when(repository.saveAndFlush(any(BookAboutAudio.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.save(110L, mp3(), "new.mp3", null, null)).isInstanceOf(IllegalStateException.class);

        verify(storage).deleteFile("books/110/about-audio/new.mp3");
        verify(storage, never()).deleteFile("books/110/about-audio/old.mp3");
    }

    @Test
    void aFailureDeletingTheOldFileDoesNotFailTheSave() {
        BookAboutAudio old = new BookAboutAudio();
        old.setBookId(110L);
        old.setStoragePath("books/110/about-audio/old.mp3");
        when(repository.findById(110L)).thenReturn(Optional.of(old));
        doThrow(new IllegalStateException("r2 down")).when(storage).deleteFile("books/110/about-audio/old.mp3");

        assertThat(service.save(110L, mp3(), "new.mp3", null, null).getUrl()).contains("new.mp3");
    }

    // ---------------------------------------------------------------- refusing bad input

    @Test
    void aBookThatDoesNotExistIsRefusedBeforeAnythingIsUploaded() {
        when(bookRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.save(999L, mp3(), "a.mp3", null, null)).isInstanceOf(ResourceNotFoundException.class);

        verify(storage, never()).storeBytes(any(), anyString(), anyString(), anyString());
    }

    @Test
    void anEmptyOrNonAudioFileIsRefusedBeforeAnythingIsUploaded() {
        assertThatThrownBy(() -> service.save(110L, new byte[0], "a.mp3", null, null))
                .isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getMessageKey()).isEqualTo(ApiMessageKey.BOOK_ABOUT_AUDIO_INVALID));
        assertThatThrownBy(() -> service.save(110L, "hello".getBytes(), "a.mp3", null, null))
                .isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getMessageKey()).isEqualTo(ApiMessageKey.BOOK_ABOUT_AUDIO_INVALID));

        verify(storage, never()).storeBytes(any(), anyString(), anyString(), anyString());
    }

    @Test
    void aFileOver15MbIsRefused() {
        byte[] big = new byte[(int) BookAboutAudioServiceImpl.MAX_BYTES + 1];
        big[0] = 'I';
        big[1] = 'D';
        big[2] = '3';

        assertThatThrownBy(() -> service.save(110L, big, "a.mp3", null, null))
                .isInstanceOfSatisfying(BadRequestException.class, e -> assertThat(e.getMessageKey()).isEqualTo(ApiMessageKey.BOOK_ABOUT_AUDIO_TOO_LARGE));

        verify(storage, never()).storeBytes(any(), anyString(), anyString(), anyString());
    }

    @Test
    void aTooLongDescriptionOrAnImpossibleDurationIsRefused() {
        String tooLong = "ا".repeat(BookAboutAudioServiceImpl.MAX_DESCRIPTION_CHARS + 1);

        assertThatThrownBy(() -> service.save(110L, mp3(), "a.mp3", tooLong, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.save(110L, mp3(), "a.mp3", null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.save(110L, mp3(), "a.mp3", null, 99999)).isInstanceOf(BadRequestException.class);

        verify(storage, never()).storeBytes(any(), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------- reading and deleting

    @Test
    void booksWithAnAudioAreReturnedKeyedByBookInOneQuery() {
        BookAboutAudio one = new BookAboutAudio();
        one.setBookId(110L);
        one.setStoragePath("p/110.mp3");
        one.setMimeType("audio/mpeg");
        one.setDescription("d110");
        BookAboutAudio two = new BookAboutAudio();
        two.setBookId(122L);
        two.setStoragePath("p/122.mp3");
        two.setMimeType("audio/mpeg");
        when(repository.findAllByBookIdIn(anyCollection())).thenReturn(List.of(one, two));

        Map<Long, BookAboutAudioResponse> byBook = service.findAll(List.of(110L, 122L, 999L));

        assertThat(byBook).containsOnlyKeys(110L, 122L);
        assertThat(byBook.get(110L).getUrl()).isEqualTo("https://signed/p/110.mp3");
        assertThat(byBook.get(122L).getDescription()).isNull();
        verify(repository).findAllByBookIdIn(List.of(110L, 122L, 999L));
    }

    @Test
    void askingForNoBooksMakesNoQuery() {
        assertThat(service.findAll(List.of())).isEmpty();
        assertThat(service.findAll(null)).isEmpty();

        verify(repository, never()).findAllByBookIdIn(anyCollection());
    }

    @Test
    void aBookWithoutAnAudioHasNone() {
        assertThat(service.find(110L)).isEmpty();
    }

    @Test
    void deletingRemovesTheRowAndTheFile() {
        BookAboutAudio row = new BookAboutAudio();
        row.setBookId(110L);
        row.setStoragePath("books/110/about-audio/a.mp3");
        when(repository.findById(110L)).thenReturn(Optional.of(row));

        service.delete(110L);

        verify(repository).delete(row);
        verify(storage).deleteFile("books/110/about-audio/a.mp3");
    }

    @Test
    void deletingWhatDoesNotExistIsANotFound() {
        assertThatThrownBy(() -> service.delete(110L)).isInstanceOf(ResourceNotFoundException.class);

        verify(storage, never()).deleteFile(anyString());
    }
}

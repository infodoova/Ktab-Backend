package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookAboutAudioResponse;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.BookAboutAudio;
import com.doova.ktab.repository.book.BookAboutAudioRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookAboutAudioService;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookAboutAudioServiceImpl implements BookAboutAudioService {

    static final long MAX_BYTES = 15L * 1024 * 1024;
    static final int MAX_DESCRIPTION_CHARS = 2000;
    static final int MAX_DURATION_SECONDS = 3600;

    private final BookAboutAudioRepository repository;
    private final BookRepository bookRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public BookAboutAudioResponse save(Long bookId, byte[] content, String originalFileName, String description, Integer durationSeconds) {
        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException(ApiMessageKey.RESOURCE_NOT_FOUND);
        }
        if (content == null || content.length == 0) {
            throw new BadRequestException(ApiMessageKey.BOOK_ABOUT_AUDIO_INVALID);
        }
        if (content.length > MAX_BYTES) {
            throw new BadRequestException(ApiMessageKey.BOOK_ABOUT_AUDIO_TOO_LARGE);
        }
        AudioFormat format = AudioFormat.detect(content)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.BOOK_ABOUT_AUDIO_INVALID));
        String cleanDescription = cleanDescription(description);
        if (durationSeconds != null && (durationSeconds < 1 || durationSeconds > MAX_DURATION_SECONDS)) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        String newPath = fileStorageService.storeBytes(content, format.contentType, "books/" + bookId + "/about-audio", format.extension);

        Optional<BookAboutAudio> existing = repository.findById(bookId);
        String oldPath = existing.map(BookAboutAudio::getStoragePath).orElse(null);
        BookAboutAudio row = existing.orElseGet(BookAboutAudio::new);
        row.setBookId(bookId);
        row.setStoragePath(newPath);
        row.setFileName(cleanFileName(originalFileName, format));
        row.setMimeType(format.contentType);
        row.setFileSize(content.length);
        row.setDurationSeconds(durationSeconds);
        row.setDescription(cleanDescription);
        try {
            repository.saveAndFlush(row);
        } catch (RuntimeException e) {
            // The file would otherwise be left in storage with nothing pointing at it.
            deleteQuietly(newPath);
            throw e;
        }

        if (oldPath != null && !oldPath.equals(newPath)) {
            deleteQuietly(oldPath);
        }
        log.info("BOOK_ABOUT_AUDIO_SAVED bookId={} path={} bytes={}", bookId, newPath, content.length);
        return toResponse(row);
    }

    @Override
    @Transactional
    public void delete(Long bookId) {
        BookAboutAudio row = repository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.BOOK_ABOUT_AUDIO_NOT_FOUND));
        repository.delete(row);
        repository.flush();
        deleteQuietly(row.getStoragePath());
        log.info("BOOK_ABOUT_AUDIO_DELETED bookId={} path={}", bookId, row.getStoragePath());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookAboutAudioResponse> find(Long bookId) {
        return repository.findById(bookId).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BookAboutAudioResponse> findAll(Collection<Long> bookIds) {
        Map<Long, BookAboutAudioResponse> byBook = new HashMap<>();
        if (bookIds == null || bookIds.isEmpty()) {
            return byBook;
        }
        for (BookAboutAudio row : repository.findAllByBookIdIn(bookIds)) {
            byBook.put(row.getBookId(), toResponse(row));
        }
        return byBook;
    }

    private BookAboutAudioResponse toResponse(BookAboutAudio row) {
        return BookAboutAudioResponse.builder()
                .url(fileStorageService.getFileUrl(row.getStoragePath(), UrlStrategy.SIGNED))
                .description(row.getDescription())
                .durationSeconds(row.getDurationSeconds())
                .mimeType(row.getMimeType())
                .build();
    }

    private void deleteQuietly(String path) {
        try {
            fileStorageService.deleteFile(path);
        } catch (RuntimeException e) {
            log.warn("BOOK_ABOUT_AUDIO_FILE_NOT_DELETED path={} err={}", path, e.toString());
        }
    }

    private static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_CHARS) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }
        return trimmed;
    }

    /** The name the file had, without any folders, or a generic one when there was none. */
    private static String cleanFileName(String original, AudioFormat format) {
        if (original == null || original.isBlank()) {
            return "about-audio." + format.extension;
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            return "about-audio." + format.extension;
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /** The audio formats accepted, recognised from the first bytes of the file. */
    enum AudioFormat {
        MP3("audio/mpeg", "mp3"),
        M4A("audio/mp4", "m4a"),
        WAV("audio/wav", "wav"),
        AAC("audio/aac", "aac"),
        OGG("audio/ogg", "ogg");

        final String contentType;
        final String extension;

        AudioFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static Optional<AudioFormat> detect(byte[] b) {
            if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
                return Optional.of(MP3);
            }
            if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
                return Optional.of(M4A);
            }
            if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') {
                return Optional.of(WAV);
            }
            if (b.length >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') {
                return Optional.of(OGG);
            }
            if (b.length >= 2 && (b[0] & 0xFF) == 0xFF) {
                // ADTS (AAC) frames have layer bits 00; MPEG audio (MP3) frames have a non-zero layer.
                if ((b[1] & 0xF6) == 0xF0) {
                    return Optional.of(AAC);
                }
                if ((b[1] & 0xE0) == 0xE0) {
                    return Optional.of(MP3);
                }
            }
            return Optional.empty();
        }
    }
}

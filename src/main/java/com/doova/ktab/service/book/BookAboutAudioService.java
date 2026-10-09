package com.doova.ktab.service.book;

import com.doova.ktab.dto.book.BookAboutAudioResponse;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The short audio that introduces a book. It is recorded elsewhere and uploaded here: the file goes to object storage
 * and its path and description are kept in the database, so it can be returned together with the book.
 */
public interface BookAboutAudioService {

    /**
     * Stores the audio of a book, replacing the one it had (the old file is deleted from storage).
     *
     * @param content         the audio file; MP3, M4A, WAV, AAC or OGG, at most 15 MB. The type is recognised from the
     *                        content itself, not from the name the client gave
     * @param originalFileName the name the file had, kept for reference
     * @param description     what the audio is about; optional, at most 2000 characters
     * @param durationSeconds its length in seconds; optional
     */
    BookAboutAudioResponse save(Long bookId, byte[] content, String originalFileName, String description, Integer durationSeconds);

    /** Removes the audio of a book from storage and the database. */
    void delete(Long bookId);

    Optional<BookAboutAudioResponse> find(Long bookId);

    /** The audio of each of the given books that has one, keyed by book id; one query for all. */
    Map<Long, BookAboutAudioResponse> findAll(Collection<Long> bookIds);
}

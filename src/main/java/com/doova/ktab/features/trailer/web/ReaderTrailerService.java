package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** The newest finished trailer of a published book, for readers. Anything else is "not found", never a hint of what exists. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class ReaderTrailerService {

    static final Duration LINK_LIFETIME = Duration.ofMinutes(10);

    private final BookRepository books;
    private final BookTrailerRepository trailers;
    private final FileStorageService storage;

    @Transactional(readOnly = true)
    public ReaderTrailerView latest(Long bookId) {
        boolean published = books.findById(bookId).map(b -> b.getStatus() == BookStatus.PUBLISHED).orElse(false);
        if (!published) {
            throw new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND);
        }
        // READY only: a trailer held for review, failed or cancelled is never shown to readers.
        BookTrailer t = trailers.findFirstByBookIdAndStatusOrderByIdDesc(bookId, TrailerStatus.READY)
                .filter(found -> found.getVideoKey() != null)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
        return new ReaderTrailerView(t.getId(),
                presign(t.getVideoKey(), "trailer-" + bookId + ".mp4"),
                t.getCleanVideoKey() == null ? null : presign(t.getCleanVideoKey(), "trailer-" + bookId + "-clean.mp4"),
                t.getCaptionsKey() == null ? null : presign(t.getCaptionsKey(), "trailer-" + bookId + "-ar.srt"),
                LINK_LIFETIME.toSeconds());
    }

    private String presign(String key, String filename) {
        return storage.getPreSignedDownloadUrl(key, LINK_LIFETIME, filename);
    }
}

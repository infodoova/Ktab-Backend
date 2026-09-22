package com.doova.ktab.features.ocr.image;

import com.doova.ktab.repository.book.BookPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageRenumberer {

    private final BookPageRepository bookPageRepository;

    /**
     * Transactionally shifts all page numbers starting from `fromPage` by `delta`.
     * Used when an unsplit spread is split post-OCR, introducing a new book page.
     */
    @Transactional
    public void shiftPageNumbers(Long bookId, int fromPage, int delta) {
        log.info("Shifting page numbers for bookId={} from page {} by delta {}", bookId, fromPage, delta);
        bookPageRepository.shiftPageNumbers(bookId, fromPage, delta);
    }
}

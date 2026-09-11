package com.doova.ktab.features.ocr.service.impl;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.features.ocr.service.OcrPageProcessingService;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of OCR page processing domain logic.
 * Encapsulates transactional state, idempotency check, concurrency throttling, and AI execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrPageProcessingServiceImpl implements OcrPageProcessingService {

    private final GeminiOcrService geminiService;
    private final DynamicConcurrencyGate concurrencyGate;
    private final BookPageRepository sectionRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public boolean processPage(OcrPageMessage pageMessage) {
        // 1. Idempotency Check
        if (sectionRepository.existsByBook_IdAndPageNumber(pageMessage.bookId(), pageMessage.pageNumber())) {
            log.debug("Page already processed, skipping: book={}, page={}",
                    pageMessage.bookId(), pageMessage.pageNumber());
            return false;
        }

        // 2. Throttled AI Execution
        GeminiOcrResponse response;
        try (var ignored = concurrencyGate.acquire()) {
            response = geminiService.ocrOnePageFromUrl(
                    pageMessage.presignedUrl(),
                    pageMessage.mime()
            );
        }

        // 3. Entity Construction & Persistence
        Book book = entityManager.getReference(Book.class, pageMessage.bookId());

        BookPage page = new BookPage();
        page.setBook(book);
        page.setPageNumber(pageMessage.pageNumber());
        page.setMarkdownContent(response.markdown());
        page.setWordCount(response.wordCount());
        page.setStatus(OcrStatus.COMPLETED);

        sectionRepository.save(page);

        log.debug("Successfully saved processed page {} for book {}",
                pageMessage.pageNumber(), pageMessage.bookId());
        return true;
    }
}

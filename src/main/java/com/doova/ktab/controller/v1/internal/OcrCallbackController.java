package com.doova.ktab.controller.v1.internal;

import com.doova.ktab.config.qstash.QStashSignatureVerifier;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/internal/ocr", "/internal/ocr"})
@RequiredArgsConstructor
@Slf4j
public class OcrCallbackController {

    private final QStashSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final GeminiOcrService geminiService;
    private final DynamicConcurrencyGate concurrencyGate;
    private final BookPageRepository sectionRepository;
    private final BookRepository bookRepository;
    private final EntityManager entityManager;
    private final MeterRegistry meterRegistry;

    @PostMapping(value = "/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> processOcrPage(
            @RequestHeader(value = "Upstash-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        Timer.Sample sample = Timer.start(meterRegistry);

        // 1. Signature Verification
        if (!signatureVerifier.verify(signature, rawBody)) {
            log.warn("Unauthorized OCR callback received - signature check failed");
            meterRegistry.counter("ocr.qstash.unauthorized").increment();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        OcrPageMessage pageMessage;
        try {
            pageMessage = objectMapper.readValue(rawBody, OcrPageMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse OCR message body: {}", e.getMessage());
            meterRegistry.counter("ocr.qstash.parse.errors").increment();
            return ResponseEntity.badRequest().build();
        }

        try {
            // 2. Idempotency Check
            if (sectionRepository.existsByBook_IdAndPageNumber(pageMessage.bookId(), pageMessage.pageNumber())) {
                log.debug("Page already processed, skipping: book={}, page={}",
                        pageMessage.bookId(), pageMessage.pageNumber());
                meterRegistry.counter("ocr.qstash.pages.skipped").increment();
                return ResponseEntity.ok().build();
            }

            // 3. Process Page
            processPage(pageMessage);

            meterRegistry.counter("ocr.qstash.pages.processed").increment();
            sample.stop(meterRegistry.timer("ocr.qstash.page.duration", "status", "success"));
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to process OCR page {} for book {}: {}",
                    pageMessage.pageNumber(), pageMessage.bookId(), e.getMessage(), e);
            meterRegistry.counter("ocr.qstash.pages.failed").increment();
            sample.stop(meterRegistry.timer("ocr.qstash.page.duration", "status", "failure"));

            // Returning 5xx signals QStash to retry according to configured retry policy
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Transactional
    protected void processPage(OcrPageMessage pageMessage) {
        try (var ignored = concurrencyGate.acquire()) {
            GeminiOcrResponse response = geminiService.ocrOnePageFromUrl(
                    pageMessage.presignedUrl(),
                    pageMessage.mime()
            );

            Book book = entityManager.getReference(Book.class, pageMessage.bookId());

            BookPage page = new BookPage();
            page.setBook(book);
            page.setPageNumber(pageMessage.pageNumber());
            page.setMarkdownContent(response.markdown());
            page.setWordCount(response.wordCount());
            page.setStatus(OcrStatus.COMPLETED);

            sectionRepository.save(page);

            log.debug("Processed page {} for book {} via QStash webhook",
                    pageMessage.pageNumber(), pageMessage.bookId());
        }
    }
}

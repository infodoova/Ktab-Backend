package com.doova.ktab.ocr.batch;

import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.service.ocr.S3OcrStorageService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemStreamSupport;

import java.util.List;

/**
 * Restartable page reader that uses presigned URLs instead of loading bytes into memory.
 * This prevents OOM errors when processing large books with many pages.
 */
@Slf4j
public class RestartableS3PageReader extends ItemStreamSupport implements ItemStreamReader<PageItem> {

    private static final String CTX_CURSOR = "cursor";

    private final Long bookId;
    private final List<String> pageKeys;
    private final S3OcrStorageService s3;
    private final BookPageRepository sections;
    private final MeterRegistry meterRegistry;

    private int cursor;
    private int totalPages;
    private int skippedPages;

    public RestartableS3PageReader(
            Long bookId,
            List<String> pageKeys,
            S3OcrStorageService s3,
            BookPageRepository sections,
            MeterRegistry meterRegistry
    ) {
        this.bookId = bookId;
        this.pageKeys = pageKeys;
        this.s3 = s3;
        this.sections = sections;
        this.meterRegistry = meterRegistry;
        this.totalPages = pageKeys.size();
    }

    @Override
    public void open(ExecutionContext executionContext) {
        this.cursor = executionContext.containsKey(CTX_CURSOR)
                ? executionContext.getInt(CTX_CURSOR)
                : 0;
        this.skippedPages = 0;
        
        log.info("Opening page reader for book {} at cursor {}, total pages: {}", 
                bookId, cursor, totalPages);
        meterRegistry.gauge("ocr.reader.total_pages", bookId, b -> totalPages);
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putInt(CTX_CURSOR, cursor);
    }

    @Override
    public void close() {
        log.info("Closing page reader for book {}. Processed: {}, Skipped: {}", 
                bookId, cursor, skippedPages);
    }

    @Override
    public PageItem read() {
        while (cursor < pageKeys.size()) {
            int pageNumber = cursor + 1;
            String key = pageKeys.get(cursor);
            cursor++;

            // Idempotency: skip already processed pages (restart-safe)
            if (sections.existsByBook_IdAndPageNumber(bookId, pageNumber)) {
                skippedPages++;
                meterRegistry.counter("ocr.reader.pages.skipped").increment();
                log.debug("Skipping already processed page {} for book {}", pageNumber, bookId);
                continue;
            }

            try {
                // Generate presigned URL instead of loading bytes into memory
                String presignedUrl = s3.generatePresignedUrl(key);
                String mime = resolveMime(key);

                meterRegistry.counter("ocr.reader.pages.read").increment();
                
                return new PageItem(bookId, pageNumber, key, mime, presignedUrl);

            } catch (Exception e) {
                // Log and skip failed pages - the skip listener will handle recording
                log.error("Failed to prepare OCR page [bookId={}, page={}, key={}]",
                        bookId, pageNumber, key, e);
                meterRegistry.counter("ocr.reader.pages.errors").increment();
                // Continue to next page
            }
        }
        
        log.info("Page reader exhausted for book {}. Total read: {}", bookId, cursor);
        return null; // End of stream
    }

    private String resolveMime(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "image/png"; // Default
    }
}

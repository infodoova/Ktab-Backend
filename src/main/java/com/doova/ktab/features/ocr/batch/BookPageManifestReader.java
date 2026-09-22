package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemStreamSupport;

import java.util.List;

/**
 * ItemReader that reads pages from the database manifest (tbl_book_pages)
 * with status PENDING, FLAGGED, or FAILED.
 */
@Slf4j
public class BookPageManifestReader extends ItemStreamSupport implements ItemStreamReader<PageItem> {

    private static final String CTX_CURSOR = "manifest_cursor";

    private final Long bookId;
    private final BookPageRepository pageRepository;
    private final S3OcrStorageService s3;
    private final MeterRegistry meterRegistry;

    private List<BookPage> pagesToProcess;
    private int cursor;

    public BookPageManifestReader(
            Long bookId,
            BookPageRepository pageRepository,
            S3OcrStorageService s3,
            MeterRegistry meterRegistry
    ) {
        this.bookId = bookId;
        this.pageRepository = pageRepository;
        this.s3 = s3;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        this.cursor = executionContext.containsKey(CTX_CURSOR)
                ? executionContext.getInt(CTX_CURSOR)
                : 0;

        // Fetch pages needing processing
        this.pagesToProcess = pageRepository.findByBookIdAndStatusInOrderByPageNumberAsc(
                bookId,
                List.of(OcrStatus.PENDING, OcrStatus.FLAGGED, OcrStatus.FAILED)
        );

        log.info("Opening BookPageManifestReader for book {} at cursor {}, pending pages: {}",
                bookId, cursor, pagesToProcess.size());
        meterRegistry.gauge("ocr.manifest.reader.pending_pages", bookId, b -> pagesToProcess.size());
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putInt(CTX_CURSOR, cursor);
    }

    @Override
    public void close() {
        log.info("Closing BookPageManifestReader for book {}. Processed {}/{} pages.",
                bookId, cursor, pagesToProcess != null ? pagesToProcess.size() : 0);
    }

    @Override
    public PageItem read() {
        if (pagesToProcess == null) {
            open(new ExecutionContext());
        }
        if (pagesToProcess == null || cursor >= pagesToProcess.size()) {
            return null; // Exhausted
        }

        BookPage page = pagesToProcess.get(cursor++);
        int pageNumber = page.getPageNumber();
        String key = String.format("books/%d/pages/page-%04d.png", bookId, pageNumber);
        String presignedUrl = s3.generatePresignedUrl(key);
        String mime = "image/png";

        meterRegistry.counter("ocr.manifest.reader.pages.read").increment();

        return new PageItem(
                bookId,
                pageNumber,
                key,
                mime,
                presignedUrl,
                page.getSourcePdfPage() != null ? page.getSourcePdfPage() : pageNumber,
                page.getSpreadSide(),
                page.getRotationDegrees()
        );
    }
}

package com.doova.ktab.features.extraction.batch;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.dto.BookExtractionResult;
import com.doova.ktab.features.extraction.pdf.PdfRejectedException;
import com.doova.ktab.features.extraction.persist.ExtractionPersister;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.storage.ObjectStorageService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/** The one step of nativeIngestionJob: read the PDF from storage, extract it with Ktab's own service, save it. */
@Slf4j
public class NativeExtractTasklet implements Tasklet {

    private final Long bookId;
    private final String pdfKey;
    private final ObjectStorageService storage;
    private final BookExtractionService extraction;
    private final ExtractionPersister persister;
    private final BookRepository bookRepository;
    private final MeterRegistry meters;

    public NativeExtractTasklet(Long bookId, String pdfKey, ObjectStorageService storage, BookExtractionService extraction,
                                ExtractionPersister persister, BookRepository bookRepository, MeterRegistry meters) {
        this.bookId = bookId;
        this.pdfKey = pdfKey;
        this.storage = storage;
        this.extraction = extraction;
        this.persister = persister;
        this.bookRepository = bookRepository;
        this.meters = meters;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));
        BookExtractionResult result;
        try {
            result = extraction.extract(storage.getBytes(pdfKey));
        } catch (PdfRejectedException e) {
            book.setOcrStatus(OcrStatus.FAILED);
            bookRepository.save(book);
            meters.counter("extraction.native.rejected", "reason", e.getReason().name()).increment();
            log.warn("native extraction rejected bookId={} reason={}: {}", bookId, e.getReason(), e.getMessage());
            throw new IllegalStateException(e.getReason() == PdfRejectedException.Reason.NO_TEXT_LAYER
                    ? e.getMessage()
                    : "Native extraction rejected the PDF: " + e.getReason() + " - " + e.getMessage(), e);
        }
        persister.persist(book, result);
        meters.counter("extraction.native.result", "source", result.structureDetection().source().name()).increment();
        result.warnings().forEach(w -> meters.counter("extraction.native.warnings", "code", w.code().name()).increment());
        log.info("native extraction saved bookId={} pages={} chapters={} source={} confidence={}", bookId,
                result.pages().size(), result.chapters().size(), result.structureDetection().source(),
                result.structureDetection().confidence());
        return RepeatStatus.FINISHED;
    }
}

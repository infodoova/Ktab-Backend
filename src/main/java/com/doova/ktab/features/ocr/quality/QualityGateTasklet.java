package com.doova.ktab.features.ocr.quality;

import com.doova.ktab.enums.book.ImageQuality;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class QualityGateTasklet implements Tasklet {

    private final Long bookId;
    private final BookRepository bookRepository;
    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final OcrProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("QualityGate START for bookId={}", bookId);

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        List<BookPage> pages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        List<BookSection> sections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);

        if (pages.isEmpty()) {
            log.warn("No pages to evaluate in QualityGate for bookId={}", bookId);
            return RepeatStatus.FINISHED;
        }

        int totalPages = pages.size();
        long completedPages = 0;
        long flaggedPages = 0;
        long failedPages = 0;
        long poorQualityPages = 0;
        long bodyPages = 0;
        long bodyPagesWithSection = 0;

        for (BookPage page : pages) {
            if (page.getStatus() == OcrStatus.FAILED) {
                failedPages++;
            } else if (page.getStatus() == OcrStatus.FLAGGED) {
                flaggedPages++;
            } else {
                completedPages++;
            }

            if (page.getImageQuality() == ImageQuality.POOR) {
                poorQualityPages++;
            }

            if (page.getPageKind() == PageKind.BODY) {
                bodyPages++;
                if (page.getSection() != null && page.getSection().getSectionType() != SectionType.FRONT_MATTER) {
                    bodyPagesWithSection++;
                }
            }
        }

        double poorRatio = (double) poorQualityPages / totalPages;
        double sectionCoverage = bodyPages > 0 ? (double) bodyPagesWithSection / bodyPages : 1.0;
        long lowConfidenceSections = sections.stream()
                .filter(s -> s.isNeedsReview() || s.getConfidence().doubleValue() < properties.getStructure().getReviewConfidence())
                .count();

        boolean needsReview = flaggedPages > 0 || failedPages > 0 ||
                poorRatio > properties.getQuality().getMaxPoorRatio() ||
                lowConfidenceSections > 0 || sectionCoverage < 0.80;

        if (book.getStructureStatus() != StructureStatus.MANUAL) {
            book.setStructureStatus(needsReview ? StructureStatus.NEEDS_REVIEW : StructureStatus.RESOLVED);
        }

        // Update book overall OCR status
        if (failedPages > 0) {
            book.setOcrStatus(OcrStatus.FAILED);
        } else if (flaggedPages > 0 || needsReview) {
            book.setOcrStatus(OcrStatus.FLAGGED);
        } else {
            book.setOcrStatus(OcrStatus.COMPLETED);
        }

        bookRepository.save(book);

        final long finalCompleted = completedPages;
        final long finalFlagged = flaggedPages;
        final long finalFailed = failedPages;
        final long finalPoor = poorQualityPages;
        final long finalLowConf = lowConfidenceSections;
        final int finalTotal = totalPages;

        meterRegistry.gauge("ocr.quality.pages.total", bookId, b -> finalTotal);
        meterRegistry.gauge("ocr.quality.pages.completed", bookId, b -> finalCompleted);
        meterRegistry.gauge("ocr.quality.pages.flagged", bookId, b -> finalFlagged);
        meterRegistry.gauge("ocr.quality.pages.failed", bookId, b -> finalFailed);
        meterRegistry.gauge("ocr.quality.pages.poor", bookId, b -> finalPoor);
        meterRegistry.gauge("ocr.quality.sections.low_confidence", bookId, b -> finalLowConf);

        log.info("QualityGate DONE for bookId={}: status={}, ocrStatus={}, flagged={}, failed={}, poorRatio={:.2f}, coverage={:.2f}",
                bookId, book.getStructureStatus(), book.getOcrStatus(), flaggedPages, failedPages, poorRatio, sectionCoverage);

        return RepeatStatus.FINISHED;
    }
}

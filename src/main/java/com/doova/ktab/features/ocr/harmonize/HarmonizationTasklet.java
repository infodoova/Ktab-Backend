package com.doova.ktab.features.ocr.harmonize;

import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class HarmonizationTasklet implements Tasklet {

    private final Long bookId;
    private final BookPageRepository pageRepository;
    private final HarmonizationService harmonizationService;
    private final PageStitcher pageStitcher;
    private final OcrProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (!properties.getHarmonize().isEnabled()) {
            log.info("Harmonization step is disabled via ktab.ocr.harmonize.enabled=false. Skipping for bookId={}", bookId);
            return RepeatStatus.FINISHED;
        }

        log.info("Harmonization START for bookId={}", bookId);
        meterRegistry.counter("ocr.harmonize.jobs.started").increment();

        List<BookPage> pages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        pageStitcher.evaluateStitching(pages);

        int harmonizedCount = 0;
        int rejectedCount = 0;

        for (int i = 0; i < pages.size(); i++) {
            BookPage curr = pages.get(i);
            String rawText = curr.getMarkdownContent();
            if (rawText == null || rawText.isBlank()) continue;

            String prevTail = (i > 0) ? extractTail(pages.get(i - 1).getMarkdownContent()) : null;
            String nextHead = (i + 1 < pages.size()) ? extractHead(pages.get(i + 1).getMarkdownContent()) : null;
            String sectionTitle = curr.getSection() != null ? curr.getSection().getTitle() : null;

            Optional<String> cleanOpt = harmonizationService.harmonize(prevTail, rawText, nextHead, sectionTitle);
            if (cleanOpt.isPresent()) {
                curr.setMarkdownClean(cleanOpt.get());
                harmonizedCount++;
            } else {
                curr.setMarkdownClean(null);
                rejectedCount++;
            }
        }

        pageRepository.saveAll(pages);
        log.info("Harmonization DONE for bookId={}. Harmonized: {}, Rejected: {}",
                bookId, harmonizedCount, rejectedCount);
        meterRegistry.counter("ocr.harmonize.pages.success").increment(harmonizedCount);
        meterRegistry.counter("ocr.harmonize.pages.rejected").increment(rejectedCount);

        return RepeatStatus.FINISHED;
    }

    private String extractTail(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\n");
        return lines[lines.length - 1].trim();
    }

    private String extractHead(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\n");
        return lines[0].trim();
    }
}

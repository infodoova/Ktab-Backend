package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class TocExtractionTasklet implements Tasklet {

    private final Long bookId;
    private final List<TocSource> tocSources;
    private final BookRepository bookRepository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("TocExtraction START for bookId={}", bookId);
        meterRegistry.counter("ocr.toc.extraction.started").increment();

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        boolean found = false;
        for (TocSource source : tocSources) {
            log.debug("Trying TOC source {} for bookId={}", source.source(), bookId);
            Optional<RawToc> result = source.extract(bookId);

            if (result.isPresent() && !result.get().entries().isEmpty()) {
                RawToc rawToc = result.get();
                String json = objectMapper.writeValueAsString(rawToc);

                book.setTocRaw(json);
                book.setStructureSource(source.source());
                bookRepository.save(book);

                log.info("Successfully extracted {} TOC entries via {} for bookId={}",
                        rawToc.entries().size(), source.source(), bookId);
                meterRegistry.counter("ocr.toc.extraction.success", "source", source.source().name()).increment();
                found = true;
                break;
            }
        }

        if (!found) {
            log.info("No TOC found for bookId={}; falling back to HEADINGS source", bookId);
            book.setStructureSource(StructureSource.HEADINGS);
            bookRepository.save(book);
            meterRegistry.counter("ocr.toc.extraction.fallback_headings").increment();
        }

        return RepeatStatus.FINISHED;
    }
}

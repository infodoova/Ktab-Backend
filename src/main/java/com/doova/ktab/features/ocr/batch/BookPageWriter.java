package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookPageWriter implements ItemWriter<OcrResult> {

    private final EntityManager em;
    private final BookPageRepository repository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:100}")
    private int batchSize;

    @Override
    @Transactional
    public void write(Chunk<? extends OcrResult> chunk) throws Exception {
        if (chunk.isEmpty()) return;

        Timer.Sample sample = Timer.start(meterRegistry);
        Long bookId = chunk.getItems().getFirst().bookId();
        Book book = em.getReference(Book.class, bookId);

        int written = 0;

        for (int i = 0; i < chunk.size(); i++) {
            OcrResult result = chunk.getItems().get(i);

            Optional<BookPage> existing = repository.findByBookIdAndPageNumber(bookId, result.pageNumber());
            BookPage page;
            if (existing.isPresent()) {
                page = existing.get();
            } else {
                page = new BookPage();
                page.setBook(book);
                page.setPageNumber(result.pageNumber());
            }

            page.setMarkdownContent(result.bodyMarkdown());
            page.setFootnotesMarkdown(result.footnotesMarkdown());
            page.setPageKind(result.pageKind());
            if (result.imageQuality() != null) {
                page.setImageQuality(result.imageQuality());
            }
            page.setPrintedPageLabel(result.printedPageLabel());
            page.setRunningHeader(result.runningHeader());
            page.setHeadings(objectMapper.writeValueAsString(result.headings()));
            page.setStartsMidSentence(result.startsMidSentence());
            page.setEndsMidSentence(result.endsMidSentence());
            page.setQualityFlags(objectMapper.writeValueAsString(result.qualityFlags()));
            page.setOcrModel(result.model());
            page.setPromptVersion(result.promptVersion());
            page.setWordCount(result.wordCount());
            page.setStatus(result.status());
            page.setRotationDegrees(result.rotationDegrees());
            page.setErrorMessage(null);

            if (page.getId() == null) {
                em.persist(page);
            } else {
                em.merge(page);
            }
            written++;

            if (written > 0 && written % batchSize == 0) {
                em.flush();
                em.clear();
                book = em.getReference(Book.class, bookId);
            }
        }

        if (written % batchSize != 0) {
            em.flush();
            em.clear();
        }

        sample.stop(meterRegistry.timer("ocr.writer.chunk.duration"));
        meterRegistry.counter("ocr.writer.pages.written").increment(written);

        int totalWords = chunk.getItems().stream().mapToInt(OcrResult::wordCount).sum();
        meterRegistry.counter("ocr.writer.words.total").increment(totalWords);

        log.debug("Written {} structured OCR pages for book {}", written, bookId);
    }
}

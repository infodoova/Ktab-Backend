package com.doova.ktab.ocr.batch;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class BookSectionWriter implements ItemWriter<OcrResult> {

    private final EntityManager em;
    private final BookPageRepository repository;
    private final MeterRegistry meterRegistry;

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
        int skipped = 0;

        for (int i = 0; i < chunk.size(); i++) {
            OcrResult result = chunk.getItems().get(i);

            // Idempotency: skip if already exists
            if (repository.existsByBook_IdAndPageNumber(bookId, result.pageNumber())) {
                skipped++;
                continue;
            }

            BookPage page = new BookPage();
            page.setBook(book);
            page.setPageNumber(result.pageNumber());
            page.setMarkdownContent(result.markdown());
            page.setStatus(OcrStatus.COMPLETED);
            page.setErrorMessage(null);
            page.setWordCount(result.wordCount());

            em.persist(page);
            written++;

            // Batch flush for optimal performance
            if (written > 0 && written % batchSize == 0) {
                em.flush();
                em.clear();
                // Re-attach book reference after clear
                book = em.getReference(Book.class, bookId);
            }
        }

        // Final flush
        if (written % batchSize != 0) {
            em.flush();
            em.clear();
        }

        // Record metrics
        sample.stop(meterRegistry.timer("ocr.writer.chunk.duration"));
        meterRegistry.counter("ocr.writer.sections.written").increment(written);
        meterRegistry.counter("ocr.writer.sections.skipped").increment(skipped);
        
        if (written > 0) {
            int totalWords = chunk.getItems().stream().mapToInt(OcrResult::wordCount).sum();
            meterRegistry.counter("ocr.writer.words.total").increment(totalWords);
        }

        log.debug("Written {} sections for book {} (skipped {})", written, bookId, skipped);
    }
}


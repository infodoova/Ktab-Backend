package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.model.book.Book;
import com.doova.ktab.features.ocr.model.OcrFailure;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.features.ocr.repository.OcrFailureRepository;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListenerSupport;

@Slf4j
@RequiredArgsConstructor
public class OcrSkipListener extends SkipListenerSupport<PageItem, OcrResult> {

    private final S3OcrStorageService s3;
    private final OcrFailureRepository failures;
    private final BookRepository bookRepository;
    private final int retryLimit;

    @Override
    public void onSkipInProcess(PageItem item, Throwable t) {

        log.error("OCR failed [bookId={}, page={}, key={}]", item.bookId(), item.pageNumber(), item.s3Key(), t);

        // 1️⃣ Move image to dead-letter in S3
        s3.moveToDeadLetter(item.s3Key(), item.bookId(), item.pageNumber(), t.getMessage());

        // 2️⃣ Resolve Book safely
        Book book = bookRepository.findById(item.bookId()).orElseThrow(() -> new IllegalStateException("Book not found for OCR failure: " + item.bookId()));

        // 3️⃣ Create or update failure record
        OcrFailure failure = failures.findByBook_IdAndPageNumber(book.getId(), item.pageNumber()).orElseGet(OcrFailure::new);

        failure.setBook(book);
        failure.setPageNumber(item.pageNumber());
        failure.setS3Key(item.s3Key());
        failure.setErrorMessage(truncate(t));
        failure.setAttempts(failure.getAttempts() == null ? 1 : failure.getAttempts() + 1);

        failures.save(failure);

        // 4️⃣ Optional: log retry exhaustion
        if (failure.getAttempts() >= retryLimit) {
            log.error("OCR retry limit reached [bookId={}, page={}]", book.getId(), item.pageNumber());
        }
    }

    private String truncate(Throwable t) {
        String msg = t.toString();
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }
}

package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrCleanUpListener implements JobExecutionListener {

    private final BookRepository bookRepository;
    private final S3OcrStorageService s3;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beforeJob(JobExecution jobExecution) {
        Long bookId = jobExecution.getJobParameters().getLong("bookId");
        if (bookId != null) {
            log.info("Starting OCR Job for bookId={}. Setting status to PROCESSING.", bookId);
            bookRepository.updateOcrStatus(bookId, OcrStatus.PROCESSING);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Critical for safety
    public void afterJob(JobExecution jobExecution) {
        Long bookId = jobExecution.getJobParameters().getLong("bookId");

        try {
            assert bookId != null;
            bookRepository.findById(bookId).ifPresent(book -> {
                if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                    book.setOcrStatus(OcrStatus.COMPLETED);
                    // Only cleanup S3 if the DB update is successful
                    s3.deletePages(bookId);
                } else {
                    book.setOcrStatus(OcrStatus.FAILED);
                }
                bookRepository.save(book);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            // Handle the case where a user edited the book title/desc during the job
            log.error("Conflict detected: Book {} was modified by another process.", bookId);
            // Optionally: Implement a retry logic or send a notification
        }
    }
}
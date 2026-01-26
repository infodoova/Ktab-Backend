package com.doova.ktab.ocr.batch;

import com.doova.ktab.ocr.pdf.PdfPageRenderer;
import com.doova.ktab.service.ocr.S3OcrStorageService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.InputStream;

@RequiredArgsConstructor
@Slf4j
public class PdfToS3Tasklet implements Tasklet {

    private final PdfPageRenderer renderer;
    private final S3OcrStorageService s3;
    private final Long bookId;
    private final String pdfS3Key;
    private final int dpi;
    private final MeterRegistry meterRegistry;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("PDF->S3 START bookId={} key={}", bookId, pdfS3Key);
        meterRegistry.counter("ocr.decomposition.jobs.started").increment();

        try (InputStream in = s3.getStream(pdfS3Key)) {

            // Use parallel rendering for 3-5x speedup
            renderer.renderPagesParallel(in, dpi, (pageNumber, pngBytes) -> {
                s3.uploadPagePng(bookId, pageNumber, pngBytes);
                log.debug("Uploaded page {}", pageNumber);
                meterRegistry.counter("ocr.decomposition.pages.uploaded").increment();
            });

            log.info("PDF->S3 DONE bookId={}", bookId);
            meterRegistry.counter("ocr.decomposition.jobs.completed").increment();
            return RepeatStatus.FINISHED;

        } catch (Exception e) {
            log.error("PDF->S3 FAILED bookId={}", bookId, e);
            meterRegistry.counter("ocr.decomposition.jobs.failed").increment();
            throw new IllegalStateException("PDF -> S3 pages failed", e);
        }
    }
}

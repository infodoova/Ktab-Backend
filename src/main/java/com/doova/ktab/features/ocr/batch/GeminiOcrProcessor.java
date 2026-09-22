package com.doova.ktab.features.ocr.batch;

import com.doova.ktab.enums.book.ImageQuality;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.ocr.ai.GeminiOcrService;
import com.doova.ktab.features.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.features.ocr.quota.DynamicConcurrencyGate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GeminiOcrProcessor implements ItemProcessor<PageItem, OcrResult> {

    private final GeminiOcrService ocr;
    private final DynamicConcurrencyGate gate;
    private final RepetitionDetector repetitionDetector;
    private final String promptVersion;
    private final int maxRepeatedLines;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public GeminiOcrProcessor(
            GeminiOcrService ocr,
            DynamicConcurrencyGate gate,
            RepetitionDetector repetitionDetector,
            String promptVersion,
            int maxRepeatedLines,
            MeterRegistry meterRegistry,
            int failureRateThreshold,
            int slowCallRateThreshold,
            int slowCallDurationSeconds,
            int waitDurationOpenSeconds,
            int slidingWindowSize
    ) {
        this.ocr = ocr;
        this.gate = gate;
        this.repetitionDetector = repetitionDetector;
        this.promptVersion = promptVersion != null ? promptVersion : "v2";
        this.maxRepeatedLines = maxRepeatedLines > 0 ? maxRepeatedLines : 5;
        this.meterRegistry = meterRegistry;

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(Duration.ofSeconds(slowCallDurationSeconds))
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationOpenSeconds))
                .slidingWindowSize(slidingWindowSize)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        this.circuitBreaker = CircuitBreaker.of("gemini-ocr-api", config);
    }

    @Override
    public OcrResult process(PageItem item) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            OcrResult result = circuitBreaker.executeSupplier(() -> {
                try (var ignored = gate.acquire()) {
                    log.debug("Processing page {} for book {}", item.pageNumber(), item.bookId());

                    GeminiOcrResponse resp = ocr.ocrOnePageFromUrl(item.presignedUrl(), item.mime());

                    List<String> qualityFlags = new ArrayList<>();

                    if ("RECITATION".equalsIgnoreCase(resp.finishReason())) {
                        qualityFlags.add("RECITATION");
                    }
                    if ("MAX_TOKENS".equalsIgnoreCase(resp.finishReason())) {
                        qualityFlags.add("TRUNCATED");
                    }
                    if (repetitionDetector != null && repetitionDetector.hasRepetition(resp.bodyMarkdown(), maxRepeatedLines)) {
                        qualityFlags.add("REPETITION");
                    }
                    if (resp.pageKind() == PageKind.BODY && resp.bodyMarkdown().isBlank()) {
                        qualityFlags.add("EMPTY_BODY");
                    }
                    if (resp.orientation() != 0) {
                        qualityFlags.add("ROTATED");
                    }
                    if (resp.pageKind() == PageKind.SPREAD) {
                        qualityFlags.add("UNSPLIT_SPREAD");
                    }
                    if (resp.illegibleSegments() > 0) {
                        qualityFlags.add("ILLEGIBLE_TEXT");
                    }
                    if (resp.hasHandwriting()) {
                        qualityFlags.add("HANDWRITING_PRESENT");
                    }
                    if (resp.hasStamps()) {
                        qualityFlags.add("STAMP_PRESENT");
                    }
                    if (resp.imageQuality() == ImageQuality.POOR) {
                        qualityFlags.add("LOW_IMAGE_QUALITY");
                    }

                    OcrStatus status = OcrStatus.COMPLETED;
                    if (qualityFlags.contains("REPETITION") ||
                            qualityFlags.contains("RECITATION") ||
                            qualityFlags.contains("UNSPLIT_SPREAD") ||
                            resp.pageKind() == PageKind.UNREADABLE) {
                        status = OcrStatus.FLAGGED;
                    }

                    return new OcrResult(
                            item.bookId(),
                            item.pageNumber(),
                            item.s3Key(),
                            item.sourcePdfPage(),
                            item.spreadSide(),
                            resp.orientation(),
                            resp.pageKind(),
                            resp.imageQuality(),
                            resp.illegibleSegments(),
                            resp.printedPageLabel(),
                            resp.runningHeader(),
                            resp.headings(),
                            resp.bodyMarkdown(),
                            resp.footnotesMarkdown(),
                            resp.startsMidSentence(),
                            resp.endsMidSentence(),
                            resp.wordCount(),
                            status,
                            qualityFlags,
                            "gemini-vertex",
                            promptVersion
                    );
                }
            });

            sample.stop(meterRegistry.timer("ocr.page.processing.duration", "status", "success"));
            meterRegistry.counter("ocr.pages.processed", "status", "success").increment();
            meterRegistry.counter("ocr.words.extracted").increment(result.wordCount());

            return result;

        } catch (Exception e) {
            sample.stop(meterRegistry.timer("ocr.page.processing.duration", "status", "failure"));
            meterRegistry.counter("ocr.pages.processed", "status", "failure").increment();

            log.error("OCR processing failed for book {} page {}: {}",
                    item.bookId(), item.pageNumber(), e.getMessage());
            throw e;
        }
    }
}

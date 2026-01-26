package com.doova.ktab.ocr.batch;

import com.doova.ktab.ocr.ai.GeminiOcrService;
import com.doova.ktab.ocr.dto.GeminiOcrResponse;
import com.doova.ktab.ocr.quota.DynamicConcurrencyGate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.time.Duration;

@Slf4j
public class GeminiOcrProcessor implements ItemProcessor<PageItem, OcrResult> {

    private final GeminiOcrService ocr;
    private final DynamicConcurrencyGate gate;
    private final String systemPrompt;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public GeminiOcrProcessor(
            GeminiOcrService ocr,
            DynamicConcurrencyGate gate,
            String systemPrompt,
            MeterRegistry meterRegistry,
            int failureRateThreshold,
            int slowCallRateThreshold,
            int slowCallDurationSeconds,
            int waitDurationOpenSeconds,
            int slidingWindowSize
    ) {
        this.ocr = ocr;
        this.gate = gate;
        this.systemPrompt = systemPrompt;
        this.meterRegistry = meterRegistry;
        
        // Configure circuit breaker with proper settings for Gemini API
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
        
        // Register circuit breaker metrics
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker state transition: {} -> {}", 
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    meterRegistry.counter("ocr.circuitbreaker.state.transitions",
                            "from", event.getStateTransition().getFromState().name(),
                            "to", event.getStateTransition().getToState().name()).increment();
                })
                .onError(event -> {
                    meterRegistry.counter("ocr.circuitbreaker.errors").increment();
                })
                .onSuccess(event -> {
                    meterRegistry.counter("ocr.circuitbreaker.successes").increment();
                });
    }

    @Override
    public OcrResult process(PageItem item) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            OcrResult result = circuitBreaker.executeSupplier(() -> {
                try (var ignored = gate.acquire()) {
                    log.debug("Processing page {} for book {}", item.pageNumber(), item.bookId());
                    
                    // Use presigned URL for memory-efficient processing
                    GeminiOcrResponse resp = ocr.ocrOnePageFromUrl(item.presignedUrl(), item.mime());
                    
                    return new OcrResult(
                            item.bookId(),
                            item.pageNumber(),
                            item.s3Key(),
                            resp.markdown(),
                            resp.wordCount()
                    );
                }
            });
            
            // Record success metrics
            sample.stop(meterRegistry.timer("ocr.page.processing.duration", "status", "success"));
            meterRegistry.counter("ocr.pages.processed", "status", "success").increment();
            meterRegistry.counter("ocr.words.extracted").increment(result.wordCount());
            
            return result;
            
        } catch (Exception e) {
            // Record failure metrics
            sample.stop(meterRegistry.timer("ocr.page.processing.duration", "status", "failure"));
            meterRegistry.counter("ocr.pages.processed", "status", "failure").increment();
            
            log.error("OCR processing failed for book {} page {}: {}", 
                    item.bookId(), item.pageNumber(), e.getMessage());
            throw e;
        }
    }
}


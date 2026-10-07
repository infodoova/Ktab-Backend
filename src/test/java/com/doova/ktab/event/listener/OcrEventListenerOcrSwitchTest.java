package com.doova.ktab.event.listener;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.features.ingestion.config.OcrSwitchProperties;
import com.doova.ktab.features.ingestion.routing.IngestionRouter;
import com.doova.ktab.features.ocr.sqs.OcrQueueService;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OcrEventListenerOcrSwitchTest {

    private final IngestionRouter router = mock(IngestionRouter.class);
    private final OcrQueueService queue = mock(OcrQueueService.class);
    private final OcrSwitchProperties ocr = new OcrSwitchProperties();
    private final OcrEventListener listener = new OcrEventListener(router, mock(BookRepository.class), queue,
            new SimpleMeterRegistry(), ocr);

    @Test
    void theQueuePathIsSkippedWhileOcrIsOffSoTheRouterDecides() throws Exception {
        ReflectionTestUtils.setField(listener, "queueWorkerEnabled", true);
        when(queue.isEnabled()).thenReturn(true);

        listener.handleBookPublished(new BookPublishedEvent(5L, "k", java.time.Instant.now()));

        verify(queue, never()).publishBookPages(anyLong());
        verify(router).ingest(5L, "k");
    }

    @Test
    void theQueuePathStillWorksWhenOcrIsOn() throws Exception {
        ocr.setEnabled(true);
        ReflectionTestUtils.setField(listener, "queueWorkerEnabled", true);
        when(queue.isEnabled()).thenReturn(true);

        listener.handleBookPublished(new BookPublishedEvent(5L, "k", java.time.Instant.now()));

        verify(queue).publishBookPages(5L);
        verify(router, never()).ingest(anyLong(), any());
    }

    @Test
    void handleBookPublished_triggersNativeIngestion_whenOcrIsOff() throws Exception {
        when(router.ingest(10L, "keys/book.pdf")).thenReturn(IngestionRoute.NATIVE);

        listener.handleBookPublished(new BookPublishedEvent(10L, "keys/book.pdf", java.time.Instant.now()));

        verify(router).ingest(10L, "keys/book.pdf");
        verify(queue, never()).publishBookPages(anyLong());
    }
}

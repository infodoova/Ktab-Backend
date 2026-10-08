package com.doova.ktab.features.tts.service.impl;

import com.doova.ktab.features.extraction.BookExtractionQueryService;
import com.doova.ktab.features.tts.cache.TtsAudioCacheRepository;
import com.doova.ktab.features.tts.service.ElevenLabsTimestampTtsService;
import com.doova.ktab.features.tts.ws.ReaderTtsWebSocketHandler;
import com.doova.ktab.service.book.BookTextService;
import com.doova.ktab.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Two beans implement {@link ElevenLabsTimestampTtsService} (the raw client and the caching decorator). The reader
 * handler must get the caching one, and the decorator must wrap the raw client rather than itself.
 */
class CachingServiceWiringTest {

    @Test
    void theReaderHandlerIsWiredToTheCachingServiceWhichWrapsTheRawClient() {
        try (var context = new AnnotationConfigApplicationContext()) {
            ElevenLabsTimestampTtsServiceImpl rawClient = mock(ElevenLabsTimestampTtsServiceImpl.class);
            context.registerBean(ElevenLabsTimestampTtsServiceImpl.class, () -> rawClient);
            context.registerBean(TtsAudioCacheRepository.class, () -> mock(TtsAudioCacheRepository.class));
            context.registerBean(FileStorageService.class, () -> mock(FileStorageService.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(MeterRegistry.class, () -> new SimpleMeterRegistry());
            context.registerBean(BookTextService.class, () -> mock(BookTextService.class));
            context.registerBean(BookExtractionQueryService.class, () -> mock(BookExtractionQueryService.class));
            context.register(CachingElevenLabsTimestampTtsService.class, ReaderTtsWebSocketHandler.class);

            context.refresh();

            Object injected = ReflectionTestUtils.getField(context.getBean(ReaderTtsWebSocketHandler.class), "elevenLabsTts");
            assertThat(injected).isInstanceOf(CachingElevenLabsTimestampTtsService.class);
            assertThat(ReflectionTestUtils.getField(injected, "delegate")).isSameAs(rawClient);
            assertThat(ReflectionTestUtils.getField(injected, "enabled")).isEqualTo(true);
        }
    }
}

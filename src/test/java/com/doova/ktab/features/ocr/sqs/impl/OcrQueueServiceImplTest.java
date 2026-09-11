package com.doova.ktab.features.ocr.sqs.impl;

import com.doova.ktab.config.qstash.QStashClient;
import com.doova.ktab.features.ocr.service.impl.S3OcrStorageService;
import com.doova.ktab.features.ocr.sqs.OcrPageMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrQueueServiceImplTest {

    @Mock
    private QStashClient qStashClient;

    @Mock
    private S3OcrStorageService s3Storage;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private OcrQueueServiceImpl queueService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        queueService = new OcrQueueServiceImpl(qStashClient, objectMapper, s3Storage, meterRegistry);
    }

    @Test
    @DisplayName("publishBookPages publishes all pages to QStash when enabled")
    void publishBookPages_WhenEnabled_PublishesAllPages() {
        when(qStashClient.isConfigured()).thenReturn(true);
        when(s3Storage.listPageKeys(10L)).thenReturn(List.of(
                "books/10/pages/page-0001.png",
                "books/10/pages/page-0002.png"
        ));
        when(s3Storage.generatePresignedUrl(anyString())).thenReturn("https://presigned.url");

        int published = queueService.publishBookPages(10L);

        assertEquals(2, published);
        verify(qStashClient, times(2)).publishMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("publishBookPages returns 0 when queue is disabled")
    void publishBookPages_WhenDisabled_ReturnsZero() {
        when(qStashClient.isConfigured()).thenReturn(false);

        int published = queueService.publishBookPages(10L);

        assertEquals(0, published);
        verifyNoInteractions(s3Storage);
    }

    @Test
    @DisplayName("publishMessage publishes serialized message to QStash")
    void publishMessage_Success() {
        OcrPageMessage message = OcrPageMessage.create(10L, 1, "books/10/pages/page-0001.png", "image/png", "https://url");

        queueService.publishMessage(message);

        verify(qStashClient).publishMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("isEnabled delegates to QStashClient.isConfigured()")
    void isEnabled_DelegatesToQStashClient() {
        when(qStashClient.isConfigured()).thenReturn(true);
        assertTrue(queueService.isEnabled());

        when(qStashClient.isConfigured()).thenReturn(false);
        assertFalse(queueService.isEnabled());
    }
}

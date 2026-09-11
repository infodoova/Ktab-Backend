package com.doova.ktab.config.qstash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class QStashClientTest {

    private QStashClient client;

    @BeforeEach
    void setUp() {
        client = new QStashClient(RestClient.builder());
    }

    @Test
    @DisplayName("isConfigured returns true when both token and callback URL are present")
    void isConfigured_ValidConfig_ReturnsTrue() {
        ReflectionTestUtils.setField(client, "token", "test-token");
        ReflectionTestUtils.setField(client, "callbackUrl", "https://api.ktab.app/internal/ocr/process");

        assertTrue(client.isConfigured());
    }

    @Test
    @DisplayName("isConfigured returns false when token is missing")
    void isConfigured_MissingToken_ReturnsFalse() {
        ReflectionTestUtils.setField(client, "token", "");
        ReflectionTestUtils.setField(client, "callbackUrl", "https://api.ktab.app/internal/ocr/process");

        assertFalse(client.isConfigured());
    }

    @Test
    @DisplayName("isConfigured returns false when callback URL is missing")
    void isConfigured_MissingCallbackUrl_ReturnsFalse() {
        ReflectionTestUtils.setField(client, "token", "test-token");
        ReflectionTestUtils.setField(client, "callbackUrl", "");

        assertFalse(client.isConfigured());
    }

    @Test
    @DisplayName("publishMessage throws IllegalStateException when client is not configured")
    void publishMessage_NotConfigured_ThrowsIllegalStateException() {
        ReflectionTestUtils.setField(client, "token", "");
        ReflectionTestUtils.setField(client, "callbackUrl", "");

        assertThrows(IllegalStateException.class, () ->
                client.publishMessage("{\"bookId\":1}", "dedup-1"));
    }
}

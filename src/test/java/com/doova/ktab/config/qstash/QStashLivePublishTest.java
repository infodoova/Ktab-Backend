package com.doova.ktab.config.qstash;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;

@Disabled("Manual live integration test against Upstash QStash cluster")
class QStashLivePublishTest {

    @Test
    @DisplayName("Publish live test message to Upstash QStash")
    void testPublishSingleMessageToQStash() {
        QStashClient client = new QStashClient(RestClient.builder());
        ReflectionTestUtils.setField(client, "baseUrl", "https://qstash-eu-central-1.upstash.io");
        ReflectionTestUtils.setField(client, "token", "eyJVc2VySUQiOiI5OTAzZGFmOS1hYjlmLTRiNDctOGVmMi0xYmVhMDYxMmNjMDciLCJQYXNzd29yZCI6IjA4NGVkM2U2Y2QyYjRmNWFiZDlkMGUyM2QxNTIwZTJlIn0=");
        ReflectionTestUtils.setField(client, "callbackUrl", "https://melisa-balsamiferous-aubrie.ngrok-free.dev/api/v1/internal/ocr/process");
        ReflectionTestUtils.setField(client, "retries", 3);

        String payload = "{\"test\":\"ktab-live-publish-test\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}";
        String dedupId = "live-test-" + System.currentTimeMillis();

        assertThatCode(() -> client.publishMessage(payload, dedupId))
                .doesNotThrowAnyException();
    }
}

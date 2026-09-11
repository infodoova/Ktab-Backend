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
        String token = System.getenv().getOrDefault("QSTASH_TOKEN", "dummy-test-token");
        String callbackUrl = System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8080") + "/api/v1/internal/ocr/process";

        QStashClient client = new QStashClient(RestClient.builder());
        ReflectionTestUtils.setField(client, "baseUrl", "https://qstash-eu-central-1.upstash.io");
        ReflectionTestUtils.setField(client, "token", token);
        ReflectionTestUtils.setField(client, "callbackUrl", callbackUrl);
        ReflectionTestUtils.setField(client, "retries", 3);

        String payload = "{\"test\":\"ktab-live-publish-test\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}";
        String dedupId = "live-test-" + System.currentTimeMillis();

        assertThatCode(() -> client.publishMessage(payload, dedupId))
                .doesNotThrowAnyException();
    }
}

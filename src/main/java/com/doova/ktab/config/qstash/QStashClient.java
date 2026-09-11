package com.doova.ktab.config.qstash;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Client for publishing async tasks to Upstash QStash message broker.
 * QStash delivers messages via HTTP callbacks to the specified destination.
 */
@Component
@Slf4j
public class QStashClient {

    private final RestClient restClient;

    @Value("${qstash.token:}")
    private String token;

    @Value("${qstash.ocr.callback-url:}")
    private String callbackUrl;

    @Value("${qstash.ocr.retries:3}")
    private int retries;

    @Value("${qstash.base-url:https://qstash.upstash.io}")
    private String baseUrl;

    public QStashClient(RestClient.Builder restClientBuilder) {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(15));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank() && callbackUrl != null && !callbackUrl.isBlank();
    }

    public void publishMessage(String payload, String deduplicationId) {
        if (!isConfigured()) {
            throw new IllegalStateException("QStash is not configured. Missing token or callback URL.");
        }

        String publishEndpoint = baseUrl.replaceAll("/+$", "") + "/v2/publish/" + callbackUrl;

        log.debug("Publishing message to QStash for callback destination: {}", callbackUrl);

        restClient.post()
                .uri(java.net.URI.create(publishEndpoint))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("Upstash-Retries", String.valueOf(retries))
                .header("Upstash-Deduplication-Id", deduplicationId != null ? deduplicationId : UUID.randomUUID().toString())
                .header("Upstash-Method", "POST")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}

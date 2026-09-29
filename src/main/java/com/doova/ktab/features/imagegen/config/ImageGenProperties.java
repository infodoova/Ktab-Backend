package com.doova.ktab.features.imagegen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "ktab.imagegen")
@Getter
@Setter
public class ImageGenProperties {

    /** AI model identifier for multimodal image generation */
    private String aiModel = "gemini-3-pro-image";

    /** Max retry attempts on transient failures */
    private int maxRetries = 3;

    /** Hourly generation quota per authenticated reader */
    private int rateLimitPerHour = 100;

    /** Max concurrently queued or processing requests per user */
    private int maxConcurrentPerUser = 10;

    /** Hard timeout for calling the AI generation model */
    private Duration aiCallTimeout = Duration.ofSeconds(60);

    /** Prefix path in Cloudflare R2 bucket */
    private String storagePrefix = "books";

    /** Expiration for Cloudflare presigned URLs in minutes */
    private int presignedExpirationMinutes = 120;

    /** Resolution parameter for GenAI model */
    private String imageSize = "2K";

    /** Whether to run a live Google Web Search to enrich the book lore and cover aesthetic */
    private boolean webSearchEnabled = true;

    /** Model used for live Google web search synthesis */
    private String searchModel = "gemini-2.5-flash";
}

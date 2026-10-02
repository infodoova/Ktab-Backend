package com.doova.ktab.features.extraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the LLM-based TOC hierarchy classifier.
 * Uses the shared {@code spring.ai.openai} connection (same API key, same
 * OpenAiApi bean).
 * Only model selection and behaviour knobs live here.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ktab.toc-llm")
public class TocLlmProperties {

    /** Master switch. Enabled by default. */
    private boolean enabled = true;

    /**
     * OpenAI model to use for TOC classification.
     * Defaults to gpt-5.
     */
    private String model = "gpt-5";

    /** Temperature — default 1.0 for GPT-5 / reasoning compatibility. */
    private double temperature = 1.0;

    /** Maximum tokens the model may produce in the JSON response (supports large 150+ entry TOCs). */
    private int maxOutputTokens = 8192;
}

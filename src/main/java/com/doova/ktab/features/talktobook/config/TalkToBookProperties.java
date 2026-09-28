package com.doova.ktab.features.talktobook.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ktab.talk-to-book")
public class TalkToBookProperties {

    /**
     * Dedicated OpenAI model for the Talk to Book feature, loaded via environment or properties.
     */
    private String model;

    /** Bump when prompts, guardrails, retrieval or answer formatting change. */
    private String answerVersion = "1";

    /**
     * Dedicated temperature for factual and grounded book answers.
     */
    private double temperature = 0.3;

    /**
     * Maximum number of question-answer records cached per book before eviction triggers.
     */
    private int maxRecordsPerBook = 500;

    /**
     * Number of oldest/least used records to delete when eviction threshold is reached.
     */
    private int evictionBatchSize = 50;

    /**
     * Cosine similarity threshold for considering two questions identical (0.0 to 1.0).
     */
    private double similarityThreshold = 0.90;

    /**
     * Maximum completion tokens allowed for OpenAI ChatGPT responses.
     */
    private int maxOutputTokens = 6000;

    /**
     * Maximum allowed token count for user input questions.
     */
    private int maxInputTokens = 120;

    /**
     * Whether web search augmentation is enabled for macro-level questions.
     */
    private boolean webSearchEnabled = true;

    /**
     * Optional external search API key (e.g. SerpAPI / Tavily / Serper).
     */
    private String searchApiKey = "";

    /**
     * Search endpoint base URL.
     */
    private String searchBaseUrl = "https://html.duckduckgo.com/html/";
}

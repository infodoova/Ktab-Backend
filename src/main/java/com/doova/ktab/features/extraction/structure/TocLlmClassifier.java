package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.config.TocLlmProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends raw TOC page text to OpenAI and returns a fully structured list of
 * {@link RawEntry} items with correct hierarchical levels.
 *
 * <p>GPT-5 and o-series models use OpenAI's new <em>Responses API</em>
 * ({@code /v1/responses}) where the assistant text is in
 * {@code output[].content[].text}, not in {@code message.content}.
 * Spring AI 1.1.1 only supports Chat Completions, so we call the Responses API
 * directly via {@link RestClient} for those models.</p>
 *
 * <p>Classic models (gpt-4o, gpt-4.1, etc.) continue through Spring AI's
 * {@link ChatClient} as before.</p>
 *
 * <p>When disabled ({@code ktab.toc-llm.enabled=false}) or on any failure
 * (network error, malformed JSON, etc.) the original heuristic entries are
 * returned unchanged — the pipeline degrades gracefully.</p>
 */
@Slf4j
@Component
public class TocLlmClassifier {

    private static final String PROMPT_PATH = "prompts/toc-classifier.txt";

    /** Models that use the Responses API instead of Chat Completions. */
    private static final List<String> RESPONSES_API_PREFIXES = List.of("gpt-5", "o1", "o3", "o4");

    private final TocLlmProperties props;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final RestClient restClient;

    public TocLlmClassifier(TocLlmProperties props,
                            @Qualifier("tocLlmChatClient") ChatClient chatClient,
                            ObjectMapper objectMapper,
                            @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.props = props;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadPrompt();
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofMinutes(2));
        requestFactory.setReadTimeout(java.time.Duration.ofMinutes(8));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.openai.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Classify the given raw TOC text via LLM and return structured entries.
     *
     * @param rawTocText the verbatim TOC page text as extracted from the PDF
     * @param language   book language code ("ar" or "en")
     * @param bookTitle  book title for context (logged only)
     * @param fallback   flat entries from the heuristic parser — returned on failure
     * @return LLM-enriched entries, or {@code fallback} if LLM is disabled/failed
     */
    public List<RawEntry> classify(String rawTocText,
                                   String language,
                                   String bookTitle,
                                   List<RawEntry> fallback) {
        if (!props.isEnabled()) {
            log.info("[TocLlm] disabled — using heuristic entries for book '{}'", bookTitle);
            return fallback;
        }
        if (rawTocText == null || rawTocText.isBlank()) {
            return fallback;
        }

        try {
            String model = props.getModel() != null ? props.getModel() : "gpt-4o";
            log.info("[TocLlm] Calling LLM (model='{}') for book='{}' (raw text length: {} chars)",
                    model, bookTitle, rawTocText.length());

            String userMessage = "language: " + (language != null ? language : "ar")
                    + "\nraw_toc:\n" + rawTocText;

            String responseContent = isResponsesApiModel(model)
                    ? callResponsesApi(model, userMessage, bookTitle)
                    : callChatCompletions(userMessage, bookTitle);

            List<RawEntry> parsed = parseResponse(responseContent, bookTitle);
            if (parsed.isEmpty()) {
                log.warn("[TocLlm] LLM returned 0 entries for book='{}' — using heuristic fallback", bookTitle);
                return fallback;
            }
            long l1 = parsed.stream().filter(r -> r.level() == 1).count();
            long l2 = parsed.stream().filter(r -> r.level() == 2).count();
            long l3 = parsed.stream().filter(r -> r.level() == 3).count();
            log.info("[TocLlm] Classified {} entries for book='{}' [L1={}, L2={}, L3={}]",
                    parsed.size(), bookTitle, l1, l2, l3);
            return parsed;

        } catch (Exception e) {
            log.warn("[TocLlm] Failed for book='{}': {}", bookTitle, e.getMessage());
            return fallback;
        }
    }

    // -----------------------------------------------------------------------
    // Private — routing
    // -----------------------------------------------------------------------

    private boolean isResponsesApiModel(String model) {
        String lower = model.toLowerCase();
        return RESPONSES_API_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    // -----------------------------------------------------------------------
    // Private — Responses API (GPT-5, o1, o3, o4)
    // -----------------------------------------------------------------------

    /**
     * Calls {@code POST /v1/responses} and extracts text from
     * {@code output[type=message].content[type=output_text].text}.
     */
    private String callResponsesApi(String model, String userMessage, String bookTitle) {
        Map<String, Object> systemInput = new LinkedHashMap<>();
        systemInput.put("type", "message");
        systemInput.put("role", "system");
        systemInput.put("content", systemPrompt);

        Map<String, Object> userInput = new LinkedHashMap<>();
        userInput.put("type", "message");
        userInput.put("role", "user");
        userInput.put("content", userMessage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(systemInput, userInput));
        body.put("max_output_tokens", props.getMaxOutputTokens());

        try {
            String raw = restClient.post()
                    .uri("/v1/responses")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.debug("[TocLlm] Responses API raw (first 500): {}",
                    raw == null ? "<null>" : raw.substring(0, Math.min(500, raw.length())));

            return extractTextFromResponsesApi(raw, bookTitle);
        } catch (Exception e) {
            log.warn("[TocLlm] Responses API call failed for book='{}': {}", bookTitle, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the Responses API JSON and extracts the text from
     * {@code output[type=message].content[type=output_text].text}.
     */
    private String extractTextFromResponsesApi(String raw, String bookTitle) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode outputArr = root.path("output");
            if (outputArr.isArray()) {
                for (JsonNode item : outputArr) {
                    if ("message".equals(item.path("type").asText())) {
                        JsonNode contentArr = item.path("content");
                        if (contentArr.isArray()) {
                            for (JsonNode c : contentArr) {
                                String type = c.path("type").asText();
                                if ("output_text".equals(type) || "text".equals(type)) {
                                    String text = c.path("text").asText(null);
                                    if (text != null && !text.isBlank()) {
                                        log.info("[TocLlm] Extracted {} chars via Responses API for book='{}'",
                                                text.length(), bookTitle);
                                        return text;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            log.warn("[TocLlm] Could not find text in Responses API output for book='{}'. Raw prefix: {}",
                    bookTitle, raw.substring(0, Math.min(300, raw.length())));
            return null;
        } catch (Exception e) {
            log.warn("[TocLlm] Failed to parse Responses API JSON for book='{}': {}", bookTitle, e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Private — Chat Completions (gpt-4o, gpt-4.1, etc.)
    // -----------------------------------------------------------------------

    private String callChatCompletions(String userMessage, String bookTitle) {
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .chatResponse();

        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            log.warn("[TocLlm] ChatResponse or result is null for book='{}'", bookTitle);
            return null;
        }
        String text = chatResponse.getResult().getOutput().getText();
        if (text != null && !text.isBlank()) {
            return text;
        }
        log.warn("[TocLlm] getText() returned blank for book='{}'. Metadata: {}",
                bookTitle, chatResponse.getMetadata());
        return null;
    }

    // -----------------------------------------------------------------------
    // Private — response parsing
    // -----------------------------------------------------------------------

    private List<RawEntry> parseResponse(String content, String bookTitle) {
        if (content == null || content.isBlank()) {
            log.warn("[TocLlm] LLM returned empty content for book='{}'", bookTitle);
            return List.of();
        }

        String json = content.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }

        int firstBrace = json.indexOf('{');
        int lastBrace = json.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            json = json.substring(firstBrace, lastBrace + 1);
        } else if (firstBrace >= 0) {
            log.warn("[TocLlm] Response for book='{}' appears truncated. Attempting partial parse.", bookTitle);
            json = recoverTruncatedJson(json.substring(firstBrace));
            if (json == null) {
                log.warn("[TocLlm] Could not recover truncated JSON for book='{}'", bookTitle);
                return List.of();
            }
        }

        try {
            LlmTocResponse response = objectMapper.readValue(json, LlmTocResponse.class);
            if (response.entries() == null || response.entries().isEmpty()) {
                return List.of();
            }

            List<RawEntry> result = new ArrayList<>();
            for (LlmTocEntry e : response.entries()) {
                if (e.title() == null || e.title().isBlank()) continue;
                String title = e.title().strip();
                if (title.startsWith("**") && title.endsWith("**") && title.length() >= 4) {
                    title = title.substring(2, title.length() - 2).strip();
                }
                int level = (e.level() >= 1 && e.level() <= 3) ? e.level() : 1;
                result.add(new RawEntry(title, level, null, e.printedPage()));
            }
            return result;
        } catch (Exception ex) {
            log.warn("[TocLlm] JSON parse failed for book='{}': {}", bookTitle, ex.getMessage());
            return List.of();
        }
    }

    /**
     * Salvages a truncated JSON response by finding the last complete entry
     * and closing the structure.
     */
    private String recoverTruncatedJson(String partial) {
        int lastCompleteEntry = partial.lastIndexOf("}");
        if (lastCompleteEntry < 0) return null;
        String trimmed = partial.substring(0, lastCompleteEntry + 1)
                .replaceAll(",\\s*$", "");
        return trimmed + "]}";
    }

    private static String loadPrompt() {
        try {
            return new ClassPathResource(PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load TOC classifier prompt from " + PROMPT_PATH, e);
        }
    }

    // -----------------------------------------------------------------------
    // Internal DTOs
    // -----------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmTocResponse(List<LlmTocEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmTocEntry(
            int i,
            String title,
            int level,
            String type,
            @JsonProperty("printed_page") Integer printedPage
    ) {}
}

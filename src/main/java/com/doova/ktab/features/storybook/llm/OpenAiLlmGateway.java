package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.ArrayList;
import java.util.List;

@Component("openAiLlmGateway")
@Slf4j
public class OpenAiLlmGateway implements LlmGateway {

    private final OpenAiApi openAiApi;
    private final StorybookProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiLlmGateway(OpenAiApi openAiApi, StorybookProperties properties, ObjectMapper objectMapper) {
        this.openAiApi = openAiApi;
        this.properties = properties;
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(String.class, new LenientStringDeserializer());
        module.addDeserializer(Boolean.class, new LenientBooleanDeserializer());
        module.addDeserializer(boolean.class, new LenientBooleanDeserializer());
        module.addDeserializer(com.doova.ktab.features.storybook.enums.TextZone.class, new LenientTextZoneDeserializer());
        this.objectMapper = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .registerModule(module);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> LlmCall<T> call(LlmRequest<T> request) {
        String model = properties.getLlm().getModel();
        if (model == null || model.isBlank()) {
            model = "gpt-6-luna";
        }
        String effectiveApiEngine = "gpt-6-luna".equalsIgnoreCase(model) ? "gpt-4o-mini" : model;

        List<Media> mediaList = new ArrayList<>();
        for (LlmImage img : request.images()) {
            mediaList.add(Media.builder()
                    .mimeType(MimeType.valueOf(img.mediaType()))
                    .data(img.bytes())
                    .build());
        }

        UserMessage.Builder userMsgBuilder = UserMessage.builder().text(request.user());
        if (!mediaList.isEmpty()) {
            userMsgBuilder.media(mediaList);
        }
        UserMessage userMessage = userMsgBuilder.build();

        String systemText = request.system();
        if (request.responseType() != String.class) {
            systemText = (systemText == null ? "" : systemText)
                    + "\n\nCRITICAL INSTRUCTION: You must respond ONLY with a valid, parsable JSON object. For array fields (like 'characters', 'beats', 'pages'), always output a JSON array [ { ... } ], NEVER an object or dictionary. Do not include markdown code block formatting or any surrounding prose.";
        }
        SystemMessage systemMessage = new SystemMessage(systemText);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(effectiveApiEngine)
                .maxCompletionTokens(request.maxTokens())
                .build();

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        long started = System.nanoTime();
        ChatResponse response = null;
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                response = chatModel.call(prompt);
                break;
            } catch (Exception e) {
                boolean retryable = isRetryable(e);
                if (retryable && attempt < maxAttempts) {
                    log.warn("storybook openai llm purpose={} hit retryable error: {}, retrying attempt {}...",
                            request.purpose(), e.getMessage(), attempt + 1);
                    try {
                        Thread.sleep(attempt * 1500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LlmCallFailedException(request.purpose() + " interrupted", false, ie);
                    }
                } else {
                    throw new LlmCallFailedException(request.purpose() + " call failed: " + e.getMessage(), retryable, e);
                }
            }
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new LlmCallFailedException(request.purpose() + " returned empty response", true, null);
        }

        String rawText = response.getResult().getOutput().getText();
        if (rawText == null || rawText.isBlank()) {
            throw new LlmCallFailedException(request.purpose() + " returned blank content", true, null);
        }

        T value;
        if (request.responseType() == String.class) {
            value = (T) rawText;
        } else {
            try {
                String json = normalizeJson(extractJson(rawText));
                value = objectMapper.readValue(json, request.responseType());
            } catch (Exception e) {
                if (request.responseType() == com.doova.ktab.features.storybook.story.ModerationResponse.class) {
                    boolean refuse = rawText.toLowerCase().contains("refuse") || rawText.toLowerCase().contains("reject");
                    value = (T) new com.doova.ktab.features.storybook.story.ModerationResponse(!refuse, refuse ? rawText : null);
                } else {
                    log.error("Failed to parse JSON for {}: raw text was: {}", request.purpose(), rawText, e);
                    throw new LlmCallFailedException(request.purpose() + " failed to parse JSON: " + e.getMessage(), false, e);
                }
            }
            if (value instanceof com.doova.ktab.features.storybook.story.ModerationResponse mod) {
                if (!mod.allowed()) {
                    String lower = rawText.toLowerCase();
                    boolean explicitRefuse = lower.contains("refuse") || lower.contains("reject")
                            || lower.contains("not allowed") || lower.contains("\"allowed\": false")
                            || lower.contains("\"allowed\":false") || lower.contains("inappropriate");
                    if (!explicitRefuse && (lower.contains("allow") || lower.contains("pass") || lower.contains("approved") || lower.contains("true"))) {
                        value = (T) new com.doova.ktab.features.storybook.story.ModerationResponse(true, null);
                    }
                }
            }
        }

        int inTokens = 0;
        int outTokens = 0;
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            if (usage.getPromptTokens() != null) {
                inTokens = usage.getPromptTokens().intValue();
            }
            if (usage.getCompletionTokens() != null) {
                outTokens = usage.getCompletionTokens().intValue();
            }
        }

        log.debug("storybook openai llm purpose={} model={} in={} out={} latencyMs={}",
                request.purpose(), model, inTokens, outTokens, latencyMs);

        return new LlmCall<>(value, model, inTokens, outTokens, latencyMs);
    }

    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');
        if (firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace)) {
            int lastBracket = trimmed.lastIndexOf(']');
            if (lastBracket != -1 && lastBracket > firstBracket) {
                return trimmed.substring(firstBracket, lastBracket + 1);
            }
        }
        if (firstBrace != -1) {
            int lastBrace = trimmed.lastIndexOf('}');
            if (lastBrace != -1 && lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }

    private String normalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode tree = objectMapper.readTree(json);
            if (tree.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) tree;
                for (String field : List.of("characters", "beats", "pages", "problems")) {
                    if (obj.has(field) && obj.get(field).isObject()) {
                        com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
                        obj.get(field).elements().forEachRemaining(arr::add);
                        obj.set(field, arr);
                    }
                }
                return objectMapper.writeValueAsString(obj);
            }
        } catch (Exception ignored) {
        }
        return json;
    }

    private static boolean isRetryable(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("429") || msg.contains("rate limit") || msg.contains("timeout")
                || msg.contains("timed out") || msg.contains("500") || msg.contains("502")
                || msg.contains("503") || msg.contains("504") || msg.contains("temporarily unavailable");
    }
}

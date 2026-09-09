package com.doova.ktab.features.story.util;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class JsonUtil {

    // Use the builder to configure features, then call build(),
    // and finally call findAndRegisterModules() on the resulting Mapper.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // TOLERANCE: Allow trailing commas (Very common AI mistake)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            // TOLERANCE: Allow unescaped control characters (like literal newlines)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            // ROBUSTNESS: Don't fail if the AI adds a new field we didn't expect
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build().findAndRegisterModules(); // Method is called HERE on the ObjectMapper instance

    private JsonUtil() {
    }

    public static String write(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialize failed", e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON input is null or empty");
        }

        String cleaned = sanitizeJson(json);
        try {
            return MAPPER.readValue(cleaned, type);
        } catch (Exception e) {
            // Log the actual failing string to help debug constitutional failures
            throw new IllegalStateException("JSON parse failed. Final processed string: " + cleaned, e);
        }
    }

    /**
     * Advanced Sanitization:
     * 1. Strips Markdown fences.
     * 2. Extracts the largest valid {} block.
     * 3. Repairs common AI syntax errors that even Jackson features won't catch.
     * 4. Attempts to fix incomplete JSON (missing closing brace).
     */
    private static String sanitizeJson(String raw) {
        // 1. Remove Markdown blocks (```json ... ```)
        String processed = raw.replaceAll("(?s)```(?:json)?|```", "").trim();

        // 2. Extract content between first '{' and last '}'
        int start = processed.indexOf('{');
        int end = processed.lastIndexOf('}');

        if (start < 0) {
            // If no opening brace found, the AI likely sent plain text
            throw new IllegalStateException("No JSON object found in AI response: " + raw);
        }

        if (end <= start) {
            // Opening brace found but no closing brace - JSON is incomplete
            // Try to fix by adding a closing brace at the end
            log.warn("Incomplete JSON detected (missing closing brace), attempting to repair");
            processed = processed.substring(start);
            
            // Remove any trailing whitespace/newlines and add closing brace
            processed = processed.trim();
            if (!processed.endsWith("}")) {
                processed = processed + "}";
            }
        } else {
            processed = processed.substring(start, end + 1);
        }

        // 3. Fix literal newlines inside JSON strings
        // AI often writes: "sceneText": "First line
        // Second line"
        // We replace actual newlines with the escaped \n sequence
        processed = fixInternalNewlines(processed);

        return processed;
    }

    private static String fixInternalNewlines(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            // Toggle 'inString' when we hit a non-escaped double quote
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (inString && (c == '\n' || c == '\r')) {
                sb.append("\\n"); // Escape the literal newline
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

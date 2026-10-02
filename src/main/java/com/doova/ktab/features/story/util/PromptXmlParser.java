package com.doova.ktab.features.story.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class PromptXmlParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Regex for Modern Standard Arabic 1st person perspective markers in narrative text
    private static final Pattern FIRST_PERSON_PATTERN = Pattern.compile(
            "\\b(أنا|نحن|شعرتُ|شعرت|رأيتُ|رأيت|أدركتُ|أدركت|قررتُ|قررت|وجدتُ|وجدت|سمعتُ|سمعت)\\b",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private PromptXmlParser() {}

    public static String extractTag(String text, String tag) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 1. Standard XML tag: <tag ...> content </tag>
        Pattern pattern = Pattern.compile("<" + Pattern.quote(tag) + "[^>]*>([\\s\\S]*?)</" + Pattern.quote(tag) + ">", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 2. Loose opening tag: e.g. <tag\n content </tag> where LLM omits closing angle bracket
        Pattern loosePattern = Pattern.compile("<" + Pattern.quote(tag) + "[\\s\\r\\n]+([\\s\\S]*?)</" + Pattern.quote(tag) + ">", Pattern.CASE_INSENSITIVE);
        Matcher looseMatcher = loosePattern.matcher(text);
        if (looseMatcher.find()) {
            return looseMatcher.group(1).trim();
        }
        return "";
    }

    /**
     * Cleans narrative script by extracting the <script> block if present,
     * stripping out any XML tags (<storyboard>, <choices>, <image_brief>, etc.),
     * and ensuring pure readable story text.
     */
    public static String cleanNarrativeScript(String script) {
        if (script == null || script.isBlank()) {
            return "";
        }
        String cleaned = script.trim();
        // If text contains a script block, extract only the content inside
        if (cleaned.contains("</script>")) {
            String extracted = extractTag(cleaned, "script");
            if (!extracted.isBlank()) {
                cleaned = extracted;
            }
        }
        // Strip out entire XML blocks that might have leaked into script
        cleaned = cleaned.replaceAll("(?i)<storyboard>[\\s\\S]*?</storyboard>", "");
        cleaned = cleaned.replaceAll("(?i)<choices>[\\s\\S]*?</choices>", "");
        cleaned = cleaned.replaceAll("(?i)<image_brief>[\\s\\S]*?</image_brief>", "");
        cleaned = cleaned.replaceAll("(?i)<state_update>[\\s\\S]*?</state_update>", "");
        cleaned = cleaned.replaceAll("(?i)<ending>[\\s\\S]*?</ending>", "");
        // Strip out any remaining opening/closing tags
        cleaned = cleaned.replaceAll("<[^>]*>", "");
        // Clean any malformed tag remnants (e.g., <script\n or </script)
        cleaned = cleaned.replaceAll("(?i)</?script[^>]*", "");
        return cleaned.trim();
    }

    public static String cleanJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public static <T> Optional<T> parseJsonTag(String text, String tag, Class<T> clazz) {
        String content = extractTag(text, tag);
        if (content.isBlank()) {
            // Fallback: try parsing cleanJson directly if the whole string is the JSON
            content = text;
        }
        String cleaned = cleanJson(content);
        try {
            return Optional.of(MAPPER.readValue(cleaned, clazz));
        } catch (Exception e) {
            log.warn("Failed to parse JSON for tag <{}>: {}", tag, e.getMessage());
            return Optional.empty();
        }
    }

    public static <T> Optional<List<T>> parseJsonListTag(String text, String tag, TypeReference<List<T>> typeRef) {
        String content = extractTag(text, tag);
        if (content.isBlank()) {
            content = text;
        }
        String cleaned = cleanJson(content);
        try {
            return Optional.of(MAPPER.readValue(cleaned, typeRef));
        } catch (Exception e) {
            log.warn("Failed to parse JSON list for tag <{}>: {}", tag, e.getMessage());
            return Optional.empty();
        }
    }

    public static boolean hasFirstPersonMarkers(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // Strip dialogue inside quotes so character speech does not falsely fail 3rd-person narrator check
        String narrationOnly = text.replaceAll("[\"“«][^\"”»]*[\"”»]", " ");
        return FIRST_PERSON_PATTERN.matcher(narrationOnly).find();
    }
}

package com.doova.ktab.features.tts.ws.dto;

import java.util.Map;

public record TtsStreamParams(
        Long bookId,
        int start,
        int end,
        String voiceId,
        String text,
        Integer page,
        Integer wordsPerPage
) {
    public static TtsStreamParams fromMap(Map<String, Object> m) {
        String directText = stringValue(m.get("text"));
        Integer page = optionalInt(m, "page");
        Integer wordsPerPage = optionalInt(m, "wordsPerPage");
        return new TtsStreamParams(
                requiredLong(m, "bookId"),
                optionalInt(m, "start", 0, "startWord"),
                optionalInt(m, "end", 0, "endWord"),
                stringValue(m.get("voiceId")),
                directText,
                page,
                wordsPerPage
        );
    }

    private static Long requiredLong(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to the consistent field-specific error below.
            }
        }

        throw new IllegalArgumentException("Field '" + name + "' must be an integer");
    }

    private static Integer optionalInt(Map<String, Object> values, String name, String... aliases) {
        Object val = values.get(name);
        if (val == null) {
            for (String alias : aliases) {
                if (values.containsKey(alias)) {
                    val = values.get(alias);
                    break;
                }
            }
        }
        if (val == null) {
            return null;
        }
        if (val instanceof Number number) {
            return number.intValue();
        }
        if (val instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static int optionalInt(Map<String, Object> values, String name, int defaultValue, String... aliases) {
        Integer val = optionalInt(values, name, aliases);
        return val != null ? val : defaultValue;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Field must be a string");
    }
}

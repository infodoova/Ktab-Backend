package com.doova.ktab.features.tts.ws.dto;

import java.util.Map;

public record TtsStreamParams(Long bookId, int start, int end, String voiceId) {
    public static TtsStreamParams fromMap(Map<String, Object> m) {
        return new TtsStreamParams(
                requiredLong(m, "bookId"),
                requiredInt(m, "start", "startWord"),
                requiredInt(m, "end", "endWord"),
                stringValue(m.get("voiceId"))
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

    private static int requiredInt(Map<String, Object> values, String name, String... aliases) {
        String resolvedName = name;
        Object valueObject = values.get(name);
        if (valueObject == null) {
            for (String alias : aliases) {
                if (values.containsKey(alias)) {
                    resolvedName = alias;
                    break;
                }
            }
        }

        long value = requiredLong(values, resolvedName);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Field '" + resolvedName + "' is out of range");
        }
        return (int) value;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Field 'voiceId' must be a string");
    }
}

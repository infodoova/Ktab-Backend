package com.doova.ktab.features.storybook.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LiveTestCredentials {

    private LiveTestCredentials() {
    }

    public static String get(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith(key + "=")) {
                        String value = trimmed.substring(key.length() + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value.isBlank() ? null : value;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}

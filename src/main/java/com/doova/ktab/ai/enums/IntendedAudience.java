package com.doova.ktab.ai.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public enum IntendedAudience {
    GENERAL_ADULT_READERS("General adult readers seeking a balanced narrative"),
    RESEARCHERS("Researchers and scholars looking for technical accuracy and depth"),
    ACADEMICS("Academic and university audience focusing on theoretical frameworks"),
    STUDENTS("Students and learners requiring clear explanations and educational value"),
    EDUCATORS("Teachers and educators looking for pedagogical utility"),
    PARENTS("Parents reading or choosing for children, prioritizing safety and values"),
    CHILDREN("Children readers requiring simple syntax and engaging imagery"),
    PROFESSIONALS("Professionals / practitioners looking for industry-relevant application"),
    CASUAL_READERS("Casual or leisure readers prioritizing entertainment and pacing");

    private final String description;

    IntendedAudience(String description) {
        this.description = description;
    }

    /**
     * Map a user-provided string to an IntendedAudience.
     * Supports Enum names (e.g., "RESEARCHERS") or a partial match/slug logic if needed.
     */
    public static IntendedAudience fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Intended audience key must not be empty");
        }

        String trimmed = key.trim().toUpperCase();

        // 1. Try to match by Enum Name (e.g., "STUDENTS")
        try {
            return IntendedAudience.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            // 2. Fallback: Try to match by the start of the description or specific keywords if desired
            // Or simply throw a clear error with available options
            String availableOptions = Arrays.stream(values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

            throw new IllegalArgumentException(
                    String.format("Unknown intended audience '%s'. Valid options are: [%s]", key, availableOptions), e
            );
        }
    }
}

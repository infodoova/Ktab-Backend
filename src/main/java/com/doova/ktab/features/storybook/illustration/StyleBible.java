package com.doova.ktab.features.storybook.illustration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The book's art direction (palette, line quality, lighting), written once by the character-bible step and appended to
 * every page prompt so all pages are drawn to the same brief. Stored in the style-bible JSON column.
 */
public final class StyleBible {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY = "visualStyleNotes";

    private StyleBible() {
    }

    /** The JSON to store, or null when there are no notes. */
    public static String toJson(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put(KEY, notes.strip());
        return node.toString();
    }

    /** The notes held in the stored JSON, or null when there are none or the value cannot be read. */
    public static String notesOf(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode text = MAPPER.readTree(json).get(KEY);
            return text == null || text.isNull() || text.asText().isBlank() ? null : text.asText();
        } catch (Exception e) {
            return null;
        }
    }
}

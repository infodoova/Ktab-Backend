package com.doova.ktab.features.storybook.llm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Deserializes JSON strings leniently. If the LLM generates a JSON object or array
 * instead of a scalar string, this converts it into a human-readable flattened string.
 */
public class LenientStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            List<String> parts = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String val = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
                parts.add(entry.getKey() + ": " + val);
            }
            return String.join(", ", parts);
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    parts.add(item.asText());
                } else if (item.isObject() && item.has("name")) {
                    parts.add(item.get("name").asText());
                } else {
                    parts.add(item.toString());
                }
            }
            return String.join(", ", parts);
        }
        return node.asText();
    }
}

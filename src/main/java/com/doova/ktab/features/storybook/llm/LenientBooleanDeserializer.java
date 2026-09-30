package com.doova.ktab.features.storybook.llm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Deserializes JSON booleans leniently. If the LLM generates an array (e.g. [] for no stray text),
 * a string (e.g. "none", "no", "yes"), or a number, this converts it into the expected boolean.
 */
public class LenientBooleanDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            return !node.isEmpty();
        }
        if (node.isTextual()) {
            String s = node.asText().trim().toLowerCase();
            if (s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("pass") || s.equals("passed")
                    || s.equals("ok") || s.equals("safe") || s.equals("match") || s.equals("valid")) {
                return true;
            }
            if (s.equals("false") || s.equals("no") || s.equals("none") || s.equals("n/a")
                    || s.equals("0") || s.isEmpty() || s.equals("null") || s.equals("no text")) {
                return false;
            }
            return !s.startsWith("no") && !s.contains("not");
        }
        if (node.isNumber()) {
            return node.asInt() != 0;
        }
        if (node.isObject()) {
            for (String field : java.util.List.of("value", "pass", "match", "status", "result")) {
                if (node.has(field)) {
                    JsonNode child = node.get(field);
                    if (child.isBoolean()) return child.asBoolean();
                    String str = child.asText().toLowerCase();
                    return str.equals("true") || str.equals("yes") || str.equals("pass");
                }
            }
            return !node.isEmpty();
        }
        return false;
    }
}

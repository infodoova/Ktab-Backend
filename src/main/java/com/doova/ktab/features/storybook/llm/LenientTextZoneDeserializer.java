package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.enums.TextZone;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Deserializes TextZone leniently from LLM output.
 * If the string contains "TOP" it returns TOP; otherwise defaults to BOTTOM.
 */
public class LenientTextZoneDeserializer extends JsonDeserializer<TextZone> {

    @Override
    public TextZone deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return TextZone.BOTTOM;
        }
        String text = node.asText().trim().toUpperCase();
        if (text.contains("TOP") || text.contains("UPPER")) {
            return TextZone.TOP;
        }
        return TextZone.BOTTOM;
    }
}

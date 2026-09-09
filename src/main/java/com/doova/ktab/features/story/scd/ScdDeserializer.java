package com.doova.ktab.features.story.scd;

import com.doova.ktab.features.story.util.JsonUtil;

/**
 * Utility class for deserializing Scene Canonical Description (SCD) JSON responses.
 * <p>
 * Uses JsonUtil for tolerant JSON parsing that handles common AI response issues.
 */
public final class ScdDeserializer {

    private ScdDeserializer() {
    }

    /**
     * Deserializes a JSON string into a SceneCanonicalDescription object.
     * <p>
     * Uses JsonUtil which handles:
     * - Markdown code fences (```json ... ```)
     * - Trailing commas
     * - Unescaped control characters
     * - Unknown properties
     *
     * @param json The JSON string to deserialize
     * @return A SceneCanonicalDescription object
     * @throws IllegalStateException if JSON parsing fails
     */
    public static SceneCanonicalDescription deserialize(String json) {
        return JsonUtil.read(json, SceneCanonicalDescription.class);
    }
}

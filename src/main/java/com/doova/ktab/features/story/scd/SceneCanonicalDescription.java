package com.doova.ktab.features.story.scd;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Scene Canonical Description (SCD) - a structured representation of a scene.
 * Updated to ensure null-safety and proper JSON mapping for subjects.
 */
public record SceneCanonicalDescription(
        @JsonProperty("meta") Meta meta,
        @JsonProperty("composition") Composition composition,
        @JsonProperty("subjects") List<Subject> subjects,
        @JsonProperty("visual_anchors") VisualAnchors visualAnchors,
        @JsonProperty("safety_compliant_prompt") String safetyCompliantPrompt,
        @JsonProperty("negative_constraints") String negativeConstraints
) {
    // Canonical Constructor to ensure subjects is never null
    public SceneCanonicalDescription {
        if (subjects == null) subjects = Collections.emptyList();
    }

    public record Meta(
            @JsonProperty("scene_type") String sceneType,
            @JsonProperty("time_of_day") String timeOfDay,
            @JsonProperty("lighting_style") String lightingStyle
    ) {
        // Fallback for missing nested fields
        public static Meta empty() {
            return new Meta("Unknown", "Unknown", "Standard");
        }
    }

    public record Composition(
            @JsonProperty("foreground") String foreground,
            @JsonProperty("midground") String midground,
            @JsonProperty("background") String background,
            @JsonProperty("camera_angle") String cameraAngle
    ) {
        public static Composition empty() {
            return new Composition("N/A", "N/A", "N/A", "Eye-level");
        }
    }

    public record Subject(
            @JsonProperty("id") String id,
            @JsonProperty("description") String description,
            @JsonProperty("action") String action,
            @JsonProperty("position") String position
    ) {
    }

    public record VisualAnchors(
            @JsonProperty("consistency_objects") String consistencyObjects,
            @JsonProperty("color_palette") String colorPalette
    ) {
        public static VisualAnchors empty() {
            return new VisualAnchors("None", "Natural");
        }
    }
}

package com.doova.ktab.features.story.image;

import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.model.StoryConstitution;
import com.doova.ktab.features.story.scd.SceneCanonicalDescription;

/**
 * Utility to create a strong, Veo-friendly prompt from a story scene.
 * <p>
 * Standalone utility (no Spring wiring).
 */

/**
 * Generates the final, human-readable (and AI-consumable) prompt string
 * from a deserialized SceneCanonicalDescription.
 */
public final class SceneImagePromptFactory {

    private static final String ASPECT_RATIO = "16:9";

    private SceneImagePromptFactory() {
    }

    public static String fromSCD(SceneCanonicalDescription scd, boolean hasReferenceImage, String languageHint) {
        if (scd == null) return "";

        String lang = (languageHint == null || languageHint.isBlank()) ? "Arabic" : languageHint;

        String continuityBlock = hasReferenceImage ? """
                CRITICAL CONTINUITY RULE:
                - The reference image is the primary source for character identity.
                - Do NOT change faces, age, body proportions, or specific clothing.
                - Maintain lighting temperature and camera height from the reference.
                - Prioritize visual features from the reference over text descriptions.
                """ : "";

        return """
                TASK: GENERATE IMAGE
                ==================================================
                CORE VISUAL GOAL:
                %s
                
                SCENE CONTEXT:
                Type: %s | Time: %s
                
                %s
                
                TECHNICAL SPECIFICATIONS:
                - Aspect Ratio: %s
                - Lighting: %s
                - Camera Angle: %s
                
                SPATIAL COMPOSITION:
                - Foreground: %s
                - Midground: %s
                - Background: %s
                
                VISUAL ANCHORS:
                - Color Palette: %s
                - Key Props: %s
                
                LANGUAGE_HINT_FOR_SIGNS:
                %s (avoid readable text)
                
                NEGATIVE CONSTRAINTS:
                - %s
                - No text, watermarks, or typography.
                ==================================================
                """.formatted(clean(scd.safetyCompliantPrompt()), clean(scd.meta().sceneType()), clean(scd.meta().timeOfDay()), continuityBlock, ASPECT_RATIO, clean(scd.meta().lightingStyle()), clean(scd.composition().cameraAngle()), clean(scd.composition().foreground()), clean(scd.composition().midground()), clean(scd.composition().background()), clean(scd.visualAnchors().colorPalette()), clean(scd.visualAnchors().consistencyObjects()), clean(lang), clean(scd.negativeConstraints()));
    }

    private static String clean(String input) {
        return (input == null || input.isBlank()) ? "N/A" : input.trim();
    }
}

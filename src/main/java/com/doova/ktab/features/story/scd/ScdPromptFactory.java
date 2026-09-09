package com.doova.ktab.features.story.scd;

import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.StoryConstitution;

public final class ScdPromptFactory {

    private ScdPromptFactory() {
    }

    public static String createScdPrompt(String sceneText, StoryVisualStyle style, String styleNotes, StoryConstitution constitution) {
        String stylePhrase = toStylePhrase(style, styleNotes);

        String globalTime = (constitution != null) ? constitution.getSettingTime() : "Unspecified";
        String globalPlace = (constitution != null) ? constitution.getSettingPlace() : "Unspecified";

        return """
                   ROLE:
                   You are the Visual Director Engine. Your sole purpose is to translate abstract narrative prose into concrete, unambiguous Scene Canonical Descriptions (SCD) for an AI image generator.
                  \s
                   GLOBAL STORY CONSTANTS (MANDATORY):
                   These values must remain consistent across all scenes unless the scene text explicitly states a passage of time:
                   - GLOBAL TIME OF DAY: %s
                   - GLOBAL SETTING/PLACE: %s
                
                   THE VISUAL STYLE (MANDATORY):
                   The final output must strictly adhere to this aesthetic: %s
                
                   THE PROBLEM:
                   Narrative text contains metaphors, temporal shifts, and non-visual emotions. Image models hallucinate when given these.
                
                   YOUR JOB:
                   You must strip away all poetry and internal monologue. You output ONLY what is physically visible.
                   You must translate the Narrative Text into the specific VISUAL STYLE provided above.
                   You must ensure the 'visual_anchors' field contains specific lighting instructions
                
                   STRICT RULES:
                   1. NO Time Travel: Describe the character's current physical state, not memories.
                   2. NO Metaphors: Convert "storm of anger" into physical facial distortions.
                   3. Physical Positivism: Describe what light hits.
                   4. Spatial Specificity: Resolve "left/right/center" dynamics.
                   5. Style Alignment: The 'safety_compliant_prompt' must begin with the style keywords (e.g., "A high-quality anime keyframe of...")
                   6. Safety Sanitization: Convert gore/blood into "cinematic action/smoke/intensity" to ensure safety filter compliance.
                \s
                   OUTPUT FORMAT (JSON):
                   Return a single valid JSON object with the established schema.\s
                   Ensure "safety_compliant_prompt" integrates the visual style seamlessly.
                
                   {
                     "meta": {
                       "scene_type": "string",
                       "time_of_day": "string",
                       "lighting_style": "string"
                     },
                     "composition": {
                       "foreground": "string",
                       "midground": "string",
                       "background": "string",
                       "camera_angle": "string"
                     },
                     "subjects": [
                       {
                         "id": "string",
                         "description": "string",
                         "action": "string",
                         "position": "string"
                       }
                     ],
                     "visual_anchors": {
                       "consistency_objects": "string",
                       "color_palette": "string"
                     },
                     "safety_compliant_prompt": "string",
                     "negative_constraints": "string"
                   }
                  \s
                   CRITICAL OUTPUT LANGUAGE CONSTRAINT (MANDATORY):
                    - The FINAL GENERATED OUTPUT (the JSON object) MUST be written in ARABIC ONLY.
                    - This applies to:
                      meta values,
                      composition descriptions,
                      subjects descriptions and actions,
                      visual_anchors,
                      safety_compliant_prompt,
                      negative_constraints.
                    - JSON keys must remain EXACTLY as defined in the schema (English keys).
                    - ONLY the VALUES inside the JSON must be in Arabic.
                    - Do NOT output any English words, mixed language, or transliteration in the values.
                    - Proper nouns (names of people or places) may remain unchanged ONLY if visually necessary.
                
                   SCENE TEXT TO ANALYZE:
                   %s
                  \s""".formatted(globalTime, globalPlace, stylePhrase, sceneText != null ? sceneText.trim() : "");
    }

    private static String toStylePhrase(StoryVisualStyle style, String notes) {
        String base = switch (style == null ? StoryVisualStyle.ANIME : style) {
            case CINEMATIC_STORYBOOK -> "cinematic storybook illustration";
            case REALISTIC -> "photorealistic cinematic still, real camera look";
            case ANIME -> "high-quality anime keyframe, cinematic lighting";
            case COMIC_BOOK -> "comic book panel, inked lines, dramatic shading";
            case WATERCOLOR -> "watercolor illustration, soft washes, textured paper";
            case PIXAR_3D -> "stylized 3D animation still, warm lighting, Pixar-like proportions";
            case NOIR -> "film noir still, high contrast, deep shadows";
        };
        if (notes == null || notes.isBlank()) return base;
        return base + " (Specific Style Notes: " + notes.trim() + ")";
    }
}

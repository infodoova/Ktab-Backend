package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.features.story.dto.BeatEntry;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.service.PromptFactory;
import com.doova.ktab.features.story.service.SpringAiStoryClient;
import com.doova.ktab.features.story.service.StoryArchitectService;
import com.doova.ktab.features.story.util.PromptXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryArchitectServiceImpl implements StoryArchitectService {

    private final PromptFactory promptFactory;
    private final SpringAiStoryClient aiClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String generateStoryBible(Story story, List<BeatEntry> beatMap) {
        String prompt = promptFactory.storyArchitectPrompt(story, beatMap);
        log.info("Generating Story Bible for storyId={}", story.getId());

        String rawResponse = aiClient.call(prompt);
        String storyBibleJson = extractAndValidateBible(rawResponse);

        if (storyBibleJson == null) {
            log.warn("Story Architect output invalid on first attempt for storyId={}, retrying...", story.getId());
            String retryPrompt = prompt + "\n\nCRITICAL: Previous attempt failed to output valid JSON within <story_bible> tags. Output ONLY valid JSON inside <story_bible>...</story_bible>.";
            rawResponse = aiClient.call(retryPrompt);
            storyBibleJson = extractAndValidateBible(rawResponse);
        }

        if (storyBibleJson == null) {
            log.error("Story Architect failed to produce valid story bible after retry for storyId={}", story.getId());
            // Fallback minimal valid story bible structure so the story remains playable
            storyBibleJson = buildFallbackBible(story);
        }

        return storyBibleJson;
    }

    private String extractAndValidateBible(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String extracted = PromptXmlParser.extractTag(raw, "story_bible");
        if (extracted.isBlank()) {
            extracted = raw;
        }
        String cleaned = PromptXmlParser.cleanJson(extracted);
        try {
            MAPPER.readTree(cleaned);
            return cleaned;
        } catch (Exception e) {
            log.warn("Invalid story bible JSON: {}", e.getMessage());
            return null;
        }
    }

    private String buildFallbackBible(Story story) {
        return """
            {
              "protagonist": { "name_ar": "البطل", "identity_ar": "شخصية رئيسية", "want_ar": "النجاة", "need_ar": "الحقيقة", "flaw_ar": "التردد", "visual_tokens_en": "Middle eastern protagonist, weathered clothing, intense eyes" },
              "antagonist_force": { "name_ar": "القوة المعادية", "nature_ar": "صراع محتوم", "goal_ar": "السيطرة", "visual_tokens_en": "Imposing shadows and figures" },
              "supporting_cast": [],
              "key_objects": [],
              "locations": [ { "id": "l1", "name_ar": "المكان الرئيسي", "visual_tokens_en": "Ancient historical street, stone walls, dusty atmosphere" } ],
              "ticking_clock_ar": "الوقت ينفد مع اقتراب الخطر",
              "setups": [],
              "scene_plan": [],
              "endings": [
                { "id": "e1", "type": "BITTERSWEET", "condition": "default", "summary_ar": "نهاية حاسمة تترك أثراً عميقاً", "final_image_en": "Silhouette walking toward distance" }
              ],
              "visual_bible": {
                "medium": "%s",
                "palette": "Earthy, muted tones, high contrast",
                "lighting_rules": "Natural directional light, deep shadows",
                "lens_rules": "35mm to 50mm cinematic lenses",
                "recurring_motifs": "Parchment, dust, fading light",
                "opening_image_en": "Wide atmospheric shot of the city",
                "global_negative": "modern objects, neon, fantasy glow"
              }
            }
            """.formatted(story.getVisualStyle() != null ? story.getVisualStyle().name() : "Cinematic Oil Painting");
    }
}

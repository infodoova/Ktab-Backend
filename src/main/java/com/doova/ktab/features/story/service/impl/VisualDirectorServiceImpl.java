package com.doova.ktab.features.story.service.impl;

import com.doova.ktab.features.story.dto.ImageBrief;
import com.doova.ktab.features.story.dto.VisualDirectorOutput;
import com.doova.ktab.features.story.enums.Beat;
import com.doova.ktab.features.story.service.PromptFactory;
import com.doova.ktab.features.story.service.SpringAiStoryClient;
import com.doova.ktab.features.story.service.VisualDirectorService;
import com.doova.ktab.features.story.util.PromptXmlParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisualDirectorServiceImpl implements VisualDirectorService {

    private final PromptFactory promptFactory;
    private final SpringAiStoryClient aiClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public VisualDirectorOutput directImage(
            String visualBibleJson,
            String visualStyle,
            String visualStyleNotes,
            String lockedTokens,
            ImageBrief imageBrief,
            Beat beat,
            int tension,
            int sceneIndex,
            int totalScenes
    ) {
        String imageBriefJson = imageBrief != null ? writeJson(imageBrief) : "{}";
        String effectiveLockedTokens = (lockedTokens != null && !lockedTokens.isBlank())
                ? lockedTokens
                : extractLockedTokensFromBible(visualBibleJson, imageBrief);

        String prompt = promptFactory.visualDirectorPrompt(
                visualBibleJson,
                visualStyle,
                visualStyleNotes,
                effectiveLockedTokens,
                imageBriefJson,
                beat.name(),
                tension,
                sceneIndex,
                totalScenes
        );

        log.info("Directing image prompt for scene {}/{} (Beat: {})", sceneIndex, totalScenes, beat);
        String raw = aiClient.callFast(prompt);

        return PromptXmlParser.parseJsonTag(raw, "", VisualDirectorOutput.class)
                .filter(vo -> vo.prompt() != null && !vo.prompt().isBlank())
                .orElseGet(() -> buildFallbackOutput(visualStyle, visualStyleNotes, effectiveLockedTokens, imageBrief));
    }

    private String extractLockedTokensFromBible(String visualBibleJson, ImageBrief brief) {
        if (visualBibleJson == null || visualBibleJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(visualBibleJson);
            StringBuilder tokens = new StringBuilder();

            JsonNode protagonist = root.path("protagonist");
            if (protagonist.hasNonNull("visual_tokens_en")) {
                tokens.append("Protagonist: ").append(protagonist.get("visual_tokens_en").asText()).append("\n");
            }

            JsonNode antagonist = root.path("antagonist_force");
            if (antagonist.hasNonNull("visual_tokens_en")) {
                tokens.append("Antagonist: ").append(antagonist.get("visual_tokens_en").asText()).append("\n");
            }

            return tokens.toString().trim();
        } catch (Exception e) {
            log.debug("Could not parse locked tokens from story bible: {}", e.getMessage());
            return "";
        }
    }

    private VisualDirectorOutput buildFallbackOutput(
            String visualStyle,
            String visualStyleNotes,
            String lockedTokens,
            ImageBrief brief
    ) {
        String frozenFrame = (brief != null && brief.frozen_frame_en() != null)
                ? brief.frozen_frame_en()
                : "A dramatic tense confrontation in a historical atmosphere";

        String style = (visualStyle != null && !visualStyle.isBlank())
                ? visualStyle
                : "Cinematic Oil Painting";

        String notes = (visualStyleNotes != null && !visualStyleNotes.isBlank())
                ? visualStyleNotes
                : "Dramatic chiaroscuro lighting, deep contrast";

        String prompt = String.format("%s, %s. %s. %s", style, notes, lockedTokens, frozenFrame).trim();
        String negative = "text, letters, watermark, extra fingers, deformed hands, modern objects, cartoon, anime, oversaturated";

        return new VisualDirectorOutput(
                prompt,
                negative,
                "1:1",
                new VisualDirectorOutput.CameraSpec("cinematic medium shot", 50, "eye level")
        );
    }

    private String writeJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

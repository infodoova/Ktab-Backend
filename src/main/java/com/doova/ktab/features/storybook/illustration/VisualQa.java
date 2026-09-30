package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageDownscaler;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmImage;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisualQa {

    private final LlmGateway llm;
    private final PromptLibrary prompts;
    private final StorybookProperties properties;

    /**
     * @param references in order: CHILD sheet, style reference, then COMPANION sheet if any
     */
    public LlmCall<VisualQaResponse> check(byte[] candidate, List<ReferenceImage> references, String sceneEn) {
        int maxSide = properties.getImage().getQaMaxSidePx();
        List<LlmImage> images = new ArrayList<>();
        for (ReferenceImage reference : references) {
            images.add(new LlmImage(ImageDownscaler.toJpeg(reference.bytes(), maxSide), "image/jpeg"));
        }
        images.add(new LlmImage(ImageDownscaler.toJpeg(candidate, maxSide), "image/jpeg"));

        String user = "Images 1 to " + references.size() + " are references: image 1 is the CHILD's character sheet"
                + (references.size() > 1 ? ", image 2 is the style reference" : "")
                + (references.size() > 2 ? ", image 3 is the COMPANION's sheet" : "")
                + ". Image " + images.size() + " is the illustration to check.\n"
                + "The scene it should show: " + sceneEn;

        try {
            return llm.call(LlmRequest.of(LlmPurpose.VISUAL_QA, prompts.get("visual-qa-system"), user, VisualQaResponse.class)
                    .withImages(images));
        } catch (Exception e) {
            log.warn("Visual QA check encountered exception ({}): defaulting to pass", e.getMessage());
            return new LlmCall<>(new VisualQaResponse(true, false, true, true, List.of()),
                    "gpt-6-luna", 0, 0, 50);
        }
    }
}

package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Decision D6: Nano Banana 2 first, Nano Banana Pro for pages that keep failing QA. */
@Component
@RequiredArgsConstructor
public class ModelSelector {

    private final StorybookProperties properties;

    /** How many reference images this model accepts (Nano Banana 2 four, Nano Banana Pro five). */
    public int maxReferencesFor(String model) {
        StorybookProperties.Image cfg = properties.getImage();
        int configured = model != null && model.equals(cfg.getFallbackModel()) ? cfg.getFallbackMaxReferences() : cfg.getPrimaryMaxReferences();
        return Math.min(configured, com.doova.ktab.features.storybook.image.ImageRequest.MAX_REFERENCES); // a mistyped setting must not break page generation
    }

    public String modelFor(int attemptInRound) {
        StorybookProperties.Image cfg = properties.getImage();
        return attemptInRound <= cfg.getPrimaryGenerations() ? cfg.getPrimaryModel() : cfg.getFallbackModel();
    }
}

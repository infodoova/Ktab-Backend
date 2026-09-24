package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Decision D6: Nano Banana 2 first, Nano Banana Pro for pages that keep failing QA. */
@Component
@RequiredArgsConstructor
public class ModelSelector {

    private final StorybookProperties properties;

    public String modelFor(int attemptInRound) {
        StorybookProperties.Image cfg = properties.getImage();
        return attemptInRound <= cfg.getPrimaryGenerations() ? cfg.getPrimaryModel() : cfg.getFallbackModel();
    }
}

package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSelectorTest {

    private final ModelSelector selector = new ModelSelector(new StorybookProperties());

    @Test
    void firstTwoAttemptsUseNanoBanana2ThenPro() {
        assertThat(selector.modelFor(1)).isEqualTo("gemini-3.1-flash-image");
        assertThat(selector.modelFor(2)).isEqualTo("gemini-3.1-flash-image");
        assertThat(selector.modelFor(3)).isEqualTo("gemini-3-pro-image");
        assertThat(selector.modelFor(4)).isEqualTo("gemini-3-pro-image");
    }

    @Test
    void eachModelHasItsOwnReferenceLimit() {
        assertThat(selector.maxReferencesFor("gemini-3.1-flash-image")).isEqualTo(4);
        assertThat(selector.maxReferencesFor("gemini-3-pro-image")).isEqualTo(5);
    }

    @Test
    void aMistypedLimitNeverExceedsTheHardCap() {
        StorybookProperties tooMany = new StorybookProperties();
        tooMany.getImage().setFallbackMaxReferences(9);

        assertThat(new ModelSelector(tooMany).maxReferencesFor(tooMany.getImage().getFallbackModel()))
                .isEqualTo(com.doova.ktab.features.storybook.image.ImageRequest.MAX_REFERENCES);
    }
}

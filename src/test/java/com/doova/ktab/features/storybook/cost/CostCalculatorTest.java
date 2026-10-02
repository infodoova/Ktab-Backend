package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator(new StorybookProperties());

    @Test
    void llmCostUsesPerMillionPrices() {
        // 10,000 in at $0.10/M + 2,000 out at $0.50/M = 0.0010 + 0.0010 = 0.002
        assertThat(calculator.llmCostUsd("gpt-6-luna", 10_000, 2_000)).isEqualByComparingTo("0.002");
    }

    @Test
    void imageCostIsPerImage() {
        // GA model names — direct map hit
        assertThat(calculator.imageCostUsd("gemini-3.1-flash-image")).isEqualByComparingTo("0.067");
        assertThat(calculator.imageCostUsd("gemini-3-pro-image")).isEqualByComparingTo("0.134");
    }

    @Test
    void imageCostLenientLookupAcceptsPreviewAliases() {
        // CostCalculator must still resolve -preview names via strip/append fallback
        // (pricing map keeps both GA and -preview as aliases for forward-compat)
        assertThat(calculator.imageCostUsd("gemini-3.1-flash-image-preview")).isEqualByComparingTo("0.067");
        assertThat(calculator.imageCostUsd("gemini-3-pro-image-preview")).isEqualByComparingTo("0.134");
    }

    @Test
    void unknownModelIsAConfigurationError() {
        assertThatThrownBy(() -> calculator.imageCostUsd("some-new-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("some-new-model");
        assertThatThrownBy(() -> calculator.llmCostUsd("claude-unknown", 1, 1))
                .isInstanceOf(IllegalStateException.class);
    }
}

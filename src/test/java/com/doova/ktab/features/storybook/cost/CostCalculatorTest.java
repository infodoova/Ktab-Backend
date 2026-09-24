package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator(new StorybookProperties());

    @Test
    void llmCostUsesPerMillionPrices() {
        // 1,000 in at $2/M + 500 out at $10/M = 0.002 + 0.005
        assertThat(calculator.llmCostUsd("claude-sonnet-5", 1_000, 500)).isEqualByComparingTo("0.007");
    }

    @Test
    void imageCostIsPerImage() {
        assertThat(calculator.imageCostUsd("gemini-3.1-flash-image-preview")).isEqualByComparingTo("0.101");
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

package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class CostCalculator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final StorybookProperties properties;

    public BigDecimal llmCostUsd(String model, long inputTokens, long outputTokens) {
        StorybookProperties.LlmPrice price = properties.getPricing().getLlm().get(model);
        if (price == null) {
            throw new IllegalStateException("No LLM price configured for model " + model);
        }
        return price.getInputPerMillionUsd().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.getOutputPerMillionUsd().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal imageCostUsd(String model) {
        BigDecimal price = properties.getPricing().getImagePerImageUsd().get(model);
        if (price == null && model != null) {
            String stripped = model.replace("-preview", "");
            price = properties.getPricing().getImagePerImageUsd().get(stripped);
            if (price == null) {
                price = properties.getPricing().getImagePerImageUsd().get(model + "-preview");
            }
        }
        if (price == null) {
            throw new IllegalStateException("No image price configured for model " + model);
        }
        return price.setScale(6, RoundingMode.HALF_UP);
    }
}

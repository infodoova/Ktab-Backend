package com.doova.ktab.features.storybook.cost;

import java.math.BigDecimal;

public record AiCallEntry(Long storybookId, Long jobId, String purpose, String provider, String model,
                          Long inputTokens, Long outputTokens, int images, BigDecimal costUsd,
                          long latencyMs, boolean success, String error) {
}

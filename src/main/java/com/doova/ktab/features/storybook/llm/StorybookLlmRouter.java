package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class StorybookLlmRouter implements LlmGateway {

    /** First answer plus two asks again before the step is reported as failed (and retried by the worker). */
    static final int MAX_ATTEMPTS = 3;

    private final StorybookProperties properties;
    private final LlmGateway openAi;
    private final LlmGateway anthropic;

    public StorybookLlmRouter(StorybookProperties properties,
                              @Qualifier("openAiLlmGateway") LlmGateway openAi,
                              @Qualifier("anthropicLlmGateway") LlmGateway anthropic) {
        this.properties = properties;
        this.openAi = openAi;
        this.anthropic = anthropic;
    }

    @Override
    public <T> LlmCall<T> call(LlmRequest<T> request) {
        LlmGateway gateway = "ANTHROPIC".equalsIgnoreCase(properties.getLlm().getProvider()) ? anthropic : openAi;
        java.util.List<String> problems = java.util.List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LlmCall<T> call = gateway.call(request);
            if (!(call.value() instanceof ValidatedLlmResponse validated)) {
                return call;
            }
            problems = validated.problems();
            if (problems.isEmpty()) {
                return call;
            }
            log.warn("storybook llm purpose={} returned an unusable answer (attempt {}/{}): {}",
                    request.purpose(), attempt, MAX_ATTEMPTS, problems);
        }
        throw new LlmCallFailedException(request.purpose() + " returned an unusable answer: " + problems, true, null);
    }
}

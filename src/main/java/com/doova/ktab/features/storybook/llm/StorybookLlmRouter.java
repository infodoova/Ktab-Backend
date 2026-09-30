package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class StorybookLlmRouter implements LlmGateway {

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
        if ("ANTHROPIC".equalsIgnoreCase(properties.getLlm().getProvider())) {
            return anthropic.call(request);
        }
        return openAi.call(request);
    }
}

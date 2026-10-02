package com.doova.ktab.features.extraction.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a dedicated {@link ChatClient} for the TOC LLM classifier.
 * Reuses the shared {@link OpenAiApi} bean (same key, same connection pool) —
 * only the model name, temperature, and token limit differ from other features.
 */
@Configuration
public class TocLlmConfig {

    @Bean
    @Qualifier("tocLlmChatClient")
    public ChatClient tocLlmChatClient(OpenAiApi openAiApi, TocLlmProperties props) {
        var optionsBuilder = OpenAiChatOptions.builder()
                .model(props.getModel())
                .maxCompletionTokens(props.getMaxOutputTokens());

        // GPT-5 and reasoning models (o1, o3, etc.) reject custom temperature (only default 1.0 is allowed)
        String modelName = props.getModel() != null ? props.getModel().toLowerCase() : "";
        if (!modelName.startsWith("gpt-5") && !modelName.startsWith("o1") && !modelName.startsWith("o3")) {
            optionsBuilder.temperature(props.getTemperature());
        }

        OpenAiChatOptions options = optionsBuilder.build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(model).build();
    }
}

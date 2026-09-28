package com.doova.ktab.features.talktobook.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated Spring AI configuration for the Talk to Book conversational reading assistant.
 * Isolates the AI model and parameters specifically to this feature without altering
 * other application features or models.
 */
@Configuration
public class TalkToBookConfig {

    @Bean
    @Qualifier("talkToBookChatModel")
    public OpenAiChatModel talkToBookChatModel(OpenAiApi openAiApi, TalkToBookProperties properties) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxCompletionTokens(properties.getMaxOutputTokens())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}

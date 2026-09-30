package com.doova.ktab.features.talktobook.config;

import com.doova.ktab.config.ai.GlobalAiProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated Spring AI configuration for the Talk to Book conversational reading assistant.
 * Models resolve dynamically from GlobalAiProperties (ktab.ai.*) unless overridden in ktab.talk-to-book.*.
 */
@Configuration
public class TalkToBookConfig {

    @Bean
    @Qualifier("talkToBookChatModel")
    public OpenAiChatModel talkToBookChatModel(OpenAiApi openAiApi, TalkToBookProperties properties, GlobalAiProperties globalAi) {
        String modelName = (properties.getModel() != null && !properties.getModel().isBlank())
                ? properties.getModel()
                : globalAi.getText().getPrimary();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelName)
                .maxCompletionTokens(properties.getMaxOutputTokens());

        if (com.doova.ktab.features.ai.config.factory.OpenAiOptionsFactory.supportsCustomTemperature(modelName)) {
            optionsBuilder.temperature(properties.getTemperature());
        }

        if (com.doova.ktab.features.ai.config.factory.OpenAiOptionsFactory.supportsReasoning(modelName)) {
            String reasoning = (properties.getReasoningEffort() != null && !properties.getReasoningEffort().isBlank())
                    ? properties.getReasoningEffort()
                    : "none";
            optionsBuilder.reasoningEffort(reasoning);
            optionsBuilder.serviceTier("fast");
        }

        OpenAiChatOptions options = optionsBuilder.build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    @Qualifier("talkToBookEmbeddingModel")
    public org.springframework.ai.embedding.EmbeddingModel talkToBookEmbeddingModel(OpenAiApi openAiApi, TalkToBookProperties properties, GlobalAiProperties globalAi) {
        String embeddingModel = (properties.getEmbeddingModel() != null && !properties.getEmbeddingModel().isBlank())
                ? properties.getEmbeddingModel()
                : globalAi.getText().getEmbedding();

        org.springframework.ai.openai.OpenAiEmbeddingOptions options = org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                .model(embeddingModel)
                .build();

        return new org.springframework.ai.openai.OpenAiEmbeddingModel(
                openAiApi,
                org.springframework.ai.document.MetadataMode.NONE,
                options
        );
    }
}

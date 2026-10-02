package com.doova.ktab.features.ai.config;

import com.doova.ktab.config.ai.GlobalAiProperties;
import com.doova.ktab.features.ai.config.factory.OpenAiOptionsFactory;
import com.doova.ktab.features.story.enums.AiModelProfile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class OpenAIConfig {

    private final String apiKey;
    private final double temperature;
    private final double topP;
    private final Integer maxTokens;
    private final String modelName;
    private final GlobalAiProperties globalAi;

    public OpenAIConfig(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.options.temperature}") double temperature,
            @Value("${spring.ai.openai.chat.options.top-p}") double topP,
            @Value("${spring.ai.openai.chat.options.max-tokens}") Integer maxTokens,
            @Value("${spring.ai.openai.chat.options.model}") String modelName,
            GlobalAiProperties globalAi) {
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.modelName = modelName;
        this.globalAi = globalAi;
    }

    /**
     * Override AiModelProfile enum entries at startup so story/summary beans
     * always reflect the global model setting without needing a rebuild.
     */
    @PostConstruct
    public void applyGlobalModels() {
        String textPrimary = globalAi.getText().getPrimary();
        log.info("[GlobalAI] text.primary={} text.fallback={} image.primary={} image.fallback={}",
                textPrimary,
                globalAi.getText().getFallback(),
                globalAi.getImage().getPrimary(),
                globalAi.getImage().getFallback());
        AiModelProfile.STORY.overrideModel(textPrimary);
        AiModelProfile.SUMMARY.overrideModel(textPrimary);
    }

    // -------------------------------------------------------
    // Core API Bean
    // -------------------------------------------------------
    @Bean
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder().apiKey(apiKey).build();
    }

    // --- NON-STREAMING MODEL ---
    @Bean
    @Qualifier("nonStreamingModel")
    public OpenAiChatModel openAiNonStreamingModel(OpenAiApi api) {
        OpenAiChatOptions opts = OpenAiOptionsFactory.createOptions(
                globalAi.getText().getPrimary(), temperature, topP, maxTokens, false,
                globalAi.getText().getReasoningEffort(), globalAi.getText().getServiceTier());
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts).build();
    }

    // --- STREAMING MODEL ---
    @Bean
    @Qualifier("streamingModel")
    public OpenAiChatModel openAiStreamingModel(OpenAiApi api) {
        OpenAiChatOptions opts = OpenAiOptionsFactory.createOptions(
                globalAi.getText().getPrimary(), temperature, topP, maxTokens, true,
                globalAi.getText().getReasoningEffort(), globalAi.getText().getServiceTier());
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts).build();
    }

    // -------------------------------------------------------
    // Interactive Story Model (Primary & Fallback)
    // -------------------------------------------------------
    @Bean
    @Qualifier("interactiveStoryModel")
    public OpenAiChatModel interactiveStoryModel(OpenAiApi api) {
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(AiModelProfile.STORY.options()).build();
    }

    @Bean
    @Qualifier("interactiveStoryFallbackModel")
    public OpenAiChatModel interactiveStoryFallbackModel(OpenAiApi api) {
        String fallbackModel = globalAi.getText().getFallback();
        OpenAiChatOptions.Builder b = OpenAiChatOptions.builder()
                .model(fallbackModel)
                .maxCompletionTokens(8192);
        if (OpenAiOptionsFactory.supportsCustomTemperature(fallbackModel)) {
            b.temperature(0.7).topP(0.9);
        }
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(b.build()).build();
    }

    // -------------------------------------------------------
    // Summary Model (Primary & Fallback)
    // -------------------------------------------------------
    @Bean
    @Qualifier("summaryModel")
    public OpenAiChatModel summaryModel(OpenAiApi api) {
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(AiModelProfile.SUMMARY.options()).build();
    }

    @Bean
    @Qualifier("summaryFallbackModel")
    public OpenAiChatModel summaryFallbackModel(OpenAiApi api) {
        String fallbackModel = globalAi.getText().getFallback();
        OpenAiChatOptions.Builder b = OpenAiChatOptions.builder()
                .model(fallbackModel)
                .maxCompletionTokens(4096);
        if (OpenAiOptionsFactory.supportsCustomTemperature(fallbackModel)) {
            b.temperature(0.2);
        }
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(b.build()).build();
    }

    // -------------------------------------------------------
    // ChatClients
    // -------------------------------------------------------
    @Bean
    @Qualifier("interactiveStoryChatClient")
    public ChatClient interactiveStoryChatClient(@Qualifier("interactiveStoryModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    @Qualifier("interactiveStoryFallbackChatClient")
    public ChatClient interactiveStoryFallbackChatClient(@Qualifier("interactiveStoryFallbackModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    @Qualifier("summaryChatClient")
    public ChatClient summaryChatClient(@Qualifier("summaryModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    @Qualifier("summaryFallbackChatClient")
    public ChatClient summaryFallbackChatClient(@Qualifier("summaryFallbackModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }
}

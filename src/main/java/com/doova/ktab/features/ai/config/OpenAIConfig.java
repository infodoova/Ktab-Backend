package com.doova.ktab.features.ai.config;

import com.doova.ktab.features.ai.config.factory.OpenAiOptionsFactory;
import com.doova.ktab.features.story.enums.AiModelProfile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    private final String apiKey;
    private final double temperature;
    private final double topP;
    private final Integer maxTokens;
    private final String modelName; // 💡 OOP: Constant for the model name

    // ---------------------------------------------
    // (Constructor Injection)
    // ---------------------------------------------
    // 💡 Design Pattern: Dependency Injection (via constructor is preferred)
    public OpenAIConfig(@Value("${spring.ai.openai.api-key}") String apiKey, @Value("${spring.ai.openai.chat.options.temperature}") double temperature, @Value("${spring.ai.openai.chat.options.top-p}") double topP, @Value("${spring.ai.openai.chat.options.max-tokens}") Integer maxTokens, @Value("${spring.ai.openai.chat.options.model}") String modelName) {
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.modelName = modelName;
    }

    // ---------------------------------------------
    // 🛠️ Core API Bean
    // ---------------------------------------------
    // 💡 OOP: Encapsulation - apiKey is managed within the bean creation process.
    @Bean
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder().apiKey(apiKey).build();
    }

    // --- NON-STREAMING MODEL (GPT-5) ---
    @Bean
    @Qualifier("nonStreamingModel") // Use an explicit name
    public OpenAiChatModel openAiNonStreamingModel(OpenAiApi api) {

        // 🏭 Use the Factory to create options
        OpenAiChatOptions opts = OpenAiOptionsFactory.createOptions(modelName, temperature, topP, maxTokens, false // NON-STREAMING
        );

        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts).build();
    }

    // --- STREAMING MODEL (GPT-5) ---
    @Bean
    @Qualifier("streamingModel")// Use an explicit name
    public OpenAiChatModel openAiStreamingModel(OpenAiApi api) {

        // 🏭 Use the Factory to create options
        OpenAiChatOptions opts = OpenAiOptionsFactory.createOptions(modelName, temperature, topP, maxTokens, true // STREAMING
        );

        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts).build();
    }

    @Bean
    @Qualifier("interactiveStoryModel")
    public OpenAiChatModel interactiveStoryModel(OpenAiApi api) {

        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(AiModelProfile.STORY.options()).build();
    }

    // ---------------------------------------------
    // SUMMARY MODEL
    // ---------------------------------------------
    @Bean
    @Qualifier("summaryModel")
    public OpenAiChatModel summaryModel(OpenAiApi api) {

        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(AiModelProfile.SUMMARY.options()).build();
    }

    // ---------------------------------------------
// 🎭 INTERACTIVE STORY ChatClient
// ---------------------------------------------
    @Bean
    @Qualifier("interactiveStoryChatClient")
    public ChatClient interactiveStoryChatClient(@Qualifier("interactiveStoryModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }

    // ---------------------------------------------
// 🧠 SUMMARY ChatClient
// ---------------------------------------------
    @Bean
    @Qualifier("summaryChatClient")
    public ChatClient summaryChatClient(@Qualifier("summaryModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }
}

package com.doova.ktab.features.ai.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

import static com.google.cloud.vertexai.Transport.GRPC;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${spring.ai.vertex.ai.gemini.credentials-uri:}")
    private org.springframework.core.io.Resource credentialsResource;

    @Value("${gcp.credentials.base64:${GCP_CREDENTIALS_BASE64:}}")
    private String credentialsBase64;

    @Value("${spring.ai.vertex.ai.gemini.project-id:${GCP_PROJECT_ID:ktab-prod}}")
    private String projectId;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiConfig.class);

    private GoogleCredentials getCredentials() throws IOException {
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            try {
                byte[] decoded = java.util.Base64.getDecoder().decode(credentialsBase64.trim());
                return GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(decoded));
            } catch (Exception e) {
                log.warn("Failed to load Google credentials from GCP_CREDENTIALS_BASE64: {}. Falling back to alternative credentials.", e.getMessage());
            }
        }
        if (credentialsResource != null && credentialsResource.exists()) {
            return GoogleCredentials.fromStream(credentialsResource.getInputStream());
        }
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            // Provide a dummy credential so the application can start locally without credentials
            return GoogleCredentials.create(
                    new com.google.auth.oauth2.AccessToken("dummy", new java.util.Date(Long.MAX_VALUE))
            );
        }
    }

    /*
     * ---------------------------------
     * Vertex AI Context
     * Switching to "global" location
     * ----------------------------------
     */
    @Bean
    public VertexAI vertexAI() throws IOException {
        // We use the Builder to prevent the SDK from
        // prefixing the endpoint with "global-"
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation("global")
                .setApiEndpoint("aiplatform.googleapis.com")
                .setTransport(GRPC)
                .setCredentials(getCredentials())
                .build();
    }

    /*
     * ---------------------------------
     * Gemini Models
     * ----------------------------------
     */

    @Bean
    @Qualifier("endingGeneratorGeminiModel")
    public VertexAiGeminiChatModel endingGeneratorGeminiModel(
            VertexAI vertexAI,
            @Value("${spring.ai.vertex.ai.gemini.chat.options.model:gemini-3-flash-preview}") String modelName) {
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.3)
                        .topP(0.9)
                        .topK(64)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("ocrGeminiModel")
    public VertexAiGeminiChatModel ocrGeminiModel(
            VertexAI vertexAI,
            @Value("${spring.ai.vertex.ai.gemini.chat.options.model:gemini-3-flash-preview}") String modelName) {
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.1)
                        .topP(0.9)
                        .build())
                .build();
    }

    /*
     * ---------------------------------
     * Chat Clients
     * ----------------------------------
     */

    @Bean
    @Qualifier("endingGeneratorChatClient")
    public ChatClient endingGeneratorChatClient(
            @Qualifier("endingGeneratorGeminiModel") VertexAiGeminiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    @Qualifier("ocrChatClient")
    public ChatClient ocrChatClient(
            @Qualifier("ocrGeminiModel") VertexAiGeminiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    @Qualifier("storyGeminiModel")
    public VertexAiGeminiChatModel storyGeminiModel(
            VertexAI vertexAI,
            @Value("${ktab.story.ai.model:gemini-3-flash-preview}") String modelName,
            @Value("${ktab.story.ai.temperature:0.7}") double temperature,
            @Value("${ktab.story.ai.max-tokens:8192}") int maxTokens) {
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .topP(0.9)
                        .topK(64)
                        .maxOutputTokens(maxTokens)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("storySummaryGeminiModel")
    public VertexAiGeminiChatModel storySummaryGeminiModel(
            VertexAI vertexAI,
            @Value("${ktab.story.ai.model:gemini-3-flash-preview}") String modelName) {
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.2)
                        .topP(0.9)
                        .topK(64)
                        .maxOutputTokens(4096)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("storyGeminiChatClient")
    public ChatClient storyGeminiChatClient(
            @Qualifier("storyGeminiModel") VertexAiGeminiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    @Qualifier("storySummaryGeminiChatClient")
    public ChatClient storySummaryGeminiChatClient(
            @Qualifier("storySummaryGeminiModel") VertexAiGeminiChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    public Client vertexGenAiClient() throws IOException {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return Client.builder()
                    .apiKey(geminiApiKey.trim())
                    .build();
        }
        return Client.builder()
                .project(projectId)
                .location("global")
                .vertexAI(true) // ✅ forces Vertex AI
                .credentials(getCredentials())
                .build(); // ✅ uses ADC / service account
    }
}

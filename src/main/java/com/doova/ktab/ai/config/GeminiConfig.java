package com.doova.ktab.ai.config;

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

    @Value("${spring.ai.vertex.ai.gemini.credentials-uri:}")
    private org.springframework.core.io.Resource credentialsResource;

    private GoogleCredentials getCredentials() throws IOException {
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
                .setProjectId("ktab-prod")
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
    public VertexAiGeminiChatModel endingGeneratorGeminiModel(VertexAI vertexAI) {
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model("gemini-3-flash-preview") // Corrected model ID
                        .temperature(0.3)
                        .topP(0.9)
                        // Note: Gemini 3 models often use topK=64 by default
                        .topK(64)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("ocrGeminiModel")
    public VertexAiGeminiChatModel ocrGeminiModel(VertexAI vertexAI) {
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model("gemini-3-flash-preview") // Corrected model ID
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
    public Client vertexGenAiClient() throws IOException {
        return Client.builder()
                .project("ktab-prod")
                .location("global")
                .vertexAI(true) // ✅ forces Vertex AI
                .credentials(getCredentials())
                .build(); // ✅ uses ADC / service account
    }
}

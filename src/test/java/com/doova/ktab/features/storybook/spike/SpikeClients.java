package com.doova.ktab.features.storybook.spike;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.image.GeminiImageProvider;
import com.doova.ktab.features.storybook.llm.AnthropicLlmGateway;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;

import java.io.IOException;

/** Builds real clients without starting Spring (no DB needed for the spike). */
final class SpikeClients {

    private SpikeClients() {
    }

    static GeminiImageProvider imageProvider(StorybookProperties properties) throws IOException {
        Client client = Client.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location("global")
                .vertexAI(true)
                .credentials(GoogleCredentials.getApplicationDefault())
                .build();
        return new GeminiImageProvider(client, properties);
    }

    static AnthropicLlmGateway llm(StorybookProperties properties) {
        return new AnthropicLlmGateway(AnthropicOkHttpClient.fromEnv(), properties);
    }
}

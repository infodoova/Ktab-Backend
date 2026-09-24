package com.doova.ktab.features.storybook.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicClientConfig {

    @Bean
    public AnthropicClient storybookAnthropicClient(StorybookProperties properties) {
        StorybookProperties.Llm llm = properties.getLlm();
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .timeout(llm.getTimeout())
                .maxRetries(llm.getMaxRetries());
        if (llm.getApiKey() != null && !llm.getApiKey().isBlank()) {
            builder.apiKey(llm.getApiKey());
        } else {
            // Lets local development start without a key; calls fail with an auth error.
            builder.apiKey("missing-anthropic-api-key");
        }
        return builder.build();
    }
}

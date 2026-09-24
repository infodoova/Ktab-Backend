package com.doova.ktab.features.storybook.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "STORYBOOK_LIVE_TESTS", matches = "true")
class AnthropicLlmGatewayLiveTest {

    record Capital(@JsonPropertyDescription("The capital city, in English") String city) {}

    @Test
    void returnsTypedStructuredOutputAndUsage() {
        AnthropicLlmGateway gateway = new AnthropicLlmGateway(AnthropicOkHttpClient.fromEnv(), new StorybookProperties());

        LlmCall<Capital> call = gateway.call(LlmRequest.of(LlmPurpose.MODERATION,
                "Answer the question.", "What is the capital of Lebanon?", Capital.class));

        assertThat(call.value().city()).containsIgnoringCase("beirut");
        assertThat(call.model()).isEqualTo("claude-sonnet-5");
        assertThat(call.inputTokens()).isPositive();
        assertThat(call.outputTokens()).isPositive();
    }
}

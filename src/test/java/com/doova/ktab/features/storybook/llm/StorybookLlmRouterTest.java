package com.doova.ktab.features.storybook.llm;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorybookLlmRouterTest {

    private StorybookProperties properties;
    private LlmGateway openAiGateway;
    private LlmGateway anthropicGateway;
    private StorybookLlmRouter router;

    @BeforeEach
    void setUp() {
        properties = new StorybookProperties();
        openAiGateway = mock(LlmGateway.class);
        anthropicGateway = mock(LlmGateway.class);
        router = new StorybookLlmRouter(properties, openAiGateway, anthropicGateway);
    }

    @Test
    @DisplayName("call_providerOpenAi_delegatesToOpenAiGateway")
    void call_providerOpenAi_delegatesToOpenAiGateway() {
        properties.getLlm().setProvider("OPENAI");
        LlmCall<String> expected = new LlmCall<>("response", "gpt-6-luna", 100, 50, 10);
        doReturn(expected).when(openAiGateway).call(any());

        LlmRequest<String> request = LlmRequest.of(LlmPurpose.STORY_PLAN, "system", "user", String.class);
        LlmCall<String> actual = router.call(request);

        assertThat(actual).isSameAs(expected);
        verify(openAiGateway).call(request);
        verifyNoInteractions(anthropicGateway);
    }

    @Test
    @DisplayName("call_providerAnthropic_delegatesToAnthropicGateway")
    void call_providerAnthropic_delegatesToAnthropicGateway() {
        properties.getLlm().setProvider("ANTHROPIC");
        LlmCall<String> expected = new LlmCall<>("response", "claude-sonnet-5", 100, 50, 10);
        doReturn(expected).when(anthropicGateway).call(any());

        LlmRequest<String> request = LlmRequest.of(LlmPurpose.STORY_PLAN, "system", "user", String.class);
        LlmCall<String> actual = router.call(request);

        assertThat(actual).isSameAs(expected);
        verify(anthropicGateway).call(request);
        verifyNoInteractions(openAiGateway);
    }

    @Test
    @DisplayName("call_providerMixedCaseAnthropic_delegatesToAnthropicGateway")
    void call_providerMixedCaseAnthropic_delegatesToAnthropicGateway() {
        properties.getLlm().setProvider("Anthropic");
        LlmCall<String> expected = new LlmCall<>("response", "claude-sonnet-5", 100, 50, 10);
        doReturn(expected).when(anthropicGateway).call(any());

        LlmRequest<String> request = LlmRequest.of(LlmPurpose.STORY_PLAN, "system", "user", String.class);
        LlmCall<String> actual = router.call(request);

        assertThat(actual).isSameAs(expected);
        verify(anthropicGateway).call(request);
        verifyNoInteractions(openAiGateway);
    }

    @Test
    @DisplayName("call_defaultProvider_delegatesToOpenAiGateway")
    void call_defaultProvider_delegatesToOpenAiGateway() {
        // Default provider in StorybookProperties is OPENAI
        LlmCall<String> expected = new LlmCall<>("response", "gpt-6-luna", 100, 50, 10);
        doReturn(expected).when(openAiGateway).call(any());

        LlmRequest<String> request = LlmRequest.of(LlmPurpose.STORY_PLAN, "system", "user", String.class);
        LlmCall<String> actual = router.call(request);

        assertThat(actual).isSameAs(expected);
        verify(openAiGateway).call(request);
        verifyNoInteractions(anthropicGateway);
    }
}

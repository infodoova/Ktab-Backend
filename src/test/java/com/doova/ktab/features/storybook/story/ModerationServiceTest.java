package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationServiceTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final ModerationService service = new ModerationService(llm, new PromptLibrary(), new StorybookProperties());

    @Test
    void blankIsAllowedWithoutCallingTheLlm() {
        assertThat(service.moderate("  ").allowed()).isTrue();
        assertThat(llm.requests()).isEmpty();
    }

    @Test
    void phoneNumbersAreRejectedWithoutCallingTheLlm() {
        ModerationService.ModerationOutcome outcome = service.moderate("إلى سامي، اتصل بنا ٠٧١٢٣٤٥٦٧");
        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.llmCall()).isNull();
    }

    @Test
    void linksAreRejected() {
        assertThat(service.moderate("see www.example.com").allowed()).isFalse();
    }

    @Test
    void tooLongIsRejected() {
        assertThat(service.moderate("ح".repeat(301)).allowed()).isFalse();
    }

    @Test
    void ordinaryTextGoesToTheLlm() {
        llm.enqueue(new ModerationResponse(true, null));
        ModerationService.ModerationOutcome outcome = service.moderate("إلى سامي الحبيب، نحبك كثيرًا");
        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.llmCall()).isNotNull();
    }

    @Test
    void llmRefusalIsPassedThrough() {
        llm.enqueue(new ModerationResponse(false, "Contains an insult."));
        ModerationService.ModerationOutcome outcome = service.moderate("نص");
        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reason()).isEqualTo("Contains an insult.");
    }
}

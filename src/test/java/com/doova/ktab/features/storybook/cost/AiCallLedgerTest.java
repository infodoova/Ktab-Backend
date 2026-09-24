package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookAiCall;
import com.doova.ktab.features.storybook.repository.StorybookAiCallRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiCallLedgerTest {

    private StorybookAiCallRepository calls;
    private StorybookRepository books;
    private CostCalculator costs;
    private AiCallLedger ledger;

    @BeforeEach
    void setUp() {
        calls = mock(StorybookAiCallRepository.class);
        books = mock(StorybookRepository.class);

        StorybookProperties props = new StorybookProperties();
        props.getPricing().setLlm(Map.of(
                "claude-sonnet-5", new StorybookProperties.LlmPrice("3.00", "15.00")
        ));
        props.getPricing().setImagePerImageUsd(Map.of(
                "gemini-3.1-flash-image-preview", new BigDecimal("0.101")
        ));
        costs = new CostCalculator(props);
        ledger = new AiCallLedger(calls, books, costs);
    }

    @Test
    void recordLlmSavesSuccessAndAddsCostToBook() {
        LlmCall<String> call = new LlmCall<>("resp", "claude-sonnet-5", 1_000, 500, 1200);
        BigDecimal cost = ledger.recordLlm(42L, 7L, LlmPurpose.STORY_PLAN, call);

        assertThat(cost).isEqualByComparingTo("0.0105");

        ArgumentCaptor<StorybookAiCall> captor = ArgumentCaptor.forClass(StorybookAiCall.class);
        verify(calls).save(captor.capture());
        StorybookAiCall saved = captor.getValue();
        assertThat(saved.getStorybookId()).isEqualTo(42L);
        assertThat(saved.getJobId()).isEqualTo(7L);
        assertThat(saved.getPurpose()).isEqualTo("STORY_PLAN");
        assertThat(saved.getProvider()).isEqualTo("ANTHROPIC");
        assertThat(saved.getModel()).isEqualTo("claude-sonnet-5");
        assertThat(saved.getInputTokens()).isEqualTo(1_000L);
        assertThat(saved.getOutputTokens()).isEqualTo(500L);
        assertThat(saved.getImages()).isEqualTo(0);
        assertThat(saved.getCostUsd()).isEqualByComparingTo("0.0105");
        assertThat(saved.getLatencyMs()).isEqualTo(1200L);
        assertThat(saved.isSuccess()).isTrue();
        assertThat(saved.getError()).isNull();

        verify(books).addCost(eq(42L), eq(cost));
    }

    @Test
    void recordImageSavesSuccessAndAddsCostToBook() {
        ImageResult imageResult = new ImageResult(new byte[]{1, 2}, "image/png", "gemini-3.1-flash-image-preview", 4500);
        BigDecimal cost = ledger.recordImage(42L, 8L, "IMAGE_PAGE", imageResult);

        assertThat(cost).isEqualByComparingTo("0.101");

        ArgumentCaptor<StorybookAiCall> captor = ArgumentCaptor.forClass(StorybookAiCall.class);
        verify(calls).save(captor.capture());
        StorybookAiCall saved = captor.getValue();
        assertThat(saved.getStorybookId()).isEqualTo(42L);
        assertThat(saved.getJobId()).isEqualTo(8L);
        assertThat(saved.getPurpose()).isEqualTo("IMAGE_PAGE");
        assertThat(saved.getProvider()).isEqualTo("GOOGLE");
        assertThat(saved.getImages()).isEqualTo(1);
        assertThat(saved.getCostUsd()).isEqualByComparingTo("0.101");
        assertThat(saved.getLatencyMs()).isEqualTo(4500L);
        assertThat(saved.isSuccess()).isTrue();

        verify(books).addCost(eq(42L), eq(cost));
    }

    @Test
    void recordFailureSavesZeroCostAndDoesNotAddCostToBook() {
        String longError = "E".repeat(3000);
        ledger.recordFailure(42L, 9L, "IMAGE_PAGE", "GOOGLE", "gemini-3.1-flash-image-preview", 800, longError);

        ArgumentCaptor<StorybookAiCall> captor = ArgumentCaptor.forClass(StorybookAiCall.class);
        verify(calls).save(captor.capture());
        StorybookAiCall saved = captor.getValue();
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getError()).hasSize(2000);

        verify(books, never()).addCost(any(), any());
    }
}

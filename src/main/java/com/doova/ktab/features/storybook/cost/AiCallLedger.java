package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookAiCall;
import com.doova.ktab.features.storybook.repository.StorybookAiCallRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Spec: "Every AI call logs model, cost and latency." Each write is its own transaction so a
 * paid call stays on record even when the step that made it rolls back.
 */
@Component
@RequiredArgsConstructor
public class AiCallLedger {

    private final StorybookAiCallRepository calls;
    private final StorybookRepository books;
    private final CostCalculator costs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal recordLlm(Long storybookId, Long jobId, LlmPurpose purpose, LlmCall<?> call) {
        BigDecimal cost = costs.llmCostUsd(call.model(), call.inputTokens(), call.outputTokens());
        save(new AiCallEntry(storybookId, jobId, purpose.name(), "ANTHROPIC", call.model(),
                call.inputTokens(), call.outputTokens(), 0, cost, call.latencyMs(), true, null));
        return cost;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal recordImage(Long storybookId, Long jobId, String purpose, ImageResult result) {
        BigDecimal cost = costs.imageCostUsd(result.model());
        save(new AiCallEntry(storybookId, jobId, purpose, "GOOGLE", result.model(),
                null, null, 1, cost, result.latencyMs(), true, null));
        return cost;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long storybookId, Long jobId, String purpose, String provider, String model,
                              long latencyMs, String error) {
        save(new AiCallEntry(storybookId, jobId, purpose, provider, model, null, null, 0,
                BigDecimal.ZERO, latencyMs, false, error));
    }

    private void save(AiCallEntry e) {
        StorybookAiCall row = new StorybookAiCall();
        row.setStorybookId(e.storybookId());
        row.setJobId(e.jobId());
        row.setPurpose(e.purpose());
        row.setProvider(e.provider());
        row.setModel(e.model());
        row.setInputTokens(e.inputTokens());
        row.setOutputTokens(e.outputTokens());
        row.setImages(e.images());
        row.setCostUsd(e.costUsd());
        row.setLatencyMs(e.latencyMs());
        row.setSuccess(e.success());
        row.setError(e.error() == null ? null : e.error().substring(0, Math.min(2000, e.error().length())));
        calls.save(row);
        if (e.storybookId() != null && e.costUsd().signum() > 0) {
            books.addCost(e.storybookId(), e.costUsd());
        }
    }
}

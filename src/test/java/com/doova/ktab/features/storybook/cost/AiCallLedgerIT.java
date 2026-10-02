package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookAiCallRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import({AiCallLedger.class, CostCalculator.class, StorybookProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiCallLedgerIT extends StorybookJpaIT {

    @Autowired AiCallLedger ledger;
    @Autowired StorybookAiCallRepository calls;
    @Autowired StorybookRepository books;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void recordsLlmAndImageCallsAndAccumulatesBookCost() {
        Storybook book = new TransactionTemplate(txManager).execute(s -> StorybookEntityFixtures.newBook(em,
                UserFixtures.reader(em, "ledger-" + System.nanoTime() + "@example.com")));

        ledger.recordLlm(book.getId(), null, LlmPurpose.STORY_PLAN,
                new LlmCall<>("x", "claude-sonnet-5", 1_000, 500, 1200));
        ledger.recordImage(book.getId(), null, "IMAGE_PAGE",
                new ImageResult(new byte[]{1}, "image/png", "gemini-3.1-flash-image", 8000));
        ledger.recordFailure(book.getId(), null, "IMAGE_PAGE", "GOOGLE", "gemini-3.1-flash-image", 300, "HTTP 503");

        assertThat(calls.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(c -> c.getPurpose() + ":" + c.isSuccess())
                .containsExactly("STORY_PLAN:true", "IMAGE_PAGE:true", "IMAGE_PAGE:false");
        // 0.007 (LLM) + 0.067 (gemini-3.1-flash-image, see ktab.storybook.pricing) = 0.074
        assertThat(books.findById(book.getId()).orElseThrow().getTotalCostUsd()).isEqualByComparingTo("0.0740");
    }
}

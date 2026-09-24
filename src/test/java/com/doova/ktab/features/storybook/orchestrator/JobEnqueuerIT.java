package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JobEnqueuer.class)
class JobEnqueuerIT extends StorybookJpaIT {

    @Autowired JobEnqueuer enqueuer;
    @Autowired StorybookJobRepository jobs;

    @Test
    void sameKeyIsInsertedOnce() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "enq@example.com"));

        assertThat(enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 3, 1)).isTrue();
        assertThat(enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 3, 1)).isFalse();
        assertThat(enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 3, 2)).isTrue();

        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getIdempotencyKey() + ":" + j.getStatus())
                .containsExactly(book.getId() + ":ILLUSTRATE_PAGE:3:1:" + JobStatus.PENDING,
                        book.getId() + ":ILLUSTRATE_PAGE:3:2:" + JobStatus.PENDING);
    }

    @Test
    void bookLevelStepsUsePageMinusOne() {
        assertThat(JobEnqueuer.key(9L, JobStep.STORY_PLAN, -1, 0)).isEqualTo("9:STORY_PLAN:-1:0");
    }
}

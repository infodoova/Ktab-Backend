package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JobEnqueuerTest {

    private final StorybookJobRepository repository = mock(StorybookJobRepository.class);
    private final JobEnqueuer enqueuer = new JobEnqueuer(repository);

    @Test
    void keyFormatsCorrectly() {
        assertThat(JobEnqueuer.key(42L, JobStep.STORY_PLAN, -1, 0))
                .isEqualTo("42:STORY_PLAN:-1:0");
        assertThat(JobEnqueuer.key(42L, JobStep.ILLUSTRATE_PAGE, 3, 2))
                .isEqualTo("42:ILLUSTRATE_PAGE:3:2");
    }

    @Test
    void enqueueReturnsTrueWhenRowInserted() {
        when(repository.insertIfAbsent(42L, "STORY_PLAN", -1, 0, "42:STORY_PLAN:-1:0"))
                .thenReturn(1);
        assertThat(enqueuer.enqueue(42L, JobStep.STORY_PLAN, -1, 0)).isTrue();
    }

    @Test
    void enqueueReturnsFalseWhenRowAlreadyExisted() {
        when(repository.insertIfAbsent(42L, "STORY_PLAN", -1, 0, "42:STORY_PLAN:-1:0"))
                .thenReturn(0);
        assertThat(enqueuer.enqueue(42L, JobStep.STORY_PLAN, -1, 0)).isFalse();
    }
}

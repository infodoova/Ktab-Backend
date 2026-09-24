package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({StorybookResumeService.class, StorybookStateMachine.class, StorybookAccessGuard.class, JobEnqueuer.class})
class StorybookResumeServiceIT extends StorybookJpaIT {

    @Autowired StorybookResumeService resume;
    @Autowired JobEnqueuer enqueuer;
    @Autowired StorybookJobRepository jobs;
    @Autowired StorybookRepository books;

    @Test
    void revivesOnlyDeadJobsAndRestoresTheStatus() {
        User owner = UserFixtures.reader(em, "resume@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        enqueuer.enqueue(book.getId(), JobStep.STORY_PLAN, -1, 0);
        enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, 4, 1);
        var all = jobs.findByStorybookIdOrderByIdAsc(book.getId());
        all.get(0).setStatus(JobStatus.SUCCEEDED);
        StorybookJob dead = all.get(1);
        dead.setStatus(JobStatus.DEAD);
        dead.setAttempts(5);
        dead.setLastError("HTTP 503");
        book.setStatus(StorybookStatus.FAILED);
        book.setFailedFromStatus(StorybookStatus.ILLUSTRATING);
        em.flush();
        em.clear();

        resume.resume(owner, book.getId());
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        var after = jobs.findByStorybookIdOrderByIdAsc(book.getId());
        assertThat(after.get(0).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(after.get(1).getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(after.get(1).getAttempts()).isZero();
        assertThat(after.get(1).getLastError()).isEqualTo("HTTP 503");
    }

    @Test
    void onlyFailedBooksCanResume() {
        User owner = UserFixtures.reader(em, "resume2@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        assertThatThrownBy(() -> resume.resume(owner, book.getId())).isInstanceOf(StorybookStateConflictException.class);
    }
}

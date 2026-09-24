package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
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

@Import({StoryApprovalService.class, StorybookAccessGuard.class, JobEnqueuer.class})
class StoryApprovalServiceIT extends StorybookJpaIT {

    @Autowired StoryApprovalService approvals;
    @Autowired StorybookJobRepository jobs;
    @Autowired StorybookRepository books;

    @Test
    void approvingTwiceIsAConflictAndEnqueuesOneSheetJob() {
        User owner = UserFixtures.reader(em, "approve@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        book.setStatus(StorybookStatus.STORY_READY);
        em.flush();

        approvals.approveStory(owner, book.getId());
        assertThatThrownBy(() -> approvals.approveStory(owner, book.getId()))
                .isInstanceOf(StorybookStateConflictException.class);

        assertThat(books.findById(book.getId()).orElseThrow().getStoryApprovedAt()).isNotNull();
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.CHARACTER_SHEET + ":1");
    }

    @Test
    void cannotApproveADraft() {
        User owner = UserFixtures.reader(em, "approve2@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        assertThatThrownBy(() -> approvals.approveStory(owner, book.getId()))
                .isInstanceOf(StorybookStateConflictException.class);
    }
}

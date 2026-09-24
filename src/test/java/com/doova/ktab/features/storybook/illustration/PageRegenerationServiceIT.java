package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
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

@Import({PageRegenerationService.class, StorybookAccessGuard.class, JobEnqueuer.class, StorybookStateMachine.class,
        StorybookProperties.class, StoryPersistence.class})
class PageRegenerationServiceIT extends StorybookJpaIT {

    @Autowired PageRegenerationService regeneration;
    @Autowired StoryPersistence stories;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookJobRepository jobs;

    @Test
    void regeneratesOnePageAndCountsIt() {
        User owner = UserFixtures.reader(em, "regen@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        pages.findByStorybook_IdAndPageIndex(book.getId(), 6).orElseThrow().setGeneration(2);
        book.setStatus(StorybookStatus.READY);
        em.flush();

        regeneration.regenerate(owner, book.getId(), 6);

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(book.getPageRegenerations()).isEqualTo(1);
        var page = pages.findByStorybook_IdAndPageIndex(book.getId(), 6).orElseThrow();
        assertThat(page.getGeneration()).isEqualTo(3);
        assertThat(page.getRoundStartGeneration()).isEqualTo(3);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getPageIndex() + ":" + j.getGeneration())
                .contains(JobStep.ILLUSTRATE_PAGE + ":6:3");
    }

    @Test
    void limitsAndStateAreEnforced() {
        User owner = UserFixtures.reader(em, "regen2@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);

        assertThatThrownBy(() -> regeneration.regenerate(owner, book.getId(), 1)).isInstanceOf(StorybookStateConflictException.class);

        book.setStatus(StorybookStatus.READY);
        assertThatThrownBy(() -> regeneration.regenerate(owner, book.getId(), 42)).isInstanceOf(ResourceNotFoundException.class);

        book.setPageRegenerations(3);
        assertThatThrownBy(() -> regeneration.regenerate(owner, book.getId(), 1))
                .isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_LIMIT_REACHED");
    }
}

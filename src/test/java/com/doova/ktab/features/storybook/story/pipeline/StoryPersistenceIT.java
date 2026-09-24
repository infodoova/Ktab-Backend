package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import({StoryPersistence.class, JobEnqueuer.class, StorybookStateMachine.class})
class StoryPersistenceIT extends StorybookJpaIT {

    @Autowired StoryPersistence persistence;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookJobRepository jobs;
    @Autowired StorybookRepository books;

    @Test
    void savePlanStoresCoverAndPagesAndEnqueuesTheCritic() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p1@example.com"));

        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سامي."), 0);
        em.flush();
        em.clear();

        var stored = pages.findByStorybook_IdOrderByPageIndexAsc(book.getId());
        assertThat(stored).hasSize(11);
        assertThat(stored.get(0).getKind()).isEqualTo(PageKind.COVER);
        assertThat(stored.get(0).getTextAr()).isNull();
        assertThat(stored.get(0).getCharacters()).extracting(c -> c.ref()).containsExactly("CHILD", "COMPANION");
        assertThat(stored.get(10).getPageIndex()).isEqualTo((short) 10);
        assertThat(books.findById(book.getId()).orElseThrow().getTitleAr()).isEqualTo("يومي الأول");
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.STORY_CRITIC + ":0");
    }

    @Test
    void loadRebuildsTheStoredPlan() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p2@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سامي."), 0);
        em.flush();
        em.clear();

        StoryContext ctx = persistence.load(book.getId());
        assertThat(ctx.status()).isEqualTo(StorybookStatus.DRAFT);
        assertThat(ctx.request().childNameAr()).isEqualTo("سامي");
        assertThat(ctx.storedPlan().pages()).hasSize(10);
        assertThat(ctx.storedPlan().coverSceneEn()).isEqualTo("The CHILD at the school gate.");
    }

    @Test
    void acceptStoryMovesTheBookToStoryReady() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p3@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سَامِي."), 0);
        StoryPlanResponse checked = StoryFixtures.plan(10, "ذَهَبَ سامي.");

        persistence.acceptStory(book.getId(), checked);
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.STORY_READY);
        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 1).orElseThrow().getTextAr()).isEqualTo("ذَهَبَ سامي.");
    }

    @Test
    void restartDeletesPagesAndEnqueuesTheNextPlan() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p4@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);

        persistence.restartPlan(book.getId(), 1, Map.of(3, List.of("bad")));
        em.flush();
        em.clear();

        assertThat(pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())).isEmpty();
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.STORY_CRITIC + ":0", JobStep.STORY_PLAN + ":1");
    }
}

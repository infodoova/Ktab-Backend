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

    @Test
    void unresolvedComplaintsAreStoredPerPageAndLabelledByCritic() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p5@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);

        persistence.recordUnresolvedProblems(book.getId(), "story", Map.of(0, List.of("title case"), 4, List.of("unclear")), java.util.Set.of());
        persistence.recordUnresolvedProblems(book.getId(), "language", Map.of(4, List.of("dialect")), java.util.Set.of());
        em.flush();
        em.clear();

        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 0).orElseThrow().getCriticProblems()).containsExactly("story: title case");
        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow().getCriticProblems())
                .containsExactlyInAnyOrder("story: unclear", "language: dialect");
        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 5).orElseThrow().getCriticProblems()).isNullOrEmpty();
    }

    @Test
    void aCritcsOwnOldComplaintsAreReplacedAndRewrittenPagesDropTheOtherCriticsStaleOnes() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p6@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        persistence.recordUnresolvedProblems(book.getId(), "story", Map.of(2, List.of("old story"), 3, List.of("keep me")), java.util.Set.of());

        // the language critic rewrote page 2 and still dislikes page 6
        persistence.recordUnresolvedProblems(book.getId(), "language", Map.of(6, List.of("dialect")), java.util.Set.of(2));
        em.flush();
        em.clear();

        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 2).orElseThrow().getCriticProblems()).isNullOrEmpty();
        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 3).orElseThrow().getCriticProblems()).containsExactly("story: keep me");
        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 6).orElseThrow().getCriticProblems()).containsExactly("language: dialect");
    }

    @Autowired com.doova.ktab.features.storybook.repository.StorybookCharacterRepository characterRows;

    @Test
    void loadCarriesTheSupportingCharactersInOrderWithTheirTags() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p7@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        for (String id : new String[]{"grandpa", "salma"}) {
            com.doova.ktab.features.storybook.model.StorybookCharacter c = new com.doova.ktab.features.storybook.model.StorybookCharacter();
            c.setStorybook(book);
            c.setKind(com.doova.ktab.features.storybook.enums.CharacterKind.SUPPORTING);
            c.setCharacterId(id);
            c.setRelationship(id.equals("grandpa") ? "grandfather" : "friend");
            c.setAttributes(new com.doova.ktab.features.storybook.model.CharacterAttributes(null, null));
            em.persist(c);
        }
        em.flush();
        em.clear();

        StoryContext ctx = persistence.load(book.getId());

        assertThat(ctx.supporting()).extracting(c -> c.ref() + ":" + c.id()).containsExactly("SUPPORT_1:grandpa", "SUPPORT_2:salma");
    }
}

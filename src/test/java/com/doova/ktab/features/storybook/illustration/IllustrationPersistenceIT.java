package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({IllustrationPersistence.class, JobEnqueuer.class, StorybookStateMachine.class, StorybookProperties.class,
        StoryPersistence.class})
class IllustrationPersistenceIT extends StorybookJpaIT {

    @Autowired IllustrationPersistence persistence;
    @Autowired StoryPersistence stories;
    @Autowired StorybookRepository books;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookPageImageRepository images;
    @Autowired StorybookJobRepository jobs;

    private static final VisualQaResponse PASS = new VisualQaResponse(true, false, true, true, List.of());
    private static final VisualQaResponse FAIL = new VisualQaResponse(false, false, true, true, List.of("hair colour"));

    /** 10-page book in ILLUSTRATING with a GENERATED generation-1 image on the cover and every page. */
    private Storybook illustratedBook() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "qa-" + System.nanoTime() + "@example.com"));
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        for (StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())) {
            p.setGeneration(1);
            p.setRoundStartGeneration(1);
            persistence.savePageImage(book.getId(), p.getId(), p.getPageIndex(), 1, "k" + p.getPageIndex(), "m", BigDecimal.ZERO);
        }
        em.flush();
        return book;
    }

    private Long imageId(Long bookId, int pageIndex, int generation) {
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, pageIndex).orElseThrow();
        return images.findByPage_IdAndGeneration(page.getId(), generation).orElseThrow().getId();
    }

    private void judge(Storybook book, int pageIndex, int generation, VisualQaResponse verdict) {
        StorybookPage page = pages.findByStorybook_IdAndPageIndex(book.getId(), pageIndex).orElseThrow();
        persistence.recordQa(book.getId(), page.getId(), imageId(book.getId(), pageIndex, generation), generation, verdict);
        em.flush();
    }

    @Test
    void lastPassingPageAdvancesTheBookOnce() {
        Storybook book = illustratedBook();
        for (int i = 0; i <= 10; i++) {
            judge(book, i, 1, PASS);
            assertThat(books.findById(book.getId()).orElseThrow().getStatus())
                    .isEqualTo(i < 10 ? StorybookStatus.ILLUSTRATING : StorybookStatus.RENDERING);
        }
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.RENDER_PDF).hasSize(1);
    }

    @Test
    void aFailedPageGetsTheNextGeneration() {
        Storybook book = illustratedBook();
        judge(book, 4, 1, FAIL);

        StorybookPage page = pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow();
        assertThat(page.getGeneration()).isEqualTo(2);
        assertThat(images.findById(imageId(book.getId(), 4, 1)).orElseThrow().getStatus()).isEqualTo(PageImageStatus.QA_FAILED);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getPageIndex() + ":" + j.getGeneration())
                .contains(JobStep.ILLUSTRATE_PAGE + ":4:2");
    }

    @Test
    void theFourthFailureFlagsThePageAndParksTheBookInQa() {
        Storybook book = illustratedBook();
        for (int i = 0; i <= 10; i++) {
            if (i != 4) judge(book, i, 1, PASS);
        }
        StorybookPage page4 = pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow();
        for (int g = 1; g <= 4; g++) {
            if (g > 1) {
                persistence.savePageImage(book.getId(), page4.getId(), 4, g, "k4-" + g, "m", BigDecimal.ZERO);
            }
            judge(book, 4, g, FAIL);
        }

        assertThat(images.findById(imageId(book.getId(), 4, 4)).orElseThrow().getStatus()).isEqualTo(PageImageStatus.FLAGGED);
        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.QA);
    }
}

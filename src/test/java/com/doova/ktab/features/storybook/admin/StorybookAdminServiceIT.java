package com.doova.ktab.features.storybook.admin;

import com.doova.ktab.features.storybook.billing.AdminGrantedCreditAdapter;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({StorybookAdminService.class, IllustrationPersistence.class, JobEnqueuer.class, StorybookStateMachine.class,
        StorybookProperties.class, StoryPersistence.class, AdminGrantedCreditAdapter.class})
class StorybookAdminServiceIT extends StorybookJpaIT {

    @Autowired StorybookAdminService admin;
    @Autowired IllustrationPersistence illustration;
    @Autowired StoryPersistence stories;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookPageImageRepository images;
    @Autowired StorybookJobRepository jobs;
    @MockitoBean FileStorageService storage;

    private static final VisualQaResponse PASS = new VisualQaResponse(true, false, true, true, List.of());
    private static final VisualQaResponse FAIL = new VisualQaResponse(false, true, true, true, List.of("letters on a sign"));

    /** A 10-page book parked in QA with page 4 flagged and every other page passed. */
    private Storybook bookInQa() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "admin-" + System.nanoTime() + "@example.com"));
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        for (StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())) {
            p.setGeneration(p.getPageIndex() == 4 ? 4 : 1);
            p.setRoundStartGeneration(1);
            int g = p.getGeneration();
            illustration.savePageImage(book.getId(), p.getId(), p.getPageIndex(), g, "k" + p.getPageIndex(), "m", BigDecimal.ZERO);
            em.flush();
            Long imageId = images.findByPage_IdAndGeneration(p.getId(), g).orElseThrow().getId();
            illustration.recordQa(book.getId(), p.getId(), imageId, g, p.getPageIndex() == 4 ? FAIL : PASS);
        }
        em.flush();
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.QA);
        return book;
    }

    @Test
    void listsAndAcceptsAFlaggedPage() {
        Storybook book = bookInQa();

        List<FlaggedPageView> flagged = admin.flaggedPages();
        FlaggedPageView view = flagged.stream().filter(f -> f.bookId().equals(book.getId())).findFirst().orElseThrow();
        assertThat(view.pageIndex()).isEqualTo(4);
        assertThat(view.problems()).containsExactly("letters on a sign");

        admin.accept(view.pageId());
        em.flush();

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.RENDERING);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.RENDER_PDF).hasSize(1);
    }

    @Test
    void regenerationIsOneFinalAttemptOnTheFallbackModel() {
        Storybook book = bookInQa();
        StorybookPage page4 = pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow();

        admin.regenerate(page4.getId());
        em.flush();

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(page4.getGeneration()).isEqualTo(5);
        assertThat(5 - page4.getRoundStartGeneration() + 1).isEqualTo(4); // last attempt of a round
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getPageIndex() + ":" + j.getGeneration())
                .contains(JobStep.ILLUSTRATE_PAGE + ":4:5");
    }
}

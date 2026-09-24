package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import({RenderPersistence.class, StorybookStateMachine.class, StoryPersistence.class, JobEnqueuer.class,
        com.doova.ktab.features.storybook.billing.AdminGrantedCreditAdapter.class})
class RenderPersistenceIT extends StorybookJpaIT {

    @Autowired RenderPersistence persistence;
    @Autowired StoryPersistence stories;

    @Test
    void contextListsCoverAndPagesAndFinishMakesTheBookReady() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "render@example.com"));
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سامي."), 0);
        book.setStatus(StorybookStatus.RENDERING);
        em.flush();

        RenderContext ctx = persistence.context(book.getId());
        assertThat(ctx.pages()).hasSize(11);
        assertThat(ctx.childNameAr()).isEqualTo("سامي");

        persistence.finish(book.getId(), "storybook/x/book-r0.pdf");
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.READY);
        assertThat(book.getPdfKey()).isEqualTo("storybook/x/book-r0.pdf");
    }
}

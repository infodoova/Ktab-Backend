package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.CharacterAttributes;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
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

@Import({LookService.class, StorybookAccessGuard.class, JobEnqueuer.class, StorybookStateMachine.class,
        StorybookProperties.class, StoryPersistence.class})
class LookServiceIT extends StorybookJpaIT {

    @Autowired LookService looks;
    @Autowired StoryPersistence stories;
    @Autowired StorybookRepository books;
    @Autowired StorybookJobRepository jobs;

    private User owner;

    private Storybook characterReadyBook() {
        owner = UserFixtures.reader(em, "look-" + System.nanoTime() + "@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0); // cover + 10 pages
        book.setStatus(StorybookStatus.CHARACTER_READY);
        book.setStoryApprovedAt(java.time.Instant.now());
        StorybookCharacter child = new StorybookCharacter();
        child.setStorybook(book);
        child.setKind(CharacterKind.CHILD);
        child.setAttributes(CharacterAttributes.ofChild(book.getInputs().appearance()));
        child.setSheetKey("k");
        child.setSheetVersion(1);
        child.setSheetStatus(CharacterSheetStatus.GENERATED);
        em.persist(child);
        em.flush();
        return book;
    }

    @Test
    void approvingStartsIllustrationWithTheCoverOnly() {
        Storybook book = characterReadyBook();

        looks.approve(owner, book.getId());
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.ILLUSTRATE_PAGE)
                .extracting(j -> (int) j.getPageIndex())
                .containsExactly(0);
    }

    @Test
    void regenerationIsCappedAndBlocksApprovalUntilTheNewSheetExists() {
        Storybook book = characterReadyBook();

        looks.regenerate(owner, book.getId());
        assertThatThrownBy(() -> looks.approve(owner, book.getId())).isInstanceOf(StorybookStateConflictException.class);
        assertThatThrownBy(() -> looks.regenerate(owner, book.getId())).isInstanceOf(StorybookStateConflictException.class);

        // the new sheet arrives
        var child = em.getEntityManager().createQuery(
                "select c from StorybookCharacter c where c.storybook.id = :id and c.kind = :k", StorybookCharacter.class)
                .setParameter("id", book.getId()).setParameter("k", CharacterKind.CHILD).getSingleResult();
        child.setSheetVersion(2);
        looks.regenerate(owner, book.getId());   // 2nd regeneration: allowed (limit 2)
        child.setSheetVersion(3);
        assertThatThrownBy(() -> looks.regenerate(owner, book.getId()))
                .isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_LIMIT_REACHED");
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.CHARACTER_SHEET)
                .extracting(j -> j.getGeneration()).containsExactly(2, 3);
    }
}

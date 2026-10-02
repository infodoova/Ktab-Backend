package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import({IllustrationPersistence.class, JobEnqueuer.class, StorybookStateMachine.class, ModelSelector.class,
        com.doova.ktab.features.storybook.config.StorybookProperties.class})
class IllustrationPersistenceSheetIT extends StorybookJpaIT {

    @Autowired IllustrationPersistence persistence;
    @Autowired StorybookRepository books;
    @Autowired StorybookCharacterRepository characters;
    @Autowired StorybookJobRepository jobs;

    private Storybook storyReadyBookWithCharacters() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "sheet-" + System.nanoTime() + "@example.com"));
        book.setStatus(StorybookStatus.STORY_READY);
        book.setStoryApprovedAt(java.time.Instant.now());
        var child = new com.doova.ktab.features.storybook.model.StorybookCharacter();
        child.setStorybook(book);
        child.setKind(CharacterKind.CHILD);
        child.setAttributes(com.doova.ktab.features.storybook.model.CharacterAttributes.ofChild(book.getInputs().appearance()));
        child.setPhotoKey("storybook/" + book.getId() + "/photo/source.enc");
        child.setPhotoConsentAt(java.time.Instant.now());
        em.persist(child);
        em.flush();
        return book;
    }

    @Test
    void savingSheetsReadiesTheCharacterAndSchedulesThePhotoPurge() {
        Storybook book = storyReadyBookWithCharacters();

        persistence.saveSheets(book.getId(), 1, "k-child", null, true);
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.CHARACTER_READY);
        var child = characters.findByStorybook_IdAndKind(book.getId(), CharacterKind.CHILD).orElseThrow();
        assertThat(child.getSheetKey()).isEqualTo("k-child");
        assertThat(child.getSheetVersion()).isEqualTo(1);
        assertThat(child.getSheetStatus()).isEqualTo(CharacterSheetStatus.GENERATED);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.PURGE_PHOTO + ":1");
    }

    @Test
    void purgeClearsTheKey() {
        Storybook book = storyReadyBookWithCharacters();
        persistence.markPhotoPurged(book.getId());
        em.flush();
        em.clear();

        var child = characters.findByStorybook_IdAndKind(book.getId(), CharacterKind.CHILD).orElseThrow();
        assertThat(child.getPhotoKey()).isNull();
        assertThat(child.getPhotoPurgedAt()).isNotNull();
        assertThat(persistence.photoKey(book.getId())).isNull();
    }

    @Test
    void supportingSheetsAreSavedPerCharacterAndLoadedBackInTheSheetContext() {
        Storybook book = storyReadyBookWithCharacters();
        for (String id : new String[]{"grandpa", "salma"}) {
            var c = new com.doova.ktab.features.storybook.model.StorybookCharacter();
            c.setStorybook(book);
            c.setKind(CharacterKind.SUPPORTING);
            c.setCharacterId(id);
            c.setRelationship(id.equals("grandpa") ? "grandfather" : "friend");
            c.setClothing(id.equals("grandpa") ? "a brown jalabiya" : null);
            c.setAttributes(new com.doova.ktab.features.storybook.model.CharacterAttributes(null, null));
            em.persist(c);
        }
        em.flush();

        persistence.saveSheets(book.getId(), 1, "k-child", null, false, java.util.Map.of("grandpa", "k-grandpa"));
        em.flush();
        em.clear();

        SheetContext ctx = persistence.sheetContext(book.getId());
        assertThat(ctx.supporting()).extracting(SheetContext.SupportingSheet::id).containsExactly("grandpa", "salma");
        assertThat(ctx.supporting()).extracting(SheetContext.SupportingSheet::ref).containsExactly("SUPPORT_1", "SUPPORT_2");
        assertThat(ctx.supporting().get(0).sheetKey()).isEqualTo("k-grandpa");
        assertThat(ctx.supporting().get(1).sheetKey()).isNull();
        assertThat(ctx.supporting().get(0).clothing()).isEqualTo("a brown jalabiya");
    }
}

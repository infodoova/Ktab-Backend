package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookMappingIT extends StorybookJpaIT {

    @Autowired StorybookRepository books;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookPageImageRepository images;

    private static Storybook newBook(org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager em, User owner) {
        return StorybookEntityFixtures.newBook(em, owner);
    }

    @Test
    void jsonColumnsRoundTrip() {
        Storybook saved = newBook(em, UserFixtures.reader(em, "a@example.com"));
        em.clear();

        Storybook loaded = books.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getInputs().companion().nameAr()).isEqualTo("بسبوسة");
        assertThat(loaded.getInputs().blueprint().beatsFor(10)).hasSize(10);
        assertThat(loaded.getInputs().appearance()).isEqualTo(StoryFixtures.APPEARANCE);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void pagesAndImagesRoundTripWithQaResult() {
        Storybook book = newBook(em, UserFixtures.reader(em, "b@example.com"));
        StorybookPage page = new StorybookPage();
        page.setStorybook(book);
        page.setPageIndex(1);
        page.setKind(PageKind.STORY);
        page.setTextAr("ذَهَبَ سامي.");
        page.setSceneEn("The CHILD walks.");
        page.setCharacters(List.of(new CharacterInScene("CHILD", "happy")));
        page.setTextZone(TextZone.TOP);
        page.setCriticProblems(List.of("problem one"));
        em.persist(page);

        StorybookPageImage image = new StorybookPageImage();
        image.setPage(page);
        image.setGeneration(1);
        image.setImageKey("storybook/1/pages/1/g1.png");
        image.setModel("gemini-3.1-flash-image-preview");
        image.setStatus(PageImageStatus.QA_PASSED);
        image.setQaResult(new VisualQaResponse(true, false, true, true, List.of()));
        image.setCostUsd(new BigDecimal("0.1010"));
        em.persist(image);
        page.setCurrentImage(image);
        em.flush();
        em.clear();

        StorybookPage loaded = pages.findByStorybook_IdAndPageIndex(book.getId(), 1).orElseThrow();
        assertThat(loaded.getCharacters()).extracting(CharacterInScene::ref).containsExactly("CHILD");
        assertThat(loaded.getCriticProblems()).containsExactly("problem one");
        assertThat(loaded.getCurrentImage().getQaResult().passed()).isTrue();
        assertThat(images.findByPage_IdAndGeneration(loaded.getId(), 1)).isPresent();
    }

    @Test
    void ownerScopedLookupHidesOtherUsersBooks() {
        User alice = UserFixtures.reader(em, "alice@example.com");
        User bob = UserFixtures.reader(em, "bob@example.com");
        Storybook alicesBook = newBook(em, alice);

        assertThat(books.findByIdAndOwner_Id(alicesBook.getId(), alice.getId())).isPresent();
        assertThat(books.findByIdAndOwner_Id(alicesBook.getId(), bob.getId())).isEmpty();
    }
}

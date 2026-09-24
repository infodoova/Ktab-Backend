package com.doova.ktab.features.storybook.support;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.model.user.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

public final class StorybookEntityFixtures {

    private StorybookEntityFixtures() {
    }

    /** A 10-page MSA DRAFT book for "سامي" (boy, 6-8) with an orange-cat companion. */
    public static Storybook newBook(TestEntityManager em, User owner) {
        ChildProfile child = new ChildProfile();
        child.setOwner(owner);
        child.setNameAr("سامي");
        child.setGender(ChildGender.BOY);
        child.setAgeBand(AgeBand.AGE_6_8);
        child.setAppearance(StoryFixtures.APPEARANCE);
        em.persist(child);

        Storybook book = new Storybook();
        book.setOwner(owner);
        book.setChildProfile(child);
        book.setInputs(new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE,
                List.of(Interest.FOOTBALL),
                new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوسة", null, CompanionSpec.PetColor.ORANGE),
                StorySetting.BEIRUT, StoryFixtures.CATALOG.get("first-day-of-school")));
        book.setBlueprintKey("first-day-of-school");
        book.setBlueprintVersion(1);
        book.setStyle(ArtStyle.SOFT_WATERCOLOR);
        book.setVariety(LanguageVariety.MSA);
        book.setTashkeelLevel(TashkeelLevel.FULL);
        book.setPageCount(10);
        book.setStatus(StorybookStatus.DRAFT);
        return em.persistAndFlush(book);
    }
}

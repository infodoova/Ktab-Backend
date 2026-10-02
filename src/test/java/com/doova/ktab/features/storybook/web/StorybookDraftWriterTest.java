package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CharacterInput;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("StorybookDraftWriter Tests")
class StorybookDraftWriterTest {

    private StorybookRepository books;
    private StorybookCharacterRepository characters;
    private ApplicationEventPublisher events;
    private StorybookDraftWriter writer;

    private User owner;
    private ChildProfile child;

    @BeforeEach
    void setUp() {
        books = mock(StorybookRepository.class);
        characters = mock(StorybookCharacterRepository.class);
        events = mock(ApplicationEventPublisher.class);
        writer = new StorybookDraftWriter(books, characters, events);

        owner = new User();
        owner.setId(10L);

        child = new ChildProfile();
        child.setId(20L);
        child.setNameAr("سامي");
        child.setGender(ChildGender.BOY);
        child.setAgeBand(AgeBand.AGE_6_8);
        child.setAppearance(StoryFixtures.APPEARANCE);

        when(books.save(any(Storybook.class))).thenAnswer(inv -> {
            Storybook b = inv.getArgument(0);
            b.setId(42L);
            return b;
        });
    }

    private StoryInputs createInputs(CompanionSpec companion) {
        return new StoryInputs(
                "سامي",
                ChildGender.BOY,
                AgeBand.AGE_6_8,
                StoryFixtures.APPEARANCE,
                List.of(Interest.FOOTBALL),
                companion,
                StorySetting.BEIRUT,
                StoryFixtures.CATALOG.get("first-day-of-school")
        );
    }

    @Test
    void insertDraft_singleChildWithoutCharactersList_createsBookAndChildCharacterAndPublishesEvent() {
        StoryInputs inputs = createInputs(null);
        CreateStorybookRequest req = new CreateStorybookRequest(
                20L, "first-day-of-school", List.of(Interest.FOOTBALL), null, StorySetting.BEIRUT,
                ArtStyle.SOFT_WATERCOLOR, 12, LanguageVariety.MSA, TashkeelLevel.FULL, "إلى سامي البطل"
        );
        StorybookDraftWriter.ResolvedSettings settings = new StorybookDraftWriter.ResolvedSettings(
                req, LanguageVariety.MSA, TashkeelLevel.FULL, "إلى سامي البطل"
        );

        Storybook savedBook = writer.insertDraft(owner, child, inputs, settings);

        assertThat(savedBook.getId()).isEqualTo(42L);
        assertThat(savedBook.getStatus()).isEqualTo(StorybookStatus.DRAFT);
        assertThat(savedBook.getPageCount()).isEqualTo((short) 12);
        assertThat(savedBook.getDedication()).isEqualTo("إلى سامي البطل");
        assertThat(savedBook.getVariety()).isEqualTo(LanguageVariety.MSA);
        assertThat(savedBook.getTashkeelLevel()).isEqualTo(TashkeelLevel.FULL);

        ArgumentCaptor<StorybookCharacter> charCaptor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters).save(charCaptor.capture());
        StorybookCharacter savedChar = charCaptor.getValue();
        assertThat(savedChar.getStorybook()).isEqualTo(savedBook);
        assertThat(savedChar.getKind()).isEqualTo(CharacterKind.CHILD);
        assertThat(savedChar.getCharacterId()).isEqualTo("child");
        assertThat(savedChar.getAttributes()).isNotNull();

        verify(events).publishEvent(any(StorybookCreatedEvent.class));
    }

    @Test
    void insertDraft_withCompanionInInputs_createsBothChildAndCompanionCharacters() {
        CompanionSpec companion = new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوس", null, CompanionSpec.PetColor.ORANGE);
        StoryInputs inputs = createInputs(companion);
        CreateStorybookRequest req = new CreateStorybookRequest(
                20L, "first-day-of-school", List.of(Interest.CATS), companion, StorySetting.BEIRUT,
                ArtStyle.SOFT_WATERCOLOR, 15, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );
        StorybookDraftWriter.ResolvedSettings settings = new StorybookDraftWriter.ResolvedSettings(
                req, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        Storybook savedBook = writer.insertDraft(owner, child, inputs, settings);

        ArgumentCaptor<StorybookCharacter> charCaptor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters, times(2)).save(charCaptor.capture());
        List<StorybookCharacter> savedChars = charCaptor.getAllValues();

        assertThat(savedChars).hasSize(2);
        StorybookCharacter childChar = savedChars.get(0);
        assertThat(childChar.getKind()).isEqualTo(CharacterKind.CHILD);
        assertThat(childChar.getCharacterId()).isEqualTo("child");

        StorybookCharacter companionChar = savedChars.get(1);
        assertThat(companionChar.getKind()).isEqualTo(CharacterKind.COMPANION);
        assertThat(companionChar.getCharacterId()).isEqualTo("companion");
        assertThat(companionChar.getAttributes().companion()).isEqualTo(companion);
    }

    @Test
    void insertDraft_withDynamicCharacterList_createsAllCustomCharactersWithRolesAndOutfits() {
        StoryInputs inputs = createInputs(null);
        CharacterInput char1 = new CharacterInput(
                "sami", "سامي", "HUMAN", "PROTAGONIST", "Self",
                "Green sweater and brown boots", List.of("Curious", "Brave"),
                StoryFixtures.APPEARANCE
        );
        CharacterInput char2 = new CharacterInput(
                "layla", "ليلى", "HUMAN", "COMPANION", "Sister",
                "Pink dress and sneakers", List.of("Supportive", "Wise"),
                new ChildAppearance(ChildAppearance.SkinTone.LIGHT, ChildAppearance.HairColor.BROWN,
                        ChildAppearance.HairStyle.LONG_STRAIGHT, ChildAppearance.EyeColor.GREEN, false, false)
        );

        CreateStorybookRequest req = new CreateStorybookRequest(
                20L, "first-day-of-school", List.of(), null, null, ArtStyle.SOFT_WATERCOLOR,
                18, LanguageVariety.MSA, TashkeelLevel.FULL, null,
                "Friendship and Courage", "Warm and adventurous", "Never give up",
                "Two siblings climbing a magic hill", List.of("Spiders", "Dark caves"),
                "PORTRAIT", List.of(char1, char2)
        );
        StorybookDraftWriter.ResolvedSettings settings = new StorybookDraftWriter.ResolvedSettings(
                req, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        Storybook savedBook = writer.insertDraft(owner, child, inputs, settings);

        assertThat(savedBook.getTheme()).isEqualTo("Friendship and Courage");
        assertThat(savedBook.getStoryTone()).isEqualTo("Warm and adventurous");
        assertThat(savedBook.getLesson()).isEqualTo("Never give up");
        assertThat(savedBook.getStoryIdea()).isEqualTo("Two siblings climbing a magic hill");
        assertThat(savedBook.getThingsToAvoid()).containsExactly("Spiders", "Dark caves");
        assertThat(savedBook.getOrientation()).isEqualTo("PORTRAIT");

        ArgumentCaptor<StorybookCharacter> charCaptor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters, times(2)).save(charCaptor.capture());
        List<StorybookCharacter> savedChars = charCaptor.getAllValues();

        StorybookCharacter sc1 = savedChars.get(0);
        assertThat(sc1.getCharacterId()).isEqualTo("sami");
        assertThat(sc1.getRole()).isEqualTo("PROTAGONIST");
        assertThat(sc1.getRelationship()).isEqualTo("Self");
        assertThat(sc1.getClothing()).isEqualTo("Green sweater and brown boots");
        assertThat(sc1.getPersonality()).containsExactly("Curious", "Brave");

        StorybookCharacter sc2 = savedChars.get(1);
        assertThat(sc2.getCharacterId()).isEqualTo("layla");
        assertThat(sc2.getRole()).isEqualTo("COMPANION");
        assertThat(sc2.getRelationship()).isEqualTo("Sister");
        assertThat(sc2.getClothing()).isEqualTo("Pink dress and sneakers");
        assertThat(sc2.getPersonality()).containsExactly("Supportive", "Wise");
    }

    @Test
    void insertDraft_withDynamicCharacterWithoutId_generatesDefaultIdFromName() {
        StoryInputs inputs = createInputs(null);
        CharacterInput charWithoutId = new CharacterInput(
                null, "Kareem", "HUMAN", null, null, "Blue shirt", List.of("Kind"), null
        );

        CreateStorybookRequest req = new CreateStorybookRequest(
                20L, "first-day-of-school", List.of(), null, null, ArtStyle.SOFT_WATERCOLOR,
                10, LanguageVariety.MSA, TashkeelLevel.FULL, null,
                null, null, null, null, null, null, List.of(charWithoutId)
        );
        StorybookDraftWriter.ResolvedSettings settings = new StorybookDraftWriter.ResolvedSettings(
                req, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        writer.insertDraft(owner, child, inputs, settings);

        ArgumentCaptor<StorybookCharacter> charCaptor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters).save(charCaptor.capture());
        StorybookCharacter sc = charCaptor.getValue();
        assertThat(sc.getCharacterId()).isEqualTo("kareem");
        assertThat(sc.getRole()).isEqualTo("MAIN");
        assertThat(sc.getCharacterType()).isEqualTo("HUMAN");
    }

    @Test
    void insertDraft_withDynamicCharacterWithNullIdAndName_defaultsIdToChild() {
        StoryInputs inputs = createInputs(null);
        CharacterInput anonymousChar = new CharacterInput(
                null, null, null, null, null, null, null, null
        );

        CreateStorybookRequest req = new CreateStorybookRequest(
                20L, "first-day-of-school", List.of(), null, null, ArtStyle.SOFT_WATERCOLOR,
                10, LanguageVariety.MSA, TashkeelLevel.FULL, null,
                null, null, null, null, null, null, List.of(anonymousChar)
        );
        StorybookDraftWriter.ResolvedSettings settings = new StorybookDraftWriter.ResolvedSettings(
                req, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        writer.insertDraft(owner, child, inputs, settings);

        ArgumentCaptor<StorybookCharacter> charCaptor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters).save(charCaptor.capture());
        StorybookCharacter sc = charCaptor.getValue();
        assertThat(sc.getCharacterId()).isEqualTo("child");
        assertThat(sc.getRole()).isEqualTo("MAIN");
        assertThat(sc.getCharacterType()).isEqualTo("HUMAN");
    }

    @Test
    void insertDraft_withThreeCharacters_makesTheThirdOneSupportingAndKeepsItsOwnDetails() {
        StoryInputs inputs = createInputs(null);
        CharacterInput kid = new CharacterInput("layla", "ليلى", "HUMAN", "PROTAGONIST", "Self", "yellow dress", List.of("curious"), StoryFixtures.APPEARANCE);
        CharacterInput parrot = new CharacterInput("zomorrod", "زمرد", "ANIMAL", "COMPANION", "pet parrot", "green feathers", List.of("brave"), null);
        CharacterInput grandpa = new CharacterInput("grandpa", "الجد", "HUMAN", 68, ChildGender.BOY, "Elder", "grandfather",
                List.of("wise"), null, "a brown jalabiya and a white keffiyeh", "patience", "stubborn", "reading", "a brass pocket watch", "calm");
        CreateStorybookRequest req = new CreateStorybookRequest(20L, null, List.of(), null, null, ArtStyle.SOFT_WATERCOLOR,
                18, LanguageVariety.MSA, TashkeelLevel.FULL, null, "t", "w", "l", "i", List.of(), "PORTRAIT", List.of(kid, parrot, grandpa));

        writer.insertDraft(owner, child, inputs, new StorybookDraftWriter.ResolvedSettings(req, LanguageVariety.MSA, TashkeelLevel.FULL, null));

        ArgumentCaptor<StorybookCharacter> captor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters, times(3)).save(captor.capture());
        List<StorybookCharacter> saved = captor.getAllValues();
        assertThat(saved).extracting(StorybookCharacter::getKind)
                .containsExactly(CharacterKind.CHILD, CharacterKind.COMPANION, CharacterKind.SUPPORTING);
        StorybookCharacter third = saved.get(2);
        assertThat(third.getCharacterId()).isEqualTo("grandpa");
        assertThat(third.getRelationship()).isEqualTo("grandfather");
        assertThat(third.getClothing()).isEqualTo("a brown jalabiya and a white keffiyeh");
        assertThat(third.getAdvancedDetails()).containsEntry("name", "الجد").containsEntry("age", 68)
                .containsEntry("signatureItem", "a brass pocket watch");
    }

    @Test
    void insertDraft_aSupportingCharacterWithoutIdGetsAUniqueGeneratedId() {
        StoryInputs inputs = createInputs(null);
        CharacterInput kid = new CharacterInput("layla", "ليلى", "HUMAN", "PROTAGONIST", "Self", null, List.of(), StoryFixtures.APPEARANCE);
        CharacterInput pet = new CharacterInput("pet", "زمرد", "ANIMAL", "COMPANION", "pet", null, List.of(), null);
        CharacterInput friend = new CharacterInput(null, "سلمى", "HUMAN", "FRIEND", "friend", null, List.of(), null);
        CharacterInput neighbour = new CharacterInput(null, null, "HUMAN", "NEIGHBOUR", "neighbour", null, List.of(), null);
        CreateStorybookRequest req = new CreateStorybookRequest(20L, null, List.of(), null, null, ArtStyle.SOFT_WATERCOLOR,
                18, LanguageVariety.MSA, TashkeelLevel.FULL, null, "t", "w", "l", "i", List.of(), "PORTRAIT", List.of(kid, pet, friend, neighbour));

        writer.insertDraft(owner, child, inputs, new StorybookDraftWriter.ResolvedSettings(req, LanguageVariety.MSA, TashkeelLevel.FULL, null));

        ArgumentCaptor<StorybookCharacter> captor = ArgumentCaptor.forClass(StorybookCharacter.class);
        verify(characters, times(4)).save(captor.capture());
        assertThat(captor.getAllValues().subList(2, 4)).extracting(StorybookCharacter::getCharacterId).doesNotHaveDuplicates()
                .doesNotContainNull();
    }
}

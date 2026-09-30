package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.ModerationResponse;
import com.doova.ktab.features.storybook.story.ModerationService;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CharacterInput;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StorybookService Multi-Character and Specification Tests")
class StorybookServiceMultiCharacterTest {

    private ChildProfileService children;
    private ModerationService moderation;
    private AiCallLedger ledger;
    private StorybookDraftWriter writer;
    private StorybookViewMapper mapper;
    private StorybookAccessGuard guard;
    private StorybookRepository books;
    private StorybookPageRepository pages;
    private StorybookCharacterRepository characters;
    private StorybookProperties properties;
    private StorybookService service;

    private User owner;
    private ChildProfile child;

    @BeforeEach
    void setUp() {
        children = mock(ChildProfileService.class);
        moderation = mock(ModerationService.class);
        ledger = mock(AiCallLedger.class);
        writer = mock(StorybookDraftWriter.class);
        mapper = mock(StorybookViewMapper.class);
        guard = mock(StorybookAccessGuard.class);
        books = mock(StorybookRepository.class);
        pages = mock(StorybookPageRepository.class);
        characters = mock(StorybookCharacterRepository.class);
        properties = new StorybookProperties();

        service = new StorybookService(
                children,
                StoryFixtures.CATALOG,
                moderation,
                ledger,
                writer,
                mapper,
                guard,
                books,
                pages,
                characters,
                properties
        );

        owner = new User();
        owner.setId(1L);

        child = new ChildProfile();
        child.setId(5L);
        child.setNameAr("سامي");
        child.setGender(ChildGender.BOY);
        child.setAgeBand(AgeBand.AGE_6_8);
        child.setAppearance(StoryFixtures.APPEARANCE);

        when(children.requireOwned(owner, 5L)).thenReturn(child);
        when(moderation.moderate(any())).thenReturn(new ModerationService.ModerationOutcome(true, null, null));
        when(writer.insertDraft(any(), any(), any(), any())).thenAnswer(inv -> {
            Storybook b = new Storybook();
            b.setId(42L);
            b.setStatus(StorybookStatus.DRAFT);
            return b;
        });
        when(guard.requireOwned(eq(42L), eq(owner))).thenAnswer(inv -> {
            Storybook b = new Storybook();
            b.setId(42L);
            b.setStatus(StorybookStatus.DRAFT);
            return b;
        });
    }

    private CreateStorybookRequest baseRequest(int pages) {
        return new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, StorySetting.BEIRUT,
                ArtStyle.SOFT_WATERCOLOR, pages, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 12, 15, 18, 20})
    void create_allSupportedPageCounts_acceptedSuccessfully(int pageCount) {
        service.create(owner, baseRequest(pageCount));

        ArgumentCaptor<StorybookDraftWriter.ResolvedSettings> settingsCaptor =
                ArgumentCaptor.forClass(StorybookDraftWriter.ResolvedSettings.class);
        verify(writer).insertDraft(eq(owner), eq(child), any(), settingsCaptor.capture());
        assertThat(settingsCaptor.getValue().request().pageCount()).isEqualTo(pageCount);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 11, 13, 14, 16, 17, 19, 21, 25})
    void create_unsupportedPageCounts_throwsBadRequestException(int pageCount) {
        assertThatThrownBy(() -> service.create(owner, baseRequest(pageCount)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_INVALID_PAGE_COUNT");
        verifyNoInteractions(writer);
    }

    @Test
    void create_withMultipleCharacters_passesThroughToWriter() {
        CharacterInput protagonist = new CharacterInput(
                "sami", "سامي", "HUMAN", "PROTAGONIST", "Self",
                "Blue hoodie", List.of("Brave"), StoryFixtures.APPEARANCE
        );
        CharacterInput companion = new CharacterInput(
                "layla", "ليلى", "HUMAN", "COMPANION", "Sister",
                "Pink scarf", List.of("Smart"), null
        );

        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, StorySetting.BEIRUT,
                ArtStyle.SOFT_WATERCOLOR, 15, LanguageVariety.MSA, TashkeelLevel.FULL, null,
                "Adventures in Beirut", "Exciting and warm", "Kindness", "Finding a lost map",
                List.of("Scary monsters"), "PORTRAIT", List.of(protagonist, companion)
        );

        service.create(owner, req);

        ArgumentCaptor<StorybookDraftWriter.ResolvedSettings> captor =
                ArgumentCaptor.forClass(StorybookDraftWriter.ResolvedSettings.class);
        verify(writer).insertDraft(eq(owner), eq(child), any(), captor.capture());
        List<CharacterInput> passedChars = captor.getValue().request().characters();
        assertThat(passedChars).hasSize(2);
        assertThat(passedChars.get(0).id()).isEqualTo("sami");
        assertThat(passedChars.get(1).id()).isEqualTo("layla");
    }

    @Test
    void create_withThreeUniqueInterests_succeeds() {
        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(Interest.CATS, Interest.FOOTBALL, Interest.DRAWING),
                null, StorySetting.BEIRUT, ArtStyle.SOFT_WATERCOLOR, 10,
                LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        service.create(owner, req);

        verify(writer).insertDraft(any(), any(), argThat(inputs -> inputs.interests().size() == 3), any());
    }

    @Test
    void create_withDuplicateInterests_deduplicatesAndSucceeds() {
        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(Interest.CATS, Interest.CATS, Interest.FOOTBALL, Interest.FOOTBALL),
                null, StorySetting.BEIRUT, ArtStyle.SOFT_WATERCOLOR, 10,
                LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        service.create(owner, req);

        verify(writer).insertDraft(any(), any(), argThat(inputs -> inputs.interests().size() == 2), any());
    }

    @Test
    void create_withMoreThanThreeDistinctInterests_throwsTooManyInterests() {
        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(Interest.CATS, Interest.FOOTBALL, Interest.DRAWING, Interest.SPACE),
                null, StorySetting.BEIRUT, ArtStyle.SOFT_WATERCOLOR, 10,
                LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_TOO_MANY_INTERESTS");
    }

    @Test
    void create_dialectWithTashkeel_throwsDialectRequiresNoTashkeel() {
        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, null,
                ArtStyle.SOFT_WATERCOLOR, 10, LanguageVariety.EGYPTIAN, TashkeelLevel.FULL, null
        );

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL");
    }

    @Test
    void create_dialectWithoutTashkeel_defaultsToNone() {
        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, null,
                ArtStyle.SOFT_WATERCOLOR, 10, LanguageVariety.LEBANESE, null, null
        );

        service.create(owner, req);

        verify(writer).insertDraft(any(), any(), any(), argThat(settings -> settings.tashkeelLevel() == TashkeelLevel.NONE));
    }

    @Test
    void create_msaWithoutTashkeel_throwsTashkeelRequired() {
        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, null,
                ArtStyle.SOFT_WATERCOLOR, 10, LanguageVariety.MSA, null, null
        );

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_TASHKEEL_REQUIRED");
    }

    @Test
    void create_blueprintNotAllowedForAgeBand_throwsBlueprintNotAllowed() {
        child.setAgeBand(AgeBand.AGE_9_10); // first-day-of-school is for 3-5 and 6-8, not 9-10

        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, null,
                ArtStyle.SOFT_WATERCOLOR, 10, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_BLUEPRINT_NOT_ALLOWED");
    }

    @Test
    void create_invalidCompanionName_throwsValidationFailed() {
        CompanionSpec companionWithEnglishName = new CompanionSpec(
                CompanionSpec.CompanionType.CAT, "Kitty123", null, CompanionSpec.PetColor.ORANGE
        );

        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), companionWithEnglishName, StorySetting.BEIRUT,
                ArtStyle.SOFT_WATERCOLOR, 10, LanguageVariety.MSA, TashkeelLevel.FULL, null
        );

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("VALIDATION_FAILED");
    }

    @Test
    void create_exceedsDailyLimit_throwsLimitReachedBeforeModeration() {
        when(books.countByOwner_IdAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);

        CreateStorybookRequest req = baseRequest(10);

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_LIMIT_REACHED");

        verifyNoInteractions(moderation);
        verifyNoInteractions(writer);
    }

    @Test
    void create_rejectedDedication_recordsLlmUsageAndThrowsBadRequest() {
        LlmCall<ModerationResponse> llmCall = new LlmCall<>(
                new ModerationResponse(false, "inappropriate language"), "gpt-6-luna", 50, 10, 100
        );
        when(moderation.moderate("bad dedication text"))
                .thenReturn(new ModerationService.ModerationOutcome(false, "inappropriate language", llmCall));

        CreateStorybookRequest req = new CreateStorybookRequest(
                5L, "first-day-of-school", List.of(), null, StorySetting.BEIRUT,
                ArtStyle.SOFT_WATERCOLOR, 10, LanguageVariety.MSA, TashkeelLevel.FULL, "bad dedication text"
        );

        assertThatThrownBy(() -> service.create(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("STORYBOOK_DEDICATION_REJECTED");

        verify(ledger).recordLlm(isNull(), isNull(), eq(LlmPurpose.MODERATION), eq(llmCall));
        verifyNoInteractions(writer);
    }
}

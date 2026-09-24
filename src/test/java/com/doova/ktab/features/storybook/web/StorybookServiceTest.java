package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.story.ModerationResponse;
import com.doova.ktab.features.storybook.story.ModerationService;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorybookServiceTest {

    private final ChildProfileService children = mock(ChildProfileService.class);
    private final ModerationService moderation = mock(ModerationService.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final StorybookDraftWriter writer = mock(StorybookDraftWriter.class);
    private final StorybookViewMapper mapper = mock(StorybookViewMapper.class);
    private final com.doova.ktab.features.storybook.repository.StorybookRepository books =
            mock(com.doova.ktab.features.storybook.repository.StorybookRepository.class);
    private final StorybookService service = new StorybookService(children, StoryFixtures.CATALOG, moderation, ledger,
            writer, mapper, mock(StorybookAccessGuard.class),
            books,
            mock(com.doova.ktab.features.storybook.repository.StorybookPageRepository.class),
            mock(com.doova.ktab.features.storybook.repository.StorybookCharacterRepository.class),
            new StorybookProperties());

    private final User owner = new User();
    private final ChildProfile child = new ChildProfile();

    @BeforeEach
    void setUp() {
        owner.setId(1L);
        child.setNameAr("سامي");
        child.setGender(ChildGender.BOY);
        child.setAgeBand(AgeBand.AGE_6_8);
        child.setAppearance(StoryFixtures.APPEARANCE);
        when(children.requireOwned(owner, 5L)).thenReturn(child);
        when(moderation.moderate(any())).thenReturn(new ModerationService.ModerationOutcome(true, null, null));
        when(writer.insertDraft(any(), any(), any(), any())).thenAnswer(inv -> {
            Storybook b = new Storybook();
            b.setId(42L);
            return b;
        });
    }

    private CreateStorybookRequest request(int pages, LanguageVariety variety, TashkeelLevel level,
                                           List<Interest> interests, StorySetting setting, String dedication) {
        return new CreateStorybookRequest(5L, "first-day-of-school", interests, null, setting,
                ArtStyle.SOFT_WATERCOLOR, pages, variety, level, dedication);
    }

    @Test
    void fourthDraftInADayIsRejectedBeforeAnyLlmCall() {
        when(books.countByOwner_IdAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, "إلى سامي"))).hasMessage("STORYBOOK_LIMIT_REACHED");
        verifyNoInteractions(moderation);
    }

    @Test
    void validRequestSavesADraftWithASnapshotOfTheInputs() {
        service.create(owner, request(12, LanguageVariety.MSA, TashkeelLevel.PARTIAL, List.of(Interest.CATS),
                StorySetting.BEIRUT, "إلى سامي"));

        ArgumentCaptor<com.doova.ktab.features.storybook.model.StoryInputs> inputs =
                ArgumentCaptor.forClass(com.doova.ktab.features.storybook.model.StoryInputs.class);
        verify(writer).insertDraft(eq(owner), eq(child), inputs.capture(), any());
        assertThat(inputs.getValue().childNameAr()).isEqualTo("سامي");
        assertThat(inputs.getValue().blueprint().key()).isEqualTo("first-day-of-school");
    }

    @Test
    void rejectsElevenPages() {
        assertThatThrownBy(() -> service.create(owner, request(11, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, null))).isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_INVALID_PAGE_COUNT");
    }

    @Test
    void rejectsFourInterests() {
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(Interest.CATS, Interest.DOGS, Interest.CARS, Interest.MUSIC), null, null)))
                .hasMessage("STORYBOOK_TOO_MANY_INTERESTS");
    }

    @Test
    void rejectsADialectWithTashkeel() {
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.LEBANESE, TashkeelLevel.FULL,
                List.of(), null, null))).hasMessage("STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL");
    }

    @Test
    void dialectDefaultsToNoTashkeel() {
        service.create(owner, request(10, LanguageVariety.GULF, null, List.of(), null, null));
        verify(writer).insertDraft(any(), any(), any(), argThat(s -> s.tashkeelLevel() == TashkeelLevel.NONE));
    }

    @Test
    void msaRequiresATashkeelLevel() {
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, null, List.of(), null, null)))
                .hasMessage("STORYBOOK_TASHKEEL_REQUIRED");
    }

    @Test
    void rejectsABlueprintOutsideTheChildsAgeBand() {
        child.setAgeBand(AgeBand.AGE_9_10);
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, null))).hasMessage("STORYBOOK_BLUEPRINT_NOT_ALLOWED");
    }

    @Test
    void rejectedDedicationStopsCreationAndIsCosted() {
        LlmCall<ModerationResponse> call = new LlmCall<>(new ModerationResponse(false, "insult"), "claude-sonnet-5", 10, 5, 1);
        when(moderation.moderate("bad")).thenReturn(new ModerationService.ModerationOutcome(false, "insult", call));

        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, "bad"))).hasMessage("STORYBOOK_DEDICATION_REJECTED");
        verify(ledger).recordLlm(isNull(), isNull(), eq(LlmPurpose.MODERATION), eq(call));
        verify(writer, never()).insertDraft(any(), any(), any(), any());
    }
}

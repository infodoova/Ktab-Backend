package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.story.ModerationResponse;
import com.doova.ktab.features.storybook.story.ModerationService;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
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
    private final com.doova.ktab.features.storybook.character.PhotoIntakeService photoIntakeService =
            mock(com.doova.ktab.features.storybook.character.PhotoIntakeService.class);
    private final com.doova.ktab.features.storybook.repository.StorybookPageRepository pageRepository =
            mock(com.doova.ktab.features.storybook.repository.StorybookPageRepository.class);
    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final StorybookService service = new StorybookService(children, moderation, ledger,
            writer, mapper, guard,
            books,
            pageRepository,
            mock(com.doova.ktab.features.storybook.repository.StorybookCharacterRepository.class),
            new StorybookProperties(),
            photoIntakeService,
            new org.springframework.transaction.support.TransactionTemplate(
                    mock(org.springframework.transaction.PlatformTransactionManager.class)));

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
        return new CreateStorybookRequest(5L, interests, null, setting,
                ArtStyle.SOFT_WATERCOLOR, pages, variety, level, dedication);
    }

    @Test
    void fourthDraftInADayIsRejectedBeforeAnyLlmCall() {
        when(books.countByOwner_IdAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);
        assertThatThrownBy(() -> service.create(owner, request(15, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, "إلى سامي"))).hasMessage("STORYBOOK_LIMIT_REACHED");
        verifyNoInteractions(moderation);
    }

    @Test
    void validRequestSavesADraftWithASnapshotOfTheInputs() {
        service.create(owner, request(15, LanguageVariety.MSA, TashkeelLevel.PARTIAL, List.of(Interest.CATS),
                StorySetting.BEIRUT, "إلى سامي"));

        ArgumentCaptor<com.doova.ktab.features.storybook.model.StoryInputs> inputs =
                ArgumentCaptor.forClass(com.doova.ktab.features.storybook.model.StoryInputs.class);
        verify(writer).insertDraft(eq(owner), eq(child), inputs.capture(), any());
        assertThat(inputs.getValue().childNameAr()).isEqualTo("سامي");
        assertThat(inputs.getValue().blueprint().key()).isEqualTo("custom");
    }

    @Test
    void aStoryIsBuiltFromTheInputsAlone() {
        CreateStorybookRequest scratch = new CreateStorybookRequest(5L, List.of(Interest.SPACE), null,
                StorySetting.DUBAI, ArtStyle.SOFT_WATERCOLOR, 18, LanguageVariety.MSA, TashkeelLevel.PARTIAL, null,
                "SCIENCE", "ADVENTUROUS", "الفضول يصنع المعجزات", "رحلة إلى القمر", List.of(), "PORTRAIT", null);

        service.create(owner, scratch);

        ArgumentCaptor<com.doova.ktab.features.storybook.model.StoryInputs> inputs =
                ArgumentCaptor.forClass(com.doova.ktab.features.storybook.model.StoryInputs.class);
        verify(writer).insertDraft(eq(owner), eq(child), inputs.capture(), any());
        assertThat(inputs.getValue().blueprint().key()).isEqualTo("custom");
        assertThat(inputs.getValue().blueprint().beats()).isEmpty();
        assertThat(inputs.getValue().setting()).isEqualTo(StorySetting.DUBAI);
    }

    @Test
    void create_withInlineChildProfile_createsChildProfileAndDraftInSingleRequest() {
        CreateChildProfileRequest inlineChild = new CreateChildProfileRequest(
                "سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE
        );
        CreateStorybookRequest singleRequest = new CreateStorybookRequest(
                inlineChild, List.of(Interest.SPACE), null,
                StorySetting.DUBAI, ArtStyle.SOFT_WATERCOLOR, 18, LanguageVariety.MSA, TashkeelLevel.PARTIAL, null
        );

        when(children.createProfile(eq(owner), eq(inlineChild))).thenReturn(child);

        service.create(owner, singleRequest);

        verify(children).createProfile(eq(owner), eq(inlineChild));
        verify(writer).insertDraft(eq(owner), eq(child), any(), any());
    }

    @Test
    void create_withoutChildOrChildProfileId_throwsBadRequest() {
        CreateStorybookRequest invalid = new CreateStorybookRequest(
                (Long) null, List.of(Interest.SPACE), null,
                StorySetting.DUBAI, ArtStyle.SOFT_WATERCOLOR, 18, LanguageVariety.MSA, TashkeelLevel.PARTIAL, null
        );

        assertThatThrownBy(() -> service.create(owner, invalid))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("VALIDATION_FAILED");
    }

    @Test
    void rejectsElevenPages() {
        assertThatThrownBy(() -> service.create(owner, request(11, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, null))).isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_INVALID_PAGE_COUNT");
    }

    @Test
    void rejectsFourInterests() {
        assertThatThrownBy(() -> service.create(owner, request(15, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(Interest.CATS, Interest.DOGS, Interest.CARS, Interest.MUSIC), null, null)))
                .hasMessage("STORYBOOK_TOO_MANY_INTERESTS");
    }

    @Test
    void rejectsADialectWithTashkeel() {
        assertThatThrownBy(() -> service.create(owner, request(15, LanguageVariety.LEBANESE, TashkeelLevel.FULL,
                List.of(), null, null))).hasMessage("STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL");
    }

    @Test
    void dialectDefaultsToNoTashkeel() {
        service.create(owner, request(15, LanguageVariety.GULF, null, List.of(), null, null));
        verify(writer).insertDraft(any(), any(), any(), argThat(s -> s.tashkeelLevel() == TashkeelLevel.NONE));
    }

    @Test
    void msaRequiresATashkeelLevel() {
        assertThatThrownBy(() -> service.create(owner, request(15, LanguageVariety.MSA, null, List.of(), null, null)))
                .hasMessage("STORYBOOK_TASHKEEL_REQUIRED");
    }

    @Test
    void rejectedDedicationStopsCreationAndIsCosted() {
        LlmCall<ModerationResponse> call = new LlmCall<>(new ModerationResponse(false, "insult"), "claude-sonnet-5", 10, 5, 1);
        when(moderation.moderate("bad")).thenReturn(new ModerationService.ModerationOutcome(false, "insult", call));

        assertThatThrownBy(() -> service.create(owner, request(15, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, "bad"))).hasMessage("STORYBOOK_DEDICATION_REJECTED");
        verify(ledger).recordLlm(isNull(), isNull(), eq(LlmPurpose.MODERATION), eq(call));
        verify(writer, never()).insertDraft(any(), any(), any(), any());
    }

    @Test
    void createAttachesPhotosViaPhotoIntakeService() {
        Storybook book = new Storybook();
        book.setId(99L);
        when(writer.insertDraft(any(), any(), any(), any())).thenReturn(book);

        CreateStorybookRequest req = request(15, LanguageVariety.MSA, TashkeelLevel.FULL, List.of(), null, null);
        org.springframework.mock.web.MockMultipartFile photo = new org.springframework.mock.web.MockMultipartFile("childPhoto", "child.jpg", "image/jpeg", new byte[]{1});

        service.create(owner, req, photo, List.of(), true);

        verify(photoIntakeService).attachPhotos(eq(99L), eq(req), eq(photo), isNull(), eq(List.of()), eq(true));
    }

    @Test
    void list_userHasBooksWithCoverImages_returnsSummariesWithCoverImageUrls() {
        Storybook b1 = new Storybook();
        b1.setId(10L);
        b1.setStatus(StorybookStatus.READY);
        b1.setPageCount(16);

        Storybook b2 = new Storybook();
        b2.setId(20L);
        b2.setStatus(StorybookStatus.STORY_READY);
        b2.setPageCount(16);

        when(books.findByOwner_IdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of(b1, b2));

        com.doova.ktab.features.storybook.model.StorybookPage cover1 = new com.doova.ktab.features.storybook.model.StorybookPage();
        cover1.setStorybook(b1);
        com.doova.ktab.features.storybook.model.StorybookPageImage img1 = new com.doova.ktab.features.storybook.model.StorybookPageImage();
        img1.setImageKey("covers/10.png");
        cover1.setCurrentImage(img1);

        com.doova.ktab.features.storybook.model.StorybookPage cover2 = new com.doova.ktab.features.storybook.model.StorybookPage();
        cover2.setStorybook(b2);
        cover2.setCurrentImage(null);

        when(pageRepository.findCoversByStorybookIds(List.of(10L, 20L))).thenReturn(List.of(cover1, cover2));
        when(mapper.resolveCoverImageUrl(cover1)).thenReturn("https://signed/covers/10.png");
        when(mapper.toSummary(eq(b1), eq("https://signed/covers/10.png"))).thenReturn(
                new com.doova.ktab.features.storybook.web.dto.StorybookSummary(10L, "كتاب 10", "سامي",
                        StorybookStatus.READY, 16, "https://signed/covers/10.png", null));
        when(mapper.toSummary(eq(b2), isNull())).thenReturn(
                new com.doova.ktab.features.storybook.web.dto.StorybookSummary(20L, "كتاب 20", "سامي",
                        StorybookStatus.STORY_READY, 16, null, null));

        List<com.doova.ktab.features.storybook.web.dto.StorybookSummary> result = service.list(owner);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).coverImageUrl()).isEqualTo("https://signed/covers/10.png");
        assertThat(result.get(1).coverImageUrl()).isNull();
    }

    @Test
    void list_userHasNoBooks_returnsEmptyListWithoutQueryingPages() {
        when(books.findByOwner_IdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of());

        List<com.doova.ktab.features.storybook.web.dto.StorybookSummary> result = service.list(owner);

        assertThat(result).isEmpty();
        verify(pageRepository, never()).findCoversByStorybookIds(any());
    }

    @Test
    void editStory_validTitleAndPages_updatesSuccessfully() {
        Storybook b = new Storybook();
        b.setId(42L);
        b.setStatus(StorybookStatus.STORY_READY);
        b.setTitleAr("عنوان قديم");
        b.setInputs(new com.doova.ktab.features.storybook.model.StoryInputs("سامي", ChildGender.BOY,
                AgeBand.AGE_6_8, StoryFixtures.APPEARANCE, List.of(), null, null, null, null, null));

        com.doova.ktab.features.storybook.model.StorybookPage page = new com.doova.ktab.features.storybook.model.StorybookPage();
        page.setPageIndex(1);
        page.setKind(com.doova.ktab.features.storybook.enums.PageKind.STORY);
        page.setTextAr("نص قديم");
        page.setSceneEn("Old scene");

        when(guard.requireOwned(42L, owner)).thenReturn(b);
        when(pageRepository.findByStorybook_IdOrderByPageIndexAsc(42L)).thenReturn(List.of(page));
        when(moderation.moderateBatch(any())).thenReturn(new ModerationService.ModerationOutcome(true, null, null));

        com.doova.ktab.features.storybook.web.dto.EditStoryRequest req =
                new com.doova.ktab.features.storybook.web.dto.EditStoryRequest("عنوان جديد لسامي",
                        List.of(new com.doova.ktab.features.storybook.web.dto.EditPageRequest(1, "نص جديد لسامي", "New scene")));

        service.editStory(owner, 42L, req);

        assertThat(b.getTitleAr()).isEqualTo("عنوان جديد لسامي");
        assertThat(page.getTextAr()).isEqualTo("نص جديد لسامي");
        assertThat(page.getSceneEn()).isEqualTo("New scene");
        verify(books).save(b);
        verify(pageRepository).saveAll(any());
    }

    @Test
    void editStory_invalidStatus_throwsStateConflict() {
        Storybook b = new Storybook();
        b.setId(42L);
        b.setStatus(StorybookStatus.DRAFT);
        when(guard.requireOwned(42L, owner)).thenReturn(b);

        com.doova.ktab.features.storybook.web.dto.EditStoryRequest req =
                new com.doova.ktab.features.storybook.web.dto.EditStoryRequest("عنوان جديد", List.of());

        assertThatThrownBy(() -> service.editStory(owner, 42L, req))
                .isInstanceOf(com.doova.ktab.features.storybook.exception.StorybookStateConflictException.class);
    }

    @Test
    void editStory_alreadyApproved_throwsStateConflict() {
        Storybook b = new Storybook();
        b.setId(42L);
        b.setStatus(StorybookStatus.STORY_READY);
        b.setStoryApprovedAt(java.time.Instant.now());
        when(guard.requireOwned(42L, owner)).thenReturn(b);

        com.doova.ktab.features.storybook.web.dto.EditStoryRequest req =
                new com.doova.ktab.features.storybook.web.dto.EditStoryRequest("عنوان جديد", List.of());

        assertThatThrownBy(() -> service.editStory(owner, 42L, req))
                .isInstanceOf(com.doova.ktab.features.storybook.exception.StorybookStateConflictException.class);
    }

    @Test
    void editStory_emptyRequest_throwsBadRequest() {
        Storybook b = new Storybook();
        b.setId(42L);
        b.setStatus(StorybookStatus.STORY_READY);
        when(guard.requireOwned(42L, owner)).thenReturn(b);

        com.doova.ktab.features.storybook.web.dto.EditStoryRequest req =
                new com.doova.ktab.features.storybook.web.dto.EditStoryRequest(null, null);

        assertThatThrownBy(() -> service.editStory(owner, 42L, req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void editStory_moderationRejection_throwsBadRequest() {
        Storybook b = new Storybook();
        b.setId(42L);
        b.setStatus(StorybookStatus.STORY_READY);
        when(guard.requireOwned(42L, owner)).thenReturn(b);
        when(moderation.moderateBatch(List.of("محتوى مرفوض"))).thenReturn(new ModerationService.ModerationOutcome(false, null, null));

        com.doova.ktab.features.storybook.web.dto.EditStoryRequest req =
                new com.doova.ktab.features.storybook.web.dto.EditStoryRequest("محتوى مرفوض", null);

        assertThatThrownBy(() -> service.editStory(owner, 42L, req))
                .isInstanceOf(BadRequestException.class);
    }
}

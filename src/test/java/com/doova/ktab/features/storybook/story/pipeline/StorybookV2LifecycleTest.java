package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.billing.StorybookCreditPort;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.illustration.LookService;
import com.doova.ktab.features.storybook.model.*;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.*;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Storybook V2 Narrative Pipeline & Approval Gates Lifecycle Tests")
class StorybookV2LifecycleTest {

    private final StorybookRepository books = mock(StorybookRepository.class);
    private final StorybookCharacterRepository characters = mock(StorybookCharacterRepository.class);
    private final StorybookPageRepository pages = mock(StorybookPageRepository.class);
    private final StoryPersistence persistence = mock(StoryPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final JobEnqueuer enqueuer = mock(JobEnqueuer.class);
    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final StorybookCreditPort credits = mock(StorybookCreditPort.class);
    private final StorybookProperties properties = new StorybookProperties();
    private final StorybookStateMachine stateMachine = new StorybookStateMachine();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptLibrary promptLibrary = new PromptLibrary();

    private FakeLlmGateway llm;
    private CharacterBibleHandler bibleHandler;
    private StoryBlueprintHandler blueprintHandler;
    private StoryPlanHandler planHandler;
    private StoryCriticHandler storyCriticHandler;
    private LanguageCriticHandler languageCriticHandler;
    private StoryApprovalService storyApprovalService;
    private LookService lookService;

    private User owner;
    private Storybook book;
    private StorybookCharacter childCharacter;

    @BeforeEach
    void setUp() {
        llm = new FakeLlmGateway();

        bibleHandler = new CharacterBibleHandler(books, characters, llm, promptLibrary, ledger, enqueuer, objectMapper);
        blueprintHandler = new StoryBlueprintHandler(books, llm, promptLibrary, ledger, enqueuer, objectMapper);
        planHandler = new StoryPlanHandler(new StoryWriter(llm, promptLibrary), persistence, ledger);
        storyCriticHandler = new StoryCriticHandler(new StoryCritic(llm, promptLibrary), new StoryWriter(llm, promptLibrary),
                persistence, ledger, properties);
        languageCriticHandler = new LanguageCriticHandler(new LanguageCritic(llm, promptLibrary), new StoryWriter(llm, promptLibrary),
                persistence, ledger, properties);

        storyApprovalService = new StoryApprovalService(guard, enqueuer, credits, properties);
        lookService = new LookService(guard, characters, pages, enqueuer, stateMachine, properties);

        owner = new User();
        owner.setId(1L);

        book = new Storybook();
        book.setId(100L);
        book.setOwner(owner);
        book.setStatus(StorybookStatus.DRAFT);
        book.setPageCount(10);
        book.setInputs(StoryFixtures.INPUTS);
        book.setTheme("Courage and Kindness");
        book.setStoryTone("Warm and Inspiring");
        book.setLesson("Helping others brings joy");
        book.setStoryIdea("A boy and his pet explore the neighborhood");
        book.setThingsToAvoid(List.of("Monsters", "Dark rooms"));
        book.setOrientation("PORTRAIT");

        childCharacter = new StorybookCharacter();
        childCharacter.setId(50L);
        childCharacter.setStorybook(book);
        childCharacter.setKind(CharacterKind.CHILD);
        childCharacter.setCharacterId("child");
        childCharacter.setAttributes(CharacterAttributes.ofChild(StoryFixtures.APPEARANCE));

        when(books.findById(100L)).thenReturn(Optional.of(book));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of(childCharacter));
        when(characters.findByStorybook_IdAndKind(100L, CharacterKind.CHILD)).thenReturn(Optional.of(childCharacter));
        when(guard.requireOwned(100L, owner)).thenReturn(book);
    }

    private static StorybookJob makeJob(long id, JobStep step, int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(id);
        j.setStorybookId(100L);
        j.setStep(step);
        j.setGeneration(generation);
        return j;
    }

    private static CriticResponse passingVerdicts(int pageCount) {
        List<PageVerdict> verdicts = new ArrayList<>();
        verdicts.add(new PageVerdict(0, true, List.of())); // cover verdict
        for (int i = 1; i <= pageCount; i++) {
            verdicts.add(new PageVerdict(i, true, List.of()));
        }
        return new CriticResponse(verdicts);
    }

    @Test
    void lifecycle_fullSequentialProgression_succeedsThroughAllPhases() {
        // --- STEP 1: CHARACTER BIBLE ---
        CharacterBibleResponse bibleResp = new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black short curly hair, brown eyes",
                        "Bright yellow hoodie with blue trousers", "Cheerful and inquisitive")),
                "Luminous soft watercolor with warm textures",
                "A heartwarming neighborhood exploration"
        );
        llm.enqueue(bibleResp);

        StepOutcome bibleOutcome = bibleHandler.handle(makeJob(1L, JobStep.CHARACTER_BIBLE, 0));
        assertThat(bibleOutcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(book.getCharacterBible()).contains("PROTAGONIST");
        assertThat(childCharacter.getClothing()).isEqualTo("Bright yellow hoodie with blue trousers");
        verify(enqueuer).enqueue(100L, JobStep.STORY_BLUEPRINT, -1, 0);

        // --- STEP 2: STORY BLUEPRINT ---
        StoryBlueprintResponse blueprintResp = new StoryBlueprintResponse(
                "مغامرة سامي في الحي",
                "رحلة جميلة في شوارع بيروت",
                java.util.stream.IntStream.rangeClosed(1, 10)
                        .mapToObj(i -> new BlueprintPageBeat(i, "حدث الصفحة " + i, "excited", "غرفة النوم", List.of("سامي")))
                        .toList()
        );
        llm.enqueue(blueprintResp);

        StepOutcome blueprintOutcome = blueprintHandler.handle(makeJob(2L, JobStep.STORY_BLUEPRINT, 0));
        assertThat(blueprintOutcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(book.getStoryBlueprint()).contains("مغامرة سامي في الحي");
        verify(enqueuer).enqueue(100L, JobStep.STORY_PLAN, -1, 0);

        // --- STEP 3: STORY PLAN ---
        StoryPlanResponse generatedPlan = StoryFixtures.plan(10, "ذَهَبَ سامي إِلَى المَدْرَسَةِ.");
        when(persistence.load(100L)).thenReturn(new StoryContext(100L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), null));
        llm.enqueue(generatedPlan);

        StepOutcome planOutcome = planHandler.handle(makeJob(3L, JobStep.STORY_PLAN, 0));
        assertThat(planOutcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).savePlan(eq(100L), any(StoryPlanResponse.class), eq(0));

        // --- STEP 4: STORY CRITIC ---
        when(persistence.load(100L)).thenReturn(new StoryContext(100L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), generatedPlan));
        llm.enqueue(passingVerdicts(10));

        StepOutcome criticOutcome = storyCriticHandler.handle(makeJob(4L, JobStep.STORY_CRITIC, 0));
        assertThat(criticOutcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).enqueueLanguageCritic(100L, 0);

        // --- STEP 5: LANGUAGE CRITIC ---
        llm.enqueue(passingVerdicts(10));

        StepOutcome langCriticOutcome = languageCriticHandler.handle(makeJob(5L, JobStep.LANGUAGE_CRITIC, 0));
        assertThat(langCriticOutcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).acceptStory(eq(100L), any(StoryPlanResponse.class));

        // --- GATE 1: STORY APPROVAL (STORY_READY -> CHARACTER_SHEET) ---
        book.setStatus(StorybookStatus.STORY_READY);

        storyApprovalService.approveStory(owner, 100L);

        assertThat(book.getStoryApprovedAt()).isNotNull();
        verify(enqueuer).enqueue(100L, JobStep.CHARACTER_SHEET, -1, 1);

        // --- GATE 2: LOOK APPROVAL (CHARACTER_READY -> ILLUSTRATING) ---
        book.setStatus(StorybookStatus.CHARACTER_READY);
        childCharacter.setSheetStatus(CharacterSheetStatus.GENERATED);
        childCharacter.setSheetVersion(1);

        StorybookPage coverPage = new StorybookPage();
        coverPage.setPageIndex(0);
        StorybookPage storyPage1 = new StorybookPage();
        storyPage1.setPageIndex(1);
        when(pages.findByStorybook_IdOrderByPageIndexAsc(100L)).thenReturn(List.of(coverPage, storyPage1));

        lookService.approve(owner, 100L);

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(book.getLookApprovedAt()).isNotNull();
        assertThat(childCharacter.getSheetStatus()).isEqualTo(CharacterSheetStatus.APPROVED);
        // the cover goes first; its verdict releases the story pages
        verify(enqueuer).enqueue(100L, JobStep.ILLUSTRATE_PAGE, 0, 1);
        verify(enqueuer, never()).enqueue(100L, JobStep.ILLUSTRATE_PAGE, 1, 1);
    }

    @Test
    void lifecycle_gate1StoryApproval_throwsConflictWhenStatusNotStoryReady() {
        book.setStatus(StorybookStatus.DRAFT);

        assertThatThrownBy(() -> storyApprovalService.approveStory(owner, 100L))
                .isInstanceOf(StorybookStateConflictException.class);

        verify(enqueuer, never()).enqueue(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    void lifecycle_gate1StoryApproval_throwsConflictWhenAlreadyApproved() {
        book.setStatus(StorybookStatus.STORY_READY);
        book.setStoryApprovedAt(java.time.Instant.now());

        assertThatThrownBy(() -> storyApprovalService.approveStory(owner, 100L))
                .isInstanceOf(StorybookStateConflictException.class);

        verify(enqueuer, never()).enqueue(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    void lifecycle_gate2LookRegeneration_incrementsCountAndEnforcesLimit() {
        book.setStatus(StorybookStatus.CHARACTER_READY);
        book.setLookRegenerations(0);
        childCharacter.setSheetStatus(CharacterSheetStatus.GENERATED);
        childCharacter.setSheetVersion(1);

        // First regeneration (count becomes 1)
        lookService.regenerate(owner, 100L);
        assertThat(book.getLookRegenerations()).isEqualTo(1);
        verify(enqueuer).enqueue(100L, JobStep.CHARACTER_SHEET, -1, 2);

        // Second regeneration (count becomes 2)
        childCharacter.setSheetVersion(2);
        lookService.regenerate(owner, 100L);
        assertThat(book.getLookRegenerations()).isEqualTo(2);
        verify(enqueuer).enqueue(100L, JobStep.CHARACTER_SHEET, -1, 3);

        // Third regeneration attempts to exceed limit of 2 -> throws BadRequestException
        childCharacter.setSheetVersion(3);
        assertThatThrownBy(() -> lookService.regenerate(owner, 100L))
                .isInstanceOf(BadRequestException.class);
    }
}

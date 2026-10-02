package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.BlueprintPageBeat;
import com.doova.ktab.features.storybook.story.StoryBlueprintResponse;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoryBlueprintHandlerTest {

    private final StorybookRepository books = mock(StorybookRepository.class);
    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PromptLibrary prompts = new PromptLibrary();
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final JobEnqueuer enqueuer = mock(JobEnqueuer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private StoryBlueprintHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StoryBlueprintHandler(books, llm, prompts, ledger, enqueuer, objectMapper);
    }

    private static StorybookJob job() {
        StorybookJob j = new StorybookJob();
        j.setId(11L);
        j.setStorybookId(101L);
        j.setStep(JobStep.STORY_BLUEPRINT);
        j.setGeneration(0);
        return j;
    }

    @Test
    void skipsIfBlueprintAlreadyExists() {
        Storybook book = new Storybook();
        book.setId(101L);
        book.setStoryBlueprint("{\"beats\":[{\"pageNumber\":1,\"beat\":\"x\"}]}");
        when(books.findById(101L)).thenReturn(Optional.of(book));

        StepOutcome outcome = handler.handle(job());

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
        verify(enqueuer).enqueue(101L, JobStep.STORY_PLAN, -1, 0);
    }

    @Test
    void generatesBlueprintAndEnqueuesStoryPlan() {
        Storybook book = new Storybook();
        book.setId(101L);
        book.setInputs(StoryFixtures.INPUTS);
        book.setPageCount(18);
        book.setTheme("Courage and Friendship");
        book.setStoryTone("Adventurous");
        book.setLesson("Helping others");
        book.setThingsToAvoid(List.of("scary monsters", "dark caves"));
        when(books.findById(101L)).thenReturn(Optional.of(book));

        StoryBlueprintResponse response = new StoryBlueprintResponse(
                "رحلة الأصدقاء",
                "مغامرة شيقة",
                beats(18)
        );
        llm.enqueue(response);

        StepOutcome outcome = handler.handle(job());

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(book.getStoryBlueprint()).contains("رحلة الأصدقاء");

        verify(ledger).recordLlm(eq(101L), eq(11L), eq(LlmPurpose.STORY_BLUEPRINT), any());
        verify(books).save(book);
        verify(enqueuer).enqueue(101L, JobStep.STORY_PLAN, -1, 0);
    }

    private static List<BlueprintPageBeat> beats(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> new BlueprintPageBeat(i, "beat " + i, "happy", "room", List.of("سامي"))).toList();
    }

    private Storybook draft(String storedBlueprint) {
        Storybook book = new Storybook();
        book.setId(101L);
        book.setInputs(StoryFixtures.INPUTS);
        book.setPageCount(18);
        book.setStoryBlueprint(storedBlueprint);
        when(books.findById(101L)).thenReturn(Optional.of(book));
        return book;
    }

    @Test
    void anUnreadableBlueprintIsNotStoredAndTheStepIsRetried() {
        Storybook book = draft(null);
        llm.enqueue(new StoryBlueprintResponse(null, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(job()))
                .isInstanceOfSatisfying(com.doova.ktab.features.storybook.llm.LlmCallFailedException.class,
                        e -> assertThat(e.retryable()).isTrue());
        assertThat(book.getStoryBlueprint()).isNull();
        verify(enqueuer, never()).enqueue(any(), any(), anyInt(), anyInt());
    }

    @Test
    void aBlueprintWithTheWrongNumberOfBeatsIsRetried() {
        draft(null);
        llm.enqueue(new StoryBlueprintResponse("t", "p", beats(17)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(job()))
                .isInstanceOf(com.doova.ktab.features.storybook.llm.LlmCallFailedException.class);
        verify(books, never()).save(any());
    }

    @Test
    void aStoredEmptyBlueprintIsReplacedNotTrusted() {
        Storybook book = draft("{\"beats\": null, \"premise\": null, \"titleConcept\": null}");
        llm.enqueue(new StoryBlueprintResponse("t", "p", beats(18)));

        handler.handle(job());

        assertThat(book.getStoryBlueprint()).contains("beat 18");
        verify(enqueuer).enqueue(101L, JobStep.STORY_PLAN, -1, 0);
    }
}

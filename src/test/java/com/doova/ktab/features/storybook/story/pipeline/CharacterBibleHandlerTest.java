package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.model.CharacterAttributes;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.CharacterBibleResponse;
import com.doova.ktab.features.storybook.story.CharacterVisualSpec;
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

class CharacterBibleHandlerTest {

    private final StorybookRepository books = mock(StorybookRepository.class);
    private final StorybookCharacterRepository characters = mock(StorybookCharacterRepository.class);
    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PromptLibrary prompts = new PromptLibrary();
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final JobEnqueuer enqueuer = mock(JobEnqueuer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CharacterBibleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CharacterBibleHandler(books, characters, llm, prompts, ledger, enqueuer, objectMapper);
    }

    private static StorybookJob job() {
        StorybookJob j = new StorybookJob();
        j.setId(10L);
        j.setStorybookId(100L);
        j.setStep(JobStep.CHARACTER_BIBLE);
        j.setGeneration(0);
        return j;
    }

    @Test
    void skipsIfBibleAlreadyExists() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setCharacterBible("{\"characters\": []}");
        when(books.findById(100L)).thenReturn(Optional.of(book));

        StepOutcome outcome = handler.handle(job());

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
        verify(enqueuer).enqueue(100L, JobStep.STORY_BLUEPRINT, -1, 0);
    }

    @Test
    void generatesBibleAndEnqueuesBlueprint() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));

        StorybookCharacter child = new StorybookCharacter();
        child.setKind(CharacterKind.CHILD);
        child.setCharacterId("child");
        child.setAttributes(CharacterAttributes.ofChild(StoryFixtures.APPEARANCE));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of(child));

        CharacterBibleResponse response = new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black hair, green eyes", "yellow hoodie, blue jeans", "cheerful")),
                "vibrant watercolor",
                "a joyful adventure"
        );
        llm.enqueue(response);

        StepOutcome outcome = handler.handle(job());

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(book.getCharacterBible()).contains("PROTAGONIST");
        assertThat(child.getClothing()).isEqualTo("yellow hoodie, blue jeans");
        assertThat(child.getPersonality()).containsExactly("cheerful");

        verify(ledger).recordLlm(eq(100L), eq(10L), eq(LlmPurpose.CHARACTER_BIBLE), any());
        verify(books).save(book);
        verify(characters).saveAll(anyList());
        verify(enqueuer).enqueue(100L, JobStep.STORY_BLUEPRINT, -1, 0);
    }
}

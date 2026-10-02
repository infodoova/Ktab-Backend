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

    @Test
    void anUnreadableBibleIsNotStoredAndTheStepIsRetried() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of());
        llm.enqueue(new CharacterBibleResponse(null, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(job()))
                .isInstanceOfSatisfying(com.doova.ktab.features.storybook.llm.LlmCallFailedException.class,
                        e -> assertThat(e.retryable()).isTrue());
        assertThat(book.getCharacterBible()).isNull();
        verify(enqueuer, never()).enqueue(any(), any(), anyInt(), anyInt());
    }

    @Test
    void theClothesTheParentChoseAreNeverOverwrittenByTheBible() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));

        StorybookCharacter child = new StorybookCharacter();
        child.setKind(CharacterKind.CHILD);
        child.setCharacterId("child");
        child.setClothing("فستان أصفر وحجاب أبيض بسيط");
        child.setAttributes(CharacterAttributes.ofChild(StoryFixtures.APPEARANCE));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of(child));
        llm.enqueue(new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black hair", "turquoise hijab, coral tunic", "cheerful")),
                "style", "summary"));

        handler.handle(job());

        assertThat(child.getClothing()).isEqualTo("فستان أصفر وحجاب أبيض بسيط");
        assertThat(child.getPersonality()).containsExactly("cheerful");
    }

    @Test
    void theBiblesStyleNotesBecomeTheBooksStyleBible() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of());
        llm.enqueue(new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black hair", "hoodie", "cheerful")),
                "soft watercolor, warm light, thin brown outlines", "summary"));

        handler.handle(job());

        assertThat(com.doova.ktab.features.storybook.illustration.StyleBible.notesOf(book.getStyleBible()))
                .isEqualTo("soft watercolor, warm light, thin brown outlines");
    }

    private Storybook bookWithChildWearing(String clothing, StorybookCharacter[] holder) {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));
        StorybookCharacter child = new StorybookCharacter();
        child.setKind(CharacterKind.CHILD);
        child.setCharacterId("child");
        child.setClothing(clothing);
        child.setAttributes(CharacterAttributes.ofChild(StoryFixtures.APPEARANCE));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of(child));
        holder[0] = child;
        return book;
    }

    @Test
    void theParentsOutfitIsSentToTheBibleAsFixed() {
        bookWithChildWearing("yellow dress and a plain white hijab", new StorybookCharacter[1]);
        llm.enqueue(new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black hair", "x", "cheerful")), "style", "summary"));

        handler.handle(job());

        assertThat(llm.requests().get(0).user()).contains("yellow dress and a plain white hijab").containsIgnoringCase("do not change");
    }

    @Test
    void theBibleHandedToTheStoryWriterCarriesTheParentsOutfitNotTheModels() {
        StorybookCharacter[] child = new StorybookCharacter[1];
        Storybook book = bookWithChildWearing("yellow dress and a plain white hijab", child);
        llm.enqueue(new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black hair", "a lavender hijab, a coral-pink tunic and a teal skirt", "cheerful")),
                "style", "summary"));

        handler.handle(job());

        assertThat(book.getCharacterBible()).contains("yellow dress and a plain white hijab")
                .doesNotContain("lavender").doesNotContain("coral-pink").doesNotContain("teal");
        assertThat(child[0].getClothing()).isEqualTo("yellow dress and a plain white hijab");
    }

    @Test
    void withoutAParentsOutfitTheBiblesOutfitIsUsedAsBefore() {
        StorybookCharacter[] child = new StorybookCharacter[1];
        Storybook book = bookWithChildWearing(null, child);
        llm.enqueue(new CharacterBibleResponse(
                List.of(new CharacterVisualSpec("سامي", "PROTAGONIST", "black hair", "a turquoise hijab and a coral tunic", "cheerful")),
                "style", "summary"));

        handler.handle(job());

        assertThat(child[0].getClothing()).isEqualTo("a turquoise hijab and a coral tunic");
        assertThat(book.getCharacterBible()).contains("a turquoise hijab and a coral tunic");
    }

    private StorybookCharacter supportingGrandfather() {
        StorybookCharacter c = new StorybookCharacter();
        c.setKind(CharacterKind.SUPPORTING);
        c.setCharacterId("grandpa");
        c.setRole("Elder");
        c.setRelationship("grandfather");
        c.setClothing("a brown jalabiya and a white keffiyeh");
        c.setAdvancedDetails(java.util.Map.of("name", "الجد", "age", 68));
        c.setAttributes(new CharacterAttributes(null, null));
        return c;
    }

    @Test
    void aSupportingCharactersOutfitIsSentAsFixedForThatCharacterNotForTheChild() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of(supportingGrandfather()));
        llm.enqueue(new CharacterBibleResponse(List.of(new CharacterVisualSpec("الجد", "GUIDE", "white beard", "x", "wise")), "style", "summary"));

        handler.handle(job());

        String user = llm.requests().get(0).user();
        assertThat(user).contains("the character الجد").contains("a brown jalabiya and a white keffiyeh").doesNotContain("for the child (use it exactly");
    }

    @Test
    void theBibleEntryIsMatchedToTheSupportingCharacterByItsOwnNameAndKeepsThePatentsOutfit() {
        Storybook book = new Storybook();
        book.setId(100L);
        book.setInputs(StoryFixtures.INPUTS);
        when(books.findById(100L)).thenReturn(Optional.of(book));
        StorybookCharacter grandpa = supportingGrandfather();
        when(characters.findByStorybook_Id(100L)).thenReturn(List.of(grandpa));
        llm.enqueue(new CharacterBibleResponse(List.of(new CharacterVisualSpec("الجد", "GUIDE", "white beard", "a navy suit", "wise")), "style", "summary"));

        handler.handle(job());

        assertThat(book.getCharacterBible()).contains("a brown jalabiya and a white keffiyeh").doesNotContain("navy suit");
        assertThat(grandpa.getClothing()).isEqualTo("a brown jalabiya and a white keffiyeh");
    }
}

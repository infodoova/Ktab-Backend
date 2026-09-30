package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.CharacterBibleResponse;
import com.doova.ktab.features.storybook.story.CharacterVisualSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CharacterBibleHandler implements StepHandler {

    private final StorybookRepository books;
    private final StorybookCharacterRepository characters;
    private final LlmGateway llm;
    private final PromptLibrary prompts;
    private final AiCallLedger ledger;
    private final JobEnqueuer enqueuer;
    private final ObjectMapper objectMapper;

    @Override
    public JobStep step() {
        return JobStep.CHARACTER_BIBLE;
    }

    @Override
    @Transactional
    public StepOutcome handle(StorybookJob job) {
        Long bookId = job.getStorybookId();
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getCharacterBible() != null && !book.getCharacterBible().isBlank()) {
            enqueuer.enqueue(bookId, JobStep.STORY_BLUEPRINT, -1, job.getGeneration());
            return StepOutcome.success();
        }

        List<StorybookCharacter> charList = characters.findByStorybook_Id(bookId);
        String userPrompt = buildUserMessage(book, charList);

        LlmCall<CharacterBibleResponse> call = llm.call(LlmRequest.of(
                LlmPurpose.CHARACTER_BIBLE,
                prompts.get("character-bible-system"),
                userPrompt,
                CharacterBibleResponse.class
        ));
        ledger.recordLlm(bookId, job.getId(), LlmPurpose.CHARACTER_BIBLE, call);

        CharacterBibleResponse bible = call.value();
        try {
            book.setCharacterBible(objectMapper.writeValueAsString(bible));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize character bible to JSON", e);
            book.setCharacterBible(bible.toString());
        }

        if (bible.characters() != null) {
            for (CharacterVisualSpec spec : bible.characters()) {
                for (StorybookCharacter c : charList) {
                    if (matches(c, spec)) {
                        if (spec.clothing() != null && !spec.clothing().isBlank()) {
                            c.setClothing(spec.clothing());
                        }
                        if (spec.personality() != null && !spec.personality().isBlank()) {
                            c.setPersonality(List.of(spec.personality()));
                        }
                    }
                }
            }
        }

        books.save(book);
        characters.saveAll(charList);
        enqueuer.enqueue(bookId, JobStep.STORY_BLUEPRINT, -1, job.getGeneration());
        return StepOutcome.success();
    }

    private static boolean matches(StorybookCharacter c, CharacterVisualSpec spec) {
        if (c.getKind() == CharacterKind.CHILD && ("PROTAGONIST".equalsIgnoreCase(spec.role()) || "CHILD".equalsIgnoreCase(spec.role()))) {
            return true;
        }
        if (c.getKind() == CharacterKind.COMPANION && "COMPANION".equalsIgnoreCase(spec.role())) {
            return true;
        }
        if (c.getCharacterId() != null && c.getCharacterId().equalsIgnoreCase(spec.name())) {
            return true;
        }
        if (c.getRole() != null && c.getRole().equalsIgnoreCase(spec.role())) {
            return true;
        }
        return false;
    }

    private static String buildUserMessage(Storybook book, List<StorybookCharacter> charList) {
        StringBuilder sb = new StringBuilder();
        sb.append("Child Details:\n");
        sb.append("- Name: ").append(book.getInputs().childNameAr()).append("\n");
        sb.append("- Gender: ").append(book.getInputs().gender().en()).append("\n");
        sb.append("- Age: ").append(book.getInputs().ageBand().minAge()).append("-")
                .append(book.getInputs().ageBand().maxAge()).append(" years old\n");
        if (book.getInputs().appearance() != null) {
            sb.append("- Appearance: ").append(book.getInputs().appearance().describeEn()).append("\n");
        }
        if (book.getInputs().companion() != null) {
            sb.append("\nCompanion:\n");
            sb.append("- Name: ").append(book.getInputs().companion().nameAr()).append("\n");
            sb.append("- Description: ").append(book.getInputs().companion().describeEn()).append("\n");
        }
        if (charList != null && !charList.isEmpty()) {
            sb.append("\nAdditional Characters:\n");
            for (StorybookCharacter c : charList) {
                if (c.getKind() != CharacterKind.CHILD && c.getKind() != CharacterKind.COMPANION) {
                    sb.append("- Character: ").append(c.getCharacterId() != null ? c.getCharacterId() : c.getKind().name())
                            .append(", Role: ").append(c.getRole())
                            .append(", Relationship: ").append(c.getRelationship())
                            .append(", Personality: ").append(c.getPersonality() != null ? String.join(", ", c.getPersonality()) : "")
                            .append("\n");
                }
            }
        }
        sb.append("\nEstablish locked clothing (modest, long sleeves, bright everyday colors), facial features, and style notes.");
        return sb.toString();
    }
}

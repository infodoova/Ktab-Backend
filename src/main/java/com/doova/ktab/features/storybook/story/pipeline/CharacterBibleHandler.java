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

        CharacterBibleResponse bible = keepTheParentsOutfits(call.value(), charList);
        if (bible == null || bible.characters() == null || bible.characters().isEmpty()) {
            throw new com.doova.ktab.features.storybook.llm.LlmCallFailedException("Character bible has no characters", true, null);
        }
        try {
            book.setCharacterBible(objectMapper.writeValueAsString(bible));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize character bible to JSON", e);
            book.setCharacterBible(bible.toString());
        }

        // the art direction is written once and appended to every page prompt
        book.setStyleBible(com.doova.ktab.features.storybook.illustration.StyleBible.toJson(bible.visualStyleNotes()));

        if (bible.characters() != null) {
            for (CharacterVisualSpec spec : bible.characters()) {
                for (StorybookCharacter c : charList) {
                    if (matches(c, spec)) {
                        // the clothes the parent chose are never overwritten; the bible only fills a missing outfit
                        if (spec.clothing() != null && !spec.clothing().isBlank()
                                && (c.getClothing() == null || c.getClothing().isBlank())) {
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

    /** The name the parent gave a character (Arabic, usually), else its id. */
    private static String nameOf(StorybookCharacter c) {
        Object n = c.getAdvancedDetails() == null ? null : c.getAdvancedDetails().get("name");
        return n instanceof String str && !str.isBlank() ? str.strip() : c.getCharacterId();
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
        if (c.getKind() == CharacterKind.SUPPORTING && spec.name() != null && nameOf(c) != null && nameOf(c).equalsIgnoreCase(spec.name().strip())) {
            return true;
        }
        if (c.getRole() != null && c.getRole().equalsIgnoreCase(spec.role())) {
            return true;
        }
        return false;
    }

    /** The bible is fed to the story writer, so it must never carry an outfit other than the one the parent chose. */
    private CharacterBibleResponse keepTheParentsOutfits(CharacterBibleResponse bible, List<StorybookCharacter> charList) {
        if (bible == null || bible.characters() == null || charList == null) {
            return bible;
        }
        List<CharacterVisualSpec> fixed = new java.util.ArrayList<>();
        for (CharacterVisualSpec spec : bible.characters()) {
            CharacterVisualSpec out = spec;
            for (StorybookCharacter c : charList) {
                if (matches(c, spec) && c.getClothing() != null && !c.getClothing().isBlank()) {
                    out = new CharacterVisualSpec(spec.name(), spec.role(), spec.visualLock(), c.getClothing().strip(), spec.personality());
                    break;
                }
            }
            fixed.add(out);
        }
        return new CharacterBibleResponse(fixed, bible.visualStyleNotes(), bible.summary());
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
                    sb.append("- Character: ").append(nameOf(c) != null ? nameOf(c) : c.getKind().name())
                            .append(", Role: ").append(c.getRole())
                            .append(", Relationship: ").append(c.getRelationship())
                            .append(", Personality: ").append(c.getPersonality() != null ? String.join(", ", c.getPersonality()) : "")
                            .append("\n");
                }
            }
        }
        for (StorybookCharacter c : charList == null ? List.<StorybookCharacter>of() : charList) {
            if (c.getClothing() != null && !c.getClothing().isBlank()) {
                sb.append("\nFixed outfit chosen by the parent for ").append(c.getKind() == CharacterKind.COMPANION ? "the companion" : c.getKind() == CharacterKind.SUPPORTING ? "the character " + nameOf(c) : "the child")
                        .append(" (use it exactly, do not change it and do not invent another): ").append(c.getClothing().strip()).append('\n');
            }
        }
        sb.append("\nEstablish locked clothing, facial features, and style notes. Where a fixed outfit is given above, keep it exactly; "
                + "only for characters without one, choose modest, long-sleeved, bright everyday clothes.");
        return sb.toString();
    }
}

package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmCallFailedException;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.StoryBlueprintResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoryBlueprintHandler implements StepHandler {

    private final StorybookRepository books;
    private final LlmGateway llm;
    private final PromptLibrary prompts;
    private final AiCallLedger ledger;
    private final JobEnqueuer enqueuer;
    private final ObjectMapper objectMapper;

    @Override
    public JobStep step() {
        return JobStep.STORY_BLUEPRINT;
    }

    @Override
    @Transactional
    public StepOutcome handle(StorybookJob job) {
        Long bookId = job.getStorybookId();
        Storybook book = books.findById(bookId).orElseThrow();
        if (hasUsableBlueprint(book.getStoryBlueprint())) {
            enqueuer.enqueue(bookId, JobStep.STORY_PLAN, -1, job.getGeneration());
            return StepOutcome.success();
        }

        String userPrompt = buildUserMessage(book);
        LlmCall<StoryBlueprintResponse> call = llm.call(LlmRequest.of(
                LlmPurpose.STORY_BLUEPRINT,
                prompts.get("story-blueprint-system"),
                userPrompt,
                StoryBlueprintResponse.class
        ));
        ledger.recordLlm(bookId, job.getId(), LlmPurpose.STORY_BLUEPRINT, call);

        StoryBlueprintResponse blueprint = call.value();
        // A blueprint the writer cannot follow is worse than none: the story would ignore the user's idea. Retry instead.
        if (blueprint == null || blueprint.beats() == null || blueprint.beats().size() != book.getPageCount()) {
            int got = blueprint == null || blueprint.beats() == null ? 0 : blueprint.beats().size();
            throw new LlmCallFailedException("Story blueprint has " + got + " beats, expected " + book.getPageCount(), true, null);
        }
        try {
            book.setStoryBlueprint(objectMapper.writeValueAsString(blueprint));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize story blueprint to JSON", e);
            book.setStoryBlueprint(blueprint.toString());
        }

        books.save(book);
        enqueuer.enqueue(bookId, JobStep.STORY_PLAN, -1, job.getGeneration());
        return StepOutcome.success();
    }

    /** A stored blueprint only counts if it holds beats; an earlier run could store an unreadable, all-null one. */
    private boolean hasUsableBlueprint(String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        try {
            StoryBlueprintResponse parsed = objectMapper.readValue(stored, StoryBlueprintResponse.class);
            return parsed.beats() != null && !parsed.beats().isEmpty();
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String buildUserMessage(Storybook book) {
        StringBuilder sb = new StringBuilder();
        sb.append("Child Details:\n");
        sb.append("- Name: «").append(book.getInputs().childNameAr()).append("»\n");
        sb.append("- Gender: ").append(book.getInputs().gender().en()).append("\n");
        sb.append("- Target Age: ").append(book.getInputs().ageBand().minAge()).append("-")
                .append(book.getInputs().ageBand().maxAge()).append(" years old\n");
        sb.append("- Page Count: ").append(book.getPageCount()).append(" pages\n");

        if (book.getTheme() != null && !book.getTheme().isBlank()) {
            sb.append("- Theme: ").append(book.getTheme()).append("\n");
        }
        if (book.getStoryTone() != null && !book.getStoryTone().isBlank()) {
            sb.append("- Story Tone: ").append(book.getStoryTone()).append("\n");
        }
        if (book.getLesson() != null && !book.getLesson().isBlank()) {
            sb.append("- Moral Lesson: ").append(book.getLesson()).append("\n");
        }
        if (book.getStoryIdea() != null && !book.getStoryIdea().isBlank()) {
            sb.append("- Core Story Idea: ").append(book.getStoryIdea()).append("\n");
        }
        if (book.getThingsToAvoid() != null && !book.getThingsToAvoid().isEmpty()) {
            sb.append("- Things to Avoid: ").append(String.join(", ", book.getThingsToAvoid())).append("\n");
        }
        if (book.getCharacterBible() != null && !book.getCharacterBible().isBlank()) {
            sb.append("\nCharacter Bible:\n").append(book.getCharacterBible()).append("\n");
        }

        sb.append("\nGenerate exactly ").append(book.getPageCount())
                .append(" narrative beats numbered 1 to ").append(book.getPageCount())
                .append(" following the classic 6-phase children's arc.");
        return sb.toString();
    }
}

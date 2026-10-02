package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoryWriter {

    private final LlmGateway llm;
    private final PromptLibrary prompts;

    public LlmCall<StoryPlanResponse> writePlan(StoryRequest r) {
        return writePlan(r, null, null);
    }

    public LlmCall<StoryPlanResponse> writePlan(StoryRequest r, String characterBible, String storyBlueprint) {
        return writePlan(r, characterBible, storyBlueprint, null);
    }

    public LlmCall<StoryPlanResponse> writePlan(StoryRequest r, String characterBible, String storyBlueprint, String brief) {
        String user = StoryPromptBuilder.planUserMessage(r, prompts.dialectGuide(r.variety()), characterBible, storyBlueprint, brief);
        LlmCall<StoryPlanResponse> call = llm.call(LlmRequest.of(LlmPurpose.STORY_PLAN,
                prompts.get("story-plan-system"), user, StoryPlanResponse.class));
        String defaultTitle = "مغامرة " + r.childNameAr();
        String defaultCover = "Cover illustration showing " + r.childNameAr() + " with a calm top third for title";
        StoryPlanResponse normalized = call.value() == null ? null : call.value().normalized(defaultTitle, defaultCover);
        validate(normalized, r.pageCount());
        return new LlmCall<>(normalized, call.model(), call.inputTokens(), call.outputTokens(), call.latencyMs());
    }

    public LlmCall<PagePlan> rewritePage(StoryRequest r, PagePlan page, List<String> problems) {
        return rewritePage(r, page, problems, null);
    }

    public LlmCall<PagePlan> rewritePage(StoryRequest r, PagePlan page, List<String> problems, RewriteContext context) {
        String user = StoryPromptBuilder.rewriteUserMessage(r, prompts.dialectGuide(r.variety()), page, problems, context);
        LlmCall<PagePlan> call = llm.call(LlmRequest.of(LlmPurpose.STORY_PAGE_REWRITE,
                prompts.get("page-rewrite-system"), user, PagePlan.class));
        PagePlan v = call.value() == null ? null : call.value().normalized(page.pageNumber());
        if (v == null || v.textAr() == null || v.textAr().isBlank()) {
            throw new StoryPlanInvalidException("Rewrite of page " + page.pageNumber() + " returned empty text");
        }
        // The page number is ours, not the model's.
        PagePlan fixed = new PagePlan(page.pageNumber(), v.textAr(), v.sceneEn(), v.characters(), v.textZone());
        return new LlmCall<>(fixed, call.model(), call.inputTokens(), call.outputTokens(), call.latencyMs());
    }

    public LlmCall<TitleRewriteResponse> rewriteTitle(StoryRequest r, String currentTitle, List<String> problems, RewriteContext context) {
        String user = StoryPromptBuilder.titleRewriteUserMessage(r, prompts.dialectGuide(r.variety()), currentTitle, problems, context);
        return llm.call(LlmRequest.of(LlmPurpose.STORY_PAGE_REWRITE, prompts.get("title-rewrite-system"), user, TitleRewriteResponse.class));
    }

    private static void validate(StoryPlanResponse plan, int pageCount) {
        if (plan.titleAr() == null || plan.titleAr().isBlank()) {
            throw new StoryPlanInvalidException("Story plan has no title");
        }
        if (plan.pages() == null || plan.pages().size() != pageCount) {
            throw new StoryPlanInvalidException("Story plan has " + (plan.pages() == null ? 0 : plan.pages().size())
                    + " pages, expected " + pageCount);
        }
        for (int i = 0; i < pageCount; i++) {
            PagePlan p = plan.pages().get(i);
            if (p.pageNumber() != i + 1 || p.textAr() == null || p.textAr().isBlank()
                    || p.sceneEn() == null || p.sceneEn().isBlank() || p.textZone() == null) {
                throw new StoryPlanInvalidException("Story plan page at position " + (i + 1) + " is malformed");
            }
        }
    }
}

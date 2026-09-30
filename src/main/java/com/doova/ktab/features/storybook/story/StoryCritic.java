package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class StoryCritic {

    private final LlmGateway llm;
    private final PromptLibrary prompts;

    public CriticReport review(StoryRequest r, StoryPlanResponse plan) {
        StoryPlanResponse enforced = new StoryPlanResponse(
                NameEnforcer.enforce(plan.titleAr(), r.childNameAr()),
                plan.coverSceneEn(),
                plan.pages().stream()
                        .map(p -> p.withText(NameEnforcer.enforce(p.textAr(), r.childNameAr())))
                        .toList());

        Map<Integer, List<String>> problems = new TreeMap<>();
        for (PagePlan page : enforced.pages()) {
            List<String> found = DeterministicTextChecks.check(r, page);
            if (!found.isEmpty()) {
                problems.put(page.pageNumber(), new ArrayList<>(found));
            }
        }

        LlmCall<CriticResponse> call = llm.call(LlmRequest.of(LlmPurpose.STORY_CRITIC,
                prompts.get("critic-system"), userMessage(r, enforced), CriticResponse.class));

        Map<Integer, PageVerdict> verdicts = new HashMap<>();
        if (call.value().pages() != null) {
            call.value().pages().forEach(v -> verdicts.put(v.pageNumber(), v));
        }
        for (int n = 0; n <= enforced.pages().size(); n++) {
            PageVerdict v = verdicts.get(n);
            if (v != null && !v.pass()) {
                List<String> list = problems.computeIfAbsent(n, k -> new ArrayList<>());
                list.addAll(v.problems() == null || v.problems().isEmpty()
                        ? List.of("The editor flagged an issue on this page") : v.problems());
            }
        }
        return new CriticReport(enforced, problems, call);
    }

    private String userMessage(StoryRequest r, StoryPlanResponse plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("The child is a ").append(r.gender().en()).append(" aged ")
                .append(r.ageBand().minAge()).append(" to ").append(r.ageBand().maxAge())
                .append(", named «").append(r.childNameAr()).append("».\n");
        if (r.variety().isDialect()) {
            sb.append("The book is written in ").append(r.variety().en())
                    .append(" and must follow this style guide:\n\n").append(prompts.dialectGuide(r.variety())).append("\n\n");
        } else {
            sb.append("The book is written in fully vocalized Modern Standard Arabic.\n\n");
        }
        sb.append("Page 0 (title): ").append(plan.titleAr()).append('\n');
        for (PagePlan p : plan.pages()) {
            sb.append("Page ").append(p.pageNumber()).append(": ").append(p.textAr()).append('\n');
        }
        return sb.toString();
    }
}

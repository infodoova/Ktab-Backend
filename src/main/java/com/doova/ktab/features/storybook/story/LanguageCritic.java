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
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LanguageCritic {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9\\u0660-\\u0669]");

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
            List<String> found = checkPageLanguage(r, page);
            if (!found.isEmpty()) {
                problems.put(page.pageNumber(), new ArrayList<>(found));
            }
        }

        LlmCall<CriticResponse> call = llm.call(LlmRequest.of(LlmPurpose.LANGUAGE_CRITIC,
                prompts.get("language-critic-system"), userMessage(r, enforced), CriticResponse.class));

        Map<Integer, PageVerdict> verdicts = new HashMap<>();
        if (call.value().pages() != null) {
            call.value().pages().forEach(v -> verdicts.put(v.pageNumber(), v));
        }
        for (int n = 0; n <= enforced.pages().size(); n++) {
            PageVerdict v = verdicts.get(n);
            if (v != null && !v.pass()) {
                List<String> list = problems.computeIfAbsent(n, k -> new ArrayList<>());
                list.addAll(v.problems() == null || v.problems().isEmpty()
                        ? List.of("Language critic flagged this page") : v.problems());
            }
        }
        return new CriticReport(enforced, problems, call);
    }

    private static List<String> checkPageLanguage(StoryRequest r, PagePlan page) {
        List<String> list = new ArrayList<>();
        String text = page.textAr() == null ? "" : page.textAr();
        if (DIGIT_PATTERN.matcher(text).find()) {
            list.add("Page " + page.pageNumber() + " contains digits; all numbers must be written as Arabic words");
        }
        if (ArabicText.hasLatinLetters(text)) {
            list.add("Page " + page.pageNumber() + " contains Latin letters");
        }
        if (r.variety().isDialect() && ArabicText.containsTashkeel(text)) {
            list.add("Page " + page.pageNumber() + " contains tashkeel; dialect must have no tashkeel");
        }
        return list;
    }

    private String userMessage(StoryRequest r, StoryPlanResponse plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language Variety: ").append(r.variety().en()).append("\n");
        sb.append("Target Age: ").append(r.ageBand().minAge()).append(" to ")
                .append(r.ageBand().maxAge()).append(" years old\n");
        if (r.variety().isDialect()) {
            sb.append("\nDialect Style Guide:\n").append(prompts.dialectGuide(r.variety())).append("\n\n");
        } else {
            sb.append("Must be grammatically vocalized Modern Standard Arabic with full tashkeel.\n\n");
        }
        sb.append("Page 0 (title): ").append(plan.titleAr()).append("\n");
        for (PagePlan p : plan.pages()) {
            sb.append("Page ").append(p.pageNumber()).append(": ").append(p.textAr()).append("\n");
        }
        return sb.toString();
    }
}

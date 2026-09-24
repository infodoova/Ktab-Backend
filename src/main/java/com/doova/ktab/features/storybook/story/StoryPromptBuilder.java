package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.blueprint.BlueprintBeat;
import com.doova.ktab.features.storybook.enums.Interest;

import java.util.List;
import java.util.stream.Collectors;

/** Builds the per-book user messages. The system prompts stay static so they can be cached. */
public final class StoryPromptBuilder {

    private StoryPromptBuilder() {
    }

    public static String planUserMessage(StoryRequest r, String dialectGuide) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        sb.append("\nBlueprint: ").append(r.blueprint().titleEn())
                .append(" (").append(r.pageCount()).append(" pages, one beat per page)\n");
        List<BlueprintBeat> beats = r.blueprint().beatsFor(r.pageCount());
        for (int i = 0; i < beats.size(); i++) {
            sb.append("Page ").append(i + 1).append(": ").append(beats.get(i).beat()).append('\n');
        }
        sb.append("\nReturn exactly ").append(r.pageCount())
                .append(" pages numbered 1 to ").append(r.pageCount()).append(", plus the title and cover scene.");
        return sb.toString();
    }

    public static String rewriteUserMessage(StoryRequest r, String dialectGuide, PagePlan page, List<String> problems) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        sb.append("\nPage ").append(page.pageNumber()).append(" currently reads:\n").append(page.textAr()).append('\n');
        sb.append("Its scene: ").append(page.sceneEn()).append('\n');
        sb.append("\nProblems the editor found:\n");
        problems.forEach(p -> sb.append("- ").append(p).append('\n'));
        return sb.toString();
    }

    private static void appendChildAndLanguage(StringBuilder sb, StoryRequest r, String dialectGuide) {
        sb.append("The child\n");
        sb.append("- Name (write exactly like this): «").append(r.childNameAr()).append("»\n");
        sb.append("- A ").append(r.gender().en()).append(" aged ")
                .append(r.ageBand().minAge()).append(" to ").append(r.ageBand().maxAge()).append('\n');
        if (!r.interests().isEmpty()) {
            sb.append("- Loves: ").append(r.interests().stream().map(Interest::en)
                    .collect(Collectors.joining(", "))).append('\n');
        }
        if (r.setting() != null) {
            sb.append("- Setting: ").append(r.setting().sceneEn()).append('\n');
        }
        if (r.companion() != null) {
            sb.append("- Companion (COMPANION in scenes), named «").append(r.companion().nameAr()).append("»: ")
                    .append(r.companion().describeEn()).append('\n');
        }
        sb.append("\nLanguage\n");
        if (r.variety().isDialect()) {
            sb.append("- Write in ").append(r.variety().en())
                    .append(" with no tashkeel at all, following this style guide exactly:\n\n")
                    .append(dialectGuide).append("\n\n");
        } else {
            sb.append("- Write in Modern Standard Arabic, fully vocalized.\n");
        }
        sb.append("- Each page: 1 to 4 sentences, at most ").append(r.ageBand().maxWordsPerPage()).append(" words.\n");
    }
}

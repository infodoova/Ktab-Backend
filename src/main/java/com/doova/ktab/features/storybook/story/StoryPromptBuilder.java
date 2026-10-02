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
        return planUserMessage(r, dialectGuide, null, null);
    }

    public static String planUserMessage(StoryRequest r, String dialectGuide, String characterBible, String storyBlueprint) {
        return planUserMessage(r, dialectGuide, characterBible, storyBlueprint, null);
    }

    /** The user's own brief (idea, lesson, theme, tone, things to avoid) reaches the writer directly, not only through the blueprint. */
    public static String brief(String theme, String tone, String lesson, String idea, List<String> thingsToAvoid) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Story idea (the story must be about this)", idea);
        appendIfPresent(sb, "Moral lesson, shown through events and never preached", lesson);
        appendIfPresent(sb, "Theme", theme);
        appendIfPresent(sb, "Tone", tone);
        if (thingsToAvoid != null && !thingsToAvoid.isEmpty()) {
            sb.append("- Never include: ").append(String.join(", ", thingsToAvoid)).append('\n');
        }
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    public static String planUserMessage(StoryRequest r, String dialectGuide, String characterBible, String storyBlueprint, String brief) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        if (brief != null && !brief.isBlank()) {
            sb.append("\nThe parent's brief:\n").append(brief);
        }

        if (characterBible != null && !characterBible.isBlank()) {
            sb.append("\nCharacter Bible (visual locks & personalities):\n").append(characterBible).append("\n");
        }

        if (storyBlueprint != null && !storyBlueprint.isBlank()) {
            sb.append("\nStory Blueprint (follow these beats exactly):\n").append(storyBlueprint).append("\n");
        } else if (r.blueprint() != null && !r.blueprint().beats().isEmpty()) {
            sb.append("\nBlueprint: ").append(r.blueprint().titleEn())
                    .append(" (").append(r.pageCount()).append(" pages, one beat per page)\n");
            List<BlueprintBeat> beats = r.blueprint().beatsFor(r.pageCount());
            for (int i = 0; i < beats.size(); i++) {
                sb.append("Page ").append(i + 1).append(": ").append(beats.get(i).beat()).append('\n');
            }
        }
        sb.append("\nReturn exactly ").append(r.pageCount())
                .append(" pages numbered 1 to ").append(r.pageCount())
                .append(", plus the title and cover scene. Reserve the bottom 20% text zone for each story page.");
        return sb.toString();
    }

    public static String rewriteUserMessage(StoryRequest r, String dialectGuide, PagePlan page, List<String> problems) {
        return rewriteUserMessage(r, dialectGuide, page, problems, null);
    }

    public static String rewriteUserMessage(StoryRequest r, String dialectGuide, PagePlan page, List<String> problems, RewriteContext context) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        if (context != null) {
            if (!context.cast().isEmpty()) {
                sb.append("\nEveryone who belongs in this story: ").append(String.join(", ", context.cast()))
                        .append(". Do not add any person or animal who is not in this list; keep the people already on the page.\n");
            }
            if (context.beat() != null) {
                sb.append("What must happen on this page (blueprint): ").append(context.beat()).append('\n');
            }
            if (context.previousText() != null) {
                sb.append("The previous page reads: ").append(context.previousText()).append('\n');
            }
            if (context.nextText() != null) {
                sb.append("The next page reads: ").append(context.nextText()).append('\n');
            }
        }
        sb.append("\nPage ").append(page.pageNumber()).append(" currently reads:\n").append(page.textAr()).append('\n');
        sb.append("Its scene: ").append(page.sceneEn()).append('\n');
        sb.append("\nProblems the editor found:\n");
        problems.forEach(p -> sb.append("- ").append(p).append('\n'));
        return sb.toString();
    }

    public static String titleRewriteUserMessage(StoryRequest r, String dialectGuide, String currentTitle, List<String> problems, RewriteContext context) {
        StringBuilder sb = new StringBuilder();
        appendChildAndLanguage(sb, r, dialectGuide);
        sb.append("\nThe book title currently reads: ").append(currentTitle).append('\n');
        if (context != null && context.nextText() != null) {
            sb.append("The first page of the story reads: ").append(context.nextText()).append('\n');
        }
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
        int maxWords = r.ageBand().maxWordsPerPage();
        sb.append("- Each page: 1 to 4 sentences; aim for ").append(maxWords * 6 / 10).append(" to ").append(maxWords)
                .append(" words, and at most ").append(maxWords).append(" words.\n");
    }
}

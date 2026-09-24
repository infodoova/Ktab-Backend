package com.doova.ktab.features.storybook.story;

import java.util.ArrayList;
import java.util.List;

/** The checks that do not need an LLM. The critic (Task 10) runs these first. */
public final class DeterministicTextChecks {

    private DeterministicTextChecks() {
    }

    public static List<String> check(StoryRequest r, PagePlan page) {
        List<String> problems = new ArrayList<>();
        String text = page.textAr() == null ? "" : page.textAr();
        if (text.isBlank()) {
            problems.add("Page " + page.pageNumber() + " has no text");
            return problems;
        }
        int words = ArabicText.wordCount(text);
        if (words > r.ageBand().maxWordsPerPage()) {
            problems.add("Page " + page.pageNumber() + " has " + words + " words; the limit for ages "
                    + r.ageBand().minAge() + "-" + r.ageBand().maxAge() + " is " + r.ageBand().maxWordsPerPage());
        }
        int sentences = ArabicText.sentenceCount(text);
        if (sentences < 1 || sentences > 4) {
            problems.add("Page " + page.pageNumber() + " has " + sentences + " sentences; it must have 1 to 4");
        }
        if (ArabicText.hasLatinLetters(text)) {
            problems.add("Page " + page.pageNumber() + " contains Latin letters");
        }
        if (r.variety().isDialect() && ArabicText.containsTashkeel(text)) {
            problems.add("Page " + page.pageNumber() + " is in " + r.variety().en()
                    + " but contains tashkeel; dialect text must have none");
        }
        return problems;
    }
}

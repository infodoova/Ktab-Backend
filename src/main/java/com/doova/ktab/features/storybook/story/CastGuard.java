package com.doova.ktab.features.storybook.story;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches a story plan that draws a character the book does not have. The picture prompts only describe the child, the
 * companion when there is one, and the listed supporting characters, so any other COMPANION or SUPPORT_n in a scene
 * reaches the picture model as a bare word and comes out as a stranger (a second child, most often).
 */
public final class CastGuard {

    private static final Pattern TAG = Pattern.compile("\\b(COMPANION|SUPPORT_\\d+)\\b");

    private CastGuard() {
    }

    /** The tags the plan uses that this book has no character for, in order. Empty when the plan stays inside the cast. */
    public static Set<String> unknownTags(StoryPlanResponse plan, boolean hasCompanion, Collection<String> supportingRefs) {
        Set<String> found = new TreeSet<>();
        if (plan == null) {
            return found;
        }
        collect(plan.coverSceneEn(), found);
        if (plan.pages() != null) {
            for (PagePlan page : plan.pages()) {
                collect(page.sceneEn(), found);
                if (page.characters() != null) {
                    page.characters().forEach(c -> collect(c.ref(), found));
                }
            }
        }
        found.removeIf(tag -> "COMPANION".equals(tag) ? hasCompanion : supportingRefs != null && supportingRefs.contains(tag));
        return found;
    }

    private static void collect(String text, Set<String> into) {
        if (text == null) {
            return;
        }
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            into.add(m.group(1));
        }
    }
}

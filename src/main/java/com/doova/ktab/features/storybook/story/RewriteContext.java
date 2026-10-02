package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * What a rewriter needs to know about a page it fixes in isolation: the blueprint beat for the page, the pages on either
 * side, and everyone who belongs in the story. Without it a rewrite invents people (a mother appears) and drops others.
 */
public record RewriteContext(String beat, String previousText, String nextText, List<String> cast) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RewriteContext {
        cast = cast == null ? List.of() : List.copyOf(cast);
    }

    public static RewriteContext of(String blueprintJson, StoryPlanResponse plan, int pageNumber) {
        String beat = null;
        LinkedHashSet<String> cast = new LinkedHashSet<>();
        if (blueprintJson != null && !blueprintJson.isBlank()) {
            try {
                StoryBlueprintResponse blueprint = MAPPER.readValue(blueprintJson, StoryBlueprintResponse.class);
                if (blueprint.beats() != null) {
                    for (BlueprintPageBeat b : blueprint.beats()) {
                        if (b.pageNumber() == pageNumber) {
                            beat = b.beat();
                        }
                        if (b.characters() != null) {
                            cast.addAll(b.characters());
                        }
                    }
                }
            } catch (Exception ignored) {
                // an unreadable blueprint just means less context
            }
        }
        String previous = null;
        String next = null;
        if (plan != null && plan.pages() != null) {
            for (PagePlan p : plan.pages()) {
                if (p.pageNumber() == pageNumber - 1) {
                    previous = p.textAr();
                } else if (p.pageNumber() == pageNumber + 1) {
                    next = p.textAr();
                }
            }
        }
        return new RewriteContext(beat, previous, next, List.copyOf(cast));
    }
}

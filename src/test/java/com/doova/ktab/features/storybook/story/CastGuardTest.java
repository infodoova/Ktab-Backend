package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TextZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CastGuardTest {

    private static PagePlan page(int n, String scene, String... refs) {
        return new PagePlan(n, "نص", scene, java.util.Arrays.stream(refs).map(r -> new CharacterInScene(r, "happy")).toList(),
                TextZone.BOTTOM);
    }

    private static StoryPlanResponse plan(String cover, PagePlan... pages) {
        return new StoryPlanResponse("عنوان", cover, List.of(pages));
    }

    @Test
    void aPlanThatStaysOnTheChildPasses() {
        StoryPlanResponse p = plan("CHILD on a forest path.", page(1, "CHILD picks a leaf.", "CHILD"));

        assertThat(CastGuard.unknownTags(p, false, List.of())).isEmpty();
    }

    @Test
    void aCompanionTheBookDoesNotHaveIsCaughtInSceneTextAndInTheCastList() {
        StoryPlanResponse p = plan("CHILD and COMPANION stand together.",
                page(1, "CHILD walks.", "CHILD", "COMPANION"));

        assertThat(CastGuard.unknownTags(p, false, List.of())).containsExactly("COMPANION");
    }

    @Test
    void aCompanionTheBookHasIsAllowed() {
        StoryPlanResponse p = plan("CHILD and COMPANION stand together.", page(1, "CHILD and COMPANION walk.", "CHILD", "COMPANION"));

        assertThat(CastGuard.unknownTags(p, true, List.of())).isEmpty();
    }

    @Test
    void supportingTagsMustBeOnesTheBookListed() {
        StoryPlanResponse p = plan("CHILD stands.",
                page(1, "SUPPORT_1 waves to CHILD.", "CHILD", "SUPPORT_1"),
                page(2, "SUPPORT_2 sings.", "SUPPORT_2"));

        assertThat(CastGuard.unknownTags(p, false, List.of("SUPPORT_1"))).containsExactly("SUPPORT_2");
    }

    @Test
    void aWordThatOnlyContainsTheTagIsNotATag() {
        StoryPlanResponse p = plan("CHILD stands.", page(1, "A COMPANIONABLE breeze moves the leaves.", "CHILD"));

        assertThat(CastGuard.unknownTags(p, false, List.of())).isEmpty();
    }
}

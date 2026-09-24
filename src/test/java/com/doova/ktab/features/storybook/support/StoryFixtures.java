package com.doova.ktab.features.storybook.support;

import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.IntStream;

public final class StoryFixtures {

    public static final BlueprintCatalog CATALOG = new BlueprintCatalog(new ObjectMapper());

    public static final ChildAppearance APPEARANCE = new ChildAppearance(
            ChildAppearance.SkinTone.OLIVE, ChildAppearance.HairColor.BLACK,
            ChildAppearance.HairStyle.SHORT_CURLY, ChildAppearance.EyeColor.BROWN, false, false);

    private StoryFixtures() {
    }

    public static StoryRequest request(LanguageVariety variety, ChildGender gender, int pageCount) {
        return new StoryRequest("سامي", gender, AgeBand.AGE_6_8, APPEARANCE, variety,
                CATALOG.get("first-day-of-school"), pageCount, List.of(Interest.FOOTBALL), null, StorySetting.BEIRUT);
    }

    public static StoryPlanResponse plan(int pageCount, String textPerPage) {
        List<PagePlan> pages = IntStream.rangeClosed(1, pageCount)
                .mapToObj(n -> new PagePlan(n, textPerPage, "The CHILD smiles in a sunny classroom.",
                        List.of(new CharacterInScene("CHILD", "happy")), TextZone.TOP))
                .toList();
        return new StoryPlanResponse("يومي الأول", "The CHILD at the school gate.", pages);
    }
}

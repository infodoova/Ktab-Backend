package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.StoryTime;

import java.util.List;
import java.util.Objects;

public record StoryRequest(
        String childNameAr,
        ChildGender gender,
        AgeBand ageBand,
        ChildAppearance appearance,
        LanguageVariety variety,
        Blueprint blueprint,
        int pageCount,
        List<Interest> interests,
        CompanionSpec companion,
        StorySetting setting,
        StoryTime timeOfDay,
        String place
) {
    public StoryRequest {
        Objects.requireNonNull(childNameAr, "childNameAr");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(ageBand, "ageBand");
        Objects.requireNonNull(variety, "variety");
        Objects.requireNonNull(blueprint, "blueprint");
        interests = interests == null ? List.of() : List.copyOf(interests);
    }

    public StoryRequest(
            String childNameAr,
            ChildGender gender,
            AgeBand ageBand,
            ChildAppearance appearance,
            LanguageVariety variety,
            Blueprint blueprint,
            int pageCount,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting
    ) {
        this(childNameAr, gender, ageBand, appearance, variety, blueprint, pageCount,
                interests, companion, setting, null, null);
    }
}

package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.StoryTime;
import com.doova.ktab.features.storybook.story.StoryRequest;

import java.util.List;

/**
 * Everything the story was generated from, frozen when the book was created: later edits to
 * the child profile or a new blueprint version never change an existing book (decision D1).
 */
public record StoryInputs(
        String childNameAr,
        ChildGender gender,
        AgeBand ageBand,
        ChildAppearance appearance,
        List<Interest> interests,
        CompanionSpec companion,
        StorySetting setting,
        StoryTime timeOfDay,
        String place,
        Blueprint blueprint
) {
    public StoryInputs(
            String childNameAr,
            ChildGender gender,
            AgeBand ageBand,
            ChildAppearance appearance,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            Blueprint blueprint
    ) {
        this(childNameAr, gender, ageBand, appearance, interests, companion, setting, null, null, blueprint);
    }

    public StoryRequest toStoryRequest(LanguageVariety variety, int pageCount) {
        return new StoryRequest(childNameAr, gender, ageBand, appearance, variety, blueprint, pageCount,
                interests, companion, setting, timeOfDay, place);
    }
}

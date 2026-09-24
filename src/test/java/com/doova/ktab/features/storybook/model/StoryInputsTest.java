package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.story.StoryRequest;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryInputsTest {

    @Test
    void buildsAStoryRequestFromTheSnapshot() {
        StoryInputs inputs = new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE,
                List.of(Interest.CATS), null, StorySetting.CAIRO, StoryFixtures.CATALOG.get("first-day-of-school"));

        StoryRequest r = inputs.toStoryRequest(LanguageVariety.GULF, 12);

        assertThat(r.childNameAr()).isEqualTo("سامي");
        assertThat(r.variety()).isEqualTo(LanguageVariety.GULF);
        assertThat(r.pageCount()).isEqualTo(12);
        assertThat(r.blueprint().key()).isEqualTo("first-day-of-school");
        assertThat(r.setting()).isEqualTo(StorySetting.CAIRO);
    }
}

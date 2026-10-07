package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.StoryTime;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStorybookRequest(
        Long childProfileId,
        @Valid CreateChildProfileRequest child,
        @Size(max = 3) List<Interest> interests,
        @Valid CompanionSpec companion,
        StorySetting setting,
        StoryTime timeOfDay,
        @Size(max = 120) String place,
        @NotNull ArtStyle style,
        @NotNull Integer pageCount,
        LanguageVariety variety,
        TashkeelLevel tashkeelLevel,
        @Size(max = 300) String dedication,
        String theme,
        String storyTone,
        String lesson,
        String storyIdea,
        List<String> thingsToAvoid,
        String orientation,
        @Valid List<CharacterInput> characters,
        String childPhotoBase64,
        String companionPhotoBase64,
        Boolean photoConsent
) {
    public CreateStorybookRequest(
            Long childProfileId,
            CreateChildProfileRequest child,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            StoryTime timeOfDay,
            String place,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication,
            String theme,
            String storyTone,
            String lesson,
            String storyIdea,
            List<String> thingsToAvoid,
            String orientation,
            List<CharacterInput> characters,
            String childPhotoBase64,
            Boolean photoConsent
    ) {
        this(childProfileId, child, interests, companion, setting, timeOfDay, place, style, pageCount, variety,
                tashkeelLevel, dedication, theme, storyTone, lesson, storyIdea, thingsToAvoid, orientation, characters,
                childPhotoBase64, null, photoConsent);
    }

    public CreateStorybookRequest(
            Long childProfileId,
            CreateChildProfileRequest child,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            StoryTime timeOfDay,
            String place,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication,
            String theme,
            String storyTone,
            String lesson,
            String storyIdea,
            List<String> thingsToAvoid,
            String orientation,
            List<CharacterInput> characters
    ) {
        this(childProfileId, child, interests, companion, setting, timeOfDay, place, style, pageCount, variety,
                tashkeelLevel, dedication, theme, storyTone, lesson, storyIdea, thingsToAvoid, orientation, characters,
                null, null, null);
    }

    public CreateStorybookRequest(
            CreateChildProfileRequest child,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            StoryTime timeOfDay,
            String place,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication
    ) {
        this(null, child, interests, companion, setting, timeOfDay, place, style, pageCount, variety,
                tashkeelLevel, dedication, null, null, null, null, null, null, null);
    }

    public CreateStorybookRequest(
            Long childProfileId,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            StoryTime timeOfDay,
            String place,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication
    ) {
        this(childProfileId, null, interests, companion, setting, timeOfDay, place, style, pageCount, variety,
                tashkeelLevel, dedication, null, null, null, null, null, null, null);
    }

    public CreateStorybookRequest(
            CreateChildProfileRequest child,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication,
            String theme,
            String storyTone,
            String lesson,
            String storyIdea,
            List<String> thingsToAvoid,
            String orientation,
            List<CharacterInput> characters
    ) {
        this(null, child, interests, companion, setting, null, null, style, pageCount, variety,
                tashkeelLevel, dedication, theme, storyTone, lesson, storyIdea, thingsToAvoid, orientation, characters);
    }

    public CreateStorybookRequest(
            Long childProfileId,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication,
            String theme,
            String storyTone,
            String lesson,
            String storyIdea,
            List<String> thingsToAvoid,
            String orientation,
            List<CharacterInput> characters
    ) {
        this(childProfileId, null, interests, companion, setting, null, null, style, pageCount, variety,
                tashkeelLevel, dedication, theme, storyTone, lesson, storyIdea, thingsToAvoid, orientation, characters);
    }

    public CreateStorybookRequest(
            CreateChildProfileRequest child,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication
    ) {
        this(null, child, interests, companion, setting, null, null, style, pageCount, variety,
                tashkeelLevel, dedication, null, null, null, null, null, null, null);
    }

    public CreateStorybookRequest(
            Long childProfileId,
            List<Interest> interests,
            CompanionSpec companion,
            StorySetting setting,
            ArtStyle style,
            Integer pageCount,
            LanguageVariety variety,
            TashkeelLevel tashkeelLevel,
            String dedication
    ) {
        this(childProfileId, null, interests, companion, setting, null, null, style, pageCount, variety,
                tashkeelLevel, dedication, null, null, null, null, null, null, null);
    }
}

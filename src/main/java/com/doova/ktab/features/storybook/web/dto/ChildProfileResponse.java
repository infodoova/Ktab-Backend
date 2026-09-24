package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.model.ChildProfile;

public record ChildProfileResponse(Long id, String nameAr, ChildGender gender, AgeBand ageBand,
                                   ChildAppearance appearance) {
    public static ChildProfileResponse from(ChildProfile p) {
        return new ChildProfileResponse(p.getId(), p.getNameAr(), p.getGender(), p.getAgeBand(), p.getAppearance());
    }
}

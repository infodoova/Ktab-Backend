package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.enums.StorySetting;

import java.util.List;

public record BlueprintSummary(String key, String titleAr, String titleEn, String theme, boolean religious,
                               List<StorySetting> allowedSettings) {
    public static BlueprintSummary from(Blueprint b) {
        return new BlueprintSummary(b.key(), b.titleAr(), b.titleEn(), b.theme(), b.religious(), b.allowedSettings());
    }
}

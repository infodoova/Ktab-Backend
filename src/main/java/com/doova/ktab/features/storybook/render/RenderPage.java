package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.TextZone;

public record RenderPage(int order, Kind kind, String textAr, TextZone textZone, String imageFile) {
    public enum Kind { COVER, DEDICATION, STORY, BACK }
}

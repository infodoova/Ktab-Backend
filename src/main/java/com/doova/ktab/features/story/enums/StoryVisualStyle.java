package com.doova.ktab.features.story.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum StoryVisualStyle {

    CINEMATIC_STORYBOOK("سينمائي قصصي", "Cinematic Storybook"),
    MODERN_DIGITAL_ART("فن رقمي عصري", "Modern Digital Art"),
    DARK_GRAPHIC_NOVEL("رواية مصورة مظلمة", "Dark Graphic Novel"),
    ANIME("أنمي ورسوم متحركة", "Anime & Animation"),
    WATERCOLOR("ألوان مائية فنية", "Artistic Watercolor"),
    CLASSIC_OIL_PAINTING("رسم زيتي كلاسيكي", "Classic Oil Painting"),
    REALISTIC("واقعي سينمائي", "Photorealistic Cinematic"),
    COMIC_BOOK("قصص مصورة", "Comic Book"),
    PIXAR_3D("ثلاثي الأبعاد", "Stylized 3D Animation"),
    NOIR("فيلم نوار مظلم", "Film Noir");

    private final String labelAr;
    private final String labelEn;

    StoryVisualStyle(String labelAr, String labelEn) {
        this.labelAr = labelAr;
        this.labelEn = labelEn;
    }

    /**
     * Spring + Jackson safe valueOf.
     * Accepts: cinematic-storybook, CINEMATIC_STORYBOOK, cinematic_storybook
     */
    @JsonCreator
    public static StoryVisualStyle valueOfSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        try {
            return StoryVisualStyle.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid StoryVisualStyle: " + value);
        }
    }

    /**
     * Controls JSON serialization output
     */
    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}

package com.doova.ktab.features.storybook.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Avatar-builder output (spec, "Inputs"). Structured on purpose: no free text reaches the image model. */
public record ChildAppearance(
        SkinTone skinTone,
        HairColor hairColor,
        HairStyle hairStyle,
        EyeColor eyeColor,
        boolean hijab,
        boolean glasses
) {
    public ChildAppearance {
        Objects.requireNonNull(skinTone, "skinTone");
        Objects.requireNonNull(eyeColor, "eyeColor");
        if (!hijab) {
            Objects.requireNonNull(hairColor, "hairColor");
            Objects.requireNonNull(hairStyle, "hairStyle");
        }
    }

    public String describeEn() {
        List<String> parts = new ArrayList<>();
        parts.add(skinTone.en() + " skin");
        if (hijab) {
            parts.add("wears a simple plain hijab that fully covers the hair");
        } else {
            parts.add(hairColor.en() + " " + hairStyle.en() + " hair");
        }
        parts.add(eyeColor.en() + " eyes");
        if (glasses) {
            parts.add("round glasses");
        }
        return String.join(", ", parts);
    }

    public enum SkinTone {
        VERY_LIGHT("very light"), LIGHT("light"), LIGHT_OLIVE("light olive"), OLIVE("olive"),
        TAN("tan"), BROWN("brown"), DARK_BROWN("dark brown");
        private final String en;
        SkinTone(String en) { this.en = en; }
        public String en() { return en; }
    }

    public enum HairColor {
        BLACK("black"), DARK_BROWN("dark brown"), BROWN("brown"), LIGHT_BROWN("light brown"),
        BLONDE("blonde"), RED("red");
        private final String en;
        HairColor(String en) { this.en = en; }
        public String en() { return en; }
    }

    public enum HairStyle {
        VERY_SHORT("very short"), SHORT_STRAIGHT("short straight"), SHORT_CURLY("short curly"),
        MEDIUM_STRAIGHT("shoulder-length straight"), MEDIUM_CURLY("shoulder-length curly"),
        LONG_STRAIGHT("long straight"), LONG_CURLY("long curly"), PONYTAIL("ponytail"), BRAIDS("two braids");
        private final String en;
        HairStyle(String en) { this.en = en; }
        public String en() { return en; }
    }

    public enum EyeColor {
        DARK_BROWN("dark brown"), BROWN("brown"), HAZEL("hazel"), GREEN("green"), BLUE("blue"), GREY("grey");
        private final String en;
        EyeColor(String en) { this.en = en; }
        public String en() { return en; }
    }
}

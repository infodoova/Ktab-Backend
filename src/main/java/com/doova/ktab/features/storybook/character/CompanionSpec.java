package com.doova.ktab.features.storybook.character;

import java.util.Objects;

/** One companion at most in the MVP (decision D5). */
public record CompanionSpec(
        CompanionType type,
        String nameAr,
        ChildAppearance siblingAppearance,
        PetColor petColor,
        String photoBase64
) {

    public CompanionSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(nameAr, "nameAr");
        if (type.isPet()) {
            Objects.requireNonNull(petColor, "petColor is required for a pet");
        } else {
            Objects.requireNonNull(siblingAppearance, "siblingAppearance is required for a sibling");
        }
    }

    public CompanionSpec(CompanionType type, String nameAr, ChildAppearance siblingAppearance, PetColor petColor) {
        this(type, nameAr, siblingAppearance, petColor, null);
    }

    public String describeEn() {
        return type.isPet()
                ? "a " + petColor.en() + " " + type.en()
                : "the child's " + type.en() + ": " + siblingAppearance.describeEn();
    }

    public enum CompanionType {
        BROTHER("younger brother", false), SISTER("younger sister", false),
        CAT("cat", true), DOG("dog", true), RABBIT("rabbit", true), PARROT("parrot", true);
        private final String en;
        private final boolean pet;
        CompanionType(String en, boolean pet) { this.en = en; this.pet = pet; }
        public String en() { return en; }
        public boolean isPet() { return pet; }
    }

    public enum PetColor {
        WHITE("white"), BLACK("black"), GREY("grey"), ORANGE("orange"), BROWN("brown"),
        BLACK_AND_WHITE("black-and-white"), GREEN("green");
        private final String en;
        PetColor(String en) { this.en = en; }
        public String en() { return en; }
    }
}

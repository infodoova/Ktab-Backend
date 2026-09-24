package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;

/** Exactly one of the two is set, depending on the character's kind. */
public record CharacterAttributes(ChildAppearance child, CompanionSpec companion) {

    public static CharacterAttributes ofChild(ChildAppearance appearance) {
        return new CharacterAttributes(appearance, null);
    }

    public static CharacterAttributes ofCompanion(CompanionSpec companion) {
        return new CharacterAttributes(null, companion);
    }
}

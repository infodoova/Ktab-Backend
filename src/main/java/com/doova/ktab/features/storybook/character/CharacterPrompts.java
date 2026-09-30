package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;

public final class CharacterPrompts {

    private static final String NO_TEXT = "Absolutely no text, letters, numbers, signs, logos or writing anywhere in the picture.";
    private static final String STYLE = "Match the art style of the style reference image exactly: its line work, colour palette, texture and lighting.";
    private static final String MODEST = "Modest, simple, bright everyday children's clothes with long sleeves.";

    private CharacterPrompts() {
    }

    public static String sheet(ChildGender gender, AgeBand band, ChildAppearance appearance) {
        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation. One " + gender.en() + " aged about "
                + mid(band) + ", shown three times side by side on a plain white background: front view on the left, "
                + "three-quarter view in the center, and side profile view on the right, full body, standing, gentle smile, identical locked outfit in all three views. "
                + "Appearance: " + appearance.describeEn() + ". " + MODEST + " " + STYLE + " No text, labels or captions. "
                + NO_TEXT;
    }

    public static String sheetFromPhoto(ChildGender gender, AgeBand band) {
        return "Create a stylized children's picture-book character of the child in the photo reference, keeping "
                + "their recognizable features: face shape, skin tone, hair colour and style, eye colour, glasses and "
                + "headwear if any. A " + gender.en() + " aged about " + mid(band) + ". Show the character three times side "
                + "by side on a plain white background: front view on the left, three-quarter view in the center, and side profile view on the right, full "
                + "body, standing, gentle smile, identical locked outfit in all three views. " + MODEST + " " + STYLE + " " + NO_TEXT;
    }

    public static String sheetFromPreviousSheet(ChildGender gender, AgeBand band) {
        return "Create a new version of the character in the first reference image: the same child, keeping their "
                + "face shape, skin tone, hair, eye colour, glasses and headwear, but with a fresh pose and outfit. "
                + "A " + gender.en() + " aged about " + mid(band) + ". Show the character three times side by side on a plain "
                + "white background: front view on the left, three-quarter view in the center, and side profile view on the right, full body, standing, gentle "
                + "smile, identical locked outfit in all three views. " + MODEST + " " + STYLE + " " + NO_TEXT;
    }

    public static String companionSheet(CompanionSpec companion) {
        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation: " + companion.describeEn()
                + ", shown three times side by side on a plain white background: front view on the left, three-quarter view in the center, and side profile view on the right, friendly expression. "
                + STYLE + " " + NO_TEXT;
    }

    public static String scene(String sceneEn, TextZone zone, boolean hasCompanion, boolean hijab) {
        return "Illustrate one page of a children's picture book in 3:4 portrait orientation. " + STYLE + " "
                + "The CHILD is exactly the character in the character sheet: same face, skin tone, hair, eyes, glasses "
                + "and clothes" + (hijab ? ", always wearing the same hijab" : "") + ". "
                + (hasCompanion ? "The COMPANION is exactly the character in the companion sheet. " : "")
                + "Scene: " + sceneEn + " "
                + "Composition: the " + zone.name().toLowerCase() + " third of the picture (bottom 20% text container) is calm, clean, low-contrast, and empty "
                + "(plain sky, wall, grass, water or floor) with no important detail, reserved exclusively for text placement. " + NO_TEXT;
    }

    public static String cover(String coverSceneEn, boolean hasCompanion, boolean hijab) {
        return scene(coverSceneEn + " This is the book cover: warm, inviting, the CHILD clearly visible.",
                TextZone.TOP, hasCompanion, hijab);
    }

    private static String mid(AgeBand band) {
        return String.valueOf((band.minAge() + band.maxAge()) / 2);
    }
}

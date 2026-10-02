package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;

public final class CharacterPrompts {

    private static final String NO_TEXT = "Absolutely no text, letters, numbers, signs, logos or writing anywhere in the picture.";
    private static final String STYLE = "Match the art style of the style reference image exactly: its line work, colour palette, texture and lighting.";
    private static final String BACKDROP = "The backdrop is pure flat white: no colour, no scenery, no gradient, no tint.";
    private static final String SAME_STYLE_AS_CHILD = " The second reference image is the child's character sheet: draw this character in exactly the same art style, line work, colour treatment and texture as that sheet.";
    private static final String MODEST = "Modest, simple, bright everyday children's clothes with long sleeves.";

    private CharacterPrompts() {
    }

    public static String sheet(ChildGender gender, AgeBand band, ChildAppearance appearance) {
        return sheet(gender, band, appearance, null);
    }

    /** {@code clothing} is the locked outfit (the parent's own choice, else the bible's); without it the model invents one. */
    public static String sheet(ChildGender gender, AgeBand band, ChildAppearance appearance, String clothing) {
        String outfit = clothing == null || clothing.isBlank() ? ""
                : " Outfit, identical in all three views (use exactly this and do not change colours): " + clothing.strip() + ".";
        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation. One " + gender.en() + " aged about "
                + mid(band) + ", shown three times side by side on a plain white background: front view on the left, "
                + "three-quarter view in the center, and side profile view on the right, full body, standing, gentle smile, identical locked outfit in all three views. "
                + "Appearance: " + appearance.describeEn() + ". " + MODEST + outfit + " " + STYLE + " " + BACKDROP + " No text, labels or captions. "
                + NO_TEXT;
    }

    public static String sheetFromPhoto(ChildGender gender, AgeBand band) {
        return "Create a stylized children's picture-book character of the child in the photo reference, keeping "
                + "their recognizable features: face shape, skin tone, hair colour and style, eye colour, glasses and "
                + "headwear if any. A " + gender.en() + " aged about " + mid(band) + ". Show the character three times side "
                + "by side on a plain white background: front view on the left, three-quarter view in the center, and side profile view on the right, full "
                + "body, standing, gentle smile, identical locked outfit in all three views. " + MODEST + " " + STYLE + " " + BACKDROP + " " + NO_TEXT;
    }

    public static String sheetFromPreviousSheet(ChildGender gender, AgeBand band) {
        return "Create a new version of the character in the first reference image: the same child, keeping their "
                + "face shape, skin tone, hair, eye colour, glasses and headwear, but with a fresh pose and outfit. "
                + "A " + gender.en() + " aged about " + mid(band) + ". Show the character three times side by side on a plain "
                + "white background: front view on the left, three-quarter view in the center, and side profile view on the right, full body, standing, gentle "
                + "smile, identical locked outfit in all three views. " + MODEST + " " + STYLE + " " + BACKDROP + " " + NO_TEXT;
    }

    public static String companionSheet(CompanionSpec companion) {
        return companionSheet(companion, false);
    }

    /** With {@code childSheetAttached} the child's sheet is the second reference image, so both characters share one drawing style. */
    public static String companionSheet(CompanionSpec companion, boolean childSheetAttached) {
        String sameStyle = childSheetAttached ? SAME_STYLE_AS_CHILD : "";
        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation: " + companion.describeEn()
                + ", shown three times side by side on a plain white background: front view on the left, three-quarter view in the center, and side profile view on the right, friendly expression. "
                + STYLE + sameStyle + " " + BACKDROP + " " + NO_TEXT;
    }

    /**
     * What every page must repeat in words, not only through the reference pictures: left to itself the model adds glasses,
     * changes the outfit and drifts in style. {@code anchor} says the approved cover is attached as the last reference image.
     */
    public record PageLock(boolean hijab, boolean glasses, String appearanceEn, String clothing, String companionEn,
                           String styleNotes, boolean anchor, java.util.List<SupportingLook> supporting, boolean styleReference) {

        public PageLock(boolean hijab, boolean glasses, String appearanceEn, String clothing, String companionEn,
                        String styleNotes, boolean anchor, java.util.List<SupportingLook> supporting) {
            this(hijab, glasses, appearanceEn, clothing, companionEn, styleNotes, anchor, supporting, true);
        }

        public PageLock(boolean hijab, boolean glasses, String appearanceEn, String clothing, String companionEn,
                        String styleNotes, boolean anchor) {
            this(hijab, glasses, appearanceEn, clothing, companionEn, styleNotes, anchor, null, true);
        }
    }

    /** A supporting character who is in this scene and whose sheet is attached as a reference image. */
    public record SupportingLook(String ref, String describeEn, String clothing) {
    }

    /** A grandparent, a friend or any other supporting character, drawn from the parent's description and outfit. */
    public static String supportingSheet(String describeEn, String clothing, boolean childSheetAttached) {
        String outfit = clothing == null || clothing.isBlank()
                ? " Modest, simple everyday clothing."
                : " Outfit, identical in all three views (use exactly this and do not change colours): " + clothing.strip() + ".";
        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation: " + describeEn
                + ", shown three times side by side on a plain white background: front view on the left, three-quarter view in the center, "
                + "and side profile view on the right, full body, standing, calm friendly expression, identical locked outfit in all three views."
                + outfit + " " + STYLE + (childSheetAttached ? SAME_STYLE_AS_CHILD : "") + " " + BACKDROP + " " + NO_TEXT;
    }

    public static String scene(String sceneEn, TextZone zone, boolean hasCompanion, boolean hijab) {
        return buildScene(sceneEn, zone, hasCompanion, hijab, null);
    }

    public static String scene(String sceneEn, TextZone zone, boolean hasCompanion, PageLock lock) {
        return buildScene(sceneEn, zone, hasCompanion, lock.hijab(), lock);
    }

    private static String buildScene(String sceneEn, TextZone zone, boolean hasCompanion, boolean hijab, PageLock lock) {
        return "Illustrate one page of a children's picture book in 3:4 portrait orientation. " + STYLE + " "
                + "The CHILD is exactly the character in the character sheet: same face, skin tone, hair, eyes, glasses "
                + "and clothes" + (hijab ? ", always wearing the same hijab" : "") + ". "
                + (hasCompanion ? "The COMPANION is exactly the character in the companion sheet. " : "")
                + lockText(lock, hasCompanion)
                + "Scene: " + sceneEn + " "
                + "Composition: the " + zone.name().toLowerCase() + " third of the picture (bottom 20% text container) is calm, clean, low-contrast, and empty "
                + "(plain sky, wall, grass, water or floor) with no important detail, reserved exclusively for text placement. " + NO_TEXT;
    }

    public static String cover(String coverSceneEn, boolean hasCompanion, PageLock lock) {
        return scene(coverSceneEn + " This is the book cover: warm, inviting, the CHILD clearly visible.",
                TextZone.TOP, hasCompanion, lock);
    }

    public static String cover(String coverSceneEn, boolean hasCompanion, boolean hijab) {
        return scene(coverSceneEn + " This is the book cover: warm, inviting, the CHILD clearly visible.",
                TextZone.TOP, hasCompanion, hijab);
    }

    private static String lockText(PageLock lock, boolean hasCompanion) {
        if (lock == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Identity lock for the CHILD (never change any of it): ");
        if (lock.appearanceEn() != null && !lock.appearanceEn().isBlank()) {
            sb.append(lock.appearanceEn().strip()).append("; ");
        }
        sb.append(lock.glasses() ? "always wears the same glasses; " : "wears NO glasses (never draw glasses); ");
        if (lock.hijab()) {
            sb.append("wears the same hijab; ");
        }
        if (lock.clothing() != null && !lock.clothing().isBlank()) {
            sb.append("outfit, identical on every page: ").append(lock.clothing().strip()).append("; ");
        }
        sb.append("keep the same colours on every page. ");
        if (hasCompanion && lock.companionEn() != null && !lock.companionEn().isBlank()) {
            sb.append("Identity lock for the COMPANION: ").append(lock.companionEn().strip()).append(". ");
        }
        if (lock.supporting() != null) {
            for (SupportingLook l : lock.supporting()) {
                sb.append("Identity lock for ").append(l.ref()).append(" (").append(l.describeEn()).append("): drawn exactly like its character sheet in the reference images; ");
                if (l.clothing() != null && !l.clothing().isBlank()) {
                    sb.append("outfit, identical on every page: ").append(l.clothing().strip()).append("; ");
                }
                sb.append("keep the same colours on every page. ");
            }
            if (!lock.supporting().isEmpty()) {
                java.util.List<String> order = new java.util.ArrayList<>(java.util.List.of("CHILD sheet"));
                if (lock.styleReference()) {
                    order.add("style reference");
                }
                if (hasCompanion) {
                    order.add("COMPANION sheet");
                }
                lock.supporting().forEach(l -> order.add(l.ref() + " sheet"));
                if (lock.anchor()) {
                    order.add("approved book cover");
                }
                sb.append("Reference images, in order: ").append(String.join(", ", order)).append(". ");
            }
        }
        if (lock.styleNotes() != null && !lock.styleNotes().isBlank()) {
            sb.append("Art direction for the whole book (apply to every page): ").append(lock.styleNotes().strip()).append(" ");
        }
        if (lock.anchor()) {
            sb.append("The last reference image is the approved book cover: keep exactly its art style, colour palette, line quality and lighting, and the characters' same look. ");
        }
        return sb.toString();
    }

    private static String mid(AgeBand band) {
        return String.valueOf((band.minAge() + band.maxAge()) / 2);
    }
}

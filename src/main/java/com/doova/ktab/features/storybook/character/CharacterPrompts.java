package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.StoryTime;
import com.doova.ktab.features.storybook.enums.TextZone;

public final class CharacterPrompts {

    private static final String NO_TEXT = "Absolutely no text, letters, numbers, signs, logos or writing anywhere in the picture.";

    private static final String STYLE = "Match the art style of the style reference image exactly: its line work, colour palette, texture and lighting.";

    private static final String BACKDROP = "The backdrop is pure flat white: no colour, no scenery, no gradient, no tint.";

    private static final String SAME_STYLE_AS_CHILD = " The second reference image is the child's character sheet: draw this character in exactly the same art style, "
            + "line work, colour treatment and texture as that sheet.";

    private static final String MODEST = "Modest, simple, bright everyday children's clothes with long sleeves.";

    private CharacterPrompts() {
    }

    // -------------------------------------------------------------------------
    // CHILD CHARACTER SHEETS
    // -------------------------------------------------------------------------

    public static String sheet(
            ChildGender gender,
            AgeBand band,
            ChildAppearance appearance) {
        return sheet(gender, band, appearance, null);
    }

    /**
     * {@code clothing} is the locked outfit:
     * either the parent's own choice or the story bible's outfit.
     * Without it, the model is allowed to invent one.
     */
    public static String sheet(
            ChildGender gender,
            AgeBand band,
            ChildAppearance appearance,
            String clothing) {
        String outfit = clothing == null || clothing.isBlank()
                ? ""
                : " Outfit, identical in all three views (use exactly this and do not change colours): "
                        + clothing.strip()
                        + ".";

        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation. "
                + "One " + gender.en()
                + " aged about " + mid(band)
                + ", shown three times side by side on a plain white background: "
                + "front view on the left, "
                + "three-quarter view in the center, "
                + "and side profile view on the right. "
                + "Full body, standing, gentle smile, identical locked outfit in all three views. "
                + "Appearance: " + appearance.describeEn() + ". "
                + MODEST
                + outfit + " "
                + STYLE + " "
                + BACKDROP + " "
                + "No text, labels or captions. "
                + NO_TEXT;
    }

    public static String sheetFromPhoto(
            ChildGender gender,
            AgeBand band) {
        return "Create a stylized children's picture-book character of the child in the photo reference, "
                + "keeping their recognizable features: face shape, skin tone, hair colour and style, "
                + "eye colour, glasses and headwear if any. "
                + "A " + gender.en()
                + " aged about " + mid(band) + ". "
                + "Show the character three times side by side on a plain white background: "
                + "front view on the left, "
                + "three-quarter view in the center, "
                + "and side profile view on the right. "
                + "Full body, standing, gentle smile, identical locked outfit in all three views. "
                + MODEST + " "
                + STYLE + " "
                + BACKDROP + " "
                + NO_TEXT;
    }

    public static String sheetFromPreviousSheet(
            ChildGender gender,
            AgeBand band) {
        return "Create a new version of the character in the first reference image: "
                + "the same child, keeping their face shape, skin tone, hair, eye colour, "
                + "glasses and headwear, but with a fresh pose and outfit. "
                + "A " + gender.en()
                + " aged about " + mid(band) + ". "
                + "Show the character three times side by side on a plain white background: "
                + "front view on the left, "
                + "three-quarter view in the center, "
                + "and side profile view on the right. "
                + "Full body, standing, gentle smile, identical outfit in all three views. "
                + MODEST + " "
                + STYLE + " "
                + BACKDROP + " "
                + NO_TEXT;
    }

    // -------------------------------------------------------------------------
    // COMPANION CHARACTER SHEETS
    // -------------------------------------------------------------------------

    public static String companionSheet(CompanionSpec companion) {
        return companionSheet(companion, false);
    }

    /**
     * When {@code childSheetAttached} is true, the child's sheet is the second
     * reference image so the companion uses exactly the same illustration style.
     */
    public static String companionSheet(
            CompanionSpec companion,
            boolean childSheetAttached) {
        String sameStyle = childSheetAttached
                ? SAME_STYLE_AS_CHILD
                : "";

        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation: "
                + companion.describeEn()
                + ", shown three times side by side on a plain white background: "
                + "front view on the left, "
                + "three-quarter view in the center, "
                + "and side profile view on the right. "
                + "Friendly expression. "
                + STYLE
                + sameStyle + " "
                + BACKDROP + " "
                + NO_TEXT;
    }

    // -------------------------------------------------------------------------
    // SUPPORTING CHARACTERS
    // -------------------------------------------------------------------------

    /**
     * A grandparent, friend, sibling or other supporting character generated
     * from the parent's description.
     */
    public static String supportingSheet(
            String describeEn,
            String clothing,
            boolean childSheetAttached) {
        String outfit = clothing == null || clothing.isBlank()
                ? " Modest, simple everyday clothing."
                : " Outfit, identical in all three views (use exactly this and do not change colours): "
                        + clothing.strip()
                        + ".";

        return "Character reference master sheet for a children's picture book in 3:4 portrait orientation: "
                + describeEn
                + ", shown three times side by side on a plain white background: "
                + "front view on the left, "
                + "three-quarter view in the center, "
                + "and side profile view on the right. "
                + "Full body, standing, calm friendly expression, "
                + "identical locked outfit in all three views."
                + outfit + " "
                + STYLE
                + (childSheetAttached ? SAME_STYLE_AS_CHILD : "")
                + " "
                + BACKDROP + " "
                + NO_TEXT;
    }

    // -------------------------------------------------------------------------
    // PAGE LOCKS
    // -------------------------------------------------------------------------

    /**
     * Everything that must remain visually consistent throughout the book.
     *
     * anchor:
     * the approved cover is attached as the final reference image.
     *
     * styleReference:
     * a dedicated style-reference image exists among the references.
     */
    public record PageLock(
            boolean hijab,
            boolean glasses,
            String appearanceEn,
            String clothing,
            String companionEn,
            String styleNotes,
            boolean anchor,
            java.util.List<SupportingLook> supporting,
            boolean styleReference) {

        public PageLock(
                boolean hijab,
                boolean glasses,
                String appearanceEn,
                String clothing,
                String companionEn,
                String styleNotes,
                boolean anchor,
                java.util.List<SupportingLook> supporting) {
            this(
                    hijab,
                    glasses,
                    appearanceEn,
                    clothing,
                    companionEn,
                    styleNotes,
                    anchor,
                    supporting,
                    true);
        }

        public PageLock(
                boolean hijab,
                boolean glasses,
                String appearanceEn,
                String clothing,
                String companionEn,
                String styleNotes,
                boolean anchor) {
            this(
                    hijab,
                    glasses,
                    appearanceEn,
                    clothing,
                    companionEn,
                    styleNotes,
                    anchor,
                    null,
                    true);
        }
    }

    /**
     * A supporting character who is visible in the current scene and whose
     * character sheet is supplied as a reference image.
     */
    public record SupportingLook(
            String ref,
            String describeEn,
            String clothing) {
    }

    // -------------------------------------------------------------------------
    // SCENES - SIMPLE / BACKWARD COMPATIBLE
    // -------------------------------------------------------------------------

    public static String scene(
            String sceneEn,
            TextZone zone,
            boolean hasCompanion,
            boolean hijab) {
        return buildScene(
                sceneEn,
                zone,
                hasCompanion,
                hijab,
                null,
                null,
                null,
                null);
    }

    // -------------------------------------------------------------------------
    // SCENES - WITH SETTING / TIME / PLACE
    // -------------------------------------------------------------------------

    public static String scene(
            String sceneEn,
            TextZone zone,
            boolean hasCompanion,
            boolean hijab,
            StorySetting setting,
            StoryTime timeOfDay,
            String place) {
        return buildScene(
                sceneEn,
                zone,
                hasCompanion,
                hijab,
                null,
                setting,
                timeOfDay,
                place);
    }

    // -------------------------------------------------------------------------
    // SCENES - WITH PAGE LOCK
    // -------------------------------------------------------------------------

    public static String scene(
            String sceneEn,
            TextZone zone,
            boolean hasCompanion,
            PageLock lock) {
        return buildScene(
                sceneEn,
                zone,
                hasCompanion,
                lock != null && lock.hijab(),
                lock,
                null,
                null,
                null);
    }

    // -------------------------------------------------------------------------
    // SCENES - FULL VERSION
    // PAGE LOCK + SETTING + TIME + PLACE
    // -------------------------------------------------------------------------

    public static String scene(
            String sceneEn,
            TextZone zone,
            boolean hasCompanion,
            PageLock lock,
            StorySetting setting,
            StoryTime timeOfDay,
            String place) {
        return buildScene(
                sceneEn,
                zone,
                hasCompanion,
                lock != null && lock.hijab(),
                lock,
                setting,
                timeOfDay,
                place);
    }

    // -------------------------------------------------------------------------
    // INTERNAL SCENE BUILDER
    // -------------------------------------------------------------------------

    private static String buildScene(
            String sceneEn,
            TextZone zone,
            boolean hasCompanion,
            boolean hijab,
            PageLock lock,
            StorySetting setting,
            StoryTime timeOfDay,
            String place) {
        StringBuilder sb = new StringBuilder();

        sb.append(
                "Illustrate one page of a children's picture book in 3:4 portrait orientation. ");

        sb.append(STYLE).append(' ');

        // ---------------------------------------------------------------------
        // Main child identity
        // ---------------------------------------------------------------------

        sb.append(
                "The CHILD is exactly the character in the character sheet: ");

        sb.append(
                "same face, skin tone, hair, eyes, glasses and clothes");

        if (hijab) {
            sb.append(", always wearing the same hijab");
        }

        sb.append(". ");

        // ---------------------------------------------------------------------
        // Companion
        // ---------------------------------------------------------------------

        if (hasCompanion) {
            sb.append(
                    "The COMPANION is exactly the character in the companion sheet. ");
        }

        // ---------------------------------------------------------------------
        // Character / book locks
        // ---------------------------------------------------------------------

        sb.append(lockText(lock, hasCompanion));

        // ---------------------------------------------------------------------
        // Scene description
        // ---------------------------------------------------------------------

        sb.append("Scene: ")
                .append(sceneEn)
                .append(' ');

        // ---------------------------------------------------------------------
        // Environment
        // ---------------------------------------------------------------------

        appendEnvironment(
                sb,
                setting,
                timeOfDay,
                place);

        // ---------------------------------------------------------------------
        // Text-safe composition
        // ---------------------------------------------------------------------

        sb.append(textZoneInstruction(zone));

        sb.append(' ')
                .append(NO_TEXT);

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // ENVIRONMENT CONTINUITY
    // -------------------------------------------------------------------------

    private static void appendEnvironment(
            StringBuilder sb,
            StorySetting setting,
            StoryTime timeOfDay,
            String place) {
        boolean hasEnvironment = setting != null
                || timeOfDay != null
                || (place != null && !place.isBlank());

        if (!hasEnvironment) {
            return;
        }

        sb.append("Environment and setting continuity: ");

        if (setting != null) {
            sb.append("Regional setting: ")
                    .append(setting.sceneEn())
                    .append(". ");
        }

        if (place != null && !place.isBlank()) {
            sb.append("Specific location: ")
                    .append(place.strip())
                    .append(". ");
        }

        if (timeOfDay != null) {
            sb.append("Time of day and lighting: ")
                    .append(timeOfDay.sceneEn())
                    .append(". ");
        }

        sb.append(
                "Strictly preserve the established environmental identity across all pages: ");

        sb.append(
                "same regional architecture, landscape language, recurring location features, "
                        + "materials, weather conditions, colour relationships and overall lighting logic. ");

        sb.append(
                "Do not randomly change the geographical region, architectural style, season or time-of-day treatment "
                        + "unless the scene explicitly requires that change. ");
    }

    // -------------------------------------------------------------------------
    // TEXT SAFE AREA
    // -------------------------------------------------------------------------

    private static String textZoneInstruction(TextZone zone) {
        String zoneName = zone.name()
                .toLowerCase()
                .replace('_', ' ');

        return "Composition: keep the " + zoneName
                + " text-safe area, approximately 20% of the picture, calm, clean, low-contrast and visually quiet. "
                + "Use simple background elements such as plain sky, wall, grass, water or floor. "
                + "Do not place faces, characters, hands, important objects, focal points or essential story details "
                + "inside this area. Reserve it exclusively for later text placement.";
    }

    // -------------------------------------------------------------------------
    // COVERS
    // -------------------------------------------------------------------------

    public static String cover(
            String coverSceneEn,
            boolean hasCompanion,
            boolean hijab) {
        return scene(
                coverSceneEn
                        + " This is the book cover: warm, inviting, visually memorable, "
                        + "with the CHILD clearly visible and immediately recognizable.",
                TextZone.TOP,
                hasCompanion,
                hijab);
    }

    public static String cover(
            String coverSceneEn,
            boolean hasCompanion,
            boolean hijab,
            StorySetting setting,
            StoryTime timeOfDay,
            String place) {
        return scene(
                coverSceneEn
                        + " This is the book cover: warm, inviting, visually memorable, "
                        + "with the CHILD clearly visible and immediately recognizable.",
                TextZone.TOP,
                hasCompanion,
                hijab,
                setting,
                timeOfDay,
                place);
    }

    public static String cover(
            String coverSceneEn,
            boolean hasCompanion,
            PageLock lock) {
        return scene(
                coverSceneEn
                        + " This is the book cover: warm, inviting, visually memorable, "
                        + "with the CHILD clearly visible and immediately recognizable.",
                TextZone.TOP,
                hasCompanion,
                lock);
    }

    public static String cover(
            String coverSceneEn,
            boolean hasCompanion,
            PageLock lock,
            StorySetting setting,
            StoryTime timeOfDay,
            String place) {
        return scene(
                coverSceneEn
                        + " This is the book cover: warm, inviting, visually memorable, "
                        + "with the CHILD clearly visible and immediately recognizable.",
                TextZone.TOP,
                hasCompanion,
                lock,
                setting,
                timeOfDay,
                place);
    }

    // -------------------------------------------------------------------------
    // LOCK TEXT
    // -------------------------------------------------------------------------

    private static String lockText(
            PageLock lock,
            boolean hasCompanion) {
        if (lock == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(
                "Identity lock for the CHILD. Never change any of the following: ");

        // Appearance
        if (lock.appearanceEn() != null
                && !lock.appearanceEn().isBlank()) {

            sb.append(lock.appearanceEn().strip())
                    .append("; ");
        }

        // Glasses
        if (lock.glasses()) {
            sb.append(
                    "always wears exactly the same glasses; ");
        } else {
            sb.append(
                    "wears NO glasses; never add glasses; ");
        }

        // Hijab
        if (lock.hijab()) {
            sb.append(
                    "always wears the same hijab with the same colour and styling; ");
        }

        // Clothing
        if (lock.clothing() != null
                && !lock.clothing().isBlank()) {

            sb.append("outfit, identical on every page: ")
                    .append(lock.clothing().strip())
                    .append("; ");
        }

        sb.append(
                "keep all character colours, proportions and identifying features consistent on every page. ");

        // ---------------------------------------------------------------------
        // Companion
        // ---------------------------------------------------------------------

        if (hasCompanion
                && lock.companionEn() != null
                && !lock.companionEn().isBlank()) {

            sb.append("Identity lock for the COMPANION: ")
                    .append(lock.companionEn().strip())
                    .append(". ");
        }

        // ---------------------------------------------------------------------
        // Supporting characters
        // ---------------------------------------------------------------------

        if (lock.supporting() != null) {

            for (SupportingLook look : lock.supporting()) {

                sb.append("Identity lock for ")
                        .append(look.ref())
                        .append(" (")
                        .append(look.describeEn())
                        .append("): ")
                        .append(
                                "drawn exactly like its character sheet in the reference images; ");

                if (look.clothing() != null
                        && !look.clothing().isBlank()) {

                    sb.append("outfit, identical on every page: ")
                            .append(look.clothing().strip())
                            .append("; ");
                }

                sb.append(
                        "keep the same colours and identifying features on every page. ");
            }

            // -----------------------------------------------------------------
            // Reference image ordering
            // -----------------------------------------------------------------

            if (!lock.supporting().isEmpty()) {

                java.util.List<String> order = new java.util.ArrayList<>();

                order.add("CHILD sheet");

                if (lock.styleReference()) {
                    order.add("style reference");
                }

                if (hasCompanion) {
                    order.add("COMPANION sheet");
                }

                lock.supporting().forEach(
                        look -> order.add(look.ref() + " sheet"));

                if (lock.anchor()) {
                    order.add("approved book cover");
                }

                sb.append("Reference images, in order: ")
                        .append(String.join(", ", order))
                        .append(". ");
            }
        }

        // ---------------------------------------------------------------------
        // Global art direction
        // ---------------------------------------------------------------------

        if (lock.styleNotes() != null
                && !lock.styleNotes().isBlank()) {

            sb.append(
                    "Art direction for the whole book, apply this consistently to every page: ");

            sb.append(lock.styleNotes().strip())
                    .append(". ");
        }

        // ---------------------------------------------------------------------
        // Approved-cover anchor
        // ---------------------------------------------------------------------

        if (lock.anchor()) {
            sb.append(
                    "The last reference image is the approved book cover. ");

            sb.append(
                    "Use it as a strict visual anchor: preserve exactly its art style, colour palette, "
                            + "line quality, rendering treatment, texture, lighting language and character appearance. ");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // AGE
    // -------------------------------------------------------------------------

    private static String mid(AgeBand band) {
        return String.valueOf(
                (band.minAge() + band.maxAge()) / 2);
    }
}
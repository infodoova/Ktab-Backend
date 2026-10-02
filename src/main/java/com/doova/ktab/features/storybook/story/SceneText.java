package com.doova.ktab.features.storybook.story;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The outfit is fixed once, by the parent's choice, and carried by the character sheet and the identity lock. A scene description
 * that names clothes or colours contradicts it, so the picture model follows the scene on some pages and the sheet on others and the
 * checker fails correct pictures. This removes any such clause, whoever wrote it.
 */
public final class SceneText {

    private static final int MIN_LEFT = 15;
    private static final Pattern EXPRESSION = Pattern.compile("(?i)\\b(smile|smiling|grin|look|expression|frown|gasp|wonder|surprise)\\b");

    /** "CHILD wears her …" up to the next ";" or "." or ", and COMPANION". */
    private static final Pattern CHILD_WEARS = Pattern.compile(
            "\\b(?:CHILD|SUPPORT_\\d+)\\b\\s+(?:(?i:is\\s+)?(?i:wears|wearing|dressed in|puts on|pulls on))\\b[^.;]*?(?=,?\\s+and\\s+(?:COMPANION|CHILD|SUPPORT_\\d+)\\b|[.;]|$)");

    /** "COMPANION has emerald feathers and a turquoise band": only when it names a colour or a body or accessory detail. */
    private static final Pattern COMPANION_LOOKS = Pattern.compile(
            "(?:,?\\s+and\\s+)?\\bCOMPANION\\b\\s+(?i:wears|has|sports)\\s+[^.;]*?\\b(?i:feathers?|band|collar|fur|plumage|colou?rs?|green|turquoise|emerald|"
                    + "blue|red|yellow|orange|white|black|grey|gray|brown|purple|pink|golden)\\b[^.;]*");

    /** ", wearing a bright red raincoat," inside a sentence. */
    private static final Pattern PARTICIPLE = Pattern.compile(",?\\s*\\b(?i:wearing|dressed in)\\b[^,.;]*,?");

    private SceneText() {
    }

    public static String withoutOutfit(String scene) {
        if (scene == null || scene.isBlank()) {
            return scene;
        }
        String clean = remove(CHILD_WEARS, scene);
        clean = remove(COMPANION_LOOKS, clean);
        clean = remove(PARTICIPLE, clean);
        if (clean.equals(scene)) {
            return scene;
        }
        clean = tidy(clean);
        return clean.strip().length() < MIN_LEFT ? scene : clean;
    }

    private static String remove(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.replaceAll(r -> EXPRESSION.matcher(r.group()).find() ? Matcher.quoteReplacement(r.group()) : "");
    }

    private static String tidy(String text) {
        String t = text.replaceAll("\\s+([;.,])", "$1");
        t = t.replaceAll("([.!?])\\s*[;,]+\\s*", "$1 ");
        t = t.replaceAll("^[\\s;,]+", "");
        t = t.replaceAll("[;,]\\s*([.!?])", "$1");
        t = t.replaceAll("([.!?;])\\s*,?\\s*and\\s+", "$1 "); // a clause that lost its subject must not start with "and"
        t = t.replaceAll("\\s{2,}", " ");
        return t.strip();
    }
}

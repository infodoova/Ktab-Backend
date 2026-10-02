package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.model.StorybookCharacter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A character beyond the child and the companion (a grandparent, a friend). Every page names it by the same tag, SUPPORT_1,
 * SUPPORT_2 and so on, so the story, the picture prompts and the sheets all mean the same person.
 */
public record SupportingCast(String ref, String id, String name, String relationship, String role, String clothing, String describeEn) {

    public static List<SupportingCast> of(List<StorybookCharacter> supporting) {
        if (supporting == null) {
            return List.of();
        }
        List<SupportingCast> cast = new ArrayList<>();
        int n = 1;
        for (StorybookCharacter c : supporting) {
            Map<String, Object> d = c.getAdvancedDetails() == null ? Map.of() : c.getAdvancedDetails();
            String name = text(d.get("name")) != null ? text(d.get("name")) : c.getCharacterId();
            cast.add(new SupportingCast("SUPPORT_" + n++, c.getCharacterId(), name, c.getRelationship(), c.getRole(),
                    c.getClothing(), describe(c, d)));
        }
        return List.copyOf(cast);
    }

    /** What the story writer must be told so it uses these people and invents no others. */
    public static String legend(List<SupportingCast> cast) {
        if (cast == null || cast.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\nSupporting characters (refer to each ONLY by its tag in scene descriptions and in the cast list; "
                + "do not invent other people):\n");
        for (SupportingCast c : cast) {
            sb.append("- ").append(c.ref()).append(" = ").append(c.name()).append(", ").append(c.describeEn()).append('\n');
        }
        return sb.toString();
    }

    private static String describe(StorybookCharacter c, Map<String, Object> d) {
        String who = c.getRelationship() != null && !c.getRelationship().isBlank() ? c.getRelationship() : c.getRole();
        Integer age = d.get("age") instanceof Number num ? num.intValue() : null;
        String gender = text(d.get("gender"));
        boolean female = "GIRL".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender);
        String person;
        if (gender == null) {
            person = "person";
        } else if (age != null && age >= 18) {
            person = female ? "woman" : "man";
        } else {
            person = female ? "girl" : "boy";
        }
        StringBuilder sb = new StringBuilder("the ").append(who == null ? "character" : who).append(", a ").append(person);
        if (age != null) {
            sb.append(", about ").append(age).append(" years old");
        }
        if (c.getPersonality() != null && !c.getPersonality().isEmpty()) {
            sb.append("; personality: ").append(String.join(", ", c.getPersonality()));
        }
        String item = text(d.get("signatureItem"));
        if (item != null) {
            sb.append("; always carries ").append(item);
        }
        return sb.toString();
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s.strip() : null;
    }
}

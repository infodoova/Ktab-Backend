package com.doova.ktab.ai.gemini.enums;

import lombok.Getter;

@Getter
public enum TargetAudienceProfile {

    KIDS_8_10_ADVENTURE(
            "8-10",
            """
Age 8–10; early chapter-book readers; enjoys simple adventure and light fantasy;
short chapters, clear language, and straightforward plots;
sensitivity: avoid death on-page, no horror, no graphic danger; keep scares mild and quickly resolved;
desired emotional impact: warm, reassuring, and optimistic; clear sense of safety and friendship;
context: primary school readers, often reading with a parent/teacher; familiar with basic fairy-tale and cartoon tropes.
"""
    ),

    MIDDLE_GRADE_10_13_MYSTERY(
            "10-13",
            """
Age 10–13; confident middle-grade readers; enjoys school stories, mysteries, and friend drama;
can follow multiple POVs and subplots;
sensitivity: mild peril and arguments okay; avoid graphic violence, romance should stay crush-level and non-explicit;
desired emotional impact: exciting but ultimately comforting; emphasize teamwork, resilience, and learning from mistakes;
context: middle-school students used to popular MG series and streaming shows with similar tone.
"""
    ),

    TEENS_13_16_DYSTOPIAN(
            "13-16",
            """
Age 13–16; used to YA dystopian and action stories; comfortable with morally gray choices and fast pacing;
sensitivity: moderate violence and tension acceptable, but avoid gore and explicit torture; romantic tension okay, avoid explicit sexual content;
desired emotional impact: tense, cathartic, and hopeful by the end; emphasize courage, sacrifice, and found family;
context: teens familiar with popular YA dystopias and genre tropes from movies, anime, and games.
"""
    ),

    OLDER_TEENS_16_18_DRAMA_ROMANCE(
            "16-18",
            """
Age 16–18; advanced YA readers; enjoys character-driven stories, emotional conflict, and slow-burn romance;
sensitivity: complex emotions, grief, and heartbreak are fine; avoid explicit sexual scenes and explicit self-harm descriptions;
desired emotional impact: bittersweet but healing; mix of pain, growth, and a sense of emotional closure;
context: late high-school readers, familiar with contemporary YA novels and streaming dramas.
"""
    ),

    ADULTS_18_25_LITERARY(
            "18-25",
            """
Age 18–25; experienced readers comfortable with literary and psychological fiction;
can handle nonlinear structure, symbolism, and ambiguity;
sensitivity: difficult themes (trauma, mental health, loss) acceptable if handled with nuance; avoid gratuitous graphic gore or shock for its own sake;
desired emotional impact: introspective, haunting, and thought-provoking; leave some questions open but offer thematic coherence;
context: adult readers familiar with modern literary novels and festival films, comfortable reading between the lines.
"""
    ),

    ADULTS_25_40_UPMARKET(
            "25-40",
            """
Age 25–40; comfortable with upmarket genre fiction that blends mystery with family drama;
expects solid pacing, emotional depth, and believable characters;
sensitivity: moderate violence and adult themes acceptable; avoid explicit sexual detail and extreme cruelty;
desired emotional impact: satisfying, emotionally resonant, with a sense of earned resolution and subtle hope;
context: adult readers who read bestsellers and book-club picks; used to twisty plots but realistic emotions.
"""
    ),

    ADULTS_40_PLUS_HISTORICAL(
            "40+",
            """
Age 40+; enjoys historical fiction and reflective, slower-paced narratives;
appreciates rich atmosphere, clear timelines, and emotional maturity;
sensitivity: can handle loss, illness, and war if portrayed respectfully; avoid gratuitously graphic scenes;
desired emotional impact: contemplative and moving; a sense of closure, dignity, and meaning in the characters’ journeys;
context: readers familiar with classic and modern historical novels, often engaging with themes of memory, legacy, and family.
"""
    );

    private final String ageRangeKey;
    private final String profileText;

    TargetAudienceProfile(String ageRangeKey, String profileText) {
        this.ageRangeKey = ageRangeKey;
        this.profileText = profileText;
    }

    /**
     * Map a user-provided key to a profile.
     *
     * Supports:
     *  - Age range strings like "10-13", "13-16", "40+"
     *  - Enum names like "TEENS_13_16_DYSTOPIAN" (fallback)
     */
    public static TargetAudienceProfile fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Audience profile key must not be empty");
        }

        String trimmed = key.trim();

        // First: try matching by age range, e.g. "10-13"
        for (TargetAudienceProfile profile : values()) {
            if (profile.ageRangeKey.equalsIgnoreCase(trimmed)) {
                return profile;
            }
        }

        // Second: fallback to enum name, e.g. "TEENS_13_16_DYSTOPIAN"
        try {
            return TargetAudienceProfile.valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException ex) {
            // Build a helpful error message
            StringBuilder sb = new StringBuilder("Unknown audience profile key '")
                    .append(key)
                    .append("'. Expected age ranges: ");
            boolean first = true;
            for (TargetAudienceProfile p : values()) {
                if (!first) sb.append(", ");
                sb.append(p.ageRangeKey);
                first = false;
            }
            sb.append(" or enum names: ");
            first = true;
            for (TargetAudienceProfile p : values()) {
                if (!first) sb.append(", ");
                sb.append(p.name());
                first = false;
            }
            throw new IllegalArgumentException(sb.toString(), ex);
        }
    }
}

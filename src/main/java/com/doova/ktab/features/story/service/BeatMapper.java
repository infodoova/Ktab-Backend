package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.BeatEntry;
import com.doova.ktab.features.story.enums.Beat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BeatMapper {

    private BeatMapper() {}

    public static List<Beat> buildBeatMap(int n) {
        int clamped = Math.max(5, Math.min(15, n));
        Beat[] map = new Beat[clamped + 1];
        map[1] = Beat.HOOK;
        map[2] = Beat.INCITING;
        map[clamped] = Beat.RESOLUTION;
        map[clamped - 1] = Beat.CLIMAX;
        map[clamped - 2] = Beat.CRISIS;
        int mid = (int) Math.round(clamped / 2.0);
        if (mid > 2 && mid < clamped - 2) {
            map[mid] = Beat.MIDPOINT;
        }
        for (int i = 3; i < clamped - 2; i++) {
            if (map[i] == null) {
                map[i] = (i < mid) ? Beat.RISING : Beat.TIGHTENING;
            }
        }
        return Arrays.asList(map).subList(1, clamped + 1);
    }

    public static int tension(Beat b, int i, int n) {
        return switch (b) {
            case HOOK -> 3;
            case INCITING -> 4;
            case MIDPOINT -> 6;
            case RISING -> 4 + Math.min(1, i / 4);
            case TIGHTENING -> 7;
            case CRISIS -> 8;
            case CLIMAX -> 10;
            case RESOLUTION -> 5;
        };
    }

    public static List<BeatEntry> buildFullBeatMap(int n) {
        List<Beat> beats = buildBeatMap(n);
        List<BeatEntry> result = new ArrayList<>(beats.size());
        for (int i = 0; i < beats.size(); i++) {
            int sceneIndex = i + 1;
            Beat beat = beats.get(i);
            result.add(new BeatEntry(sceneIndex, beat, tension(beat, sceneIndex, beats.size())));
        }
        return result;
    }

    public static String getBeatInstructions(Beat beat) {
        return switch (beat) {
            case HOOK -> """
                <beat name="HOOK">
                Open in motion: the protagonist is already doing something concrete under pressure.
                In one scene, establish: who they are, what they want, the world's danger, and the ticking clock.
                Plant at least one setup from the bible. Stakes are personal but not yet total.
                Choices here define character more than plot; consequences are modest but must be remembered later.
                </beat>
                """;
            case INCITING -> """
                <beat name="INCITING">
                Deliver the event that makes the mainConflict unavoidable. After this scene, the protagonist cannot return to normal life.
                Every choice must commit the protagonist to a different path toward the conflict. None of them may avoid it entirely.
                </beat>
                """;
            case RISING -> """
                <beat name="RISING">
                Escalate. Introduce exactly one new complication OR reveal one secret from the bible.
                Pay off any setup scheduled for this scene. Tension must be higher than the previous scene.
                </beat>
                """;
            case MIDPOINT -> """
                <beat name="MIDPOINT">
                Reversal. Something the protagonist believed turns out false (false victory or false defeat).
                The conflict becomes personal. The ticking clock visibly accelerates.
                </beat>
                """;
            case TIGHTENING -> """
                <beat name="TIGHTENING">
                Options shrink. Costs from earlier choices return (use story_state and consequence seeds).
                An ally fails, doubts, or betrays. No scene in this phase may lower tension.
                </beat>
                """;
            case CRISIS -> """
                <beat name="CRISIS">
                The lowest point. The protagonist loses something essential: an ally, a resource, a belief, or a safe place.
                All four choices are choices between different kinds of loss. There is no clean option.
                </beat>
                """;
            case CLIMAX -> """
                <beat name="CLIMAX">
                The final confrontation with the antagonist force, at the exact center of the mainConflict.
                All four choices are irreversible and each points toward a different ending from the bible.
                Every open setup that matters must be paid off in this scene or be ready to pay off in the resolution.
                </beat>
                """;
            case RESOLUTION -> """
                <beat name="RESOLUTION">
                The final aftermath. The conflict is definitively resolved. Reflect the philosophy and player's choices.
                </beat>
                """;
        };
    }
}

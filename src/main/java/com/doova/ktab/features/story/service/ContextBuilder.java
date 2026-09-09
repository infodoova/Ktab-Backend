package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.model.SessionState;
import com.doova.ktab.features.story.model.Turn;
import com.doova.ktab.features.story.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ContextBuilder {

    public String buildTurnUserPrompt(
            String rollingSummary,
            List<Turn> lastRawTurns,
            SessionState state,
            String lastChoiceIdOrBlank,
            int nextTurnIndex,
            int maxScenes
    ) {

        String lastTurnsText = lastRawTurns.stream().sorted(Comparator.comparingInt(Turn::getTurnIndex)).map(t -> """
                TURN %d
                SCENE:
                %s
                CHOICE_TAKEN: %s
                """.formatted(t.getTurnIndex(), t.getSceneText(), t.getChosenChoiceId() == null ? "NONE" : t.getChosenChoiceId())).reduce("", (a, b) -> a + "\n" + b);

        String stateJson = JsonUtil.write(state);

        int remainingTurns = Math.max(0, (maxScenes - nextTurnIndex) + 1);
        String endingPhase =
                remainingTurns <= 1 ? "FINAL" :
                remainingTurns == 2 ? "PENULTIMATE" :
                remainingTurns <= 4 ? "ENDING_APPROACH" :
                "MIDDLE";

        boolean isFinalTurn = nextTurnIndex >= maxScenes;

        return """
                TURN_META:
                - NEXT_TURN_INDEX: %d
                - MAX_SCENES: %d
                - IS_FINAL_TURN: %s
                - REMAINING_TURNS: %d
                - ENDING_PHASE: %s
                
                ROLLING_SUMMARY (structured JSON, may be empty):
                %s
                
                LAST_RAW_TURNS (verbatim):
                %s
                
                SESSION_STATE (authoritative, do not contradict):
                %s
                
                LAST_CHOICE_ID:
                %s
                
                INSTRUCTIONS:
                - If IS_FINAL_TURN is false: write the next scene and 4 choices in the required STRICT JSON format.
                - If IS_FINAL_TURN is true: write ONLY the final scene JSON with sceneText and NO choices.
                """.formatted(
                        nextTurnIndex,
                        maxScenes,
                        isFinalTurn ? "true" : "false",
                        remainingTurns,
                        endingPhase,
                        rollingSummary == null ? "" : rollingSummary,
                        lastTurnsText,
                        stateJson,
                        lastChoiceIdOrBlank == null ? "" : lastChoiceIdOrBlank
                );
    }

    public String buildSummaryUserPrompt(String existingRollingSummary, SessionState state, List<Turn> fourTurns) {

        String turnsText = fourTurns.stream().sorted(Comparator.comparingInt(Turn::getTurnIndex)).map(t -> """
                TURN %d
                SCENE:
                %s
                CHOICE_TAKEN: %s
                """.formatted(t.getTurnIndex(), t.getSceneText(), t.getChosenChoiceId() == null ? "NONE" : t.getChosenChoiceId())).reduce("", (a, b) -> a + "\n" + b);

        return """
                EXISTING_ROLLING_SUMMARY:
                %s
                
                SESSION_STATE SNAPSHOT (authoritative):
                %s
                
                NEW_TURNS_TO_COMPRESS (exactly these turns, chronological):
                %s
                
                Return UPDATED structured memory JSON in the strict format.
                """.formatted(existingRollingSummary == null ? "" : existingRollingSummary, JsonUtil.write(state), turnsText);
    }
}

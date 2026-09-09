package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.enums.ChoiceId;
import com.doova.ktab.features.story.enums.MoralWeight;
import com.doova.ktab.features.story.enums.Visibility;
import com.doova.ktab.features.story.model.*;

public final class ChoiceEffectResolver {

    private ChoiceEffectResolver() {}

    public static SessionState apply(SessionState state, ChoiceId choice) {

        if (state instanceof PoliticalState ps) {
            PoliticalState next = ps.nextTurn();
            return switch (choice) {
                case A -> next.adjustTrust(+2, -1).withVisibility(Visibility.PUBLIC);
                case B -> next.adjustTrust(+1, 0);
                case C -> next.adjustTrust(-1, +2).withMoral(MoralWeight.COMPROMISED);
                case D -> next;
            };
        }

        if (state instanceof PsychologicalState psy) {
            PsychologicalState next = psy.nextTurn();
            return switch (choice) {
                case A -> next.bumpAnxiety(+10);
                case B -> next.bumpAttachment(+5);
                case C -> next.bumpAttachment(-5).bumpAnxiety(+5);
                case D -> next.bumpAnxiety(+2);
            };
        }

        if (state instanceof SurvivalState surv) {
            SurvivalState next = surv.nextTurn();
            return switch (choice) {
                case A -> next.scavenge();
                case B -> next.rest();
                case C -> next;
                case D -> next;
            };
        }

        if (state instanceof MoralState moral) {
            MoralState next = moral.nextTurn();
            return switch (choice) {
                case A -> next.uphold();
                case B -> next;
                case C -> next.compromise();
                case D -> next;
            };
        }

        throw new IllegalStateException("Unsupported SessionState: " + state.getClass());
    }

}

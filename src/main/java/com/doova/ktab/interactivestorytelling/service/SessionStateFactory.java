package com.doova.ktab.interactivestorytelling.service;

import com.doova.ktab.interactivestorytelling.model.*;
import org.springframework.stereotype.Component;

@Component
public class SessionStateFactory {

    public SessionState initialStateFor(Story story) {
        return switch (story.getLens()) {
            case POLITICAL -> PoliticalState.initial();
            case PSYCHOLOGICAL -> PsychologicalState.initial();
            case SURVIVAL -> SurvivalState.initial();
            case MORAL -> MoralState.initial();
        };
    }
}

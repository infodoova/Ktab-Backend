package com.doova.ktab.features.story.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PoliticalState.class, name = "PoliticalState"),
        @JsonSubTypes.Type(value = PsychologicalState.class, name = "PsychologicalState"),
        @JsonSubTypes.Type(value = SurvivalState.class, name = "SurvivalState"),
        @JsonSubTypes.Type(value = MoralState.class, name = "MoralState")
})
public sealed interface SessionState extends Serializable
        permits PoliticalState, PsychologicalState, SurvivalState, MoralState {

    int turnCount();
}

package com.doova.ktab.features.story.dto;

public record TurnResponse(int turnIndex, String sceneText, String imageUrl, String chosenId,
                           ChoiceResponse A, ChoiceResponse B, ChoiceResponse C, ChoiceResponse D) {
}

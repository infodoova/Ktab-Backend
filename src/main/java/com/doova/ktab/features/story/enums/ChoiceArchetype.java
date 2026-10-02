package com.doova.ktab.features.story.enums;

public enum ChoiceArchetype {
    CONFRONT,
    PROTECT,
    MANIPULATE,
    WITHDRAW;

    public static ChoiceArchetype forLetter(String letter) {
        if (letter == null) return CONFRONT;
        return switch (letter.trim().toUpperCase()) {
            case "A" -> CONFRONT;
            case "B" -> PROTECT;
            case "C" -> MANIPULATE;
            case "D" -> WITHDRAW;
            default -> CONFRONT;
        };
    }
}

package com.doova.ktab.features.story.enums;

public enum ChoiceId {
    A, B, C, D;

    public static ChoiceId from(String raw) {
        try {
            return ChoiceId.valueOf(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("choiceId must be A|B|C|D");
        }
    }
}

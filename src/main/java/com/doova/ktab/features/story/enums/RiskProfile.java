package com.doova.ktab.features.story.enums;

public enum RiskProfile {
    GAMBLE,
    STEADY,
    SAFE,
    PERIL;

    public static RiskProfile fromSafe(String raw) {
        if (raw == null) return STEADY;
        try {
            return RiskProfile.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STEADY;
        }
    }
}

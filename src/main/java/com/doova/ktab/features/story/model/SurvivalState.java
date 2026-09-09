package com.doova.ktab.features.story.model;

public final class SurvivalState implements SessionState {

    private final int turnCount;
    private final int stamina; // 0..100
    private final int hunger;  // 0..100

    public SurvivalState() {
        this(0, 80, 20);
    }

    public SurvivalState(int turnCount, int stamina, int hunger) {
        this.turnCount = turnCount;
        this.stamina = clamp(stamina);
        this.hunger = clamp(hunger);
    }

    public static SurvivalState initial() {
        return new SurvivalState();
    }

    @Override public int turnCount() { return turnCount; }
    public int stamina() { return stamina; }
    public int hunger() { return hunger; }

    public SurvivalState nextTurn() {
        return new SurvivalState(turnCount + 1, stamina - 5, hunger + 5);
    }

    public SurvivalState rest() {
        return new SurvivalState(turnCount, stamina + 15, hunger);
    }

    public SurvivalState scavenge() {
        return new SurvivalState(turnCount, stamina - 10, hunger - 20);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}

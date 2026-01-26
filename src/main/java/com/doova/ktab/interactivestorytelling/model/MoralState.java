package com.doova.ktab.interactivestorytelling.model;

public final class MoralState implements SessionState {

    private final int turnCount;
    private final int guilt;     // 0..100
    private final int integrity; // 0..100

    public MoralState() {
        this(0, 0, 100);
    }

    public MoralState(int turnCount, int guilt, int integrity) {
        this.turnCount = turnCount;
        this.guilt = clamp(guilt);
        this.integrity = clamp(integrity);
    }

    public static MoralState initial() {
        return new MoralState();
    }

    @Override public int turnCount() { return turnCount; }
    public int guilt() { return guilt; }
    public int integrity() { return integrity; }

    public MoralState nextTurn() {
        return new MoralState(turnCount + 1, guilt, integrity);
    }

    public MoralState compromise() {
        return new MoralState(turnCount, guilt + 15, integrity - 20);
    }

    public MoralState uphold() {
        return new MoralState(turnCount, guilt - 5, integrity + 5);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}

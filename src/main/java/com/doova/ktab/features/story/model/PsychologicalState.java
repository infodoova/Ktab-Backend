package com.doova.ktab.features.story.model;

public final class PsychologicalState implements SessionState {

    private final int turnCount;
    private final int anxiety;   // 0..100
    private final int attachment; // -50..+50

    public PsychologicalState() {
        this(0, 10, 0);
    }

    public PsychologicalState(int turnCount, int anxiety, int attachment) {
        this.turnCount = turnCount;
        this.anxiety = anxiety;
        this.attachment = attachment;
    }

    public static PsychologicalState initial() { return new PsychologicalState(); }

    @Override public int turnCount() { return turnCount; }
    public int anxiety() { return anxiety; }
    public int attachment() { return attachment; }

    public PsychologicalState nextTurn() {
        return new PsychologicalState(turnCount + 1, anxiety, attachment);
    }

    public PsychologicalState bumpAnxiety(int delta) {
        return new PsychologicalState(turnCount, clamp(anxiety + delta, 0, 100), attachment);
    }

    public PsychologicalState bumpAttachment(int delta) {
        return new PsychologicalState(turnCount, anxiety, clamp(attachment + delta, -50, 50));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}

package com.doova.ktab.features.storybook.orchestrator;

public record StepOutcome(Type type, String reason) {

    public enum Type { SUCCESS, RETRY, FAIL }

    public static StepOutcome success() {
        return new StepOutcome(Type.SUCCESS, null);
    }

    public static StepOutcome retry(String reason) {
        return new StepOutcome(Type.RETRY, reason);
    }

    public static StepOutcome fail(String reason) {
        return new StepOutcome(Type.FAIL, reason);
    }
}

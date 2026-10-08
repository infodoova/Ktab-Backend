package com.doova.ktab.features.storybook.enums;

public enum PageImageStatus {
    GENERATED, QA_PASSED, QA_FAILED, FLAGGED,
    /** Accepted by the pipeline itself after the attempts ran out and only the scene check failed. */
    ACCEPTED_AUTO,
    ACCEPTED_BY_ADMIN
}

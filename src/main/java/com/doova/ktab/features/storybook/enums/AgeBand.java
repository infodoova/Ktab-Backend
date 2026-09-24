package com.doova.ktab.features.storybook.enums;

/** Word limits are decision D2 in the overview: starting values that Phase 0 tunes. */
public enum AgeBand {
    AGE_3_5(3, 5, 25),
    AGE_6_8(6, 8, 45),
    AGE_9_10(9, 10, 70);

    private final int minAge;
    private final int maxAge;
    private final int maxWordsPerPage;

    AgeBand(int minAge, int maxAge, int maxWordsPerPage) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.maxWordsPerPage = maxWordsPerPage;
    }

    public int minAge() { return minAge; }
    public int maxAge() { return maxAge; }
    public int maxWordsPerPage() { return maxWordsPerPage; }
}

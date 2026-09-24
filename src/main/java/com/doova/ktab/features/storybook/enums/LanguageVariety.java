package com.doova.ktab.features.storybook.enums;

public enum LanguageVariety {
    MSA("Modern Standard Arabic", null),
    LEBANESE("Lebanese Arabic", "storybook/dialects/lebanese.md"),
    EGYPTIAN("Egyptian Arabic", "storybook/dialects/egyptian.md"),
    GULF("Gulf Arabic", "storybook/dialects/gulf.md");

    private final String en;
    private final String dialectGuideResource;

    LanguageVariety(String en, String dialectGuideResource) {
        this.en = en;
        this.dialectGuideResource = dialectGuideResource;
    }

    public String en() { return en; }
    public String dialectGuideResource() { return dialectGuideResource; }
    public boolean isDialect() { return this != MSA; }
}

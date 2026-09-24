package com.doova.ktab.features.storybook.enums;

/** Curated catalogue (spec: "never free-form"). One style in the MVP. */
public enum ArtStyle {
    SOFT_WATERCOLOR("storybook/styles/soft_watercolor.png");

    private final String referenceResource;
    ArtStyle(String referenceResource) { this.referenceResource = referenceResource; }
    public String referenceResource() { return referenceResource; }
}

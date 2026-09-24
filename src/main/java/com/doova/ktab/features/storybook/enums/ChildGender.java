package com.doova.ktab.features.storybook.enums;

public enum ChildGender {
    BOY("boy"), GIRL("girl");

    private final String en;
    ChildGender(String en) { this.en = en; }
    public String en() { return en; }
}

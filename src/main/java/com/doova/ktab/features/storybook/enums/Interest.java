package com.doova.ktab.features.storybook.enums;

/** Structured on purpose: interests are never free text (spec, "Inputs"). */
public enum Interest {
    FOOTBALL("playing football"),
    CATS("cats"),
    DOGS("dogs"),
    DINOSAURS("dinosaurs"),
    SPACE("space and planets"),
    SEA_CREATURES("sea creatures"),
    DRAWING("drawing and colouring"),
    MUSIC("music and singing"),
    CARS("cars"),
    HORSES("horses"),
    BOOKS("reading books"),
    COOKING("helping in the kitchen");

    private final String en;
    Interest(String en) { this.en = en; }
    public String en() { return en; }
}

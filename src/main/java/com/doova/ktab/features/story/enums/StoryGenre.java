package com.doova.ktab.features.story.enums;

import lombok.Getter;

@Getter
public enum StoryGenre {
    ADVENTURE("مغامرة", "Adventure"),
    FANTASY("خيال", "Fantasy"),
    MYSTERY("غموض", "Mystery"),
    SCI_FI("خيال علمي", "Sci-Fi"),
    HORROR("رعب", "Horror"),
    DRAMA("دراما", "Drama");

    private final String labelAr;
    private final String labelEn;

    StoryGenre(String labelAr, String labelEn) {
        this.labelAr = labelAr;
        this.labelEn = labelEn;
    }
}

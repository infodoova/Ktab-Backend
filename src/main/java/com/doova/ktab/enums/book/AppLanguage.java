package com.doova.ktab.enums.book;

import lombok.Getter;

@Getter
public enum AppLanguage {
    ARABIC("ar", "العربية", "Arabic"),
    ENGLISH("en", "الإنجليزية", "English"),
    FRENCH("fr", "الفرنسية", "French");

    private final String code;
    private final String labelAr;
    private final String labelEn;

    AppLanguage(String code, String labelAr, String labelEn) {
        this.code = code;
        this.labelAr = labelAr;
        this.labelEn = labelEn;
    }
}

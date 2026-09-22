package com.doova.ktab.enums.book;

import lombok.Getter;

@Getter
public enum BookAgeCategory {
    CHILDREN(3, 8, "أطفال (3-8 سنوات)", "Children (3-8 years)"),
    EARLY_TEENS(9, 15, "ناشئة (9-15 سنة)", "Early Teens (9-15 years)"),
    YOUTH(16, 24, "شباب (16-24 سنة)", "Youth (16-24 years)"),
    ADULTS(25, null, "كبار (+25)", "Adults (25+ years)");

    private final Integer minAge;
    private final Integer maxAge;
    private final String labelAr;
    private final String labelEn;

    BookAgeCategory(Integer minAge, Integer maxAge, String labelAr, String labelEn) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.labelAr = labelAr;
        this.labelEn = labelEn;
    }
}

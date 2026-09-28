package com.doova.ktab.features.imagegen.enums;

import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Getter
public enum ImageAspectRatio {
    SQUARE_1_1("1:1", "imagegen.aspect_ratio.square_1_1.name", "imagegen.aspect_ratio.square_1_1.desc", "Square format, balanced central composition"),
    PORTRAIT_3_4("3:4", "imagegen.aspect_ratio.portrait_3_4.name", "imagegen.aspect_ratio.portrait_3_4.desc", "Standard portrait book illustration format"),
    PORTRAIT_9_16("9:16", "imagegen.aspect_ratio.portrait_9_16.name", "imagegen.aspect_ratio.portrait_9_16.desc", "Vertical mobile screen / story format"),
    LANDSCAPE_4_3("4:3", "imagegen.aspect_ratio.landscape_4_3.name", "imagegen.aspect_ratio.landscape_4_3.desc", "Standard landscape spread illustration format"),
    LANDSCAPE_16_9("16:9", "imagegen.aspect_ratio.landscape_16_9.name", "imagegen.aspect_ratio.landscape_16_9.desc", "Cinematic widescreen landscape format"),
    BOOK_COVER_2_3("2:3", "imagegen.aspect_ratio.book_cover_2_3.name", "imagegen.aspect_ratio.book_cover_2_3.desc", "Classic vertical book cover ratio");

    private final String ratio;
    private final String nameKey;
    private final String descKey;
    private final String description;

    ImageAspectRatio(String ratio, String nameKey, String descKey, String description) {
        this.ratio = ratio;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.description = description;
    }

    public String getLocalizedName(MessageSource messageSource) {
        if (messageSource == null) {
            return ratio;
        }
        return messageSource.getMessage(nameKey, null, ratio, LocaleContextHolder.getLocale());
    }

    public String getLocalizedDescription(MessageSource messageSource) {
        if (messageSource == null) {
            return description;
        }
        return messageSource.getMessage(descKey, null, description, LocaleContextHolder.getLocale());
    }

    public static ImageAspectRatio fromRatio(String ratio) {
        if (ratio == null || ratio.isBlank()) {
            return SQUARE_1_1;
        }
        for (ImageAspectRatio ar : values()) {
            if (ar.getRatio().equalsIgnoreCase(ratio.trim())) {
                return ar;
            }
        }
        return SQUARE_1_1;
    }
}

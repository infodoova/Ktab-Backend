package com.doova.ktab.features.imagegen.enums;

import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Getter
public enum ImageTheme {
    REALISTIC(
            "imagegen.theme.realistic.name",
            "imagegen.theme.realistic.desc",
            "Photorealistic",
            "hyper-realistic photographic style, authentic textures, natural lighting, sharp focus, 8k resolution feel"
    ),
    CARTOON(
            "imagegen.theme.cartoon.name",
            "imagegen.theme.cartoon.desc",
            "Cartoon",
            "vibrant animated cartoon style, expressive contours, bold clean line art, playful aesthetic"
    ),
    WATERCOLOR(
            "imagegen.theme.watercolor.name",
            "imagegen.theme.watercolor.desc",
            "Watercolor",
            "delicate watercolor painting style, soft fluid gradients, visible paper grain, gentle bleeding pigments"
    ),
    OIL_PAINTING(
            "imagegen.theme.oil_painting.name",
            "imagegen.theme.oil_painting.desc",
            "Oil Painting",
            "classical oil painting on canvas, visible brush strokes, rich heavy impasto texture, dramatic chiaroscuro lighting"
    ),
    DIGITAL_ART(
            "imagegen.theme.digital_art.name",
            "imagegen.theme.digital_art.desc",
            "Digital Art",
            "modern digital concept art, polished matte painting, atmospheric lighting, detailed background composition"
    ),
    FLAT_DESIGN(
            "imagegen.theme.flat_design.name",
            "imagegen.theme.flat_design.desc",
            "Flat Design",
            "minimalist modern flat vector illustration, harmonious color blocking, crisp geometric shapes, elegant simplicity"
    ),
    SKETCH(
            "imagegen.theme.sketch.name",
            "imagegen.theme.sketch.desc",
            "Pencil Sketch",
            "detailed fine pencil sketch, cross-hatching, graphite shading, hand-drawn vintage illustration look"
    ),
    ANIME(
            "imagegen.theme.anime.name",
            "imagegen.theme.anime.desc",
            "Anime",
            "contemporary anime aesthetic, cel-shaded characters, vibrant luminous atmosphere, cinematic framing"
    ),
    PIXEL_ART(
            "imagegen.theme.pixel_art.name",
            "imagegen.theme.pixel_art.desc",
            "Pixel Art",
            "classic 16-bit retro pixel art, crisp pixel boundaries, carefully constrained palette, nostalgic video game charm"
    ),
    FANTASY_ART(
            "imagegen.theme.fantasy_art.name",
            "imagegen.theme.fantasy_art.desc",
            "Epic Fantasy",
            "epic high-fantasy book illustration, mythical atmosphere, magical volumetric lighting, intricate ornate details"
    );

    private final String nameKey;
    private final String descKey;
    private final String displayName;
    private final String styleDirective;

    ImageTheme(String nameKey, String descKey, String displayName, String styleDirective) {
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.displayName = displayName;
        this.styleDirective = styleDirective;
    }

    public String getLocalizedName(MessageSource messageSource) {
        if (messageSource == null) {
            return displayName;
        }
        return messageSource.getMessage(nameKey, null, displayName, LocaleContextHolder.getLocale());
    }

    public String getLocalizedDescription(MessageSource messageSource) {
        if (messageSource == null) {
            return styleDirective;
        }
        return messageSource.getMessage(descKey, null, styleDirective, LocaleContextHolder.getLocale());
    }
}

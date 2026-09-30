package com.doova.ktab.features.trailer.endcard;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EndCardHtmlTest {

    private final EndCardSpec full = new EndCardSpec("ثورة دونالد ترامب", "ألكسندر دوغين",
            Path.of("cover.jpg"), Path.of("logo.png"));

    @Test
    void eachLayerShowsOnlyItsOwnElement() {
        String html = EndCardHtml.build(full, EndCardHtml.Layer.TITLE);
        assertThat(html).contains("dir=\"rtl\"").contains("class=\"only-title\"")
                .contains("ثورة دونالد ترامب").contains("fonts/Cairo.ttf");
    }

    @Test
    void longTitleShrinksAndIsNeverTruncated() {
        String longTitle = "عنوان طويل جدا لكتاب يتجاوز الحد المعتاد من الأحرف في سطر واحد";
        String html = EndCardHtml.build(new EndCardSpec(longTitle, "م", null, Path.of("logo.png")),
                EndCardHtml.Layer.TITLE);
        assertThat(html).contains("font-size:40px").doesNotContain("line-clamp").doesNotContain("ellipsis");
    }

    @Test
    void mediumTitleUsesTheMiddleStep() {
        String html = EndCardHtml.build(new EndCardSpec("عنوان متوسط الطول لكتاب جميل جدا", "م", null,
                Path.of("logo.png")), EndCardHtml.Layer.TITLE); // 32 chars -> 60 px step
        assertThat(html).contains("font-size:60px");
    }

    @Test
    void noCoverCentresTheText() {
        EndCardSpec noCover = new EndCardSpec("ع", "م", null, Path.of("logo.png"));
        assertThat(EndCardHtml.build(noCover, EndCardHtml.Layer.TITLE)).contains("class=\"stage no-cover\"");
        assertThat(EndCardHtml.layersFor(noCover)).doesNotContain(EndCardHtml.Layer.COVER);
    }

    @Test
    void blankAuthorHasNoAuthorLayer() {
        assertThat(EndCardHtml.layersFor(new EndCardSpec("ع", " ", Path.of("c.jpg"), Path.of("l.png"))))
                .containsExactly(EndCardHtml.Layer.SCRIM, EndCardHtml.Layer.COVER,
                        EndCardHtml.Layer.TITLE, EndCardHtml.Layer.LOGO);
    }

    @Test
    void titleIsHtmlEscaped() {
        String html = EndCardHtml.build(new EndCardSpec("<b>x</b>", "م", null, Path.of("l.png")),
                EndCardHtml.Layer.TITLE);
        assertThat(html).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    }

    @Test
    void layerFileNamesMatchTheMountedPaths() {
        assertThat(EndCardHtml.Layer.SCRIM.fileName()).isEqualTo("scrim.png");
        assertThat(EndCardHtml.Layer.LOGO.fileName()).isEqualTo("logo.png");
    }
}

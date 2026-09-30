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
        assertThat(html).contains("font-size:38px").doesNotContain("line-clamp").doesNotContain("ellipsis");
    }

    @Test
    void mediumTitleUsesTheMiddleStep() {
        String html = EndCardHtml.build(new EndCardSpec("عنوان متوسط الطول لكتاب جميل جدا", "م", null,
                Path.of("logo.png")), EndCardHtml.Layer.TITLE); // 32 chars -> 52 px step
        assertThat(html).contains("font-size:52px");
    }

    @Test
    void aColonSplitsTheTitleFromItsSubtitle() {
        EndCardSpec spec = new EndCardSpec("الصين والولايات المتحدة: حتمية الحرب الاقتصادية", "عدنان منصور",
                Path.of("c.jpg"), Path.of("l.png"));
        assertThat(EndCardHtml.layersFor(spec)).containsExactly(EndCardHtml.Layer.SCRIM, EndCardHtml.Layer.COVER,
                EndCardHtml.Layer.TITLE, EndCardHtml.Layer.SUBTITLE, EndCardHtml.Layer.RULE, EndCardHtml.Layer.AUTHOR,
                EndCardHtml.Layer.LOGO);
        assertThat(EndCardHtml.build(spec, EndCardHtml.Layer.TITLE))
                .contains("<div class=\"title\" id=\"title\">الصين والولايات المتحدة</div>");
        assertThat(EndCardHtml.build(spec, EndCardHtml.Layer.SUBTITLE))
                .contains("<div class=\"subtitle\">حتمية الحرب الاقتصادية</div>").contains("class=\"only-subtitle\"");
    }

    @Test
    void aTitleWithoutAColonHasNoSubtitleLayer() {
        assertThat(EndCardHtml.layersFor(new EndCardSpec("ثورة دونالد ترامب", "م", null, Path.of("l.png"))))
                .doesNotContain(EndCardHtml.Layer.SUBTITLE);
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
    void theRuleBelongsToItsOwnLayerAndOnlyExistsWithAnAuthor() {
        EndCardSpec spec = new EndCardSpec("ع", "م", null, Path.of("l.png"));
        assertThat(EndCardHtml.build(spec, EndCardHtml.Layer.RULE)).contains("class=\"only-rule\"")
                .contains("<div class=\"rule\"></div>");
        assertThat(EndCardHtml.layersFor(new EndCardSpec("ع", "", null, Path.of("l.png"))))
                .doesNotContain(EndCardHtml.Layer.RULE);
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
        assertThat(EndCardHtml.Layer.SUBTITLE.fileName()).isEqualTo("subtitle.png");
        assertThat(EndCardHtml.Layer.RULE.fileName()).isEqualTo("rule.png");
    }
}

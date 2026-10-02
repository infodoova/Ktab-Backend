package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StorybookHtmlBuilderTest {

    public static StorybookHtmlBuilder builder() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new StorybookHtmlBuilder(engine);
    }

    private static BookRenderModel model(String dedication) {
        return RenderModelFactory.build("يومي الأول", "سامي", dedication, TashkeelLevel.FULL, List.of(
                new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.BOTTOM)));
    }

    @Test
    void isRightToLeftArabicWithSquarePages() {
        String html = builder().build(model(null));
        assertThat(html).contains("dir=\"rtl\"").contains("lang=\"ar\"").contains("size: 21cm 21cm");
        assertThat(html).contains("Cairo.ttf");
        assertThat(html.split("class=\"page ", -1)).hasSize(5); // cover, dedication, 1 story, back
    }

    @Test
    void storyTextSitsInItsZone() {
        String html = builder().build(model(null));
        assertThat(html).contains("zone-bottom").contains("ذَهَبَ سامي.").contains("img/p1.jpg");
    }

    @Test
    void escapesDedication() {
        String html = builder().build(model("<img src=x onerror=alert(1)><script>alert(2)</script>"));
        assertThat(html).doesNotContain("<img src=x").doesNotContain("<script>alert(2)");
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
    }
}

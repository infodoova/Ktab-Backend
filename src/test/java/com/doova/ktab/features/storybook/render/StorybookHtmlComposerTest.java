package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookHtmlComposerTest {

    private StorybookHtmlComposer composer;

    @BeforeEach
    void setUp() {
        org.thymeleaf.spring6.SpringTemplateEngine engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        composer = new StorybookHtmlComposer(engine);
    }

    @Test
    void composesValidHtmlWithCoverAndStoryPages() {
        Storybook book = new Storybook();
        book.setTitleAr("مغامرة سامي في الفضاء");
        book.setDedication("إلى بطلي الصغير سامي");
        book.setTashkeelLevel(TashkeelLevel.FULL);

        StoryInputs inputs = new StoryInputs("سامي", com.doova.ktab.features.storybook.enums.ChildGender.BOY,
                com.doova.ktab.features.storybook.enums.AgeBand.AGE_6_8,
                null, List.of(), null, null, null);
        book.setInputs(inputs);

        StorybookPage cover = new StorybookPage();
        cover.setPageIndex(0);
        cover.setKind(PageKind.COVER);

        StorybookPage page1 = new StorybookPage();
        page1.setPageIndex(1);
        page1.setKind(PageKind.STORY);
        page1.setTextAr("انطلقَ سامي في مركبتِهِ الفضائيةِ نحوَ النجومِ.");
        page1.setTextZone(TextZone.BOTTOM);

        byte[] dummyPng = new byte[]{1, 2, 3};
        Map<Integer, byte[]> images = Map.of(0, dummyPng, 1, dummyPng);

        String html = composer.composeHtml(book, List.of(cover, page1), images);

        assertThat(html).contains("مغامرة سامي في الفضاء");
        assertThat(html).contains("سامي");
        assertThat(html).contains("إلى بطلي الصغير سامي");
        assertThat(html).contains("انطلقَ سامي");
        assertThat(html).contains("data:image/png;base64,AQID");
        assertThat(html).contains("zone-bottom");
    }
}

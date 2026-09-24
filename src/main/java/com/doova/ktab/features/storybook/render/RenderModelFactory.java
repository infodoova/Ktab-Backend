package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.TashkeelFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared by the PDF and the reader manifest so both always show the same text. */
public final class RenderModelFactory {

    public record PageSource(int pageIndex, PageKind kind, String textAr, TextZone textZone) {
    }

    private RenderModelFactory() {
    }

    public static BookRenderModel build(String titleAr, String childNameAr, String dedication, TashkeelLevel level,
                                        List<PageSource> sources) {
        List<PageSource> sorted = sources.stream().sorted(Comparator.comparingInt(PageSource::pageIndex)).toList();
        List<RenderPage> pages = new ArrayList<>();
        int order = 0;
        for (PageSource s : sorted) {
            if (s.kind() == PageKind.COVER) {
                pages.add(new RenderPage(order++, RenderPage.Kind.COVER, null, TextZone.TOP, imageFile(s.pageIndex())));
            }
        }
        String dedicationText = dedication == null || dedication.isBlank()
                ? "إلى " + childNameAr + "، بكل الحب."
                : dedication;
        pages.add(new RenderPage(order++, RenderPage.Kind.DEDICATION, TashkeelFilter.apply(dedicationText, level), null, null));
        for (PageSource s : sorted) {
            if (s.kind() == PageKind.STORY) {
                pages.add(new RenderPage(order++, RenderPage.Kind.STORY, TashkeelFilter.apply(s.textAr(), level),
                        s.textZone(), imageFile(s.pageIndex())));
            }
        }
        String back = "النهاية\nكُتبت هذه القصة خصيصًا لـ" + childNameAr + ".";
        pages.add(new RenderPage(order, RenderPage.Kind.BACK, TashkeelFilter.apply(back, level), null, null));
        return new BookRenderModel(TashkeelFilter.apply(titleAr, level), childNameAr, pages);
    }

    public static String imageFile(int pageIndex) {
        return "img/p" + pageIndex + ".jpg";
    }
}

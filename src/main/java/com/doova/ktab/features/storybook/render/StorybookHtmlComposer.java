package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.story.TashkeelFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

@Component
@RequiredArgsConstructor
public class StorybookHtmlComposer {

    private final TemplateEngine templateEngine;

    public record RenderedPage(int pageNumber, String text, String textZone, String imageUrl) {}

    public String composeHtml(Storybook book, List<StorybookPage> pages, Map<Integer, byte[]> imagesByPageIndex) {
        Context context = new Context(new Locale("ar"));
        context.setVariable("title", book.getTitleAr());
        String childName = book.getInputs() != null ? book.getInputs().childNameAr() : "";
        context.setVariable("childName", childName);
        context.setVariable("dedication", book.getDedication());

        byte[] coverBytes = imagesByPageIndex.get(0);
        if (coverBytes != null && coverBytes.length > 0) {
            context.setVariable("coverImageUrl", toDataUri(coverBytes));
        }

        List<RenderedPage> storyPages = new ArrayList<>();
        for (StorybookPage page : pages) {
            if (page.getKind() == PageKind.COVER) {
                continue;
            }
            byte[] pageBytes = imagesByPageIndex.get(page.getPageIndex());
            String imageUrl = (pageBytes != null && pageBytes.length > 0) ? toDataUri(pageBytes) : null;
            String text = page.getTextAr();
            if (text != null && book.getTashkeelLevel() != null) {
                text = TashkeelFilter.apply(text, book.getTashkeelLevel());
            }
            String textZone = page.getTextZone() != null ? page.getTextZone().name() : "BOTTOM";
            storyPages.add(new RenderedPage(page.getPageIndex(), text, textZone, imageUrl));
        }

        context.setVariable("storyPages", storyPages);
        return templateEngine.process("storybook/book", context);
    }

    private static String toDataUri(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}

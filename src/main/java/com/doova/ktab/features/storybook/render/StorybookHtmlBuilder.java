package com.doova.ktab.features.storybook.render;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Component
public class StorybookHtmlBuilder {

    private final ITemplateEngine templateEngine;

    public StorybookHtmlBuilder(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String build(BookRenderModel model) {
        Context ctx = new Context(Locale.forLanguageTag("ar"));
        ctx.setVariable("model", model);
        return templateEngine.process("storybook/book", ctx);
    }
}

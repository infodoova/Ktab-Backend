package com.doova.ktab.features.storybook.render;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;

@Component
public class StorybookHtmlBuilder {

    private final ITemplateEngine templateEngine;

    public StorybookHtmlBuilder() {
        this(createDefaultEngine());
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public StorybookHtmlBuilder(ITemplateEngine templateEngine) {
        // Use a dedicated template engine with templates/ prefix to avoid collisions
        // with email template prefixes (e.g. templates/emails/).
        this.templateEngine = createDefaultEngine();
    }

    private static ITemplateEngine createDefaultEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    public String build(BookRenderModel model) {
        Context ctx = new Context(Locale.forLanguageTag("ar"));
        ctx.setVariable("model", model);
        return templateEngine.process("storybook/book", ctx);
    }
}

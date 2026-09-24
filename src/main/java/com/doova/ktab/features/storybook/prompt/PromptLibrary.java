package com.doova.ktab.features.storybook.prompt;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptLibrary {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String name) {
        return load("storybook/prompts/" + name + ".md");
    }

    public String dialectGuide(LanguageVariety variety) {
        return variety.isDialect() ? load(variety.dialectGuideResource()) : "";
    }

    private String load(String path) {
        return cache.computeIfAbsent(path, p -> {
            ClassPathResource resource = new ClassPathResource(p);
            if (!resource.exists()) {
                throw new IllegalStateException("Missing storybook resource: " + p);
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read storybook resource: " + p, e);
            }
        });
    }
}

package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

@Component
public class StyleReferences {

    private final Map<ArtStyle, byte[]> cache = new EnumMap<>(ArtStyle.class);

    public synchronized byte[] get(ArtStyle style) {
        return cache.computeIfAbsent(style, s -> {
            ClassPathResource resource = new ClassPathResource(s.referenceResource());
            if (!resource.exists()) {
                throw new IllegalStateException("Missing style reference " + s.referenceResource()
                        + " (art-director deliverable, see the storybook overview plan)");
            }
            try (InputStream in = resource.getInputStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + s.referenceResource(), e);
            }
        });
    }
}

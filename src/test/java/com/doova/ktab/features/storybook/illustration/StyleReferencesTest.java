package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StyleReferencesTest {

    @Test
    void everyArtStyleHasAReferenceImage() {
        StyleReferences refs = new StyleReferences();
        for (ArtStyle style : ArtStyle.values()) {
            assertThat(refs.get(style)).isNotEmpty();
        }
    }
}

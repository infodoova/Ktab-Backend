package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookKeysTest {

    @Test
    void keysAreDeterministic() {
        assertThat(StorybookKeys.characterSheet(7L, CharacterKind.CHILD, 2)).isEqualTo("storybook/7/characters/child/v2.png");
        assertThat(StorybookKeys.pageImage(7L, 0, 3)).isEqualTo("storybook/7/pages/0/g3.png");
        assertThat(StorybookKeys.photo(7L)).isEqualTo("storybook/7/photo/source.enc");
        assertThat(StorybookKeys.pdf(7L, 1)).isEqualTo("storybook/7/book-r1.pdf");
    }

    @Test
    void aSupportingCharactersSheetHasItsOwnKey() {
        assertThat(StorybookKeys.supportingSheet(7L, "grandpa", 1)).isEqualTo("storybook/7/characters/supporting/grandpa/v1.png");
        assertThat(StorybookKeys.supportingSheet(7L, "Sal ma/../x", 2)).isEqualTo("storybook/7/characters/supporting/sal-ma-x/v2.png");
    }
}

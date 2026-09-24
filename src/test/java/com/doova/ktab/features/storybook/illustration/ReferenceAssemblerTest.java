package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.story.CharacterInScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceAssemblerTest {

    private final byte[] child = {1}, style = {2}, companion = {3};

    @Test
    void childAndStyleAlwaysComeFirst() {
        var refs = ReferenceAssembler.forPage(child, style, companion, List.of(new CharacterInScene("CHILD", "happy")));
        assertThat(refs).extracting(r -> r.bytes()[0]).containsExactly((byte) 1, (byte) 2);
    }

    @Test
    void companionIsAddedOnlyWhenInTheScene() {
        var refs = ReferenceAssembler.forPage(child, style, companion,
                List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "curious")));
        assertThat(refs).extracting(r -> r.bytes()[0]).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void noCompanionSheetMeansNoCompanionReference() {
        var refs = ReferenceAssembler.forPage(child, style, null, List.of(new CharacterInScene("COMPANION", "x")));
        assertThat(refs).hasSize(2);
    }
}

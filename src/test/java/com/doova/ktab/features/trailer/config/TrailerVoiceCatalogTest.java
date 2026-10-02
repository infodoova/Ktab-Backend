package com.doova.ktab.features.trailer.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrailerVoiceCatalogTest {

    @Test
    void blankCatalogMeansNoChoice() {
        assertThat(new TrailerProperties().voiceCatalog()).isEmpty();
        TrailerProperties p = new TrailerProperties();
        p.setVoices("  ");
        assertThat(p.voiceCatalog()).isEmpty();
    }

    @Test
    void parsesTheJsonCatalog() {
        TrailerProperties p = new TrailerProperties();
        p.setVoices("[{\"id\":\"v1\",\"name\":\"Sami\",\"suits\":\"politics, history\"},"
                + "{\"id\":\"v2\",\"name\":\"Layla\",\"suits\":\"literature, self-development\"}]");

        List<TrailerVoice> voices = p.voiceCatalog();

        assertThat(voices).extracting(TrailerVoice::id).containsExactly("v1", "v2");
        assertThat(voices.get(1).suits()).contains("literature");
    }

    @Test
    void parsesThePlainTextCatalogThatSurvivesEnvFiles() {
        TrailerProperties p = new TrailerProperties();
        p.setVoices("v1|Sami|politics, history; v2|Rawi|documentary, science ;v3|Solo");

        List<TrailerVoice> voices = p.voiceCatalog();

        assertThat(voices).extracting(TrailerVoice::id).containsExactly("v1", "v2", "v3");
        assertThat(voices.get(0).name()).isEqualTo("Sami");
        assertThat(voices.get(1).suits()).isEqualTo("documentary, science");
        assertThat(voices.get(2).suits()).isNull();
    }

    @Test
    void aBrokenCatalogFailsLoudlyInsteadOfSilentlyUsingTheDefault() {
        TrailerProperties p = new TrailerProperties();
        p.setVoices("[not json");
        assertThatThrownBy(p::voiceCatalog).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KTAB_TRAILER_VOICES");
    }

    @Test
    void entriesWithoutAnIdAreRejected() {
        TrailerProperties p = new TrailerProperties();
        p.setVoices("[{\"name\":\"x\",\"suits\":\"y\"}]");
        assertThatThrownBy(p::voiceCatalog).isInstanceOf(IllegalStateException.class).hasMessageContaining("id");
    }
}

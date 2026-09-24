package com.doova.ktab.features.storybook.blueprint;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintCatalogTest {

    private final BlueprintCatalog catalog =
            new BlueprintCatalog(new ObjectMapper(), "classpath*:storybook/blueprints/*.json");

    @Test
    void loadsTheSampleBlueprint() {
        Blueprint b = catalog.get("first-day-of-school");
        assertThat(b.version()).isEqualTo(1);
        assertThat(b.religious()).isFalse();
    }

    @Test
    void beatsForEachPageCountHaveExactlyThatManyBeatsInOrder() {
        Blueprint b = catalog.get("first-day-of-school");
        assertThat(b.beatsFor(10)).hasSize(10);
        assertThat(b.beatsFor(12)).hasSize(12);
        assertThat(b.beatsFor(15)).hasSize(15);
        assertThat(b.beatsFor(15)).extracting(BlueprintBeat::order).isSorted();
    }

    @Test
    void filtersByAgeBand() {
        assertThat(catalog.forAgeBand(AgeBand.AGE_3_5)).extracting(Blueprint::key).contains("first-day-of-school");
        assertThat(catalog.forAgeBand(AgeBand.AGE_9_10)).extracting(Blueprint::key).doesNotContain("first-day-of-school");
    }

    @Test
    void unknownKeyIsRejected() {
        assertThatThrownBy(() -> catalog.get("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidBlueprintFailsLoading() {
        assertThatThrownBy(() -> new BlueprintCatalog(new ObjectMapper(), "classpath*:storybook/blueprints-invalid/*.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken");
    }

    @Test
    void unsupportedPageCountIsRejected() {
        assertThatThrownBy(() -> catalog.get("first-day-of-school").beatsFor(11))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

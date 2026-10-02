package com.doova.ktab.features.storybook.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The model replies without a schema, so it picks its own key names; the blueprint must still be read. */
class StoryBlueprintResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTheShapeTheModelActuallyReturns() throws Exception {
        String json = "{\"title\":\"سر المرصد المنسي\",\"pages\":[{\"pageNumber\":1,\"beat\":\"تبدأ الحكاية\","
                + "\"emotionalArc\":\"هادئة\",\"sceneSetting\":\"مكتبة\",\"characters\":[\"ليلى\"]}]}";

        StoryBlueprintResponse r = mapper.readValue(json, StoryBlueprintResponse.class);

        assertThat(r.titleConcept()).isEqualTo("سر المرصد المنسي");
        assertThat(r.beats()).hasSize(1);
        assertThat(r.beats().get(0).beat()).isEqualTo("تبدأ الحكاية");
    }

    @Test
    void stillReadsTheDocumentedNames() throws Exception {
        String json = "{\"titleConcept\":\"t\",\"premise\":\"p\",\"beats\":[{\"pageNumber\":1,\"beat\":\"b\"}]}";

        StoryBlueprintResponse r = mapper.readValue(json, StoryBlueprintResponse.class);

        assertThat(r.premise()).isEqualTo("p");
        assertThat(r.beats()).hasSize(1);
    }
}

package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The model replies without a schema and picks its own key names; the bible must still be read, and the prompts must name the keys. */
class CharacterBibleResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTheShapeTheModelActuallyReturns() throws Exception {
        String json = "{\"characters\":[{\"name\":\"ليلى\",\"role\":\"CHILD\",\"visual_identity\":{\"face\":\"oval\",\"hair\":\"plain white hijab\"},"
                + "\"personality\":\"curious\"}],\"setting\":{\"location\":\"Amman\"},"
                + "\"art_style\":{\"medium\":\"soft watercolor\",\"lighting\":\"warm\"}}";

        CharacterBibleResponse r = mapper.readValue(json, CharacterBibleResponse.class);

        assertThat(r.characters()).hasSize(1);
        assertThat(r.characters().get(0).visualLock()).contains("plain white hijab");
        assertThat(r.visualStyleNotes()).contains("soft watercolor");
    }

    @Test
    void stillReadsTheDocumentedNames() throws Exception {
        String json = "{\"summary\":\"s\",\"visualStyleNotes\":\"v\",\"characters\":[{\"name\":\"n\",\"role\":\"r\","
                + "\"visualLock\":\"l\",\"clothing\":\"c\",\"personality\":\"p\"}]}";

        CharacterBibleResponse r = mapper.readValue(json, CharacterBibleResponse.class);

        assertThat(r.summary()).isEqualTo("s");
        assertThat(r.characters().get(0).clothing()).isEqualTo("c");
    }

    @Test
    void everyPromptThatNeedsJsonNamesItsKeys() {
        PromptLibrary prompts = new PromptLibrary();

        assertThat(prompts.get("character-bible-system")).contains("\"characters\"").contains("\"visualLock\"")
                .contains("\"visualStyleNotes\"");
        assertThat(prompts.get("story-blueprint-system")).contains("\"beats\"").contains("\"pageNumber\"").contains("\"titleConcept\"");
        assertThat(prompts.get("story-plan-system")).contains("\"pages\"").contains("\"textAr\"").contains("\"sceneEn\"")
                .contains("\"textZone\"");
    }

    @Test
    void criticsAreToldTheFixedNamesAreNeverToBeFlagged() {
        PromptLibrary prompts = new PromptLibrary();

        // The parent's spelling of the child and companion names is enforced by the code; flagging it only makes every page
        // fail every round and burns rewrites that can never fix it.
        assertThat(prompts.get("critic-system")).containsIgnoringCase("never flag").containsIgnoringCase("name");
        assertThat(prompts.get("language-critic-system")).containsIgnoringCase("never flag").containsIgnoringCase("name")
                .containsIgnoringCase("vocalization");
    }

    @Test
    void criticsOnlyFlagWhatIsCertainlyWrong() {
        PromptLibrary prompts = new PromptLibrary();

        assertThat(prompts.get("critic-system")).containsIgnoringCase("certain").containsIgnoringCase("another natural reading");
        assertThat(prompts.get("language-critic-system")).containsIgnoringCase("certain").containsIgnoringCase("another natural reading");
    }

    @Test
    void theTitleRewritePromptNamesItsKey() {
        assertThat(new PromptLibrary().get("title-rewrite-system")).contains("\"titleAr\"");
    }

    @Test
    void theWriterAndTheRewriterAreToldNeverToDescribeClothesInTheScene() {
        PromptLibrary prompts = new PromptLibrary();

        assertThat(prompts.get("story-plan-system")).containsIgnoringCase("never describe").containsIgnoringCase("clothing");
        assertThat(prompts.get("page-rewrite-system")).containsIgnoringCase("never describe").containsIgnoringCase("clothing");
    }

    @Test
    void theWriterIsToldHowToNameSupportingCharacters() {
        PromptLibrary prompts = new PromptLibrary();

        assertThat(prompts.get("story-plan-system")).contains("SUPPORT_1").containsIgnoringCase("only");
        assertThat(prompts.get("page-rewrite-system")).contains("SUPPORT_1");
    }
}

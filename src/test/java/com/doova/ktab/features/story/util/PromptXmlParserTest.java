package com.doova.ktab.features.story.util;

import com.doova.ktab.features.story.dto.ChoiceV2;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PromptXmlParserTest {

    @Test
    @DisplayName("extractTag_presentTag_returnsInnerContentTrimmed")
    void extractTag_presentTag_returnsInnerContentTrimmed() {
        String input = """
            <script>
            ركض سامر نحو الزقاق المظلم.
            </script>
            """;

        String result = PromptXmlParser.extractTag(input, "script");
        assertThat(result).isEqualTo("ركض سامر نحو الزقاق المظلم.");
    }

    @Test
    @DisplayName("cleanJson_markdownFenced_stripsFences")
    void cleanJson_markdownFenced_stripsFences() {
        String input = "```json\n{\"id\": \"A\"}\n```";
        String cleaned = PromptXmlParser.cleanJson(input);
        assertThat(cleaned).isEqualTo("{\"id\": \"A\"}");
    }

    @Test
    @DisplayName("parseJsonListTag_choicesTag_parsesListSuccessfully")
    void parseJsonListTag_choicesTag_parsesListSuccessfully() {
        String input = """
            <choices>
            [
              {
                "id": "A",
                "archetype": "CONFRONT",
                "text_ar": "يواجه الحارس",
                "risk_profile": "GAMBLE"
              }
            ]
            </choices>
            """;

        Optional<List<ChoiceV2>> parsed = PromptXmlParser.parseJsonListTag(input, "choices", new TypeReference<List<ChoiceV2>>() {});
        assertThat(parsed).isPresent();
        assertThat(parsed.get()).hasSize(1);
        assertThat(parsed.get().get(0).id()).isEqualTo("A");
        assertThat(parsed.get().get(0).archetype()).isEqualTo("CONFRONT");
        assertThat(parsed.get().get(0).risk_profile()).isEqualTo("GAMBLE");
    }

    @Test
    @DisplayName("hasFirstPersonMarkers_withFirstPersonArabic_returnsTrue")
    void hasFirstPersonMarkers_withFirstPersonArabic_returnsTrue() {
        assertThat(PromptXmlParser.hasFirstPersonMarkers("شعرتُ بالخوف الشديد")).isTrue();
        assertThat(PromptXmlParser.hasFirstPersonMarkers("أنا سأذهب الآن")).isTrue();
        assertThat(PromptXmlParser.hasFirstPersonMarkers("رأيتُ الحارس يقترب")).isTrue();
    }

    @Test
    @DisplayName("hasFirstPersonMarkers_withStrictThirdPersonArabic_returnsFalse")
    void hasFirstPersonMarkers_withStrictThirdPersonArabic_returnsFalse() {
        assertThat(PromptXmlParser.hasFirstPersonMarkers("تسلل الحارس نحو البوابة بحذر.")).isFalse();
        assertThat(PromptXmlParser.hasFirstPersonMarkers("توقف الرجل وراقب الظلال عن كثب.")).isFalse();
    }

    @Test
    @DisplayName("extractTag_looseOpeningTag_returnsInnerContentTrimmed")
    void extractTag_looseOpeningTag_returnsInnerContentTrimmed() {
        String input = """
            <script
            يمزق كمال الصورة أمام آدم ثم يدفعه الحراس.
            </script>
            """;
        String extracted = PromptXmlParser.extractTag(input, "script");
        assertThat(extracted).isEqualTo("يمزق كمال الصورة أمام آدم ثم يدفعه الحراس.");
    }

    @Test
    @DisplayName("cleanNarrativeScript_fullRawTurnOutput_returnsOnlyPureScript")
    void cleanNarrativeScript_fullRawTurnOutput_returnsOnlyPureScript() {
        String raw = """
            <storyboard>
            beat_function: كشف ليلى والسبب الواقعي لاختفائها
            </storyboard>

            <script
            يمزق كمال الصورة أمام آدم، ثم يدفعه حارسان إلى عربة الأمتعة. خلفه ليلى مقيدة.
            </script>

            <choices>
            [{"id": "A", "text_ar": "ينقض"}]
            </choices>
            """;

        String cleaned = PromptXmlParser.cleanNarrativeScript(raw);
        assertThat(cleaned).isEqualTo("يمزق كمال الصورة أمام آدم، ثم يدفعه حارسان إلى عربة الأمتعة. خلفه ليلى مقيدة.");
        assertThat(cleaned).doesNotContain("<storyboard>");
        assertThat(cleaned).doesNotContain("<choices>");
        assertThat(cleaned).doesNotContain("<script");
    }
}

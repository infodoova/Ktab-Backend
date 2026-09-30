package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Deterministic Text Checks Comprehensive Specification Tests")
class DeterministicTextChecksComprehensiveTest {

    private static PagePlan page(int pageNum, String text) {
        return new PagePlan(pageNum, text, "A warm sunny day.", List.of(), TextZone.TOP);
    }

    private static StoryRequest requestForBand(AgeBand band, LanguageVariety variety) {
        return new StoryRequest(
                "سامي",
                ChildGender.BOY,
                band,
                StoryFixtures.APPEARANCE,
                variety,
                StoryFixtures.CATALOG.get("first-day-of-school"),
                10,
                List.of(),
                null,
                null
        );
    }

    @Test
    void check_nullText_reportsNoTextViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        List<String> problems = DeterministicTextChecks.check(request, page(1, null));

        assertThat(problems).containsExactly("Page 1 has no text");
    }

    @Test
    void check_emptyOrBlankText_reportsNoTextViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        List<String> emptyProblems = DeterministicTextChecks.check(request, page(1, ""));
        List<String> whitespaceProblems = DeterministicTextChecks.check(request, page(2, "   \t\n  "));

        assertThat(emptyProblems).containsExactly("Page 1 has no text");
        assertThat(whitespaceProblems).containsExactly("Page 2 has no text");
    }

    @Test
    void check_age3To5_exactly25Words_passes() {
        StoryRequest request = requestForBand(AgeBand.AGE_3_5, LanguageVariety.MSA);
        String text = "كلمة ".repeat(25).trim() + ".";

        List<String> problems = DeterministicTextChecks.check(request, page(1, text));

        assertThat(problems).isEmpty();
    }

    @Test
    void check_age3To5_26Words_reportsWordCountViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_3_5, LanguageVariety.MSA);
        String text = "كلمة ".repeat(26).trim() + ".";

        List<String> problems = DeterministicTextChecks.check(request, page(1, text));

        assertThat(problems).anyMatch(p -> p.contains("has 26 words; the limit for ages 3-5 is 25"));
    }

    @Test
    void check_age6To8_exactly45Words_passes() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String text = "كلمة ".repeat(45).trim() + ".";

        List<String> problems = DeterministicTextChecks.check(request, page(3, text));

        assertThat(problems).isEmpty();
    }

    @Test
    void check_age6To8_46Words_reportsWordCountViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String text = "كلمة ".repeat(46).trim() + ".";

        List<String> problems = DeterministicTextChecks.check(request, page(3, text));

        assertThat(problems).anyMatch(p -> p.contains("has 46 words; the limit for ages 6-8 is 45"));
    }

    @Test
    void check_age9To10_exactly70Words_passes() {
        StoryRequest request = requestForBand(AgeBand.AGE_9_10, LanguageVariety.MSA);
        String text = "كلمة ".repeat(70).trim() + ".";

        List<String> problems = DeterministicTextChecks.check(request, page(5, text));

        assertThat(problems).isEmpty();
    }

    @Test
    void check_age9To10_71Words_reportsWordCountViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_9_10, LanguageVariety.MSA);
        String text = "كلمة ".repeat(71).trim() + ".";

        List<String> problems = DeterministicTextChecks.check(request, page(5, text));

        assertThat(problems).anyMatch(p -> p.contains("has 71 words; the limit for ages 9-10 is 70"));
    }

    @Test
    void check_sentenceBoundaries_oneToFourSentences_passes() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);

        assertThat(DeterministicTextChecks.check(request, page(1, "ذهب سامي إلى المدرسة."))).isEmpty();
        assertThat(DeterministicTextChecks.check(request, page(2, "ذهب سامي. التقى بصديقه."))).isEmpty();
        assertThat(DeterministicTextChecks.check(request, page(3, "ذهب سامي. التقى بصديقه. لعبا سويا."))).isEmpty();
        assertThat(DeterministicTextChecks.check(request, page(4, "ذهب سامي. التقى بصديقه. لعبا سويا. عاد فرحا."))).isEmpty();
    }

    @Test
    void check_fiveSentences_reportsSentenceCountViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String fiveSentences = "الجملة الأولى. الجملة الثانية. الجملة الثالثة. الجملة الرابعة. الجملة الخامسة.";

        List<String> problems = DeterministicTextChecks.check(request, page(2, fiveSentences));

        assertThat(problems).anyMatch(p -> p.contains("has 5 sentences; it must have 1 to 4"));
    }

    @Test
    void check_arabicPunctuation_handlesQuestionMarksAndExclamations() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String text = "هل ذهب سامي إلى المدرسة؟ نعم لقد ذهب! كان اليوم جميلا.";

        List<String> problems = DeterministicTextChecks.check(request, page(1, text));

        assertThat(problems).isEmpty();
    }

    @Test
    void check_embeddedLatinWord_reportsLatinLettersViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String text = "ركب سامي الـ bicycle بسرعة.";

        List<String> problems = DeterministicTextChecks.check(request, page(4, text));

        assertThat(problems).anyMatch(p -> p.contains("contains Latin letters"));
    }

    @Test
    void check_singleEmbeddedLatinLetter_reportsLatinLettersViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String text = "قرأ سامي صفحة A من الكتاب.";

        List<String> problems = DeterministicTextChecks.check(request, page(4, text));

        assertThat(problems).anyMatch(p -> p.contains("contains Latin letters"));
    }

    @Test
    void check_egyptianDialectWithFatha_reportsTashkeelViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.EGYPTIAN);
        String text = "سامي رَاح المدرسة عشان يلعب.";

        List<String> problems = DeterministicTextChecks.check(request, page(2, text));

        assertThat(problems).anyMatch(p -> p.contains("is in Egyptian Arabic but contains tashkeel; dialect text must have none"));
    }

    @Test
    void check_lebaneseDialectWithTanween_reportsTashkeelViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.LEBANESE);
        String text = "سامي مبسوط كتيرًا اليوم.";

        List<String> problems = DeterministicTextChecks.check(request, page(3, text));

        assertThat(problems).anyMatch(p -> p.contains("is in Lebanese Arabic but contains tashkeel; dialect text must have none"));
    }

    @Test
    void check_gulfDialectWithShadda_reportsTashkeelViolation() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.GULF);
        String text = "سامي سلّم على ربعه.";

        List<String> problems = DeterministicTextChecks.check(request, page(1, text));

        assertThat(problems).anyMatch(p -> p.contains("is in Gulf Arabic but contains tashkeel; dialect text must have none"));
    }

    @ParameterizedTest
    @EnumSource(value = LanguageVariety.class, names = {"EGYPTIAN", "LEBANESE", "GULF"})
    void check_dialectsWithoutTashkeel_passCleanly(LanguageVariety dialect) {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, dialect);
        String text = "سامي راح المدرسة والتقى برفقاته وكان نهار حلو كتير.";

        List<String> problems = DeterministicTextChecks.check(request, page(1, text));

        assertThat(problems).isEmpty();
    }

    @Test
    void check_msaWithFullTashkeel_passesCleanly() {
        StoryRequest request = requestForBand(AgeBand.AGE_6_8, LanguageVariety.MSA);
        String fullyVocalizedMsa = "ذَهَبَ سَامِي إِلَى المَدْرَسَةِ فَرِحًا وَسَعِيدًا.";

        List<String> problems = DeterministicTextChecks.check(request, page(1, fullyVocalizedMsa));

        assertThat(problems).isEmpty();
    }

    @Test
    void check_multipleViolationsSimultaneously_collectsAllDistinctErrors() {
        StoryRequest request = requestForBand(AgeBand.AGE_3_5, LanguageVariety.EGYPTIAN); // age 3-5 max 25 words
        // Violation 1: > 25 words (30 words)
        // Violation 2: 5 sentences
        // Violation 3: Latin letters ("cool")
        // Violation 4: Tashkeel in dialect (فَتْحَة)
        String messyText = "سامي رَاح. وهو cool. " + "كلمة ".repeat(25) + ". sentence four! sentence five؟";

        List<String> problems = DeterministicTextChecks.check(request, page(1, messyText));

        assertThat(problems).hasSize(4);
        assertThat(problems).anyMatch(p -> p.contains("limit for ages 3-5 is 25"));
        assertThat(problems).anyMatch(p -> p.contains("has 5 sentences"));
        assertThat(problems).anyMatch(p -> p.contains("contains Latin letters"));
        assertThat(problems).anyMatch(p -> p.contains("contains tashkeel"));
    }
}

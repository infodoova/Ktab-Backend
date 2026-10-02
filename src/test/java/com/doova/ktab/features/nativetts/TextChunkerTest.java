package com.doova.ktab.features.nativetts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void neverSplitsAWordAndNeverExceedsTheLimit() {
        String text = ("كان يا ما كان في قديم الزمان، رجلٌ حكيمٌ يسكن قريةً صغيرة. ").repeat(200).strip();

        List<String> chunks = TextChunker.chunk(text, 500);

        assertThat(chunks).isNotEmpty().allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(500));
        assertThat(String.join(" ", chunks)).isEqualTo(text);
        assertThat(chunks).allSatisfy(c -> assertThat(c).doesNotStartWith(" ").doesNotEndWith(" "));
    }

    @Test
    void breaksAtParagraphsThenSentences() {
        List<String> chunks = TextChunker.chunk("فقرة أولى قصيرة.\n\nفقرة ثانية. وجملة أخرى؟ وثالثة!", 30);

        assertThat(chunks).containsExactly("فقرة أولى قصيرة.", "فقرة ثانية. وجملة أخرى؟", "وثالثة!");
        assertThat(chunks).allSatisfy(c -> assertThat(c).matches("(?s).*[.!؟?؛…]$"));
    }

    @Test
    void pdfLineWrapsAreReflowedIntoOneSentence() {
        List<String> chunks = TextChunker.chunk("كان الشيخ يجلس عند باب\nداره كل مساء ويحدث\nالصغار عن أيام الحصاد.", 200);

        assertThat(chunks).containsExactly("كان الشيخ يجلس عند باب داره كل مساء ويحدث الصغار عن أيام الحصاد.");
    }

    @Test
    void aSentenceLongerThanTheLimitIsSplitAtWhitespace() {
        List<String> chunks = TextChunker.chunk("كلمة ".repeat(20).strip(), 25);

        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(25));
        assertThat(String.join(" ", chunks)).isEqualTo("كلمة ".repeat(20).strip());
    }

    @Test
    void aSingleWordLongerThanTheLimitIsHardSplitAsALastResort() {
        assertThat(TextChunker.chunk("ا".repeat(25), 10)).containsExactly("ا".repeat(10), "ا".repeat(10), "ا".repeat(5));
    }

    @Test
    void tashkeelAndLettersSurviveUntouched() {
        String text = "كَتَبَ الطَّالِبُ الدَّرْسَ.";
        assertThat(TextChunker.chunk(text, 100)).containsExactly(text);
    }

    @Test
    void blankInputGivesNoChunks() {
        assertThat(TextChunker.chunk("  \n\n  ", 100)).isEmpty();
        assertThat(TextChunker.chunk(null, 100)).isEmpty();
    }
}

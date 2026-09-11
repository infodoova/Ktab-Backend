package com.doova.ktab.features.ocr.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ArabicPagePromptFactoryTest {

    @Test
    @DisplayName("OcrSystemPrompt constant contains all required instructions and format rules")
    void testOcrSystemPromptConstantContent() {
        String prompt = OcrSystemPrompt.SYSTEM_PROMPT;

        assertThat(prompt)
                .isNotBlank()
                .contains("multilingual document archivist")
                .contains("TTS (Text-to-Speech) optimization specialist")
                .contains("Arabic Diacritics (CRITICAL)")
                .contains("ElevenLabs Turbo v2.5/v3 models")
                .contains("The \"Sole Source\" Rule")
                .contains("Hard-Fail Mechanism")
                .contains("text : <markdown_text>")
                .contains("wordscount : <total_word_count>");
    }

    @Test
    @DisplayName("ArabicPagePromptFactory uses OcrSystemPrompt.SYSTEM_PROMPT by default")
    void testDefaultPromptUsage() {
        ArabicPagePromptFactory factory = new ArabicPagePromptFactory();

        assertThat(factory.getSystemPrompt()).isEqualTo(OcrSystemPrompt.SYSTEM_PROMPT);

        byte[] fakeImage = new byte[]{1, 2, 3, 4};
        Prompt prompt = factory.buildPrompt(fakeImage, "image/png");

        assertThat(prompt.getInstructions()).hasSize(1);
        Message message = prompt.getInstructions().get(0);
        assertThat(message.getText()).contains("multilingual document archivist");
    }

    @Test
    @DisplayName("ArabicPagePromptFactory respects custom system prompt override when provided")
    void testCustomPromptOverride() {
        ArabicPagePromptFactory factory = new ArabicPagePromptFactory();
        String customPrompt = "Custom OCR System Prompt";
        ReflectionTestUtils.setField(factory, "systemPrompt", customPrompt);

        assertThat(factory.getSystemPrompt()).isEqualTo(customPrompt);

        byte[] fakeImage = new byte[]{1, 2, 3};
        Prompt prompt = factory.buildPrompt(fakeImage, "image/jpeg");

        Message message = prompt.getInstructions().get(0);
        assertThat(message.getText()).isEqualTo(customPrompt);
    }
}

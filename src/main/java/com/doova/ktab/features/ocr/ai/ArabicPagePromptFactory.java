package com.doova.ktab.features.ocr.ai;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.List;
import java.util.Map;

@Component
public class ArabicPagePromptFactory {

    @Value("${ktab.ocr.system-prompt}")
    private String systemPrompt;

    public Prompt buildPrompt(byte[] imageBytes, String mime) {

        // 1) Render system prompt (kept identical to your pattern)
        PromptTemplate template = new PromptTemplate("{SYSTEM_PROMPT}");
        String rendered = template.render(
                Map.of("SYSTEM_PROMPT", this.systemPrompt)
        );

        // 2) Wrap bytes into a Resource (REQUIRED in Spring AI 1.1.0)
        Resource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "page.png"; // optional but recommended
            }
        };

        Media imageMedia = new Media(
                MimeType.valueOf(mime),
                imageResource
        );

        UserMessage userMessage = UserMessage.builder()
                .text(rendered)
                .media(List.of(imageMedia)) // or .media(imageMedia)
                .build();

        // 3) Wrap into Prompt
        return new Prompt(List.of(userMessage));
    }
}

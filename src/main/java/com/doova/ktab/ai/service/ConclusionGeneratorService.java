package com.doova.ktab.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.content.Content;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.chat.prompt.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ConclusionGeneratorService {

    private final OpenAiChatModel defaultChatModel;

    public Flux<String> streamConclusion(
            MultipartFile pdfFile,
            String type,
            int wordCount,
            String audience
    ) throws Exception {

        if (pdfFile.getSize() > 10_000_000)
            throw new RuntimeException("PDF size exceeds 10MB");

        String systemPrompt = ArabicPromptBuilder.build(type, wordCount, audience);

        Media pdfMedia = Media.builder()
                .mimeType(MimeType.valueOf(Objects.requireNonNull(pdfFile.getContentType())))
                .data(pdfFile.getBytes())
                .name(pdfFile.getOriginalFilename())
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text("اقرأ هذا الملف ثم أنشئ الخلاصة المطلوبة.")
                .media(pdfMedia)
                .build();

        Prompt prompt = new Prompt(
                new SystemMessage(systemPrompt),
                userMessage
        );

        return defaultChatModel.stream(prompt)
                .mapNotNull(r -> r.getResult().getOutput().getText());
    }

    public String fetchConclusion(
            MultipartFile pdfFile,
            String type,
            int wordCount,
            String audience
    ) throws Exception {

        if (pdfFile.getSize() > 10_000_000)
            throw new RuntimeException("PDF size exceeds 10MB");

        // Build main prompt text
        String systemPrompt = ArabicPromptBuilder.build(type, wordCount, audience);

        // Build PDF attachment
        Media pdfMedia = Media.builder()
                .mimeType(MimeType.valueOf(Objects.requireNonNull(pdfFile.getContentType())))
                .data(pdfFile.getBytes())
                .name(pdfFile.getOriginalFilename())
                .build();

        // Create user message
        UserMessage userMsg = UserMessage.builder()
                .text("اقرأ هذا الملف ثم أنشئ الخلاصة المطلوبة.")
                .media(pdfMedia)
                .build();

        // System + User prompt
        Prompt prompt = new Prompt(
                new SystemMessage(systemPrompt),
                userMsg
        );

        // ❗ Non-streaming inference
        var response = defaultChatModel.call(prompt);

        // Extract final text
        return response.getResult().getOutput().getText();
    }
}

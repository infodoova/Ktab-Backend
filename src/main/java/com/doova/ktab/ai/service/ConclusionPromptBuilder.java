package com.doova.ktab.ai.service;

import com.doova.ktab.ai.dto.request.ConclusionRequest;
import com.doova.ktab.ai.prompts.ArabicPromptBuilder;
import com.doova.ktab.ai.prompts.EndingGeneratorPrompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.util.Objects;

@Component
public class ConclusionPromptBuilder {

    // Define constants for better readability and maintainability
    private static final long MAX_FILE_SIZE = 10_000_000; // 10MB in bytes
    private static final String USER_PROMPT_TEXT = "اقرأ هذا الملف ثم أنشئ الخلاصة المطلوبة.";

    public Prompt build(ConclusionRequest request) throws IOException {
        // 1. Validate file size and type
//        if (request.file().getSize() > MAX_FILE_SIZE) {
//            throw new IllegalArgumentException("File size exceeds " + (MAX_FILE_SIZE / 1_000_000) + "MB limit.");
//        }

        String contentType = Objects.requireNonNull(request.file().getContentType(), "File content type cannot be null.");

        // 2. Build the System Prompt (assuming ArabicPromptBuilder is available)
        String systemPromptText = EndingGeneratorPrompt.build(
                request.wordCount(),
                request.audience()
        );

        // 3. Build PDF/File attachment (Media)
        Media fileMedia = Media.builder()
                .mimeType(MimeType.valueOf(contentType))
                .data(request.file().getBytes())
                .name(request.file().getOriginalFilename())
                .build();

        // 4. Create User Message with file attachment
        UserMessage userMessage = UserMessage.builder()
                .text(USER_PROMPT_TEXT)
                .media(fileMedia)
                .build();

        // 5. System + User prompt for the AI model
        return new Prompt(
                new SystemMessage(systemPromptText),
                userMessage
        );
    }
}
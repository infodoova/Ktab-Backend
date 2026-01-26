package com.doova.ktab.ai.service;

import com.doova.ktab.ai.dto.request.GenerateEndingCommand;
import com.doova.ktab.ai.prompt.BookEndingPromptFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Application service orchestrating prompt creation and Gemini call.
 */
@Service
public class BookEndingService {

    private final ChatModel chatModel;
    private final BookEndingPromptFactory promptFactory;

    public BookEndingService(@Qualifier("endingGeneratorGeminiModel") ChatModel chatModel, BookEndingPromptFactory promptFactory) {
        this.chatModel = chatModel;
        this.promptFactory = promptFactory;
    }

    public String generateEnding(GenerateEndingCommand command) {
        Prompt prompt = promptFactory.buildPrompt(command);

        ChatResponse response = chatModel.call(prompt);

        // Spring AI 1.0+ style:
        return response.getResult().getOutput().getText();
    }
}

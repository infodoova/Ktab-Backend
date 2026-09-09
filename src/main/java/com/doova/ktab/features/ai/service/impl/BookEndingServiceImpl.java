package com.doova.ktab.features.ai.service.impl;

import com.doova.ktab.features.ai.dto.request.GenerateEndingCommand;
import com.doova.ktab.features.ai.prompt.BookEndingPromptFactory;
import com.doova.ktab.features.ai.service.BookEndingService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class BookEndingServiceImpl implements BookEndingService {

    private final ChatModel chatModel;
    private final BookEndingPromptFactory promptFactory;

    public BookEndingServiceImpl(@Qualifier("endingGeneratorGeminiModel") ChatModel chatModel,
                                 BookEndingPromptFactory promptFactory) {
        this.chatModel = chatModel;
        this.promptFactory = promptFactory;
    }

    @Override
    public String generateEnding(GenerateEndingCommand command) {
        Prompt prompt = promptFactory.buildPrompt(command);
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }
}

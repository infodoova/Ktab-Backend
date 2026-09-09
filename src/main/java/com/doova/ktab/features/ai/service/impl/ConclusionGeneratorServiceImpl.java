package com.doova.ktab.features.ai.service.impl;

import com.doova.ktab.features.ai.dto.request.ConclusionRequest;
import com.doova.ktab.features.ai.prompt.ConclusionPromptBuilder;
import com.doova.ktab.features.ai.service.ConclusionGeneratorService;
import com.doova.ktab.enums.message.ApiMessageKey;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;

@Service
public class ConclusionGeneratorServiceImpl implements ConclusionGeneratorService {

    private final OpenAiChatModel nonStreamingModel;
    private final OpenAiChatModel streamingModel;
    private final ConclusionPromptBuilder promptBuilder;

    public ConclusionGeneratorServiceImpl(@Qualifier("nonStreamingModel") OpenAiChatModel nonStreamingModel,
                                         @Qualifier("streamingModel") OpenAiChatModel streamingModel,
                                         ConclusionPromptBuilder promptBuilder) {
        this.nonStreamingModel = nonStreamingModel;
        this.streamingModel = streamingModel;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public Flux<String> streamConclusion(ConclusionRequest request) {
        Prompt prompt;
        try {
            prompt = promptBuilder.build(request);
        } catch (IOException ex) {
            return Flux.error(new IllegalStateException(ApiMessageKey.AI_CONCLUSION_INVALID_FILE.getKey(), ex));
        }

        return streamingModel.stream(prompt).flatMap(response -> {
            if (response == null || response.getResults().isEmpty()) {
                return Flux.empty();
            }

            var generation = response.getResults().getFirst();
            if (generation == null) {
                return Flux.empty();
            } else {
                generation.getOutput();
            }

            String text = generation.getOutput().getText();
            return (text == null || text.isBlank()) ? Flux.empty() : Flux.just(text);
        });
    }

    @Override
    public String fetchConclusion(ConclusionRequest request) throws IOException {
        Prompt prompt = promptBuilder.build(request);
        var response = nonStreamingModel.call(prompt);
        response.getResult();
        return response.getResult().getOutput().getText();
    }
}

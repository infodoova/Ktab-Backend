package com.doova.ktab.ai.service;

import com.doova.ktab.ai.dto.request.ConclusionRequest;
import com.doova.ktab.ai.prompt.ConclusionPromptBuilder;
import com.doova.ktab.enums.ApiMessageKey;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;

@Service
public class ConclusionGeneratorService {

    private final OpenAiChatModel nonStreamingModel;
    private final OpenAiChatModel streamingModel;
    private final ConclusionPromptBuilder promptBuilder;

    public ConclusionGeneratorService(@Qualifier("nonStreamingModel") OpenAiChatModel nonStreamingModel, @Qualifier("streamingModel") OpenAiChatModel streamingModel, ConclusionPromptBuilder promptBuilder) {
        this.nonStreamingModel = nonStreamingModel;
        this.streamingModel = streamingModel;
        this.promptBuilder = promptBuilder;
    }

    // =========================================================================
    // STREAMING
    // =========================================================================
    public Flux<String> streamConclusion(ConclusionRequest request) {

        Prompt prompt;
        try {
            prompt = promptBuilder.build(request);
        } catch (IOException ex) {
            // Convert checked exception → reactive runtime exception
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

    // =========================================================================
    // NON-STREAMING
    // =========================================================================
    public String fetchConclusion(ConclusionRequest request) throws IOException {

        Prompt prompt = promptBuilder.build(request);

        var response = nonStreamingModel.call(prompt);

        response.getResult();

        return response.getResult().getOutput().getText();
    }
}

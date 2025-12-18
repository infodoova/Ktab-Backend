package com.doova.ktab.ai.service;

import com.doova.ktab.ai.dto.request.ConclusionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * Service responsible for generating conclusions using GPT-5.
 * Uses two models:
 * - Streaming model for live output
 * - Non-streaming model for full synchronous responses
 */
@Service
public class ConclusionGeneratorService {

    // Inject BOTH GPT-5 models
    private final OpenAiChatModel nonStreamingModel;
    private final OpenAiChatModel streamingModel;
    private final ConclusionPromptBuilder promptBuilder;

    @Autowired
    public ConclusionGeneratorService(
            @Qualifier("nonStreamingModel") OpenAiChatModel nonStreamingModel,
            @Qualifier("streamingModel") OpenAiChatModel streamingModel,
            ConclusionPromptBuilder promptBuilder
    ) {
        this.nonStreamingModel = nonStreamingModel;
        this.streamingModel = streamingModel;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Streams the conclusion response from the GPT-5 streaming model.
     */
    public Flux<String> streamConclusion(ConclusionRequest request) throws IOException {

        Prompt prompt = promptBuilder.build(request);

        return streamingModel.stream(prompt)
                .flatMap(response -> {

                    // 1. Null response → skip
                    if (response == null) return Flux.empty();

                    // 2. Missing results (metadata events → skip)
                    var results = response.getResults();
                    if (results.isEmpty()) return Flux.empty();

                    // 3. Take first generation
                    var gen = results.getFirst();
                    if (gen == null) {
                        return Flux.empty();
                    } else {
                        gen.getOutput();
                    }

                    // 4. Extract text safely
                    String text = gen.getOutput().getText();
                    if (text == null || text.isBlank()) return Flux.empty();

                    // 5. Return clean delta token
                    return Flux.just(text);

                })
                // 6. Ensure no accidental blank chunks
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .onErrorResume(e -> {
                    // ABSOLUTE GUARANTEE no "Error: null"
                    return Flux.just("[STREAM_ERROR] " + e.getMessage());
                });
    }



    /**
     * Fetches the complete conclusion response using GPT-5 non-streaming model.
     */
    public String fetchConclusion(ConclusionRequest request) throws IOException {

        Prompt prompt = promptBuilder.build(request);

        var response = nonStreamingModel.call(prompt);

        return response.getResult().getOutput().getText();
    }
}

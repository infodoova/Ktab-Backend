package com.doova.ktab.features.ocr.harmonize;

import com.doova.ktab.features.ocr.config.OcrProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class GeminiHarmonizationServiceImpl implements HarmonizationService {

    private final ChatModel chatModel;
    private final HarmonizationGuard guard;
    private final OcrProperties properties;

    public GeminiHarmonizationServiceImpl(
            @Qualifier("ocrGeminiModel") ChatModel chatModel,
            HarmonizationGuard guard,
            OcrProperties properties
    ) {
        this.chatModel = chatModel;
        this.guard = guard;
        this.properties = properties;
    }

    private static final String HARMONIZE_TEMPLATE = """
            ## ROLE
            You are a strict text-processing utility. You clean minor OCR formatting artifacts from the target text. You do NOT rewrite, summarize, or alter vocabulary.

            ## INPUTS
            - SECTION TITLE: %s
            - PREVIOUS PAGE CONTEXT: %s
            - NEXT PAGE CONTEXT: %s
            - TARGET TEXT TO CLEAN:
            %s

            ## RULES
            1. Preserve original words 1:1.
            2. Remove accidental line breaks inside paragraphs.
            3. Remove stray page numbers or header fragments left in the body.
            4. Do NOT output any preamble, explanation, or markdown code fences. Output ONLY the cleaned target text.
            """;

    @Override
    public Optional<String> harmonize(String prevTail, String rawText, String nextHead, String sectionTitle) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        try {
            String promptText = String.format(
                    HARMONIZE_TEMPLATE,
                    sectionTitle != null ? sectionTitle : "General",
                    prevTail != null ? prevTail : "None",
                    nextHead != null ? nextHead : "None",
                    rawText
            );

            ChatResponse response = chatModel.call(new Prompt(List.of(new UserMessage(promptText))));
            if (response == null || response.getResult() == null) {
                return Optional.empty();
            }

            String cleaned = response.getResult().getOutput().getText().trim();
            if (cleaned.startsWith("```markdown")) cleaned = cleaned.substring(11);
            else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();

            double minSim = properties.getHarmonize().getMinSimilarity();
            if (guard.isValid(rawText, cleaned, minSim)) {
                return Optional.of(cleaned);
            } else {
                log.warn("Harmonization rejected by guardrail: similarity or length delta failed");
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Harmonization failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

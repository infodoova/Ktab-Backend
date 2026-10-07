package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ModerationService {

    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("[0-9٠-٩]{7,}");

    private final LlmGateway llm;
    private final PromptLibrary prompts;
    private final StorybookProperties properties;

    public record ModerationOutcome(boolean allowed, String reason, LlmCall<ModerationResponse> llmCall) {
    }

    public ModerationOutcome moderate(String text) {
        if (text == null || text.isBlank()) {
            return new ModerationOutcome(true, null, null);
        }
        int max = properties.getLimits().getDedicationMaxChars();
        if (text.length() > max) {
            return new ModerationOutcome(false, "The dedication is longer than " + max + " characters.", null);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String compact = text.replaceAll("[\\s\\-]", "");
        if (lower.contains("http") || lower.contains("www.") || text.contains("@") || LONG_DIGIT_RUN.matcher(compact).find()) {
            return new ModerationOutcome(false, "Links, email addresses and phone numbers cannot be printed.", null);
        }
        LlmCall<ModerationResponse> call = llm.call(LlmRequest.of(LlmPurpose.MODERATION,
                prompts.get("moderation-system"), "Dedication:\n" + text, ModerationResponse.class));
        return new ModerationOutcome(call.value().allowed(), call.value().reason(), call);
    }

    /**
     * Moderates several texts with one LLM call. The cheap local rules (links, e-mail, phone numbers) run per text, so an
     * obvious violation is rejected without calling the model at all. Unlike {@link #moderate(String)}, there is no
     * dedication length cap here: story pages are legitimately longer than a dedication.
     */
    public ModerationOutcome moderateBatch(java.util.List<String> texts) {
        java.util.List<String> nonBlank = texts == null ? java.util.List.of()
                : texts.stream().filter(t -> t != null && !t.isBlank()).toList();
        if (nonBlank.isEmpty()) {
            return new ModerationOutcome(true, null, null);
        }
        for (String text : nonBlank) {
            String lower = text.toLowerCase(Locale.ROOT);
            String compact = text.replaceAll("[\\s\\-]", "");
            if (lower.contains("http") || lower.contains("www.") || text.contains("@") || LONG_DIGIT_RUN.matcher(compact).find()) {
                return new ModerationOutcome(false, "Links, email addresses and phone numbers cannot be printed.", null);
            }
        }
        String joined = String.join("\n---\n", nonBlank);
        LlmCall<ModerationResponse> call = llm.call(LlmRequest.of(LlmPurpose.MODERATION,
                prompts.get("moderation-system"), "Dedication:\n" + joined, ModerationResponse.class));
        return new ModerationOutcome(call.value().allowed(), call.value().reason(), call);
    }
}

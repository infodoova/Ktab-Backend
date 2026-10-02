package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.GeneratedTurn;
import com.doova.ktab.features.story.dto.MemorySummary;
import com.doova.ktab.features.story.scd.SceneCanonicalDescription;
import com.doova.ktab.features.story.scd.ScdDeserializer;
import com.doova.ktab.features.story.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringAiStoryClient {

    private final ChatClient storyChat;
    private final ChatClient storyChatFallback;
    private final ChatClient summaryChat;
    private final ChatClient summaryChatFallback;

    public SpringAiStoryClient(
            @Qualifier("interactiveStoryChatClient") ChatClient storyChat,
            @Qualifier("interactiveStoryFallbackChatClient") ChatClient storyChatFallback,
            @Qualifier("summaryChatClient") ChatClient summaryChat,
            @Qualifier("summaryFallbackChatClient") ChatClient summaryChatFallback
    ) {
        this.storyChat = storyChat;
        this.storyChatFallback = storyChatFallback;
        this.summaryChat = summaryChat;
        this.summaryChatFallback = summaryChatFallback;
    }

    // ---------------------------------------------
    // Generic Prompt Call with Fallback
    // ---------------------------------------------
    public String call(String prompt) {
        return executeWithFallback(storyChat, storyChatFallback, client -> client.prompt().user(prompt).call().content());
    }

    public String call(String systemPrompt, String userPrompt) {
        return executeWithFallback(storyChat, storyChatFallback, client -> client.prompt().system(systemPrompt).user(userPrompt).call().content());
    }

    /**
     * Fast call utilizing the zero-reasoning summary model (reasoning: none, service_tier: fast).
     * Ideal for non-deliberative tasks like visual director prompt formatting and classification.
     */
    public String callFast(String prompt) {
        return executeWithFallback(summaryChat, summaryChatFallback, client -> client.prompt().user(prompt).call().content());
    }

    // ---------------------------------------------
    // 🎭 Generate Story Turn (Legacy/Compatibility)
    // ---------------------------------------------
    public GeneratedTurn generateTurn(String systemPrompt, String userPrompt) {
        String json = call(systemPrompt, userPrompt);
        return JsonUtil.read(json, GeneratedTurn.class);
    }

    // ---------------------------------------------
    // 🧠 Summarize Memory with Fallback
    // ---------------------------------------------
    public MemorySummary summarize(String systemPrompt, String userPrompt) {
        String json = executeWithFallback(summaryChat, summaryChatFallback, client -> client.prompt().system(systemPrompt).user(userPrompt).call().content());
        return JsonUtil.read(json, MemorySummary.class);
    }

    // ---------------------------------------------
    // 🎨 Generate Scene Canonical Description
    // ---------------------------------------------
    public SceneCanonicalDescription generateScd(String prompt) {
        String json = call(prompt);
        return ScdDeserializer.deserialize(json);
    }

    private String executeWithFallback(ChatClient primary, ChatClient fallback, java.util.function.Function<ChatClient, String> action) {
        try {
            return action.apply(primary);
        } catch (Exception primaryEx) {
            log.warn("[SpringAiStoryClient] Primary text model call failed ({}), attempting fallback to lower model", primaryEx.getMessage());
            try {
                return action.apply(fallback);
            } catch (Exception fallbackEx) {
                log.error("[SpringAiStoryClient] Fallback text model call also failed", fallbackEx);
                throw primaryEx;
            }
        }
    }
}

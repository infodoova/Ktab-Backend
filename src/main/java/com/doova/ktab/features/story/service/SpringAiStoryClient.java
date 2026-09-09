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
    private final ChatClient summaryChat;

    public SpringAiStoryClient(@Qualifier("interactiveStoryChatClient") ChatClient storyChat, @Qualifier("summaryChatClient") ChatClient summaryChat) {
        this.storyChat = storyChat;
        this.summaryChat = summaryChat;
    }

    // ---------------------------------------------
    // 🎭 Generate Story Turn
    // ---------------------------------------------
    public GeneratedTurn generateTurn(String systemPrompt, String userPrompt) {

        String json = storyChat.prompt().system(systemPrompt).user(userPrompt).call().content();

        return JsonUtil.read(json, GeneratedTurn.class);
    }

    // ---------------------------------------------
    // 🧠 Summarize Memory
    // ---------------------------------------------
    public MemorySummary summarize(String systemPrompt, String userPrompt) {

        String json = summaryChat.prompt().system(systemPrompt).user(userPrompt).call().content();;

        return JsonUtil.read(json, MemorySummary.class);
    }

    // ---------------------------------------------
    // 🎨 Generate Scene Canonical Description
    // ---------------------------------------------
    public SceneCanonicalDescription generateScd(String prompt) {
        String json = storyChat.prompt().user(prompt).call().content();
        return ScdDeserializer.deserialize(json);
    }
}

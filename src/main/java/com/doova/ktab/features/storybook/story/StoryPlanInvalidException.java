package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.llm.LlmCallFailedException;

/** The model returned well-formed JSON that breaks the book's structure; a fresh attempt usually fixes it. */
public class StoryPlanInvalidException extends LlmCallFailedException {
    public StoryPlanInvalidException(String message) {
        super(message, true, null);
    }
}

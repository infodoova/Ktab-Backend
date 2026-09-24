package com.doova.ktab.features.storybook.support;

import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.llm.LlmGateway;
import com.doova.ktab.features.storybook.llm.LlmRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Returns queued values in order and records every request. */
public class FakeLlmGateway implements LlmGateway {

    private final Deque<Object> responses = new ArrayDeque<>();
    private final List<LlmRequest<?>> requests = new ArrayList<>();

    public void enqueue(Object value) {
        responses.addLast(value);
    }

    public List<LlmRequest<?>> requests() {
        return requests;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> LlmCall<T> call(LlmRequest<T> request) {
        requests.add(request);
        if (responses.isEmpty()) {
            throw new IllegalStateException("FakeLlmGateway: no response queued for " + request.purpose());
        }
        Object value = responses.removeFirst();
        if (value instanceof RuntimeException e) {
            throw e;
        }
        return new LlmCall<>((T) value, "claude-sonnet-5", 1_000, 500, 5);
    }
}

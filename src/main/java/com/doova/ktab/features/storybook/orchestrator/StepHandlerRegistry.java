package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStep;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class StepHandlerRegistry {

    private final Map<JobStep, StepHandler> handlers = new EnumMap<>(JobStep.class);

    public StepHandlerRegistry(List<StepHandler> all) {
        for (StepHandler h : all) {
            if (handlers.putIfAbsent(h.step(), h) != null) {
                throw new IllegalStateException("Two storybook handlers for step " + h.step());
            }
        }
    }

    public StepHandler get(JobStep step) {
        return handlers.get(step);
    }
}

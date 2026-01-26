package com.doova.ktab.interactivestorytelling.dto;

import java.util.List;

public record MemorySummary(List<String> canonFacts, List<String> relationships, List<String> stakes,
                            List<String> unresolvedThreads, List<String> toneRules, List<String> doNotBreak) {
}

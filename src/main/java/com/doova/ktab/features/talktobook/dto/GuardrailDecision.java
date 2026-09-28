package com.doova.ktab.features.talktobook.dto;

import com.doova.ktab.features.talktobook.enums.QueryIntent;

public record GuardrailDecision(
        boolean allowed,
        QueryIntent intent,
        String refusalReason
) {
    public static GuardrailDecision allow(QueryIntent intent) {
        return new GuardrailDecision(true, intent, null);
    }

    public static GuardrailDecision refuse(String reason) {
        return new GuardrailDecision(false, null, reason);
    }
}

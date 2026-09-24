package com.doova.ktab.features.storybook.billing;

import java.time.Instant;

public record CreditHold(
        String holdId,
        Long userId,
        Long bookId,
        int amount,
        Instant heldAt
) {
}

package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.doova.ktab.features.storybook.enums.StorybookStatus.*;

@Component
public class StorybookStateMachine {

    private static final Map<StorybookStatus, Set<StorybookStatus>> ALLOWED = new EnumMap<>(StorybookStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(STORY_READY, CANCELLED));
        ALLOWED.put(STORY_READY, EnumSet.of(CHARACTER_READY, CANCELLED));
        ALLOWED.put(CHARACTER_READY, EnumSet.of(ILLUSTRATING, CANCELLED));
        ALLOWED.put(ILLUSTRATING, EnumSet.of(RENDERING, QA));
        ALLOWED.put(QA, EnumSet.of(ILLUSTRATING, RENDERING));
        ALLOWED.put(RENDERING, EnumSet.of(READY));
        ALLOWED.put(READY, EnumSet.of(ILLUSTRATING));
        ALLOWED.put(FAILED, EnumSet.noneOf(StorybookStatus.class)); // handled specially
        ALLOWED.put(CANCELLED, EnumSet.noneOf(StorybookStatus.class));
    }

    private static final Set<StorybookStatus> FAILABLE = EnumSet.of(DRAFT, STORY_READY, CHARACTER_READY, ILLUSTRATING, QA, RENDERING);

    public boolean canTransition(StorybookStatus from, StorybookStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void transition(Storybook book, StorybookStatus to) {
        StorybookStatus from = book.getStatus();
        if (from == FAILED) {
            if (to != book.getFailedFromStatus()) {
                throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
            }
            book.setFailedFromStatus(null);
            book.setFailureReason(null);
            book.setStatus(to);
            return;
        }
        if (!canTransition(from, to)) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        book.setStatus(to);
    }

    public void fail(Storybook book, String reason) {
        if (!FAILABLE.contains(book.getStatus())) {
            return;
        }
        book.setFailedFromStatus(book.getStatus());
        book.setFailureReason(reason == null ? null : reason.substring(0, Math.min(1000, reason.length())));
        book.setStatus(FAILED);
    }
}

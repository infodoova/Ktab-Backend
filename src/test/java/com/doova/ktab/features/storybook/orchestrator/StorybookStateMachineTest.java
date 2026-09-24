package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.doova.ktab.features.storybook.enums.StorybookStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorybookStateMachineTest {

    private final StorybookStateMachine machine = new StorybookStateMachine();

    private static Storybook book(StorybookStatus status) {
        Storybook b = new Storybook();
        b.setStatus(status);
        return b;
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,STORY_READY", "STORY_READY,CHARACTER_READY", "CHARACTER_READY,ILLUSTRATING",
            "ILLUSTRATING,RENDERING", "ILLUSTRATING,QA", "QA,ILLUSTRATING", "QA,RENDERING",
            "RENDERING,READY", "READY,ILLUSTRATING",
            "DRAFT,CANCELLED", "STORY_READY,CANCELLED", "CHARACTER_READY,CANCELLED"
    })
    void allowedTransitions(StorybookStatus from, StorybookStatus to) {
        Storybook b = book(from);
        machine.transition(b, to);
        assertThat(b.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,ILLUSTRATING", "STORY_READY,READY", "READY,DRAFT", "CANCELLED,DRAFT",
            "ILLUSTRATING,CANCELLED", "RENDERING,ILLUSTRATING", "DRAFT,FAILED"
    })
    void disallowedTransitions(StorybookStatus from, StorybookStatus to) {
        assertThatThrownBy(() -> machine.transition(book(from), to))
                .isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void failRemembersWhereItFailedAndResumeGoesBackThere() {
        Storybook b = book(ILLUSTRATING);
        machine.fail(b, "image provider down");
        assertThat(b.getStatus()).isEqualTo(FAILED);
        assertThat(b.getFailedFromStatus()).isEqualTo(ILLUSTRATING);
        assertThat(b.getFailureReason()).isEqualTo("image provider down");

        assertThatThrownBy(() -> machine.transition(b, DRAFT)).isInstanceOf(StorybookStateConflictException.class);

        machine.transition(b, ILLUSTRATING);
        assertThat(b.getStatus()).isEqualTo(ILLUSTRATING);
        assertThat(b.getFailedFromStatus()).isNull();
        assertThat(b.getFailureReason()).isNull();
    }

    @Test
    void failDoesNotTouchFinishedOrCancelledBooks() {
        Storybook ready = book(READY);
        machine.fail(ready, "x");
        assertThat(ready.getStatus()).isEqualTo(READY);

        Storybook cancelled = book(CANCELLED);
        machine.fail(cancelled, "x");
        assertThat(cancelled.getStatus()).isEqualTo(CANCELLED);
    }

    @Test
    void failTruncatesLongReasons() {
        Storybook b = book(DRAFT);
        machine.fail(b, "x".repeat(5000));
        assertThat(b.getFailureReason()).hasSize(1000);
    }
}

package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryApprovalServiceTest {

    @Mock
    private StorybookAccessGuard guard;
    @Mock
    private JobEnqueuer enqueuer;

    private StoryApprovalService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new StoryApprovalService(guard, enqueuer);
        owner = new User();
        owner.setId(1L);
    }

    @Test
    void approveStory_notStoryReady_throwsConflict() {
        Storybook book = new Storybook();
        book.setStatus(StorybookStatus.DRAFT);
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        assertThatThrownBy(() -> service.approveStory(owner, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
        verify(enqueuer, never()).enqueue(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    void approveStory_alreadyApproved_throwsConflict() {
        Storybook book = new Storybook();
        book.setStatus(StorybookStatus.STORY_READY);
        book.setStoryApprovedAt(Instant.now());
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        assertThatThrownBy(() -> service.approveStory(owner, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
        verify(enqueuer, never()).enqueue(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    void approveStory_valid_setsApprovedAtAndEnqueuesCharacterSheet() {
        Storybook book = new Storybook();
        book.setStatus(StorybookStatus.STORY_READY);
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        service.approveStory(owner, 10L);

        assertThat(book.getStoryApprovedAt()).isNotNull();
        verify(enqueuer).enqueue(10L, JobStep.CHARACTER_SHEET, -1, 1);
    }
}

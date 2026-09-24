package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorybookResumeServiceTest {

    @Mock
    private StorybookAccessGuard guard;
    @Mock
    private StorybookJobRepository jobs;

    private StorybookStateMachine stateMachine;
    private StorybookResumeService resumeService;
    private User owner;

    @BeforeEach
    void setUp() {
        stateMachine = new StorybookStateMachine();
        resumeService = new StorybookResumeService(guard, stateMachine, jobs);
        owner = new User();
        owner.setId(1L);
    }

    @Test
    void resume_bookNotFailed_throwsStateConflict() {
        Storybook book = new Storybook();
        book.setStatus(StorybookStatus.DRAFT);
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        assertThatThrownBy(() -> resumeService.resume(owner, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
        verify(jobs, never()).reviveDeadJobs(anyLong());
    }

    @Test
    void resume_bookFailedWithoutFailedFromStatus_throwsStateConflict() {
        Storybook book = new Storybook();
        book.setStatus(StorybookStatus.FAILED);
        book.setFailedFromStatus(null);
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        assertThatThrownBy(() -> resumeService.resume(owner, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
        verify(jobs, never()).reviveDeadJobs(anyLong());
    }

    @Test
    void resume_validFailedBook_transitionsBackAndRevivesDeadJobs() {
        Storybook book = new Storybook();
        book.setStatus(StorybookStatus.FAILED);
        book.setFailedFromStatus(StorybookStatus.ILLUSTRATING);
        book.setFailureReason("Provider down");
        when(guard.requireOwned(10L, owner)).thenReturn(book);

        resumeService.resume(owner, 10L);

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(book.getFailedFromStatus()).isNull();
        assertThat(book.getFailureReason()).isNull();
        verify(jobs).reviveDeadJobs(10L);
    }
}

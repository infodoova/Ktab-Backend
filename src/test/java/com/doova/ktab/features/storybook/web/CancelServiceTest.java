package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.billing.StorybookCreditPort;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CancelServiceTest {

    @Test
    void cancelsAndRefunds() {
        StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
        StorybookCreditPort credits = mock(StorybookCreditPort.class);
        User owner = new User();
        Storybook book = new Storybook();
        book.setId(4L);
        book.setStatus(StorybookStatus.CHARACTER_READY);
        when(guard.requireOwned(4L, owner)).thenReturn(book);

        new CancelService(guard, new StorybookStateMachine(), credits).cancel(owner, 4L);

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.CANCELLED);
        verify(credits).release(4L);
    }
}

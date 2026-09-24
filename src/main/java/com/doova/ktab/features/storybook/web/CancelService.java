package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.billing.StorybookCreditPort;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelService {

    private final StorybookAccessGuard guard;
    private final StorybookStateMachine stateMachine;
    private final StorybookCreditPort credits;

    @Transactional
    public void cancel(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        stateMachine.cancel(book);
        credits.release(bookId);
    }
}

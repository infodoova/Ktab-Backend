package com.doova.ktab.features.storybook.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminGrantedCreditAdapter implements StorybookCreditPort {

    private final StorybookCreditAccountRepository accounts;
    private final StorybookCreditHoldRepository holds;

    @Override
    @Transactional
    public void reserve(Long userId, Long bookId, int units) {
        if (holds.findByStorybookId(bookId).isPresent()) {
            return;
        }
        if (accounts.tryDebit(userId, units) != 1) {
            throw new StorybookPaymentRequiredException();
        }
        StorybookCreditHold hold = new StorybookCreditHold();
        hold.setStorybookId(bookId);
        hold.setUserId(userId);
        hold.setUnits(units);
        hold.setStatus(CreditHoldStatus.HELD);
        holds.save(hold);
    }

    @Override
    @Transactional
    public void commit(Long bookId) {
        holds.findByStorybookId(bookId).filter(h -> h.getStatus() == CreditHoldStatus.HELD)
                .ifPresent(h -> h.setStatus(CreditHoldStatus.COMMITTED));
    }

    @Override
    @Transactional
    public void release(Long bookId) {
        holds.findByStorybookId(bookId).filter(h -> h.getStatus() == CreditHoldStatus.HELD).ifPresent(h -> {
            h.setStatus(CreditHoldStatus.RELEASED);
            accounts.credit(h.getUserId(), h.getUnits());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public int balance(Long userId) {
        return accounts.findByUserId(userId).map(StorybookCreditAccount::getBalance).orElse(0);
    }

    @Override
    @Transactional
    public void grant(Long userId, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        accounts.credit(userId, units);
    }
}

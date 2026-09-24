package com.doova.ktab.features.storybook.billing;

/**
 * The storybook feature's only billing dependency. Replace AdminGrantedCreditAdapter with an
 * EntitlementService-backed adapter (reserve -> execute -> commit/release, see
 * docs/Ktab_Subscription_Entitlement_Architecture_Final.md section 2.3) when features.subscription ships.
 */
public interface StorybookCreditPort {
    void reserve(Long userId, Long bookId, int units);
    void commit(Long bookId);
    void release(Long bookId);
    int balance(Long userId);
    void grant(Long userId, int units);
}

package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(AdminGrantedCreditAdapter.class)
class AdminGrantedCreditAdapterIT extends StorybookJpaIT {

    @Autowired StorybookCreditPort credits;
    @Autowired StorybookCreditHoldRepository holds;

    @Test
    void reserveIsAtomicAndIdempotent() {
        User parent = UserFixtures.reader(em, "credit@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, parent);
        credits.grant(parent.getId(), 1);

        credits.reserve(parent.getId(), book.getId(), 1);
        credits.reserve(parent.getId(), book.getId(), 1); // same book: no second charge

        assertThat(credits.balance(parent.getId())).isZero();
        assertThat(holds.findByStorybookId(book.getId())).get()
                .extracting(StorybookCreditHold::getStatus).isEqualTo(CreditHoldStatus.HELD);

        Storybook other = StorybookEntityFixtures.newBook(em, parent);
        assertThatThrownBy(() -> credits.reserve(parent.getId(), other.getId(), 1))
                .isInstanceOf(StorybookPaymentRequiredException.class);
    }

    @Test
    void releaseRefundsOnceAndCommitIsFinal() {
        User parent = UserFixtures.reader(em, "credit2@example.com");
        Storybook a = StorybookEntityFixtures.newBook(em, parent);
        Storybook b = StorybookEntityFixtures.newBook(em, parent);
        credits.grant(parent.getId(), 2);
        credits.reserve(parent.getId(), a.getId(), 1);
        credits.reserve(parent.getId(), b.getId(), 1);

        credits.release(a.getId());
        credits.release(a.getId());
        credits.commit(b.getId());
        credits.release(b.getId()); // committed: no refund

        assertThat(credits.balance(parent.getId())).isEqualTo(1);
    }

    @Test
    void noAccountMeansZero() {
        assertThat(credits.balance(UserFixtures.reader(em, "credit3@example.com").getId())).isZero();
    }
}

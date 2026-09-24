package com.doova.ktab.features.storybook.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StorybookCreditAccountRepository extends JpaRepository<StorybookCreditAccount, Long> {

    Optional<StorybookCreditAccount> findByUserId(Long userId);

    /** Check and decrement in one statement (entitlement doc rule: never SELECT then UPDATE). */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE tbl_storybook_credit_accounts
            SET col_balance = col_balance - :units, updated_at = now(), version = version + 1
            WHERE col_user_id = :userId AND col_balance >= :units
            """)
    int tryDebit(@Param("userId") Long userId, @Param("units") int units);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO tbl_storybook_credit_accounts (col_user_id, col_balance, created_at, updated_at, version)
            VALUES (:userId, :units, now(), now(), 0)
            ON CONFLICT (col_user_id) DO UPDATE
            SET col_balance = tbl_storybook_credit_accounts.col_balance + :units, updated_at = now(),
                version = tbl_storybook_credit_accounts.version + 1
            """)
    int credit(@Param("userId") Long userId, @Param("units") int units);
}

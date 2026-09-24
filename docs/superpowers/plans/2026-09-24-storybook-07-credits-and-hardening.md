# Storybook 07 — Credits and Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Read `2026-09-24-storybook-00-overview.md` first — its "Shared contracts" and "Global Constraints" sections apply to every task here.

**Goal:** Make the feature safe to open to real parents: charge a credit per book (with a billing port ready for Ktab's future subscription module), let parents cancel with a refund, cap drafts and per-book AI spend, give admins a review queue for pages that failed QA, clean up R2 assets of deleted books, emit metrics, and write the launch checklist.

**Architecture:** `StorybookCreditPort` is the only billing dependency of the feature (overview decision D8 and the entitlement architecture doc's rule "feature code never depends on a payment provider"). Its first adapter, `AdminGrantedCreditAdapter`, keeps a per-user balance in Postgres with atomic decrements; it is swapped for an `EntitlementService`-backed adapter when `features.subscription` ships. A credit is reserved at story approval, committed when the book first reaches `READY`, and released on cancellation. Hardening pieces hook into existing classes at single, named points.

**Tech Stack:** Spring Boot 3.5.7, Spring Data JPA native queries, Micrometer (`Metrics` global registry), AWS SDK v2 `S3Client`, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`

## Global Constraints

See the overview. Most relevant here:

- Billing must not leak into feature code: only `StorybookCreditPort` is called from the pipeline.
- Two parent approvals come before any image money is spent; the credit is taken at the first one (D8).
- Safety and privacy are launch requirements; the photo, if any, is already purged after the sheet (sub-plan 05).
- Next free Flyway version at the time of writing is V15 — re-check before creating the file.

## Review Focus

- **Two browser tabs approve the story at once for a parent with exactly one credit.** Expected: one approval succeeds; the other gets 409 (already approved) or 402 (no credit) — never a negative balance or two holds. Test: Task 1, `AdminGrantedCreditAdapterIT.reserveIsAtomicAndIdempotent`.
- **A runaway retry loop keeps paying for images.** Expected: once a book's recorded AI cost exceeds `maxBookCostUsd` ($6.00), its next job fails the book instead of calling a provider. Test: Task 4, `JobWorkerCostGuardTest`.
- **A parent account is deleted by an admin.** Expected: the database rows cascade away and the book's R2 objects are removed by the next daily sweep. Test: Task 6, `StorybookOrphanSweeperTest`.

## File structure

```
src/main/resources/db/migration/V15__storybook_credits.sql                  (Task 1)
src/main/java/com/doova/ktab/features/storybook/billing/
├── StorybookCreditPort.java  AdminGrantedCreditAdapter.java                (Task 1)
├── StorybookCreditAccount.java  StorybookCreditHold.java  CreditHoldStatus.java (Task 1)
├── StorybookCreditAccountRepository.java  StorybookCreditHoldRepository.java (Task 1)
└── StorybookPaymentRequiredException.java                                  (Task 1)
src/main/java/com/doova/ktab/features/storybook/web/CancelService.java      (Task 2)
src/main/java/com/doova/ktab/features/storybook/orchestrator/StorybookCostGuard.java (Task 4)
src/main/java/com/doova/ktab/features/storybook/admin/StorybookAdminService.java, StorybookAdminController.java, FlaggedPageView.java (Task 5)
src/main/java/com/doova/ktab/features/storybook/storage/StorybookOrphanSweeper.java (Task 6)
src/main/java/com/doova/ktab/features/storybook/metrics/StorybookMetrics.java (Task 7)
docs/storybook/launch-checklist.md                                          (Task 8)
Modified: StorybookProperties, StoryApprovalService (04), RenderPersistence (06), StorybookStateMachine (03),
          StorybookService (02), ChildProfileService (02), JobWorker (03), IllustrationPersistence (05),
          StorybookAssetStore (05), JobOutcomeRecorder (03), AiCallLedger (02), StorybookController
```

---

### Task 1: Credit port, adapter and the reserve / commit hooks

**Files:**
- Create: `src/main/resources/db/migration/V15__storybook_credits.sql`
- Create: the `billing/` classes listed above
- Modify: `src/main/java/com/doova/ktab/features/storybook/config/StorybookProperties.java` (add `Credits`)
- Modify: `src/main/java/com/doova/ktab/features/storybook/story/pipeline/StoryApprovalService.java` (reserve)
- Modify: `src/main/java/com/doova/ktab/features/storybook/render/RenderPersistence.java` (commit)
- Test: `src/test/java/com/doova/ktab/features/storybook/billing/AdminGrantedCreditAdapterIT.java`; update `StoryApprovalServiceIT` and `RenderPersistenceIT`

**Interfaces:**
- Consumes: `ApiMessageKey.STORYBOOK_INSUFFICIENT_CREDITS` (02), `KtabException` (Ktab).
- Produces:
  - `StorybookProperties.Credits` — `required` (default `true`), `unitsPerBook` (default `1`); accessor `getCredits()`.
  - `interface StorybookCreditPort { void reserve(Long userId, Long bookId, int units); void commit(Long bookId); void release(Long bookId); int balance(Long userId); void grant(Long userId, int units); }` — `reserve` is idempotent per book (a second call for a book with an existing hold does nothing) and throws `StorybookPaymentRequiredException` (HTTP 402) when the balance is short. `commit` and `release` are idempotent and only act on a `HELD` hold. All methods join the caller's transaction.
  - `StoryApprovalService.approveStory` calls `reserve(owner.getId(), bookId, unitsPerBook)` right after its state check (skipped when `credits.required=false`).
  - `RenderPersistence.finish` calls `commit(bookId)` after moving the book to `READY`.

- [x] **Step 1: Write the migration**

`src/main/resources/db/migration/V15__storybook_credits.sql`:
```sql
-- ============================================================================
-- Flyway Migration V15: storybook credits (admin-granted until features.subscription ships)
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_storybook_credit_accounts (
    col_id               BIGSERIAL   PRIMARY KEY,
    col_user_id          BIGINT      NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_balance          INTEGER     NOT NULL DEFAULT 0 CHECK (col_balance >= 0),
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_credit_account_user UNIQUE (col_user_id)
);

CREATE TABLE IF NOT EXISTS tbl_storybook_credit_holds (
    col_id               BIGSERIAL   PRIMARY KEY,
    col_storybook_id     BIGINT      NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_user_id          BIGINT      NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_units            INTEGER     NOT NULL CHECK (col_units > 0),
    col_status           VARCHAR(20) NOT NULL,   -- HELD | COMMITTED | RELEASED
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_credit_hold_book UNIQUE (col_storybook_id)
);
```

- [x] **Step 2: Write the failing test**

`AdminGrantedCreditAdapterIT.java`:
```java
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
```

- [x] **Step 3: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=AdminGrantedCreditAdapterIT`
Expected: COMPILATION ERROR.

- [x] **Step 4: Implement the billing package**

`CreditHoldStatus.java`:
```java
package com.doova.ktab.features.storybook.billing;

public enum CreditHoldStatus { HELD, COMMITTED, RELEASED }
```

`StorybookCreditAccount.java`:
```java
package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_storybook_credit_accounts")
@Getter
@Setter
public class StorybookCreditAccount extends BaseEntity {

    @Column(name = "col_user_id", nullable = false)
    private Long userId;

    @Column(name = "col_balance", nullable = false)
    private int balance;
}
```

`StorybookCreditHold.java`:
```java
package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_storybook_credit_holds")
@Getter
@Setter
public class StorybookCreditHold extends BaseEntity {

    @Column(name = "col_storybook_id", nullable = false)
    private Long storybookId;

    @Column(name = "col_user_id", nullable = false)
    private Long userId;

    @Column(name = "col_units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private CreditHoldStatus status;
}
```

`StorybookCreditAccountRepository.java`:
```java
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
```

`StorybookCreditHoldRepository.java`:
```java
package com.doova.ktab.features.storybook.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorybookCreditHoldRepository extends JpaRepository<StorybookCreditHold, Long> {
    Optional<StorybookCreditHold> findByStorybookId(Long storybookId);
}
```

`StorybookPaymentRequiredException.java`:
```java
package com.doova.ktab.features.storybook.billing;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class StorybookPaymentRequiredException extends KtabException {

    public StorybookPaymentRequiredException() {
        super(ApiMessageKey.STORYBOOK_INSUFFICIENT_CREDITS);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.PAYMENT_REQUIRED;
    }
}
```

`StorybookCreditPort.java`:
```java
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
```

`AdminGrantedCreditAdapter.java`:
```java
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
```

The unique constraint on `col_storybook_id` backs the "one hold per book" rule if two approvals race past the `findByStorybookId` check; the loser's transaction fails on insert and the approval returns 409 through Ktab's `DataIntegrityViolationException` handler.

The native `tryDebit` / `credit` updates bypass the JPA persistence context. Do not load a `StorybookCreditAccount` entity and then trust its `balance` after one of those updates in the same transaction; `balance()` is meant to be a fresh read.

- [x] **Step 5: Add the properties**

In `StorybookProperties`, add `private Credits credits = new Credits();` and:
```java
    @Getter
    @Setter
    public static class Credits {
        /** false only for internal testing: approvals then never touch credits. */
        private boolean required = true;
        private int unitsPerBook = 1;
    }
```

- [x] **Step 6: Hook the reservation into story approval**

In `StoryApprovalService`, add fields `private final com.doova.ktab.features.storybook.billing.StorybookCreditPort credits;` and `private final com.doova.ktab.features.storybook.config.StorybookProperties properties;`, and in `approveStory`, right after the state check and before `book.setStoryApprovedAt(...)`:
```java
        if (properties.getCredits().isRequired()) {
            credits.reserve(owner.getId(), bookId, properties.getCredits().getUnitsPerBook());
        }
```
In `StoryApprovalServiceIT`, add `AdminGrantedCreditAdapter.class` and `StorybookProperties.class` to `@Import`, grant the owner 1 credit before approving (`credits.grant(owner.getId(), 1)` with `@Autowired StorybookCreditPort credits`), and add:
```java
    @Test
    void approvalWithoutCreditIsPaymentRequired() {
        User owner = UserFixtures.reader(em, "broke@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        book.setStatus(StorybookStatus.STORY_READY);
        em.flush();
        assertThatThrownBy(() -> approvals.approveStory(owner, book.getId()))
                .isInstanceOf(com.doova.ktab.features.storybook.billing.StorybookPaymentRequiredException.class);
        assertThat(book.getStoryApprovedAt()).isNull();
    }
```

- [x] **Step 7: Commit the credit when the book is ready**

In `RenderPersistence`, add the field `private final com.doova.ktab.features.storybook.billing.StorybookCreditPort credits;` and at the end of `finish(...)`, after the transition:
```java
        credits.commit(bookId);
```
Add `AdminGrantedCreditAdapter.class` to `RenderPersistenceIT`'s `@Import`.

- [x] **Step 8: Run the tests**

Run: `./mvnw -q test -Dtest='AdminGrantedCreditAdapterIT,StoryApprovalServiceIT,RenderPersistenceIT'`
Expected: all PASS.

- [x] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V15__storybook_credits.sql src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): reserve a credit at story approval and commit it on delivery"
```

---

### Task 2: Cancellation with refund

**Files:**
- Modify: `src/main/java/com/doova/ktab/features/storybook/orchestrator/StorybookStateMachine.java` (add `cancel`)
- Create: `src/main/java/com/doova/ktab/features/storybook/web/CancelService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/orchestrator/StorybookStateMachineTest.java` (new cases), `src/test/java/com/doova/ktab/features/storybook/web/CancelServiceTest.java`

**Interfaces:**
- Produces:
  - `StorybookStateMachine.cancel(Storybook book)` — allowed from `DRAFT`, `STORY_READY`, `CHARACTER_READY` and `FAILED` (a parent may give up on a failed book); otherwise 409. Sets `CANCELLED`.
  - `CancelService.cancel(User owner, Long bookId)` — `@Transactional`; owner check; `stateMachine.cancel`; `credits.release(bookId)`. Pending jobs become no-ops because every handler checks the book status.
  - Endpoint: `POST /storybook/books/{bookId}/cancel` → 202.

- [x] **Step 1: Write the failing tests**

Add to `StorybookStateMachineTest`:
```java
    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = StorybookStatus.class, names = {"DRAFT", "STORY_READY", "CHARACTER_READY", "FAILED"})
    void cancellableStatuses(StorybookStatus from) {
        Storybook b = book(from);
        machine.cancel(b);
        assertThat(b.getStatus()).isEqualTo(CANCELLED);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = StorybookStatus.class, names = {"ILLUSTRATING", "QA", "RENDERING", "READY", "CANCELLED"})
    void notCancellableOnceImagesAreBeingMade(StorybookStatus from) {
        assertThatThrownBy(() -> machine.cancel(book(from))).isInstanceOf(StorybookStateConflictException.class);
    }
```

`CancelServiceTest.java`:
```java
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
```

- [x] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StorybookStateMachineTest,CancelServiceTest'`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement**

Add to `StorybookStateMachine`:
```java
    private static final Set<StorybookStatus> CANCELLABLE = EnumSet.of(DRAFT, STORY_READY, CHARACTER_READY, FAILED);

    public void cancel(Storybook book) {
        if (!CANCELLABLE.contains(book.getStatus())) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        book.setStatus(CANCELLED);
    }
```

`CancelService.java`:
```java
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
```

In `StorybookController`, add the field `private final CancelService cancelService;` (after `readerService`) and:
```java
    @PostMapping("/books/{bookId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@CurrentUser User user, @PathVariable Long bookId) {
        cancelService.cancel(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }
```
Add `mock(CancelService.class)` as the next argument in `StorybookControllerTest.setUp()`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StorybookStateMachineTest,CancelServiceTest,StorybookControllerTest'`
Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): let parents cancel before illustration with a credit refund"
```

---

### Task 3: Daily draft limit

**Files:**
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookService.java`
- Modify: `src/test/java/com/doova/ktab/features/storybook/web/StorybookServiceTest.java`

**Interfaces:**
- Consumes: `StorybookRepository.countByOwner_IdAndCreatedAtAfter` (02), `StorybookProperties.getLimits().getDraftsPerUserPerDay()` (01, default 3).
- Produces: `StorybookService.create` rejects with 400 `STORYBOOK_LIMIT_REACHED` when the caller created `draftsPerUserPerDay` books in the last 24 hours. Checked first, before moderation, so a blocked request costs nothing.

- [x] **Step 1: Write the failing test**

`StorybookService` needs `StorybookProperties`; add it to the constructor call in `StorybookServiceTest` (new last argument `new StorybookProperties()`), keep a reference to the `StorybookRepository` mock as a field `books`, and add:
```java
    @Test
    void fourthDraftInADayIsRejectedBeforeAnyLlmCall() {
        when(books.countByOwner_IdAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, "إلى سامي"))).hasMessage("STORYBOOK_LIMIT_REACHED");
        verifyNoInteractions(moderation);
    }
```

- [x] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookServiceTest`
Expected: FAIL (compilation until the constructor gains `StorybookProperties`, then the assertion).

- [x] **Step 3: Implement**

Add `private final StorybookProperties properties;` as the last field of `StorybookService`, and make this the first statement of `create`:
```java
        long recent = books.countByOwner_IdAndCreatedAtAfter(owner.getId(), java.time.LocalDateTime.now().minusDays(1));
        if (recent >= properties.getLimits().getDraftsPerUserPerDay()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }
```

- [x] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=StorybookServiceTest`
Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/web/StorybookService.java src/test/java/com/doova/ktab/features/storybook/web/StorybookServiceTest.java
git commit -m "feat(storybook): cap free story drafts per parent per day"
```

---

### Task 4: Per-book cost guard

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/orchestrator/StorybookCostGuard.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/orchestrator/JobWorker.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/orchestrator/JobWorkerCostGuardTest.java`; update `JobWorkerTest`

**Interfaces:**
- Consumes: `StorybookRepository` (02), `StorybookProperties.getLimits().getMaxBookCostUsd()` (01).
- Produces:
  - `StorybookCostGuard.exceeded(Long bookId) : boolean` — `@Transactional(readOnly = true)`; `totalCostUsd > maxBookCostUsd`.
  - `JobWorker` gains a `StorybookCostGuard` constructor argument (last). In `runOne`, before calling the handler: if `exceeded` → `StepOutcome.fail("Book AI cost exceeded $<limit>")` and an `ERROR` log line. `PURGE_PHOTO` jobs are exempt (privacy work must still happen).

- [x] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobWorkerCostGuardTest {

    @Test
    void overBudgetBooksFailInsteadOfCallingTheHandler() {
        JobOutcomeRecorder recorder = mock(JobOutcomeRecorder.class);
        StorybookCostGuard guard = mock(StorybookCostGuard.class);
        when(guard.exceeded(9L)).thenReturn(true);
        AtomicBoolean called = new AtomicBoolean();
        StepHandler handler = new StepHandler() {
            public JobStep step() { return JobStep.ILLUSTRATE_PAGE; }
            public StepOutcome handle(StorybookJob job) { called.set(true); return StepOutcome.success(); }
        };
        JobWorker worker = new JobWorker(mock(JobClaimer.class), recorder, new StepHandlerRegistry(List.of(handler)),
                new SyncTaskExecutor(), new StorybookProperties(), guard);
        StorybookJob job = new StorybookJob();
        job.setId(1L);
        job.setStorybookId(9L);
        job.setStep(JobStep.ILLUSTRATE_PAGE);

        worker.runOne(job);

        assertThat(called).isFalse();
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.FAIL && o.reason().contains("6.00")));
    }
}
```

In `JobWorkerTest.worker(...)`, pass a `StorybookCostGuard` mock as the new last argument (its `exceeded` returns `false` by default).

- [x] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest='JobWorkerCostGuardTest,JobWorkerTest'`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement**

`StorybookCostGuard.java`:
```java
package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StorybookCostGuard {

    private final StorybookRepository books;
    private final StorybookProperties properties;

    @Transactional(readOnly = true)
    public boolean exceeded(Long bookId) {
        return books.findById(bookId)
                .map(b -> b.getTotalCostUsd().compareTo(properties.getLimits().getMaxBookCostUsd()) > 0)
                .orElse(false);
    }
}
```

In `JobWorker`, add the field `private final StorybookCostGuard costGuard;` and the constructor parameter `StorybookCostGuard costGuard` (last). In `runOne`, replace `if (handler == null) {` with:
```java
        if (job.getStep() != com.doova.ktab.features.storybook.enums.JobStep.PURGE_PHOTO
                && costGuard.exceeded(job.getStorybookId())) {
            log.error("storybook book {} exceeded its AI cost cap; failing job {} ({})",
                    job.getStorybookId(), job.getId(), job.getStep());
            outcome = StepOutcome.fail("Book AI cost exceeded $" + properties.getLimits().getMaxBookCostUsd());
        } else if (handler == null) {
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='JobWorkerCostGuardTest,JobWorkerTest'`
Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/orchestrator src/test/java/com/doova/ktab/features/storybook/orchestrator
git commit -m "feat(storybook): stop spending on a book past its AI cost cap"
```

---

### Task 5: Admin review queue and credit grants

**Files:**
- Modify: `src/main/java/com/doova/ktab/features/storybook/illustration/IllustrationPersistence.java` (`advance` also handles `QA`)
- Create: `src/main/java/com/doova/ktab/features/storybook/admin/FlaggedPageView.java`, `StorybookAdminService.java`, `StorybookAdminController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/admin/StorybookAdminServiceIT.java`

**Interfaces:**
- Consumes: `IllustrationPersistence.advanceIfDone` (05), `StorybookStateMachine`, `JobEnqueuer` (03), `StorybookCreditPort` (Task 1), repositories (02), `FileStorageService` (Ktab).
- Produces:
  - `IllustrationPersistence.advance(...)` acts on `ILLUSTRATING` **and** `QA`: all pages OK → `RENDERING` + `RENDER_PDF`; from `ILLUSTRATING` with a flag and nothing pending → `QA`; from `QA` with a flag still present → stays `QA`.
  - `record FlaggedPageView(Long bookId, Long pageId, int pageIndex, int generation, String imageUrl, String sceneEn, List<String> problems)`.
  - `StorybookAdminService`: `List<FlaggedPageView> flaggedPages()`, `void accept(Long pageId)` (current image must be `FLAGGED` → `ACCEPTED_BY_ADMIN`, then `advanceIfDone`), `void regenerate(Long pageId)` (book `QA → ILLUSTRATING`; `g = generation + 1`; `roundStartGeneration = g − (maxGenerations − 1)` so this is the round's last attempt, on the fallback model, and flags again if it fails; enqueue `ILLUSTRATE_PAGE`), `void grantCredits(Long userId, int units)`.
  - `StorybookAdminController` (`/admin/storybook`, `hasAnyAuthority('ADMIN')`): `GET /flagged-pages`, `POST /pages/{pageId}/accept`, `POST /pages/{pageId}/regenerate`, `POST /credits` with body `{"userId": 1, "units": 3}`.
  - `StorybookPageImageRepository.findFlaggedCurrentImages()` — JPQL: images with status `FLAGGED` that are their page's `currentImage`.

- [x] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.admin;

import com.doova.ktab.features.storybook.billing.AdminGrantedCreditAdapter;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.story.pipeline.StoryPersistence;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({StorybookAdminService.class, IllustrationPersistence.class, JobEnqueuer.class, StorybookStateMachine.class,
        StorybookProperties.class, StoryPersistence.class, AdminGrantedCreditAdapter.class})
class StorybookAdminServiceIT extends StorybookJpaIT {

    @Autowired StorybookAdminService admin;
    @Autowired IllustrationPersistence illustration;
    @Autowired StoryPersistence stories;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookPageImageRepository images;
    @Autowired StorybookJobRepository jobs;
    @MockBean FileStorageService storage;

    private static final VisualQaResponse PASS = new VisualQaResponse(true, false, true, true, List.of());
    private static final VisualQaResponse FAIL = new VisualQaResponse(false, true, true, true, List.of("letters on a sign"));

    /** A 10-page book parked in QA with page 4 flagged and every other page passed. */
    private Storybook bookInQa() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "admin-" + System.nanoTime() + "@example.com"));
        stories.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        for (StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())) {
            p.setGeneration(p.getPageIndex() == 4 ? 4 : 1);
            p.setRoundStartGeneration(1);
            int g = p.getGeneration();
            illustration.savePageImage(book.getId(), p.getId(), p.getPageIndex(), g, "k" + p.getPageIndex(), "m", BigDecimal.ZERO);
            em.flush();
            Long imageId = images.findByPage_IdAndGeneration(p.getId(), g).orElseThrow().getId();
            illustration.recordQa(book.getId(), p.getId(), imageId, g, p.getPageIndex() == 4 ? FAIL : PASS);
        }
        em.flush();
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.QA);
        return book;
    }

    @Test
    void listsAndAcceptsAFlaggedPage() {
        Storybook book = bookInQa();

        List<FlaggedPageView> flagged = admin.flaggedPages();
        FlaggedPageView view = flagged.stream().filter(f -> f.bookId().equals(book.getId())).findFirst().orElseThrow();
        assertThat(view.pageIndex()).isEqualTo(4);
        assertThat(view.problems()).containsExactly("letters on a sign");

        admin.accept(view.pageId());
        em.flush();

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.RENDERING);
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .filteredOn(j -> j.getStep() == JobStep.RENDER_PDF).hasSize(1);
    }

    @Test
    void regenerationIsOneFinalAttemptOnTheFallbackModel() {
        Storybook book = bookInQa();
        StorybookPage page4 = pages.findByStorybook_IdAndPageIndex(book.getId(), 4).orElseThrow();

        admin.regenerate(page4.getId());
        em.flush();

        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(page4.getGeneration()).isEqualTo(5);
        assertThat(5 - page4.getRoundStartGeneration() + 1).isEqualTo(4); // last attempt of a round
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getPageIndex() + ":" + j.getGeneration())
                .contains(JobStep.ILLUSTRATE_PAGE + ":4:5");
    }
}
```

`@MockBean` is deprecated in Spring Boot 3.4+ in favour of `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`); use `@MockitoBean` if the compiler warns.

- [x] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookAdminServiceIT`
Expected: COMPILATION ERROR.

- [x] **Step 3: Let `advance` handle `QA`**

In `IllustrationPersistence.advance`, replace the first `if` and the final `if/else if` with:
```java
        if (book.getStatus() != StorybookStatus.ILLUSTRATING && book.getStatus() != StorybookStatus.QA) {
            return;
        }
```
and
```java
        if (allOk) {
            stateMachine.transition(book, StorybookStatus.RENDERING);
            enqueuer.enqueue(book.getId(), JobStep.RENDER_PDF, -1, book.getPageRegenerations());
        } else if (anyFlagged && book.getStatus() == StorybookStatus.ILLUSTRATING) {
            stateMachine.transition(book, StorybookStatus.QA);
        }
```

- [x] **Step 4: Add the repository query**

Add to `StorybookPageImageRepository`:
```java
    @org.springframework.data.jpa.repository.Query("""
            select i from StorybookPageImage i join i.page p
            where i.status = com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED
              and p.currentImage = i
            order by i.createdAt asc
            """)
    List<StorybookPageImage> findFlaggedCurrentImages();
```

- [x] **Step 5: Implement the admin service and controller**

`FlaggedPageView.java`:
```java
package com.doova.ktab.features.storybook.admin;

import java.util.List;

public record FlaggedPageView(Long bookId, Long pageId, int pageIndex, int generation, String imageUrl,
                              String sceneEn, List<String> problems) {
}
```

`StorybookAdminService.java`:
```java
package com.doova.ktab.features.storybook.admin;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.billing.StorybookCreditPort;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.illustration.IllustrationPersistence;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorybookAdminService {

    private final StorybookPageImageRepository images;
    private final StorybookPageRepository pages;
    private final StorybookRepository books;
    private final IllustrationPersistence illustration;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookCreditPort credits;
    private final StorybookProperties properties;
    private final FileStorageService storage;

    @Transactional(readOnly = true)
    public List<FlaggedPageView> flaggedPages() {
        return images.findFlaggedCurrentImages().stream().map(i -> {
            StorybookPage p = i.getPage();
            return new FlaggedPageView(p.getStorybook().getId(), p.getId(), p.getPageIndex(), i.getGeneration(),
                    storage.getFileUrl(i.getImageKey(), UrlStrategy.SIGNED), p.getSceneEn(),
                    i.getQaResult() == null || i.getQaResult().problems() == null ? List.of() : i.getQaResult().problems());
        }).toList();
    }

    @Transactional
    public void accept(Long pageId) {
        StorybookPage page = pages.findById(pageId).orElseThrow();
        StorybookPageImage image = page.getCurrentImage();
        if (image == null || image.getStatus() != PageImageStatus.FLAGGED) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        image.setStatus(PageImageStatus.ACCEPTED_BY_ADMIN);
        illustration.advanceIfDone(page.getStorybook().getId());
    }

    @Transactional
    public void regenerate(Long pageId) {
        StorybookPage page = pages.findById(pageId).orElseThrow();
        Storybook book = books.findByIdForUpdate(page.getStorybook().getId()).orElseThrow();
        if (book.getStatus() != StorybookStatus.QA || page.getCurrentImage() == null
                || page.getCurrentImage().getStatus() != PageImageStatus.FLAGGED) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        stateMachine.transition(book, StorybookStatus.ILLUSTRATING);
        int next = page.getGeneration() + 1;
        page.setGeneration(next);
        page.setRoundStartGeneration(next - (properties.getImage().getMaxGenerations() - 1));
        enqueuer.enqueue(book.getId(), JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), next);
    }

    @Transactional
    public void grantCredits(Long userId, int units) {
        credits.grant(userId, units);
    }
}
```

`StorybookAdminController.java`:
```java
package com.doova.ktab.features.storybook.admin;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/storybook", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN')")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StorybookAdminController {

    private final StorybookAdminService admin;
    private final MessageSource messageSource;

    public record GrantRequest(@NotNull Long userId, @NotNull @Min(1) @Max(100) Integer units) {
    }

    @GetMapping("/flagged-pages")
    public ResponseEntity<ApiResponse<List<FlaggedPageView>>> flagged() {
        return ResponseUtils.success(admin.flaggedPages(), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @PostMapping("/pages/{pageId}/accept")
    public ResponseEntity<ApiResponse<Void>> accept(@PathVariable Long pageId) {
        admin.accept(pageId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/pages/{pageId}/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerate(@PathVariable Long pageId) {
        admin.regenerate(pageId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @PostMapping("/credits")
    public ResponseEntity<ApiResponse<Void>> grant(@Valid @RequestBody GrantRequest request) {
        admin.grantCredits(request.userId(), request.units());
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.OK);
    }
}
```

- [x] **Step 6: Run the tests**

Run: `./mvnw -q test -Dtest='StorybookAdminServiceIT,IllustrationPersistenceIT'`
Expected: all PASS.

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add admin review queue for flagged pages and credit grants"
```

---

### Task 6: R2 cleanup for deleted books

**Files:**
- Modify: `src/main/java/com/doova/ktab/features/storybook/storage/StorybookAssetStore.java` (add `listBookIds`, `deletePrefix`)
- Create: `src/main/java/com/doova/ktab/features/storybook/storage/StorybookOrphanSweeper.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/ChildProfileService.java` (release credits before delete)
- Modify: `src/main/java/com/doova/ktab/features/storybook/repository/StorybookRepository.java` (add `findByChildProfile_Id`)
- Test: `src/test/java/com/doova/ktab/features/storybook/storage/StorybookOrphanSweeperTest.java`; update `ChildProfileServiceTest`

**Interfaces:**
- Produces:
  - `StorybookAssetStore.listBookIds() : Set<Long>` — lists `storybook/<id>/` prefixes (delimiter `/`, paginated).
  - `StorybookAssetStore.deletePrefix(String prefix)` — lists and batch-deletes (≤ 1000 keys per `DeleteObjects` call).
  - `StorybookOrphanSweeper.sweep()` — `@Scheduled(cron = "${ktab.storybook.orphan-sweep.cron:0 30 3 * * *}")`, only when the feature is enabled: for every listed book id without a `tbl_storybooks` row, `deletePrefix("storybook/<id>/")`. Covers every deletion path: child-profile deletion, and admin deletion of a user account (`PublisherAdminServiceImpl`), both of which cascade the rows but cannot reach R2.
  - `ChildProfileService.delete` releases every held credit of the profile's books (`credits.release(bookId)`) before deleting.

- [x] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.repository.StorybookRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class StorybookOrphanSweeperTest {

    @Test
    void deletesOnlyPrefixesWithoutABook() {
        StorybookAssetStore store = mock(StorybookAssetStore.class);
        StorybookRepository books = mock(StorybookRepository.class);
        when(store.listBookIds()).thenReturn(Set.of(1L, 2L, 3L));
        when(books.findAllById(Set.of(1L, 2L, 3L))).thenReturn(List.of(bookWithId(2L)));

        new StorybookOrphanSweeper(store, books).sweep();

        verify(store).deletePrefix("storybook/1/");
        verify(store).deletePrefix("storybook/3/");
        verify(store, never()).deletePrefix("storybook/2/");
    }

    private static com.doova.ktab.features.storybook.model.Storybook bookWithId(Long id) {
        var b = new com.doova.ktab.features.storybook.model.Storybook();
        b.setId(id);
        return b;
    }
}
```

In `ChildProfileServiceTest`, add `@Mock StorybookRepository books;` and `@Mock StorybookCreditPort credits;`, and extend `deleteRemovesOnlyAnOwnedProfile`:
```java
        Storybook b = new Storybook();
        b.setId(33L);
        when(books.findByChildProfile_Id(any())).thenReturn(List.of(b));
        service.delete(owner, 5L);
        verify(credits).release(33L);
        verify(repository).delete(p);
```
(Give the `ChildProfile` in that test an id: `p.setId(5L);`.)

- [x] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StorybookOrphanSweeperTest,ChildProfileServiceTest'`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement**

Add to `StorybookRepository`:
```java
    List<Storybook> findByChildProfile_Id(Long childProfileId);
```

Add to `StorybookAssetStore` (imports: `ListObjectsV2Request`, `ListObjectsV2Response`, `CommonPrefix`, `S3Object`, `ObjectIdentifier`, `Delete`, `DeleteObjectsRequest`, `java.util.*`):
```java
    public Set<Long> listBookIds() {
        Set<Long> ids = new HashSet<>();
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix("storybook/").delimiter("/").continuationToken(token).build());
            for (CommonPrefix p : page.commonPrefixes()) {
                String id = p.prefix().substring("storybook/".length(), p.prefix().length() - 1);
                if (id.chars().allMatch(Character::isDigit) && !id.isEmpty()) {
                    ids.add(Long.parseLong(id));
                }
            }
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
        return ids;
    }

    public void deletePrefix(String prefix) {
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix).continuationToken(token).build());
            List<ObjectIdentifier> keys = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build()).toList();
            if (!keys.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(Delete.builder().objects(keys).build()).build());
            }
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
    }
```
(`listObjectsV2` returns at most 1000 keys per page, which is also the `DeleteObjects` limit.)

`StorybookOrphanSweeper.java`:
```java
package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Removes R2 objects of books whose rows were deleted by any path (profile or account deletion). */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StorybookOrphanSweeper {

    private final StorybookAssetStore store;
    private final StorybookRepository books;

    @Scheduled(cron = "${ktab.storybook.orphan-sweep.cron:0 30 3 * * *}")
    public void sweep() {
        Set<Long> inStorage = store.listBookIds();
        if (inStorage.isEmpty()) {
            return;
        }
        Set<Long> existing = books.findAllById(inStorage).stream().map(Storybook::getId).collect(Collectors.toSet());
        for (Long id : inStorage) {
            if (!existing.contains(id)) {
                log.info("storybook orphan sweep: deleting R2 assets of deleted book {}", id);
                store.deletePrefix("storybook/" + id + "/");
            }
        }
    }
}
```

In `ChildProfileService`, add fields `private final com.doova.ktab.features.storybook.repository.StorybookRepository books;` and `private final com.doova.ktab.features.storybook.billing.StorybookCreditPort credits;`, and change `delete` to:
```java
    @Transactional
    public void delete(User owner, Long id) {
        ChildProfile profile = requireOwned(owner, id);
        books.findByChildProfile_Id(profile.getId()).forEach(b -> credits.release(b.getId()));
        repository.delete(profile);
    }
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StorybookOrphanSweeperTest,ChildProfileServiceTest'`
Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): sweep R2 assets of deleted books and refund held credits on profile deletion"
```

---

### Task 7: Metrics

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/metrics/StorybookMetrics.java`
- Modify: `JobOutcomeRecorder` (03), `AiCallLedger` (02), `IllustrationPersistence.recordQa` (05)
- Test: `src/test/java/com/doova/ktab/features/storybook/metrics/StorybookMetricsTest.java`

**Interfaces:**
- Produces: static helpers on Micrometer's global registry (Spring Boot adds its Prometheus registry to it; in unit tests it is a no-op composite), so no constructor changes ripple through earlier sub-plans:
  - `StorybookMetrics.jobOutcome(JobStep step, StepOutcome.Type type)` → counter `storybook.jobs{step,outcome}`
  - `StorybookMetrics.aiCost(String purpose, String model, BigDecimal usd)` → counter `storybook.ai.cost.usd{purpose,model}`
  - `StorybookMetrics.qaVerdict(String result)` → counter `storybook.qa{result}` with `pass`, `retry`, `flagged`
- Call sites: end of `JobOutcomeRecorder.record` (`jobOutcome(job.getStep(), outcome.type())`); `AiCallLedger.save` for successful rows (`aiCost(purpose, model, cost)`); each branch of `IllustrationPersistence.recordQa`.

- [x] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.metrics;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    @Test
    void countsJobsCostAndQa() {
        Metrics.addRegistry(registry);

        StorybookMetrics.jobOutcome(JobStep.ILLUSTRATE_PAGE, StepOutcome.Type.RETRY);
        StorybookMetrics.aiCost("IMAGE_PAGE", "gemini-3.1-flash-image-preview", new BigDecimal("0.101"));
        StorybookMetrics.qaVerdict("flagged");

        assertThat(registry.get("storybook.jobs").tag("step", "ILLUSTRATE_PAGE").tag("outcome", "RETRY").counter().count()).isEqualTo(1);
        assertThat(registry.get("storybook.ai.cost.usd").tag("purpose", "IMAGE_PAGE").counter().count()).isEqualTo(0.101);
        assertThat(registry.get("storybook.qa").tag("result", "flagged").counter().count()).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookMetricsTest`
Expected: COMPILATION ERROR.

- [x] **Step 3: Implement and wire**

```java
package com.doova.ktab.features.storybook.metrics;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import io.micrometer.core.instrument.Metrics;

import java.math.BigDecimal;

public final class StorybookMetrics {

    private StorybookMetrics() {
    }

    public static void jobOutcome(JobStep step, StepOutcome.Type type) {
        Metrics.counter("storybook.jobs", "step", step.name(), "outcome", type.name()).increment();
    }

    public static void aiCost(String purpose, String model, BigDecimal usd) {
        Metrics.counter("storybook.ai.cost.usd", "purpose", purpose, "model", model).increment(usd.doubleValue());
    }

    public static void qaVerdict(String result) {
        Metrics.counter("storybook.qa", "result", result).increment();
    }
}
```

Wire the call sites:
- `JobOutcomeRecorder.record`: add `StorybookMetrics.jobOutcome(job.getStep(), outcome.type());` as the last statement.
- `AiCallLedger.save`: after `calls.save(row);` add `if (e.success()) StorybookMetrics.aiCost(e.purpose(), e.model(), e.costUsd());`.
- `IllustrationPersistence.recordQa`: add `StorybookMetrics.qaVerdict("pass");`, `StorybookMetrics.qaVerdict("retry");` and `StorybookMetrics.qaVerdict("flagged");` in the three branches.

Confirm `management.metrics.use-global-registry` is not set to `false` in Ktab's properties (Spring Boot's default is `true`).

- [x] **Step 4: Run the tests and the whole storybook suite**

Run: `./mvnw -q test -Dtest=StorybookMetricsTest && ./mvnw -q test -Dtest='com.doova.ktab.features.storybook.**'`
Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add job, cost and QA metrics"
```

---

### Task 8: Launch checklist

**Files:**
- Create: `docs/storybook/launch-checklist.md`

- [x] **Step 1: Write the checklist**

`docs/storybook/launch-checklist.md`:
```markdown
# Personalized storybook — launch checklist

## Before enabling anywhere
- [ ] Phase 0 recorded a "go" in `docs/storybook/phase-0-results.md`.
- [ ] Nano Banana 2 / Nano Banana Pro enabled for the GCP project on Vertex AI, location `global`; SLA of the preview model accepted.
- [ ] Style reference delivered: `src/main/resources/storybook/styles/soft_watercolor.png`.
- [ ] 6–8 blueprints merged under `src/main/resources/storybook/blueprints/`; Eid/Ramadan ones have `"religious": true`.
- [ ] Dialect guides signed off by native Lebanese, Egyptian and Gulf reviewers.
- [ ] `PARTIAL` tashkeel rule confirmed by the MSA editor (decision D3).
- [ ] Legal review done for each launch market (GDPR, Saudi PDPL, UAE PDPL); photo consent wording and the retention policy page published.
- [ ] Data-use terms confirmed in writing for Google Vertex AI and Anthropic: inputs (including the child's photo) are not used for model training (spec: "no training on them"); the consent text names both processors.
- [ ] Owner of `features.studio` agrees that enabling scheduling also runs `StudioOrphanReconciler`.

## Environment variables
| Variable | Purpose |
|---|---|
| `KTAB_STORYBOOK_ENABLED=true` | Registers controllers, worker and sweeper |
| `ANTHROPIC_API_KEY` | Claude (text, critic, QA, moderation) |
| `GCP_PROJECT_ID` + existing Vertex credentials | Nano Banana |
| `KTAB_STORYBOOK_PHOTO_KEY` | Base64 of 32 random bytes (`openssl rand -base64 32`); per environment; never committed |

## Rollout
1. Staging: enable, run 5 books end to end (one per variety + one with a photo + one with a companion); open every PDF and check Arabic shaping, tashkeel, RTL order and that no image contains text.
2. Production, closed beta: enable with `ktab.storybook.credits.required=true`; grant credits to beta parents through `POST /api/v1/admin/storybook/credits`.
3. Watch for a week: `storybook.jobs{outcome="FAIL"}`, `storybook.qa{result="flagged"}`, `storybook.ai.cost.usd` per book (target ≤ $4), and the admin flagged-pages queue.
4. Before opening to all parents: connect `StorybookCreditPort` to the real payment flow (Ktab `features.subscription` or a one-off purchase flow) once the payment provider and retail price are decided.

## Operations
- Flagged pages: `GET /api/v1/admin/storybook/flagged-pages`; accept or regenerate.
- A stuck book: `tbl_storybook_jobs` (`col_status`, `col_last_error`); the parent (or support, as that user) can `POST /books/{id}/resume`.
- Cost incident: lower `ktab.storybook.limits.max-book-cost-usd` or set `KTAB_STORYBOOK_ENABLED=false` (in-flight jobs stop being claimed; nothing is lost).
```

- [x] **Step 2: Commit**

```bash
git add docs/storybook/launch-checklist.md
git commit -m "docs(storybook): add launch checklist"
```

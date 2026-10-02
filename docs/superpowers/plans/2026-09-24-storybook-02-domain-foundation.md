# Storybook 02 — Domain Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Read `2026-09-24-storybook-00-overview.md` first — its "Shared contracts" and "Global Constraints" sections apply to every task here.

**Goal:** Give the storybook feature its database schema, JPA entities, a cost ledger for every AI call, and the REST endpoints for child profiles and for creating, listing and reading books — with input validation and per-user ownership checks.

**Architecture:** One Flyway migration adds seven `tbl_storybook_*` tables following Ktab's `tbl_*`/`col_*` + `BaseEntity` conventions. Entities live in `features.storybook.model`; structured inputs (appearance, companion, the full blueprint snapshot) are stored as typed JSONB. Controllers follow Ktab's style (`@ApiVersion(1)`, `ResponseUtils`, `ApiMessageKey` messages) and are only registered when `ktab.storybook.enabled=true`. Creating a book validates and moderates inputs, saves a `DRAFT` and publishes `StorybookCreatedEvent`; sub-plan 04 listens to it.

**Tech Stack:** Spring Boot 3.5.7, Spring Data JPA, Hibernate 6 JSON mapping, Flyway, PostgreSQL, Testcontainers, MockMvc (standalone), Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`

## Global Constraints

See the overview. Most relevant here:

- Tables `tbl_storybook_*`, columns `col_*`, the `BaseEntity` audit block (`col_id`, `col_created_by`, `col_last_modified_by`, `created_at`, `updated_at`, `version`); migration = next free `V<n>` (V14 when written).
- Child data: first name (Arabic script, optional tashkeel), gender, age band, appearance. Nothing else.
- Page count 10, 12 or 15. Up to 3 interests. At most one companion (D5). Dialect ⇒ tashkeel `NONE` (D4). Dedication is moderated.
- Every AI call is logged with model, cost and latency.

## Review Focus

Owned by this sub-plan: **#4 a signed-in user requests another user's book or child profile by id.** Expected: 404, indistinguishable from "does not exist". Tests: Task 6 (`StorybookAccessGuardTest`), Task 5 (`ChildProfileServiceTest.otherUsersProfileIsNotFound`).

Also here: a child profile name typed with Latin letters, digits or tatweel is rejected with a field error (Task 5, `CreateChildProfileRequestValidationTest`).

## File structure

```
pom.xml                                                     (Task 1: testcontainers)
src/main/resources/db/migration/V14__storybook_core.sql     (Task 2)
src/main/java/com/doova/ktab/features/storybook/
├── enums/ ArtStyle  StorybookStatus  JobStep  JobStatus  PageKind  PageImageStatus
│          CharacterKind  CharacterSheetStatus                          (Task 2)
├── model/ ChildProfile  Storybook  StoryInputs  StorybookCharacter  CharacterAttributes
│          StorybookPage  StorybookPageImage  StorybookJob  StorybookAiCall (Task 2)
├── repository/ ChildProfileRepository  StorybookRepository  StorybookCharacterRepository
│               StorybookPageRepository  StorybookPageImageRepository
│               StorybookJobRepository  StorybookAiCallRepository         (Task 2)
├── cost/ AiCallLedger  AiCallEntry                                       (Task 3)
├── exception/ StorybookStateConflictException                            (Task 4)
├── event/ StorybookCreatedEvent                                          (Task 7)
└── web/
    ├── dto/ CreateChildProfileRequest  ChildProfileResponse  CreateStorybookRequest
    │        StorybookSummary  StorybookDetail  PageView  BlueprintSummary (Tasks 5, 7)
    ├── ChildProfileService  ChildProfileController                       (Task 5)
    ├── StorybookAccessGuard                                              (Task 6)
    ├── StorybookService  StorybookDraftWriter  StorybookViewMapper        (Task 7)
    └── StorybookController                                               (Task 8)
src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java  (Task 4: new keys)
src/main/resources/messages.properties                          (Task 4: new messages)
src/test/java/com/doova/ktab/features/storybook/support/StorybookJpaIT.java, UserFixtures.java (Task 1)
```

---

### Task 1: Testcontainers and a Postgres-backed JPA test base

**Files:**
- Modify: `pom.xml` (test dependencies)
- Create: `src/test/java/com/doova/ktab/features/storybook/support/StorybookJpaIT.java`, `UserFixtures.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/support/FlywayOnPostgresIT.java`

**Interfaces:**
- Consumes: Ktab's Flyway migrations, `config/jpa/JpaAuditingConfig`.
- Produces: abstract `StorybookJpaIT` (annotated `@DataJpaTest`, real Postgres via Testcontainers, full Flyway history, `ddl-auto=validate`, imports `JpaAuditingConfig`, exposes `protected TestEntityManager em`); `UserFixtures.reader(TestEntityManager em, String email) : User`.

Why a real Postgres: the job claimer (sub-plan 03) relies on `FOR UPDATE SKIP LOCKED`, and the entities use JSONB; neither can be tested on H2. `ddl-auto=validate` makes Hibernate check every storybook entity against the migrated schema, which is what the development profile does at startup.

- [ ] **Step 1: Add the dependencies**

Add inside `<dependencies>` in `pom.xml` (versions come from Spring Boot's dependency management):
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
```

Run: `./mvnw -q -DskipTests dependency:resolve`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Write the failing smoke test**

```java
package com.doova.ktab.features.storybook.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayOnPostgresIT extends StorybookJpaIT {

    @Test
    void theWholeKtabMigrationHistoryApplies() {
        Number users = (Number) em.getEntityManager()
                .createNativeQuery("select count(*) from information_schema.tables where table_name = 'tbl_users'")
                .getSingleResult();
        assertThat(users.intValue()).isEqualTo(1);
    }

    @Test
    void canCreateAReader() {
        assertThat(UserFixtures.reader(em, "parent@example.com").getId()).isNotNull();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=FlywayOnPostgresIT`
Expected: COMPILATION ERROR — `StorybookJpaIT` does not exist.

- [ ] **Step 4: Write the base class and fixture**

`StorybookJpaIT.java`:
```java
package com.doova.ktab.features.storybook.support;

import com.doova.ktab.config.jpa.JpaAuditingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Postgres + Ktab's full Flyway history. Needs Docker. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
public abstract class StorybookJpaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected TestEntityManager em;
}
```

`UserFixtures.java`:
```java
package com.doova.ktab.features.storybook.support;

import com.doova.ktab.model.user.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

public final class UserFixtures {

    private UserFixtures() {
    }

    public static User reader(TestEntityManager em, String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Parent");
        user.setPasswordDigest("not-a-real-hash");
        user.setRole("20"); // READER
        return em.persistAndFlush(user);
    }
}
```

If `User` requires more non-null columns than these, the flush fails with the column name; set that field too.

- [ ] **Step 5: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=FlywayOnPostgresIT`
Expected: 2 tests PASS (first run pulls `postgres:16-alpine`). If Hibernate validation fails on an existing, non-storybook entity, that is pre-existing drift the development profile would also hit — report it rather than weakening `validate`.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/test/java/com/doova/ktab/features/storybook/support
git commit -m "test(storybook): add Testcontainers Postgres base for JPA integration tests"
```

---

### Task 2: Domain enums, migration, entities and repositories

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/enums/ArtStyle.java`, `StorybookStatus.java`, `JobStep.java`, `JobStatus.java`, `PageKind.java`, `PageImageStatus.java`, `CharacterKind.java`, `CharacterSheetStatus.java`
- Create: `src/main/resources/db/migration/V14__storybook_core.sql` (use the next free number if V14 is taken)
- Create: `src/main/java/com/doova/ktab/features/storybook/model/ChildProfile.java`, `StoryInputs.java`, `Storybook.java`, `CharacterAttributes.java`, `StorybookCharacter.java`, `StorybookPage.java`, `StorybookPageImage.java`, `StorybookJob.java`, `StorybookAiCall.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/repository/*.java` (7 repositories)
- Test: `src/test/java/com/doova/ktab/features/storybook/model/StorybookMappingIT.java`, `src/test/java/com/doova/ktab/features/storybook/model/StoryInputsTest.java`
- Create (test support, reused later): `src/test/java/com/doova/ktab/features/storybook/support/StorybookEntityFixtures.java` — `static Storybook newBook(TestEntityManager em, User owner)`

**Interfaces:**
- Consumes: `StorybookJpaIT`, `UserFixtures` (Task 1); sub-plan 01 types `ChildAppearance`, `CompanionSpec`, `Blueprint`, `Interest`, `StorySetting`, `ChildGender`, `AgeBand`, `LanguageVariety`, `TashkeelLevel`, `TextZone`, `StoryRequest`, `CharacterInScene`, `VisualQaResponse`.
- Produces: the domain enums and entities in the overview contract, with these accessors used by later sub-plans:
  - `Storybook`: `getOwner()`, `getChildProfile()`, `getInputs() : StoryInputs`, `getStyle()`, `getVariety()`, `getTashkeelLevel()`, `getPageCount()`, `getStatus()`/`setStatus()`, `getFailedFromStatus()`/`setFailedFromStatus()`, `getFailureReason()`/`setFailureReason()`, `getTitleAr()`/`setTitleAr()`, `getCoverSceneEn()`/`setCoverSceneEn()`, `getDedication()`, `getStoryApprovedAt()`/`setStoryApprovedAt()`, `getLookApprovedAt()`/`setLookApprovedAt()`, `getPdfKey()`/`setPdfKey()`, `getLookRegenerations()`/`setLookRegenerations()`, `getPageRegenerations()`/`setPageRegenerations()`, `getTotalCostUsd()`.
  - `StoryInputs.toStoryRequest(LanguageVariety variety, int pageCount) : StoryRequest`.
  - `StorybookPage`: `getPageIndex()` (0 = cover), `getKind()`, `getTextAr()`, `getSceneEn()`, `getCharacters()`, `getTextZone()`, `getCriticProblems()`, `getCurrentImage()`, `getGeneration()`, `getRoundStartGeneration()`, plus setters.
  - `StorybookRepository.findByIdForUpdate(Long id)` — `PESSIMISTIC_WRITE` lock.
  - `StorybookPageImage`: `getPage()`, `getGeneration()`, `getImageKey()`, `getModel()`, `getStatus()`, `getQaResult() : VisualQaResponse`, `getCostUsd()`.
  - `StorybookCharacter`: `getStorybook()`, `getKind()`, `getAttributes() : CharacterAttributes`, `getSheetKey()`, `getSheetVersion()`, `getSheetStatus()`, `getPhotoKey()`, `getPhotoConsentAt()`, `getPhotoPurgedAt()`.
  - `StorybookJob`: `getStorybookId()`, `getStep()`, `getPageIndex()`, `getGeneration()`, `getIdempotencyKey()`, `getStatus()`, `getAttempts()`, `getNextRunAt()`, `getLockedBy()`, `getLockedAt()`, `getLastError()`, `getFinishedAt()`.
  - Repository methods listed in Step 7.

- [ ] **Step 1: Write the enums**

```java
// ArtStyle.java
package com.doova.ktab.features.storybook.enums;

/** Curated catalogue (spec: "never free-form"). One style in the MVP. */
public enum ArtStyle {
    SOFT_WATERCOLOR("storybook/styles/soft_watercolor.png");

    private final String referenceResource;
    ArtStyle(String referenceResource) { this.referenceResource = referenceResource; }
    public String referenceResource() { return referenceResource; }
}
```

```java
// StorybookStatus.java
package com.doova.ktab.features.storybook.enums;

/** Spec state diagram, plus CANCELLED (sub-plan 07). */
public enum StorybookStatus {
    DRAFT, STORY_READY, CHARACTER_READY, ILLUSTRATING, QA, RENDERING, READY, FAILED, CANCELLED
}
```

```java
// JobStep.java
package com.doova.ktab.features.storybook.enums;

public enum JobStep {
    STORY_PLAN, STORY_CRITIC, CHARACTER_SHEET, ILLUSTRATE_PAGE, QA_PAGE, RENDER_PDF, PURGE_PHOTO
}
```

```java
// JobStatus.java
package com.doova.ktab.features.storybook.enums;

public enum JobStatus { PENDING, RUNNING, SUCCEEDED, DEAD }
```

```java
// PageKind.java
package com.doova.ktab.features.storybook.enums;

public enum PageKind { COVER, STORY }
```

```java
// PageImageStatus.java
package com.doova.ktab.features.storybook.enums;

public enum PageImageStatus { GENERATED, QA_PASSED, QA_FAILED, FLAGGED, ACCEPTED_BY_ADMIN }
```

```java
// CharacterKind.java
package com.doova.ktab.features.storybook.enums;

public enum CharacterKind { CHILD, COMPANION }
```

```java
// CharacterSheetStatus.java
package com.doova.ktab.features.storybook.enums;

public enum CharacterSheetStatus { NOT_STARTED, GENERATED, APPROVED }
```

- [ ] **Step 2: Write the migration**

`src/main/resources/db/migration/V14__storybook_core.sql`:
```sql
-- ============================================================================
-- Flyway Migration V14: personalized storybook (docs/superpowers/plans/2026-09-24-storybook-02-domain-foundation.md)
-- All tables follow Ktab's tbl_/col_ naming and the BaseEntity audit block.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_storybook_child_profiles (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_owner_user_id    BIGINT       NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_name_ar          VARCHAR(40)  NOT NULL,
    col_gender           VARCHAR(10)  NOT NULL,
    col_age_band         VARCHAR(10)  NOT NULL,
    col_appearance       JSONB        NOT NULL,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sb_child_owner ON tbl_storybook_child_profiles (col_owner_user_id);

CREATE TABLE IF NOT EXISTS tbl_storybooks (
    col_id                 BIGSERIAL     PRIMARY KEY,
    col_owner_user_id      BIGINT        NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_child_profile_id   BIGINT        NOT NULL REFERENCES tbl_storybook_child_profiles (col_id) ON DELETE CASCADE,
    col_blueprint_key      VARCHAR(80)   NOT NULL,
    col_blueprint_version  INTEGER       NOT NULL,
    col_inputs             JSONB         NOT NULL,  -- StoryInputs snapshot incl. the blueprint beats (decision D1)
    col_style              VARCHAR(40)   NOT NULL,
    col_language_variety   VARCHAR(20)   NOT NULL,
    col_tashkeel_level     VARCHAR(10)   NOT NULL,
    col_page_count         SMALLINT      NOT NULL CHECK (col_page_count IN (10, 12, 15)),
    col_status             VARCHAR(20)   NOT NULL,
    col_failed_from_status VARCHAR(20),
    col_failure_reason     TEXT,
    col_title_ar           VARCHAR(200),
    col_cover_scene_en     TEXT,
    col_dedication         VARCHAR(300),
    col_story_approved_at  TIMESTAMPTZ,
    col_look_approved_at   TIMESTAMPTZ,
    col_pdf_key            TEXT,
    col_look_regenerations INTEGER       NOT NULL DEFAULT 0,
    col_page_regenerations INTEGER       NOT NULL DEFAULT 0,
    col_total_cost_usd     NUMERIC(10,4) NOT NULL DEFAULT 0,
    col_created_by         BIGINT,
    col_last_modified_by   BIGINT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sb_books_owner ON tbl_storybooks (col_owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sb_books_status ON tbl_storybooks (col_status);

CREATE TABLE IF NOT EXISTS tbl_storybook_characters (
    col_id                BIGSERIAL    PRIMARY KEY,
    col_storybook_id      BIGINT       NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_kind              VARCHAR(20)  NOT NULL,
    col_attributes        JSONB        NOT NULL,
    col_sheet_key         TEXT,
    col_sheet_version     INTEGER      NOT NULL DEFAULT 0,
    col_sheet_status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    col_photo_key         TEXT,          -- encrypted object in R2; NULL once purged
    col_photo_consent_at  TIMESTAMPTZ,
    col_photo_purged_at   TIMESTAMPTZ,
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_character_kind UNIQUE (col_storybook_id, col_kind)
);

CREATE TABLE IF NOT EXISTS tbl_storybook_pages (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_storybook_id     BIGINT       NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_page_index       SMALLINT     NOT NULL,   -- 0 = cover, 1..N = story pages
    col_kind             VARCHAR(10)  NOT NULL,
    col_text_ar          TEXT,                    -- NULL for the cover (title lives on the book)
    col_scene_en         TEXT         NOT NULL,
    col_characters       JSONB        NOT NULL DEFAULT '[]',
    col_text_zone        VARCHAR(10)  NOT NULL,
    col_critic_problems  JSONB,
    col_current_image_id BIGINT,
    col_generation       INTEGER      NOT NULL DEFAULT 0,
    -- first generation of the current round (first illustration, or a parent/admin regeneration);
    -- QA retries and the primary/fallback model choice count from here (sub-plan 05)
    col_round_start_generation INTEGER NOT NULL DEFAULT 1,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_page_index UNIQUE (col_storybook_id, col_page_index)
);

CREATE TABLE IF NOT EXISTS tbl_storybook_page_images (
    col_id               BIGSERIAL     PRIMARY KEY,
    col_page_id          BIGINT        NOT NULL REFERENCES tbl_storybook_pages (col_id) ON DELETE CASCADE,
    col_generation       INTEGER       NOT NULL,
    col_image_key        TEXT          NOT NULL,
    col_model            VARCHAR(100)  NOT NULL,
    col_status           VARCHAR(20)   NOT NULL,
    col_qa_result        JSONB,
    col_cost_usd         NUMERIC(10,4) NOT NULL DEFAULT 0,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_page_generation UNIQUE (col_page_id, col_generation)
);

ALTER TABLE tbl_storybook_pages
    ADD CONSTRAINT fk_sb_page_current_image
    FOREIGN KEY (col_current_image_id) REFERENCES tbl_storybook_page_images (col_id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS tbl_storybook_jobs (
    col_id               BIGSERIAL    PRIMARY KEY,
    col_storybook_id     BIGINT       NOT NULL REFERENCES tbl_storybooks (col_id) ON DELETE CASCADE,
    col_step             VARCHAR(30)  NOT NULL,
    col_page_index       SMALLINT     NOT NULL DEFAULT -1,  -- -1 for book-level steps
    col_generation       INTEGER      NOT NULL DEFAULT 0,
    col_idempotency_key  VARCHAR(120) NOT NULL,             -- book:step:page:generation
    col_status           VARCHAR(20)  NOT NULL,
    col_attempts         INTEGER      NOT NULL DEFAULT 0,
    col_next_run_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_locked_by        VARCHAR(100),
    col_locked_at        TIMESTAMPTZ,
    col_last_error       TEXT,
    col_finished_at      TIMESTAMPTZ,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_sb_job_idempotency UNIQUE (col_idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_sb_jobs_due ON tbl_storybook_jobs (col_next_run_at) WHERE col_status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_sb_jobs_book ON tbl_storybook_jobs (col_storybook_id);

-- The spec's generation_job "provider, model, cost" live here: one job can make several calls
-- (e.g. an image and its QA check). Kept when a book is deleted, for cost reporting.
CREATE TABLE IF NOT EXISTS tbl_storybook_ai_calls (
    col_id               BIGSERIAL     PRIMARY KEY,
    col_storybook_id     BIGINT        REFERENCES tbl_storybooks (col_id) ON DELETE SET NULL,
    col_job_id           BIGINT        REFERENCES tbl_storybook_jobs (col_id) ON DELETE SET NULL,
    col_purpose          VARCHAR(40)   NOT NULL,
    col_provider         VARCHAR(20)   NOT NULL,
    col_model            VARCHAR(100)  NOT NULL,
    col_input_tokens     BIGINT,
    col_output_tokens    BIGINT,
    col_images           INTEGER       NOT NULL DEFAULT 0,
    col_cost_usd         NUMERIC(10,6) NOT NULL,
    col_latency_ms       BIGINT        NOT NULL,
    col_success          BOOLEAN       NOT NULL,
    col_error            TEXT,
    col_created_by       BIGINT,
    col_last_modified_by BIGINT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sb_ai_calls_book ON tbl_storybook_ai_calls (col_storybook_id);
```

- [ ] **Step 3: Write the failing tests**

`StoryInputsTest.java` (pure):
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.story.StoryRequest;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryInputsTest {

    @Test
    void buildsAStoryRequestFromTheSnapshot() {
        StoryInputs inputs = new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE,
                List.of(Interest.CATS), null, StorySetting.CAIRO, StoryFixtures.CATALOG.get("first-day-of-school"));

        StoryRequest r = inputs.toStoryRequest(LanguageVariety.GULF, 12);

        assertThat(r.childNameAr()).isEqualTo("سامي");
        assertThat(r.variety()).isEqualTo(LanguageVariety.GULF);
        assertThat(r.pageCount()).isEqualTo(12);
        assertThat(r.blueprint().key()).isEqualTo("first-day-of-school");
        assertThat(r.setting()).isEqualTo(StorySetting.CAIRO);
    }
}
```

`StorybookMappingIT.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookMappingIT extends StorybookJpaIT {

    @Autowired StorybookRepository books;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookPageImageRepository images;

    private static Storybook newBook(org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager em, User owner) {
        return StorybookEntityFixtures.newBook(em, owner);
    }

    @Test
    void jsonColumnsRoundTrip() {
        Storybook saved = newBook(em, UserFixtures.reader(em, "a@example.com"));
        em.clear();

        Storybook loaded = books.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getInputs().companion().nameAr()).isEqualTo("بسبوسة");
        assertThat(loaded.getInputs().blueprint().beatsFor(10)).hasSize(10);
        assertThat(loaded.getInputs().appearance()).isEqualTo(StoryFixtures.APPEARANCE);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void pagesAndImagesRoundTripWithQaResult() {
        Storybook book = newBook(em, UserFixtures.reader(em, "b@example.com"));
        StorybookPage page = new StorybookPage();
        page.setStorybook(book);
        page.setPageIndex(1);
        page.setKind(PageKind.STORY);
        page.setTextAr("ذَهَبَ سامي.");
        page.setSceneEn("The CHILD walks.");
        page.setCharacters(List.of(new CharacterInScene("CHILD", "happy")));
        page.setTextZone(TextZone.TOP);
        page.setCriticProblems(List.of("problem one"));
        em.persist(page);

        StorybookPageImage image = new StorybookPageImage();
        image.setPage(page);
        image.setGeneration(1);
        image.setImageKey("storybook/1/pages/1/g1.png");
        image.setModel("gemini-3.1-flash-image");
        image.setStatus(PageImageStatus.QA_PASSED);
        image.setQaResult(new VisualQaResponse(true, false, true, true, List.of()));
        image.setCostUsd(new BigDecimal("0.1010"));
        em.persist(image);
        page.setCurrentImage(image);
        em.flush();
        em.clear();

        StorybookPage loaded = pages.findByStorybook_IdAndPageIndex(book.getId(), 1).orElseThrow();
        assertThat(loaded.getCharacters()).extracting(CharacterInScene::ref).containsExactly("CHILD");
        assertThat(loaded.getCriticProblems()).containsExactly("problem one");
        assertThat(loaded.getCurrentImage().getQaResult().passed()).isTrue();
        assertThat(images.findByPage_IdAndGeneration(loaded.getId(), 1)).isPresent();
    }

    @Test
    void ownerScopedLookupHidesOtherUsersBooks() {
        User alice = UserFixtures.reader(em, "alice@example.com");
        User bob = UserFixtures.reader(em, "bob@example.com");
        Storybook alicesBook = newBook(em, alice);

        assertThat(books.findByIdAndOwner_Id(alicesBook.getId(), alice.getId())).isPresent();
        assertThat(books.findByIdAndOwner_Id(alicesBook.getId(), bob.getId())).isEmpty();
    }
}
```

`src/test/java/com/doova/ktab/features/storybook/support/StorybookEntityFixtures.java` (reused by sub-plans 03–07):
```java
package com.doova.ktab.features.storybook.support;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.model.user.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

public final class StorybookEntityFixtures {

    private StorybookEntityFixtures() {
    }

    /** A 10-page MSA DRAFT book for "سامي" (boy, 6-8) with an orange-cat companion. */
    public static Storybook newBook(TestEntityManager em, User owner) {
        ChildProfile child = new ChildProfile();
        child.setOwner(owner);
        child.setNameAr("سامي");
        child.setGender(ChildGender.BOY);
        child.setAgeBand(AgeBand.AGE_6_8);
        child.setAppearance(StoryFixtures.APPEARANCE);
        em.persist(child);

        Storybook book = new Storybook();
        book.setOwner(owner);
        book.setChildProfile(child);
        book.setInputs(new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE,
                List.of(Interest.FOOTBALL),
                new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوسة", null, CompanionSpec.PetColor.ORANGE),
                StorySetting.BEIRUT, StoryFixtures.CATALOG.get("first-day-of-school")));
        book.setBlueprintKey("first-day-of-school");
        book.setBlueprintVersion(1);
        book.setStyle(ArtStyle.SOFT_WATERCOLOR);
        book.setVariety(LanguageVariety.MSA);
        book.setTashkeelLevel(TashkeelLevel.FULL);
        book.setPageCount(10);
        book.setStatus(StorybookStatus.DRAFT);
        return em.persistAndFlush(book);
    }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StoryInputsTest,StorybookMappingIT'`
Expected: COMPILATION ERROR — entities do not exist.

- [ ] **Step 5: Write the entities**

`StoryInputs.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.story.StoryRequest;

import java.util.List;

/**
 * Everything the story was generated from, frozen when the book was created: later edits to
 * the child profile or a new blueprint version never change an existing book (decision D1).
 */
public record StoryInputs(
        String childNameAr,
        ChildGender gender,
        AgeBand ageBand,
        ChildAppearance appearance,
        List<Interest> interests,
        CompanionSpec companion,
        StorySetting setting,
        Blueprint blueprint
) {
    public StoryRequest toStoryRequest(LanguageVariety variety, int pageCount) {
        return new StoryRequest(childNameAr, gender, ageBand, appearance, variety, blueprint, pageCount,
                interests, companion, setting);
    }
}
```

`ChildProfile.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_storybook_child_profiles")
@Getter
@Setter
public class ChildProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_owner_user_id", nullable = false)
    private User owner;

    @Column(name = "col_name_ar", nullable = false, length = 40)
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_gender", nullable = false, length = 10)
    private ChildGender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_age_band", nullable = false, length = 10)
    private AgeBand ageBand;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_appearance", nullable = false, columnDefinition = "JSONB")
    private ChildAppearance appearance;
}
```

`Storybook.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tbl_storybooks")
@Getter
@Setter
public class Storybook extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_owner_user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_child_profile_id", nullable = false)
    private ChildProfile childProfile;

    @Column(name = "col_blueprint_key", nullable = false, length = 80)
    private String blueprintKey;

    @Column(name = "col_blueprint_version", nullable = false)
    private int blueprintVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_inputs", nullable = false, columnDefinition = "JSONB")
    private StoryInputs inputs;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_style", nullable = false, length = 40)
    private ArtStyle style;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_language_variety", nullable = false, length = 20)
    private LanguageVariety variety;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_tashkeel_level", nullable = false, length = 10)
    private TashkeelLevel tashkeelLevel;

    @Column(name = "col_page_count", nullable = false)
    private short pageCount;

    public void setPageCount(int pageCount) {
        this.pageCount = (short) pageCount;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private StorybookStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_failed_from_status", length = 20)
    private StorybookStatus failedFromStatus;

    @Column(name = "col_failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "col_title_ar", length = 200)
    private String titleAr;

    @Column(name = "col_cover_scene_en", columnDefinition = "TEXT")
    private String coverSceneEn;

    @Column(name = "col_dedication", length = 300)
    private String dedication;

    @Column(name = "col_story_approved_at")
    private Instant storyApprovedAt;

    @Column(name = "col_look_approved_at")
    private Instant lookApprovedAt;

    @Column(name = "col_pdf_key", columnDefinition = "TEXT")
    private String pdfKey;

    @Column(name = "col_look_regenerations", nullable = false)
    private int lookRegenerations;

    @Column(name = "col_page_regenerations", nullable = false)
    private int pageRegenerations;

    @Column(name = "col_total_cost_usd", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;
}
```

`CharacterAttributes.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.character.CompanionSpec;

/** Exactly one of the two is set, depending on the character's kind. */
public record CharacterAttributes(ChildAppearance child, CompanionSpec companion) {

    public static CharacterAttributes ofChild(ChildAppearance appearance) {
        return new CharacterAttributes(appearance, null);
    }

    public static CharacterAttributes ofCompanion(CompanionSpec companion) {
        return new CharacterAttributes(null, companion);
    }
}
```

`StorybookCharacter.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "tbl_storybook_characters")
@Getter
@Setter
public class StorybookCharacter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_storybook_id", nullable = false)
    private Storybook storybook;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_kind", nullable = false, length = 20)
    private CharacterKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_attributes", nullable = false, columnDefinition = "JSONB")
    private CharacterAttributes attributes;

    @Column(name = "col_sheet_key", columnDefinition = "TEXT")
    private String sheetKey;

    @Column(name = "col_sheet_version", nullable = false)
    private int sheetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_sheet_status", nullable = false, length = 20)
    private CharacterSheetStatus sheetStatus = CharacterSheetStatus.NOT_STARTED;

    @Column(name = "col_photo_key", columnDefinition = "TEXT")
    private String photoKey;

    @Column(name = "col_photo_consent_at")
    private Instant photoConsentAt;

    @Column(name = "col_photo_purged_at")
    private Instant photoPurgedAt;
}
```

`StorybookPage.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_storybook_pages")
@Getter
@Setter
public class StorybookPage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_storybook_id", nullable = false)
    private Storybook storybook;

    /** 0 = cover, 1..N = story pages. */
    @Column(name = "col_page_index", nullable = false)
    private short pageIndex;

    public void setPageIndex(int pageIndex) {
        this.pageIndex = (short) pageIndex;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "col_kind", nullable = false, length = 10)
    private PageKind kind;

    @Column(name = "col_text_ar", columnDefinition = "TEXT")
    private String textAr;

    @Column(name = "col_scene_en", nullable = false, columnDefinition = "TEXT")
    private String sceneEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_characters", nullable = false, columnDefinition = "JSONB")
    private List<CharacterInScene> characters = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "col_text_zone", nullable = false, length = 10)
    private TextZone textZone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_critic_problems", columnDefinition = "JSONB")
    private List<String> criticProblems;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_current_image_id")
    private StorybookPageImage currentImage;

    /** Latest image generation requested for this page (1-based; 0 = none yet). */
    @Column(name = "col_generation", nullable = false)
    private int generation;

    /** First generation of the current round; QA retries and model choice count from here. */
    @Column(name = "col_round_start_generation", nullable = false)
    private int roundStartGeneration = 1;
}
```

`StorybookPageImage.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.illustration.VisualQaResponse;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "tbl_storybook_page_images")
@Getter
@Setter
public class StorybookPageImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_page_id", nullable = false)
    private StorybookPage page;

    @Column(name = "col_generation", nullable = false)
    private int generation;

    @Column(name = "col_image_key", nullable = false, columnDefinition = "TEXT")
    private String imageKey;

    @Column(name = "col_model", nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private PageImageStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "col_qa_result", columnDefinition = "JSONB")
    private VisualQaResponse qaResult;

    @Column(name = "col_cost_usd", nullable = false, precision = 10, scale = 4)
    private BigDecimal costUsd = BigDecimal.ZERO;
}
```

`StorybookJob.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tbl_storybook_jobs")
@Getter
@Setter
public class StorybookJob extends BaseEntity {

    /** Plain id, not a relation: the claimer and worker never need the book row. */
    @Column(name = "col_storybook_id", nullable = false)
    private Long storybookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_step", nullable = false, length = 30)
    private JobStep step;

    /** -1 for book-level steps. */
    @Column(name = "col_page_index", nullable = false)
    private short pageIndex = -1;

    public void setPageIndex(int pageIndex) {
        this.pageIndex = (short) pageIndex;
    }

    @Column(name = "col_generation", nullable = false)
    private int generation;

    @Column(name = "col_idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "col_attempts", nullable = false)
    private int attempts;

    @Column(name = "col_next_run_at", nullable = false)
    private Instant nextRunAt = Instant.now();

    @Column(name = "col_locked_by", length = 100)
    private String lockedBy;

    @Column(name = "col_locked_at")
    private Instant lockedAt;

    @Column(name = "col_last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "col_finished_at")
    private Instant finishedAt;
}
```

`StorybookAiCall.java`:
```java
package com.doova.ktab.features.storybook.model;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tbl_storybook_ai_calls")
@Getter
@Setter
public class StorybookAiCall extends BaseEntity {

    @Column(name = "col_storybook_id")
    private Long storybookId;

    @Column(name = "col_job_id")
    private Long jobId;

    @Column(name = "col_purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "col_provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "col_model", nullable = false, length = 100)
    private String model;

    @Column(name = "col_input_tokens")
    private Long inputTokens;

    @Column(name = "col_output_tokens")
    private Long outputTokens;

    @Column(name = "col_images", nullable = false)
    private int images;

    @Column(name = "col_cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "col_latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "col_success", nullable = false)
    private boolean success;

    @Column(name = "col_error", columnDefinition = "TEXT")
    private String error;
}
```

- [ ] **Step 6: Write the repositories**

```java
// ChildProfileRepository.java
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {
    Optional<ChildProfile> findByIdAndOwner_Id(Long id, Long ownerId);
    List<ChildProfile> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);
}
```

```java
// StorybookRepository.java
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.Storybook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StorybookRepository extends JpaRepository<Storybook, Long> {
    Optional<Storybook> findByIdAndOwner_Id(Long id, Long ownerId);

    /** Serializes book-level status advancement when several page jobs finish at once (sub-plan 05). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Storybook b where b.id = :id")
    Optional<Storybook> findByIdForUpdate(@Param("id") Long id);
    List<Storybook> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);
    long countByOwner_IdAndCreatedAtAfter(Long ownerId, LocalDateTime since);

    @Modifying
    @Query("update Storybook b set b.totalCostUsd = b.totalCostUsd + :cost where b.id = :id")
    int addCost(@Param("id") Long id, @Param("cost") BigDecimal cost);
}
```

```java
// StorybookCharacterRepository.java
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookCharacterRepository extends JpaRepository<StorybookCharacter, Long> {
    Optional<StorybookCharacter> findByStorybook_IdAndKind(Long storybookId, CharacterKind kind);
    List<StorybookCharacter> findByStorybook_Id(Long storybookId);
}
```

```java
// StorybookPageRepository.java
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookPageRepository extends JpaRepository<StorybookPage, Long> {
    List<StorybookPage> findByStorybook_IdOrderByPageIndexAsc(Long storybookId);
    Optional<StorybookPage> findByStorybook_IdAndPageIndex(Long storybookId, int pageIndex);
    void deleteByStorybook_Id(Long storybookId);
}
```

```java
// StorybookPageImageRepository.java
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookPageImageRepository extends JpaRepository<StorybookPageImage, Long> {
    Optional<StorybookPageImage> findByPage_IdAndGeneration(Long pageId, int generation);
    List<StorybookPageImage> findByStatusOrderByCreatedAtAsc(PageImageStatus status);
}
```

```java
// StorybookJobRepository.java — sub-plan 03 adds the claiming queries
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorybookJobRepository extends JpaRepository<StorybookJob, Long> {
    List<StorybookJob> findByStorybookIdOrderByIdAsc(Long storybookId);
}
```

```java
// StorybookAiCallRepository.java
package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookAiCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorybookAiCallRepository extends JpaRepository<StorybookAiCall, Long> {
    List<StorybookAiCall> findByStorybookIdOrderByIdAsc(Long storybookId);
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StoryInputsTest,StorybookMappingIT'`
Expected: 4 tests PASS. A Hibernate "schema-validation" error names the column whose type or length differs between entity and migration — fix whichever side is wrong against this plan.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration src/main/java/com/doova/ktab/features/storybook/enums src/main/java/com/doova/ktab/features/storybook/model src/main/java/com/doova/ktab/features/storybook/repository src/test/java/com/doova/ktab/features/storybook/model
git commit -m "feat(storybook): add storybook schema, entities and repositories"
```

---

### Task 3: `AiCallLedger` — log every AI call with model, cost and latency

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/cost/AiCallEntry.java`, `AiCallLedger.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/cost/AiCallLedgerIT.java`

**Interfaces:**
- Consumes: `CostCalculator` (01), `LlmCall` (01), `ImageResult` (01), `StorybookAiCallRepository`, `StorybookRepository.addCost` (Task 2).
- Produces:
  - `record AiCallEntry(Long storybookId, Long jobId, String purpose, String provider, String model, Long inputTokens, Long outputTokens, int images, BigDecimal costUsd, long latencyMs, boolean success, String error)`.
  - `AiCallLedger`:
    - `BigDecimal recordLlm(Long storybookId, Long jobId, LlmPurpose purpose, LlmCall<?> call)` — computes cost, writes a success row, adds the cost to the book.
    - `BigDecimal recordImage(Long storybookId, Long jobId, String purpose, ImageResult result)` — one image, `provider = "GOOGLE"`.
    - `void recordFailure(Long storybookId, Long jobId, String purpose, String provider, String model, long latencyMs, String error)` — cost 0.
    - All run in `REQUIRES_NEW` so the ledger survives a rollback of the caller's transaction: a paid call is recorded even if the step later fails.
  - Image purposes used by later sub-plans: `"IMAGE_CHARACTER_SHEET"`, `"IMAGE_COMPANION_SHEET"`, `"IMAGE_PAGE"`.

- [ ] **Step 1: Write the failing test**

`@DataJpaTest` wraps each test in a transaction, and a `REQUIRES_NEW` write inside it cannot see an uncommitted book. So this test switches the class-level transaction off and commits its setup with a `TransactionTemplate`.

```java
package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookAiCallRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import({AiCallLedger.class, CostCalculator.class, StorybookProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiCallLedgerIT extends StorybookJpaIT {

    @Autowired AiCallLedger ledger;
    @Autowired StorybookAiCallRepository calls;
    @Autowired StorybookRepository books;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void recordsLlmAndImageCallsAndAccumulatesBookCost() {
        Storybook book = new TransactionTemplate(txManager).execute(s -> StorybookEntityFixtures.newBook(em,
                UserFixtures.reader(em, "ledger-" + System.nanoTime() + "@example.com")));

        ledger.recordLlm(book.getId(), null, LlmPurpose.STORY_PLAN,
                new LlmCall<>("x", "claude-sonnet-5", 1_000, 500, 1200));
        ledger.recordImage(book.getId(), null, "IMAGE_PAGE",
                new ImageResult(new byte[]{1}, "image/png", "gemini-3.1-flash-image", 8000));
        ledger.recordFailure(book.getId(), null, "IMAGE_PAGE", "GOOGLE", "gemini-3.1-flash-image", 300, "HTTP 503");

        assertThat(calls.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(c -> c.getPurpose() + ":" + c.isSuccess())
                .containsExactly("STORY_PLAN:true", "IMAGE_PAGE:true", "IMAGE_PAGE:false");
        // 0.007 (LLM) + 0.101 (image) = 0.108
        assertThat(books.findById(book.getId()).orElseThrow().getTotalCostUsd()).isEqualByComparingTo("0.1080");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=AiCallLedgerIT`
Expected: COMPILATION ERROR — `AiCallLedger` does not exist.

- [ ] **Step 3: Implement**

`AiCallEntry.java`:
```java
package com.doova.ktab.features.storybook.cost;

import java.math.BigDecimal;

public record AiCallEntry(Long storybookId, Long jobId, String purpose, String provider, String model,
                          Long inputTokens, Long outputTokens, int images, BigDecimal costUsd,
                          long latencyMs, boolean success, String error) {
}
```

`AiCallLedger.java`:
```java
package com.doova.ktab.features.storybook.cost;

import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookAiCall;
import com.doova.ktab.features.storybook.repository.StorybookAiCallRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Spec: "Every AI call logs model, cost and latency." Each write is its own transaction so a
 * paid call stays on record even when the step that made it rolls back.
 */
@Component
@RequiredArgsConstructor
public class AiCallLedger {

    private final StorybookAiCallRepository calls;
    private final StorybookRepository books;
    private final CostCalculator costs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal recordLlm(Long storybookId, Long jobId, LlmPurpose purpose, LlmCall<?> call) {
        BigDecimal cost = costs.llmCostUsd(call.model(), call.inputTokens(), call.outputTokens());
        save(new AiCallEntry(storybookId, jobId, purpose.name(), "ANTHROPIC", call.model(),
                call.inputTokens(), call.outputTokens(), 0, cost, call.latencyMs(), true, null));
        return cost;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal recordImage(Long storybookId, Long jobId, String purpose, ImageResult result) {
        BigDecimal cost = costs.imageCostUsd(result.model());
        save(new AiCallEntry(storybookId, jobId, purpose, "GOOGLE", result.model(),
                null, null, 1, cost, result.latencyMs(), true, null));
        return cost;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long storybookId, Long jobId, String purpose, String provider, String model,
                              long latencyMs, String error) {
        save(new AiCallEntry(storybookId, jobId, purpose, provider, model, null, null, 0,
                BigDecimal.ZERO, latencyMs, false, error));
    }

    private void save(AiCallEntry e) {
        StorybookAiCall row = new StorybookAiCall();
        row.setStorybookId(e.storybookId());
        row.setJobId(e.jobId());
        row.setPurpose(e.purpose());
        row.setProvider(e.provider());
        row.setModel(e.model());
        row.setInputTokens(e.inputTokens());
        row.setOutputTokens(e.outputTokens());
        row.setImages(e.images());
        row.setCostUsd(e.costUsd());
        row.setLatencyMs(e.latencyMs());
        row.setSuccess(e.success());
        row.setError(e.error() == null ? null : e.error().substring(0, Math.min(2000, e.error().length())));
        calls.save(row);
        if (e.storybookId() != null && e.costUsd().signum() > 0) {
            books.addCost(e.storybookId(), e.costUsd());
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=AiCallLedgerIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/cost src/test/java/com/doova/ktab/features/storybook/cost/AiCallLedgerIT.java
git commit -m "feat(storybook): add AI call ledger with per-book cost accumulation"
```

---

### Task 4: Message keys, messages and the state-conflict exception

**Files:**
- Modify: `src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java` (add a `// ===== STORYBOOK =====` group before the closing `;` of the constant list)
- Modify: `src/main/resources/messages.properties` (append)
- Create: `src/main/java/com/doova/ktab/features/storybook/exception/StorybookStateConflictException.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/exception/StorybookMessagesTest.java`

**Interfaces:**
- Produces: `ApiMessageKey` constants used by every later sub-plan: `STORYBOOK_CHILD_SAVED`, `STORYBOOK_CHILD_FETCHED`, `STORYBOOK_CHILD_DELETED`, `STORYBOOK_CHILD_NOT_FOUND`, `STORYBOOK_NOT_FOUND`, `STORYBOOK_CREATED`, `STORYBOOK_FETCHED`, `STORYBOOK_BLUEPRINTS_FETCHED`, `STORYBOOK_BLUEPRINT_NOT_ALLOWED`, `STORYBOOK_SETTING_NOT_ALLOWED`, `STORYBOOK_INVALID_PAGE_COUNT`, `STORYBOOK_TOO_MANY_INTERESTS`, `STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL`, `STORYBOOK_TASHKEEL_REQUIRED`, `STORYBOOK_DEDICATION_REJECTED`, `STORYBOOK_INVALID_STATE`, `STORYBOOK_ACTION_ACCEPTED`, `STORYBOOK_LIMIT_REACHED`, `STORYBOOK_PHOTO_CONSENT_REQUIRED`, `STORYBOOK_NOT_READY`, `STORYBOOK_INSUFFICIENT_CREDITS`; and `StorybookStateConflictException extends KtabException` → HTTP 409.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookMessagesTest {

    @Test
    void everyStorybookKeyHasAMessage() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        Arrays.stream(ApiMessageKey.values())
                .filter(k -> k.name().startsWith("STORYBOOK_"))
                .forEach(k -> assertThat(source.getMessage(k.getKey(), null, Locale.ENGLISH))
                        .as(k.name()).isNotBlank());
        assertThat(Arrays.stream(ApiMessageKey.values()).filter(k -> k.name().startsWith("STORYBOOK_"))).hasSize(21);
    }

    @Test
    void stateConflictIs409() {
        assertThat(new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookMessagesTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Add the keys**

In `ApiMessageKey.java`, add this group as the last constants (keep the existing constants and the trailing `;`):
```java
    // ===== STORYBOOK =====
    STORYBOOK_CHILD_SAVED("storybook.child.saved"), STORYBOOK_CHILD_FETCHED("storybook.child.fetched"),
    STORYBOOK_CHILD_DELETED("storybook.child.deleted"), STORYBOOK_CHILD_NOT_FOUND("storybook.child.not.found"),
    STORYBOOK_NOT_FOUND("storybook.not.found"), STORYBOOK_CREATED("storybook.created"),
    STORYBOOK_FETCHED("storybook.fetched"), STORYBOOK_BLUEPRINTS_FETCHED("storybook.blueprints.fetched"),
    STORYBOOK_BLUEPRINT_NOT_ALLOWED("storybook.blueprint.not.allowed"),
    STORYBOOK_SETTING_NOT_ALLOWED("storybook.setting.not.allowed"),
    STORYBOOK_INVALID_PAGE_COUNT("storybook.invalid.page.count"),
    STORYBOOK_TOO_MANY_INTERESTS("storybook.too.many.interests"),
    STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL("storybook.dialect.requires.no.tashkeel"),
    STORYBOOK_TASHKEEL_REQUIRED("storybook.tashkeel.required"),
    STORYBOOK_DEDICATION_REJECTED("storybook.dedication.rejected"),
    STORYBOOK_INVALID_STATE("storybook.invalid.state"), STORYBOOK_ACTION_ACCEPTED("storybook.action.accepted"),
    STORYBOOK_LIMIT_REACHED("storybook.limit.reached"),
    STORYBOOK_PHOTO_CONSENT_REQUIRED("storybook.photo.consent.required"),
    STORYBOOK_NOT_READY("storybook.not.ready"), STORYBOOK_INSUFFICIENT_CREDITS("storybook.insufficient.credits"),
```

Append to `messages.properties` (Arabic, like the rest of the file; save as UTF-8):
```properties
# ===== Storybook =====
storybook.child.saved=تم حفظ ملف الطفل.
storybook.child.fetched=تم جلب ملفات الأطفال.
storybook.child.deleted=تم حذف ملف الطفل وكتبه.
storybook.child.not.found=ملف الطفل غير موجود.
storybook.not.found=القصة غير موجودة.
storybook.created=بدأنا بكتابة القصة.
storybook.fetched=تم جلب القصة.
storybook.blueprints.fetched=تم جلب قوالب القصص.
storybook.blueprint.not.allowed=هذا القالب غير متاح لعمر الطفل.
storybook.setting.not.allowed=هذا المكان غير متاح لهذه القصة.
storybook.invalid.page.count=عدد الصفحات يجب أن يكون 10 أو 12 أو 15.
storybook.too.many.interests=يمكن اختيار 3 اهتمامات كحد أقصى.
storybook.dialect.requires.no.tashkeel=القصص باللهجات تُكتب بدون تشكيل.
storybook.tashkeel.required=يرجى اختيار مستوى التشكيل.
storybook.dedication.rejected=لا يمكن طباعة هذا الإهداء. يرجى تعديله.
storybook.invalid.state=لا يمكن تنفيذ هذا الإجراء في المرحلة الحالية للقصة.
storybook.action.accepted=تم استلام الطلب.
storybook.limit.reached=لقد وصلت إلى الحد المسموح لهذا الإجراء.
storybook.photo.consent.required=يجب الموافقة على استخدام الصورة قبل رفعها.
storybook.not.ready=القصة ليست جاهزة بعد.
storybook.insufficient.credits=لا يوجد رصيد كافٍ لإنشاء هذه القصة.
```

- [ ] **Step 4: Write the exception**

```java
package com.doova.ktab.features.storybook.exception;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

/** The request is valid but the book is not in a state that allows it (e.g. approving twice). */
public class StorybookStateConflictException extends KtabException {

    public StorybookStateConflictException(ApiMessageKey key) {
        super(key);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -q test -Dtest=StorybookMessagesTest`
Expected: 2 tests PASS. (`ApiMessageKey.getKey()` is generated by Lombok `@Getter` on the `key` field; if the field has another name, use its getter.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java src/main/resources/messages.properties src/main/java/com/doova/ktab/features/storybook/exception src/test/java/com/doova/ktab/features/storybook/exception
git commit -m "feat(storybook): add storybook API messages and state-conflict exception"
```

---

### Task 5: Child profiles — service, DTOs, controller

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/web/dto/CreateChildProfileRequest.java`, `ChildProfileResponse.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/web/ChildProfileService.java`, `ChildProfileController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/web/ChildProfileServiceTest.java`, `CreateChildProfileRequestValidationTest.java`

**Interfaces:**
- Consumes: `ChildProfile`, `ChildProfileRepository` (Task 2); `ApiMessageKey` (Task 4); `@CurrentUser User`, `ResponseUtils`, `ResourceNotFoundException`, `@ApiVersion` from Ktab.
- Produces:
  - `record CreateChildProfileRequest(@NotBlank @Pattern(ARABIC_NAME) String nameAr, @NotNull ChildGender gender, @NotNull AgeBand ageBand, @NotNull @Valid ChildAppearance appearance)` with `public static final String ARABIC_NAME` (Arabic letters, tashkeel and spaces, 2–30 chars; no digits, no tatweel).
  - `record ChildProfileResponse(Long id, String nameAr, ChildGender gender, AgeBand ageBand, ChildAppearance appearance)` with `static from(ChildProfile)`.
  - `ChildProfileService`: `ChildProfileResponse create(User owner, CreateChildProfileRequest r)`, `List<ChildProfileResponse> list(User owner)`, `ChildProfileResponse update(User owner, Long id, CreateChildProfileRequest r)`, `void delete(User owner, Long id)`, `ChildProfile requireOwned(User owner, Long id)` (404 `STORYBOOK_CHILD_NOT_FOUND` when missing or not the caller's).
  - Endpoints: `POST/GET /storybook/children`, `PUT/DELETE /storybook/children/{childId}`.

- [ ] **Step 1: Write the failing tests**

`CreateChildProfileRequestValidationTest.java`:
```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreateChildProfileRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateChildProfileRequest request(String name) {
        return new CreateChildProfileRequest(name, ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"محمد", "مُحَمَّد", "عبد الله", "آدم", "فاطمة"})
    void acceptsArabicNamesWithOrWithoutTashkeel(String name) {
        assertThat(validator.validate(request(name))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mohamed", "محمد2", "م", "محـمد", "محمد!", "   "})
    void rejectsLatinDigitsTatweelPunctuationAndTooShort(String name) {
        assertThat(validator.validate(request(name))).isNotEmpty();
    }
}
```

`ChildProfileServiceTest.java`:
```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.repository.ChildProfileRepository;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.ChildProfileResponse;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildProfileServiceTest {

    @Mock ChildProfileRepository repository;
    @InjectMocks ChildProfileService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(7L);
    }

    @Test
    void createStoresTheNameExactlyAsTyped() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ChildProfileResponse r = service.create(owner, new CreateChildProfileRequest(
                "  مُحَمَّد ", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE));
        assertThat(r.nameAr()).isEqualTo("مُحَمَّد"); // trimmed, tashkeel kept
    }

    @Test
    void otherUsersProfileIsNotFound() {
        when(repository.findByIdAndOwner_Id(99L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireOwned(owner, 99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesOnlyAnOwnedProfile() {
        ChildProfile p = new ChildProfile();
        when(repository.findByIdAndOwner_Id(5L, 7L)).thenReturn(Optional.of(p));
        service.delete(owner, 5L);
        verify(repository).delete(p);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='CreateChildProfileRequestValidationTest,ChildProfileServiceTest'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write the DTOs**

`CreateChildProfileRequest.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateChildProfileRequest(
        @NotBlank @Pattern(regexp = CreateChildProfileRequest.ARABIC_NAME) String nameAr,
        @NotNull ChildGender gender,
        @NotNull AgeBand ageBand,
        @NotNull @Valid ChildAppearance appearance
) {
    /**
     * Arabic letters (U+0621-U+063A, U+0641-U+064A, U+0671-U+06D3), tashkeel (U+064B-U+0652, U+0670)
     * and spaces; 2 to 30 characters after trimming. Excludes tatweel (U+0640) and digits.
     */
    public static final String ARABIC_NAME =
            "^\\s*[\\u0621-\\u063A\\u0641-\\u064A\\u0671-\\u06D3][\\u0621-\\u063A\\u0641-\\u064A\\u064B-\\u0652\\u0670\\u0671-\\u06D3 ]{1,29}\\s*$";
}
```

`ChildProfileResponse.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.model.ChildProfile;

public record ChildProfileResponse(Long id, String nameAr, ChildGender gender, AgeBand ageBand,
                                   ChildAppearance appearance) {
    public static ChildProfileResponse from(ChildProfile p) {
        return new ChildProfileResponse(p.getId(), p.getNameAr(), p.getGender(), p.getAgeBand(), p.getAppearance());
    }
}
```

- [ ] **Step 4: Write the service**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.repository.ChildProfileRepository;
import com.doova.ktab.features.storybook.web.dto.ChildProfileResponse;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private final ChildProfileRepository repository;

    @Transactional
    public ChildProfileResponse create(User owner, CreateChildProfileRequest r) {
        ChildProfile p = new ChildProfile();
        p.setOwner(owner);
        apply(p, r);
        return ChildProfileResponse.from(repository.save(p));
    }

    @Transactional(readOnly = true)
    public List<ChildProfileResponse> list(User owner) {
        return repository.findByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(ChildProfileResponse::from).toList();
    }

    @Transactional
    public ChildProfileResponse update(User owner, Long id, CreateChildProfileRequest r) {
        ChildProfile p = requireOwned(owner, id);
        apply(p, r);
        return ChildProfileResponse.from(p);
    }

    /** Deleting a profile cascades to its books (FK ON DELETE CASCADE). */
    @Transactional
    public void delete(User owner, Long id) {
        repository.delete(requireOwned(owner, id));
    }

    @Transactional(readOnly = true)
    public ChildProfile requireOwned(User owner, Long id) {
        return repository.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_CHILD_NOT_FOUND));
    }

    private static void apply(ChildProfile p, CreateChildProfileRequest r) {
        p.setNameAr(r.nameAr().strip());
        p.setGender(r.gender());
        p.setAgeBand(r.ageBand());
        p.setAppearance(r.appearance());
    }
}
```

- [ ] **Step 5: Write the controller**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.web.dto.ChildProfileResponse;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.validation.Valid;
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
@RequestMapping(path = "/storybook/children", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class ChildProfileController {

    private final ChildProfileService service;
    private final MessageSource messageSource;

    @PostMapping
    public ResponseEntity<ApiResponse<ChildProfileResponse>> create(@CurrentUser User user,
                                                                    @Valid @RequestBody CreateChildProfileRequest request) {
        return ResponseUtils.success(service.create(user, request),
                ApiMessageKey.STORYBOOK_CHILD_SAVED.getMessage(messageSource), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChildProfileResponse>>> list(@CurrentUser User user) {
        return ResponseUtils.success(service.list(user),
                ApiMessageKey.STORYBOOK_CHILD_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @PutMapping("/{childId}")
    public ResponseEntity<ApiResponse<ChildProfileResponse>> update(@CurrentUser User user, @PathVariable Long childId,
                                                                    @Valid @RequestBody CreateChildProfileRequest request) {
        return ResponseUtils.success(service.update(user, childId, request),
                ApiMessageKey.STORYBOOK_CHILD_SAVED.getMessage(messageSource), HttpStatus.OK);
    }

    @DeleteMapping("/{childId}")
    public ResponseEntity<ApiResponse<Void>> delete(@CurrentUser User user, @PathVariable Long childId) {
        service.delete(user, childId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_CHILD_DELETED.getMessage(messageSource), HttpStatus.OK);
    }
}
```

`ChildAppearance`'s compact constructor throws `NullPointerException` for a missing required field; Jackson wraps that as `HttpMessageNotReadableException`, which Ktab's `GlobalExceptionHandler` already maps to 400.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='CreateChildProfileRequestValidationTest,ChildProfileServiceTest'`
Expected: 14 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/web src/test/java/com/doova/ktab/features/storybook/web
git commit -m "feat(storybook): add child profile API with Arabic name validation"
```

---

### Task 6: `StorybookAccessGuard` — ownership checks

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/web/StorybookAccessGuard.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/web/StorybookAccessGuardTest.java`

**Interfaces:**
- Consumes: `StorybookRepository.findByIdAndOwner_Id` (Task 2), `ApiMessageKey.STORYBOOK_NOT_FOUND` (Task 4).
- Produces: `Storybook requireOwned(Long bookId, User user)` — returns the book, or throws `ResourceNotFoundException(STORYBOOK_NOT_FOUND)` if it does not exist **or** belongs to someone else. Admins use separate admin endpoints (sub-plan 07); the owner check has no admin bypass, so the parent endpoints never leak.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorybookAccessGuardTest {

    private final StorybookRepository repository = mock(StorybookRepository.class);
    private final StorybookAccessGuard guard = new StorybookAccessGuard(repository);

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void ownerGetsTheBook() {
        Storybook book = new Storybook();
        when(repository.findByIdAndOwner_Id(10L, 1L)).thenReturn(Optional.of(book));
        assertThat(guard.requireOwned(10L, user(1))).isSameAs(book);
    }

    @Test
    void someoneElsesBookLooksLikeItDoesNotExist() {
        when(repository.findByIdAndOwner_Id(10L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.requireOwned(10L, user(2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("STORYBOOK_NOT_FOUND");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookAccessGuardTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorybookAccessGuard {

    private final StorybookRepository repository;

    public Storybook requireOwned(Long bookId, User user) {
        return repository.findByIdAndOwner_Id(bookId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_NOT_FOUND));
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=StorybookAccessGuardTest`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/web/StorybookAccessGuard.java src/test/java/com/doova/ktab/features/storybook/web/StorybookAccessGuardTest.java
git commit -m "feat(storybook): add owner-only access guard for books"
```

---

### Task 7: Creating, listing and reading books

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/web/dto/CreateStorybookRequest.java`, `StorybookSummary.java`, `StorybookDetail.java`, `PageView.java`, `BlueprintSummary.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/event/StorybookCreatedEvent.java`
- Create: `src/main/java/com/doova/ktab/features/storybook/web/StorybookService.java`, `StorybookDraftWriter.java`, `StorybookViewMapper.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/web/StorybookServiceTest.java`, `StorybookViewMapperTest.java`

**Interfaces:**
- Consumes: `ChildProfileService.requireOwned` (Task 5), `StorybookAccessGuard` (Task 6), `BlueprintCatalog`, `ModerationService`, `TashkeelFilter` (01), `AiCallLedger` (Task 3), repositories (Task 2), `FileStorageService` (Ktab), `StorybookProperties` (01).
- Produces:
  - `record CreateStorybookRequest(@NotNull Long childProfileId, @NotBlank String blueprintKey, @Size(max = 3) List<Interest> interests, @Valid CompanionSpec companion, StorySetting setting, @NotNull ArtStyle style, @NotNull Integer pageCount, LanguageVariety variety, TashkeelLevel tashkeelLevel, @Size(max = 300) String dedication)`.
  - `record StorybookSummary(Long id, String titleAr, String childNameAr, StorybookStatus status, int pageCount, LocalDateTime createdAt)`.
  - `record PageView(int pageIndex, PageKind kind, String textAr, TextZone textZone, String imageUrl)` — `textAr` already has the book's tashkeel level applied; `imageUrl` is a signed URL or null.
  - `record StorybookDetail(Long id, StorybookStatus status, String titleAr, String childNameAr, LanguageVariety variety, TashkeelLevel tashkeelLevel, int pageCount, String dedication, List<PageView> pages, String characterSheetUrl, String failureReason, int lookRegenerationsLeft, int pageRegenerationsLeft)`.
  - `record BlueprintSummary(String key, String titleAr, String titleEn, String theme, boolean religious, List<StorySetting> allowedSettings)`.
  - `record StorybookCreatedEvent(Long storybookId)` — published inside the insert transaction; sub-plan 04 listens `AFTER_COMMIT`.
  - `StorybookService`: `StorybookDetail create(User owner, CreateStorybookRequest r)`, `List<StorybookSummary> list(User owner)`, `StorybookDetail detail(User owner, Long bookId)`, `List<BlueprintSummary> blueprints(AgeBand band)`.
  - `StorybookDraftWriter.insertDraft(...)` — `@Transactional`, separate bean so the moderation LLM call happens outside any DB transaction.
  - `StorybookViewMapper.toDetail(Storybook book, List<StorybookPage> pages, StorybookCharacter child) : StorybookDetail` — reused by later sub-plans.

Validation rules and the error each raises (all `BadRequestException` → 400):

| Rule | Key |
|---|---|
| `pageCount` ∉ {10, 12, 15} | `STORYBOOK_INVALID_PAGE_COUNT` |
| more than 3 distinct interests | `STORYBOOK_TOO_MANY_INTERESTS` |
| unknown blueprint, or blueprint's `ageBands` does not include the child's | `STORYBOOK_BLUEPRINT_NOT_ALLOWED` |
| `setting` given and not in blueprint's `allowedSettings` | `STORYBOOK_SETTING_NOT_ALLOWED` |
| `variety` is a dialect and `tashkeelLevel` is given and not `NONE` | `STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL` |
| `variety` is MSA (or null → MSA) and `tashkeelLevel` is null | `STORYBOOK_TASHKEEL_REQUIRED` |
| companion name fails `CreateChildProfileRequest.ARABIC_NAME` | `VALIDATION_FAILED` (existing Ktab key) |
| dedication rejected by `ModerationService` | `STORYBOOK_DEDICATION_REJECTED` |

- [ ] **Step 1: Write the failing tests**

`StorybookServiceTest.java`:
```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.story.ModerationResponse;
import com.doova.ktab.features.storybook.story.ModerationService;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorybookServiceTest {

    private final ChildProfileService children = mock(ChildProfileService.class);
    private final ModerationService moderation = mock(ModerationService.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final StorybookDraftWriter writer = mock(StorybookDraftWriter.class);
    private final StorybookViewMapper mapper = mock(StorybookViewMapper.class);
    private final StorybookService service = new StorybookService(children, StoryFixtures.CATALOG, moderation, ledger,
            writer, mapper, mock(StorybookAccessGuard.class),
            mock(com.doova.ktab.features.storybook.repository.StorybookRepository.class),
            mock(com.doova.ktab.features.storybook.repository.StorybookPageRepository.class),
            mock(com.doova.ktab.features.storybook.repository.StorybookCharacterRepository.class));

    private final User owner = new User();
    private final ChildProfile child = new ChildProfile();

    @BeforeEach
    void setUp() {
        owner.setId(1L);
        child.setNameAr("سامي");
        child.setGender(ChildGender.BOY);
        child.setAgeBand(AgeBand.AGE_6_8);
        child.setAppearance(StoryFixtures.APPEARANCE);
        when(children.requireOwned(owner, 5L)).thenReturn(child);
        when(moderation.moderate(any())).thenReturn(new ModerationService.ModerationOutcome(true, null, null));
        when(writer.insertDraft(any(), any(), any(), any())).thenAnswer(inv -> {
            Storybook b = new Storybook();
            b.setId(42L);
            return b;
        });
    }

    private CreateStorybookRequest request(int pages, LanguageVariety variety, TashkeelLevel level,
                                           List<Interest> interests, StorySetting setting, String dedication) {
        return new CreateStorybookRequest(5L, "first-day-of-school", interests, null, setting,
                ArtStyle.SOFT_WATERCOLOR, pages, variety, level, dedication);
    }

    @Test
    void validRequestSavesADraftWithASnapshotOfTheInputs() {
        service.create(owner, request(12, LanguageVariety.MSA, TashkeelLevel.PARTIAL, List.of(Interest.CATS),
                StorySetting.BEIRUT, "إلى سامي"));

        ArgumentCaptor<com.doova.ktab.features.storybook.model.StoryInputs> inputs =
                ArgumentCaptor.forClass(com.doova.ktab.features.storybook.model.StoryInputs.class);
        verify(writer).insertDraft(eq(owner), eq(child), inputs.capture(), any());
        assertThat(inputs.getValue().childNameAr()).isEqualTo("سامي");
        assertThat(inputs.getValue().blueprint().key()).isEqualTo("first-day-of-school");
    }

    @Test
    void rejectsElevenPages() {
        assertThatThrownBy(() -> service.create(owner, request(11, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, null))).isInstanceOf(BadRequestException.class).hasMessage("STORYBOOK_INVALID_PAGE_COUNT");
    }

    @Test
    void rejectsFourInterests() {
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(Interest.CATS, Interest.DOGS, Interest.CARS, Interest.MUSIC), null, null)))
                .hasMessage("STORYBOOK_TOO_MANY_INTERESTS");
    }

    @Test
    void rejectsADialectWithTashkeel() {
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.LEBANESE, TashkeelLevel.FULL,
                List.of(), null, null))).hasMessage("STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL");
    }

    @Test
    void dialectDefaultsToNoTashkeel() {
        service.create(owner, request(10, LanguageVariety.GULF, null, List.of(), null, null));
        verify(writer).insertDraft(any(), any(), any(), argThat(s -> s.tashkeelLevel() == TashkeelLevel.NONE));
    }

    @Test
    void msaRequiresATashkeelLevel() {
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, null, List.of(), null, null)))
                .hasMessage("STORYBOOK_TASHKEEL_REQUIRED");
    }

    @Test
    void rejectsABlueprintOutsideTheChildsAgeBand() {
        child.setAgeBand(AgeBand.AGE_9_10);
        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, null))).hasMessage("STORYBOOK_BLUEPRINT_NOT_ALLOWED");
    }

    @Test
    void rejectedDedicationStopsCreationAndIsCosted() {
        LlmCall<ModerationResponse> call = new LlmCall<>(new ModerationResponse(false, "insult"), "claude-sonnet-5", 10, 5, 1);
        when(moderation.moderate("bad")).thenReturn(new ModerationService.ModerationOutcome(false, "insult", call));

        assertThatThrownBy(() -> service.create(owner, request(10, LanguageVariety.MSA, TashkeelLevel.FULL,
                List.of(), null, "bad"))).hasMessage("STORYBOOK_DEDICATION_REJECTED");
        verify(ledger).recordLlm(isNull(), isNull(), eq(LlmPurpose.MODERATION), eq(call));
        verify(writer, never()).insertDraft(any(), any(), any(), any());
    }
}
```

`StorybookViewMapperTest.java`:
```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorybookViewMapperTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final StorybookViewMapper mapper = new StorybookViewMapper(storage, new StorybookProperties());

    @Test
    void appliesTheTashkeelLevelAndSignsImageUrls() {
        Storybook book = new Storybook();
        book.setId(1L);
        book.setStatus(StorybookStatus.STORY_READY);
        book.setVariety(LanguageVariety.MSA);
        book.setTashkeelLevel(TashkeelLevel.NONE);
        book.setPageCount(10);
        book.setInputs(new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE,
                List.of(), null, null, StoryFixtures.CATALOG.get("first-day-of-school")));
        book.setLookRegenerations(1);

        StorybookPage page = new StorybookPage();
        page.setPageIndex(1);
        page.setKind(PageKind.STORY);
        page.setTextAr("ذَهَبَ سامي.");
        page.setTextZone(TextZone.BOTTOM);
        StorybookPageImage image = new StorybookPageImage();
        image.setImageKey("k1");
        page.setCurrentImage(image);
        when(storage.getFileUrl("k1", UrlStrategy.SIGNED)).thenReturn("https://signed/k1");

        StorybookDetail detail = mapper.toDetail(book, List.of(page), null);

        assertThat(detail.pages()).singleElement().satisfies(p -> {
            assertThat(p.textAr()).isEqualTo("ذهب سامي.");
            assertThat(p.imageUrl()).isEqualTo("https://signed/k1");
        });
        assertThat(detail.lookRegenerationsLeft()).isEqualTo(1);
        assertThat(detail.pageRegenerationsLeft()).isEqualTo(3);
        assertThat(detail.characterSheetUrl()).isNull();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StorybookServiceTest,StorybookViewMapperTest'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write the DTOs and event**

`CreateStorybookRequest.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.ArtStyle;
import com.doova.ktab.features.storybook.enums.Interest;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorySetting;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStorybookRequest(
        @NotNull Long childProfileId,
        @NotBlank String blueprintKey,
        @Size(max = 3) List<Interest> interests,
        @Valid CompanionSpec companion,
        StorySetting setting,
        @NotNull ArtStyle style,
        @NotNull Integer pageCount,
        LanguageVariety variety,
        TashkeelLevel tashkeelLevel,
        @Size(max = 300) String dedication
) {
}
```

`StorybookSummary.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.StorybookStatus;

import java.time.LocalDateTime;

public record StorybookSummary(Long id, String titleAr, String childNameAr, StorybookStatus status,
                               int pageCount, LocalDateTime createdAt) {
}
```

`PageView.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TextZone;

public record PageView(int pageIndex, PageKind kind, String textAr, TextZone textZone, String imageUrl) {
}
```

`StorybookDetail.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;

import java.util.List;

public record StorybookDetail(Long id, StorybookStatus status, String titleAr, String childNameAr,
                              LanguageVariety variety, TashkeelLevel tashkeelLevel, int pageCount,
                              String dedication, List<PageView> pages, String characterSheetUrl,
                              String failureReason, int lookRegenerationsLeft, int pageRegenerationsLeft) {
}
```

`BlueprintSummary.java`:
```java
package com.doova.ktab.features.storybook.web.dto;

import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.enums.StorySetting;

import java.util.List;

public record BlueprintSummary(String key, String titleAr, String titleEn, String theme, boolean religious,
                               List<StorySetting> allowedSettings) {
    public static BlueprintSummary from(Blueprint b) {
        return new BlueprintSummary(b.key(), b.titleAr(), b.titleEn(), b.theme(), b.religious(), b.allowedSettings());
    }
}
```

`StorybookCreatedEvent.java`:
```java
package com.doova.ktab.features.storybook.event;

public record StorybookCreatedEvent(Long storybookId) {
}
```

- [ ] **Step 4: Write `StorybookDraftWriter`**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.model.CharacterAttributes;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StorybookDraftWriter {

    private final StorybookRepository books;
    private final StorybookCharacterRepository characters;
    private final ApplicationEventPublisher events;

    /** @param settings the validated request, with {@code tashkeelLevel} and {@code variety} already resolved */
    @Transactional
    public Storybook insertDraft(User owner, ChildProfile child, StoryInputs inputs, ResolvedSettings settings) {
        Storybook book = new Storybook();
        book.setOwner(owner);
        book.setChildProfile(child);
        book.setInputs(inputs);
        book.setBlueprintKey(inputs.blueprint().key());
        book.setBlueprintVersion(inputs.blueprint().version());
        book.setStyle(settings.request().style());
        book.setVariety(settings.variety());
        book.setTashkeelLevel(settings.tashkeelLevel());
        book.setPageCount(settings.request().pageCount());
        book.setDedication(settings.dedication());
        book.setStatus(StorybookStatus.DRAFT);
        books.save(book);

        StorybookCharacter childCharacter = new StorybookCharacter();
        childCharacter.setStorybook(book);
        childCharacter.setKind(CharacterKind.CHILD);
        childCharacter.setAttributes(CharacterAttributes.ofChild(inputs.appearance()));
        characters.save(childCharacter);

        if (inputs.companion() != null) {
            StorybookCharacter companion = new StorybookCharacter();
            companion.setStorybook(book);
            companion.setKind(CharacterKind.COMPANION);
            companion.setAttributes(CharacterAttributes.ofCompanion(inputs.companion()));
            characters.save(companion);
        }

        events.publishEvent(new StorybookCreatedEvent(book.getId()));
        return book;
    }

    public record ResolvedSettings(CreateStorybookRequest request,
                                   com.doova.ktab.features.storybook.enums.LanguageVariety variety,
                                   com.doova.ktab.features.storybook.enums.TashkeelLevel tashkeelLevel,
                                   String dedication) {
    }
}
```

In `StorybookServiceTest` the stubs use `writer.insertDraft(any(), any(), any(), any())` and `argThat(s -> s.tashkeelLevel() == TashkeelLevel.NONE)` — `s` is a `ResolvedSettings`.

- [ ] **Step 5: Write `StorybookViewMapper`**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.story.TashkeelFilter;
import com.doova.ktab.features.storybook.web.dto.PageView;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StorybookViewMapper {

    private final FileStorageService storage;
    private final StorybookProperties properties;

    public StorybookDetail toDetail(Storybook book, List<StorybookPage> pages, StorybookCharacter child) {
        List<PageView> views = pages.stream()
                .map(p -> new PageView(p.getPageIndex(), p.getKind(),
                        p.getTextAr() == null ? null : TashkeelFilter.apply(p.getTextAr(), book.getTashkeelLevel()),
                        p.getTextZone(),
                        p.getCurrentImage() == null ? null : storage.getFileUrl(p.getCurrentImage().getImageKey(), UrlStrategy.SIGNED)))
                .toList();
        String sheetUrl = child == null || child.getSheetKey() == null ? null
                : storage.getFileUrl(child.getSheetKey(), UrlStrategy.SIGNED);
        StorybookProperties.Limits limits = properties.getLimits();
        return new StorybookDetail(book.getId(), book.getStatus(),
                book.getTitleAr() == null ? null : TashkeelFilter.apply(book.getTitleAr(), book.getTashkeelLevel()),
                book.getInputs().childNameAr(), book.getVariety(), book.getTashkeelLevel(), book.getPageCount(),
                book.getDedication(), views, sheetUrl, book.getFailureReason(),
                Math.max(0, limits.getLookRegenerations() - book.getLookRegenerations()),
                Math.max(0, limits.getPageRegenerationsPerBook() - book.getPageRegenerations()));
    }
}
```

- [ ] **Step 6: Write `StorybookService`**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.ModerationService;
import com.doova.ktab.features.storybook.web.dto.BlueprintSummary;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StorybookService {

    private static final Set<Integer> PAGE_COUNTS = Set.of(10, 12, 15);

    private final ChildProfileService children;
    private final BlueprintCatalog blueprints;
    private final ModerationService moderation;
    private final AiCallLedger ledger;
    private final StorybookDraftWriter writer;
    private final StorybookViewMapper mapper;
    private final StorybookAccessGuard guard;
    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookCharacterRepository characters;

    /** Deliberately not @Transactional: moderation calls an LLM and must not hold a DB connection. */
    public StorybookDetail create(User owner, CreateStorybookRequest r) {
        ChildProfile child = children.requireOwned(owner, r.childProfileId());

        if (!PAGE_COUNTS.contains(r.pageCount())) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_INVALID_PAGE_COUNT);
        }
        List<com.doova.ktab.features.storybook.enums.Interest> interests =
                r.interests() == null ? List.of() : List.copyOf(new LinkedHashSet<>(r.interests()));
        if (interests.size() > 3) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_TOO_MANY_INTERESTS);
        }

        Blueprint blueprint;
        try {
            blueprint = blueprints.get(r.blueprintKey());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_BLUEPRINT_NOT_ALLOWED);
        }
        if (!blueprint.ageBands().contains(child.getAgeBand())) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_BLUEPRINT_NOT_ALLOWED);
        }
        if (r.setting() != null && !blueprint.allowedSettings().contains(r.setting())) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_SETTING_NOT_ALLOWED);
        }

        LanguageVariety variety = r.variety() == null ? LanguageVariety.MSA : r.variety();
        TashkeelLevel tashkeel;
        if (variety.isDialect()) {
            if (r.tashkeelLevel() != null && r.tashkeelLevel() != TashkeelLevel.NONE) {
                throw new BadRequestException(ApiMessageKey.STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL);
            }
            tashkeel = TashkeelLevel.NONE;
        } else {
            if (r.tashkeelLevel() == null) {
                throw new BadRequestException(ApiMessageKey.STORYBOOK_TASHKEEL_REQUIRED);
            }
            tashkeel = r.tashkeelLevel();
        }

        if (r.companion() != null && !r.companion().nameAr().matches(CreateChildProfileRequest.ARABIC_NAME)) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        String dedication = r.dedication() == null || r.dedication().isBlank() ? null : r.dedication().strip();
        ModerationService.ModerationOutcome outcome = moderation.moderate(dedication);
        if (outcome.llmCall() != null) {
            ledger.recordLlm(null, null, LlmPurpose.MODERATION, outcome.llmCall());
        }
        if (!outcome.allowed()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_DEDICATION_REJECTED);
        }

        StoryInputs inputs = new StoryInputs(child.getNameAr(), child.getGender(), child.getAgeBand(),
                child.getAppearance(), interests, r.companion(),
                r.setting(), blueprint);
        Storybook book = writer.insertDraft(owner, child, inputs,
                new StorybookDraftWriter.ResolvedSettings(r, variety, tashkeel, dedication));
        return detail(owner, book.getId());
    }

    @Transactional(readOnly = true)
    public List<StorybookSummary> list(User owner) {
        return books.findByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(b -> new StorybookSummary(b.getId(), b.getTitleAr(), b.getInputs().childNameAr(),
                        b.getStatus(), b.getPageCount(), b.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StorybookDetail detail(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        return mapper.toDetail(book, pages.findByStorybook_IdOrderByPageIndexAsc(bookId),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElse(null));
    }

    public List<BlueprintSummary> blueprints(AgeBand band) {
        return blueprints.forAgeBand(band).stream().map(BlueprintSummary::from).toList();
    }
}
```

In the unit test the guard, repositories and mapper are Mockito mocks, so the final `detail(...)` call returns `null` there; the test asserts on what was passed to the writer. In production the guard finds the book just inserted.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StorybookServiceTest,StorybookViewMapperTest'`
Expected: 9 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook/web
git commit -m "feat(storybook): create, list and read books with input validation and moderation"
```

---

### Task 8: `StorybookController`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/web/StorybookControllerTest.java`

**Interfaces:**
- Consumes: `StorybookService` (Task 7).
- Produces: `POST /storybook/books` (201), `GET /storybook/books`, `GET /storybook/books/{bookId}`, `GET /storybook/blueprints?ageBand=`. Sub-plans 03–07 add more endpoints to this controller.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.web.dto.BlueprintSummary;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorybookControllerTest {

    private final StorybookService service = mock(StorybookService.class);
    private final MessageSource messages = mock(MessageSource.class);
    private MockMvc mvc;
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(1L);
        HandlerMethodArgumentResolver currentUser = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter p) { return p.hasParameterAnnotation(CurrentUser.class); }
            public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) { return user; }
        };
        mvc = MockMvcBuilders.standaloneSetup(new StorybookController(service, messages))
                .setCustomArgumentResolvers(currentUser).build();
    }

    private static StorybookDetail detail() {
        return new StorybookDetail(42L, StorybookStatus.DRAFT, null, "سامي", LanguageVariety.MSA, TashkeelLevel.FULL,
                10, null, List.of(), null, null, 2, 3);
    }

    @Test
    void createReturns201WithTheDraft() throws Exception {
        when(service.create(eq(user), any())).thenReturn(detail());
        mvc.perform(post("/storybook/books").contentType(MediaType.APPLICATION_JSON).content("""
                {"childProfileId":5,"blueprintKey":"first-day-of-school","style":"SOFT_WATERCOLOR",
                 "pageCount":10,"variety":"MSA","tashkeelLevel":"FULL"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createWithoutPageCountIs400() throws Exception {
        mvc.perform(post("/storybook/books").contentType(MediaType.APPLICATION_JSON).content("""
                {"childProfileId":5,"blueprintKey":"first-day-of-school","style":"SOFT_WATERCOLOR"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheBook() throws Exception {
        when(service.detail(user, 42L)).thenReturn(detail());
        mvc.perform(get("/storybook/books/42")).andExpect(status().isOk()).andExpect(jsonPath("$.data.childNameAr").value("سامي"));
    }

    @Test
    void blueprintsAreFilteredByAgeBand() throws Exception {
        when(service.blueprints(AgeBand.AGE_3_5)).thenReturn(List.of(
                new BlueprintSummary("first-day-of-school", "يومي الأول في المدرسة", "My First Day at School", "school", false, List.of())));
        mvc.perform(get("/storybook/blueprints").param("ageBand", "AGE_3_5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].key").value("first-day-of-school"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StorybookControllerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package com.doova.ktab.features.storybook.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.web.dto.BlueprintSummary;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import jakarta.validation.Valid;
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
@RequestMapping(path = "/storybook", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class StorybookController {

    private final StorybookService service;
    private final MessageSource messageSource;

    @PostMapping("/books")
    public ResponseEntity<ApiResponse<StorybookDetail>> create(@CurrentUser User user,
                                                               @Valid @RequestBody CreateStorybookRequest request) {
        return ResponseUtils.success(service.create(user, request),
                ApiMessageKey.STORYBOOK_CREATED.getMessage(messageSource), HttpStatus.CREATED);
    }

    @GetMapping("/books")
    public ResponseEntity<ApiResponse<List<StorybookSummary>>> list(@CurrentUser User user) {
        return ResponseUtils.success(service.list(user), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<StorybookDetail>> get(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.detail(user, bookId), ApiMessageKey.STORYBOOK_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/blueprints")
    public ResponseEntity<ApiResponse<List<BlueprintSummary>>> blueprints(@RequestParam AgeBand ageBand) {
        return ResponseUtils.success(service.blueprints(ageBand),
                ApiMessageKey.STORYBOOK_BLUEPRINTS_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=StorybookControllerTest`
Expected: 4 tests PASS.

- [ ] **Step 5: Run the whole storybook suite**

Run: `./mvnw -q test -Dtest='com.doova.ktab.features.storybook.**'`
Expected: all storybook tests PASS (spike and live tests are skipped).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java src/test/java/com/doova/ktab/features/storybook/web/StorybookControllerTest.java
git commit -m "feat(storybook): add book and blueprint REST endpoints"
```

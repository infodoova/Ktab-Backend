> **SUPERSEDED** by `2026-09-28-trailer-agent.md` (agent-orchestrated on Claude Managed Agents). Not implemented; kept for its analysis.

# Book Trailer (Higgsfield + ElevenLabs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From inside Ktab, an author, librarian or admin clicks "generate trailer" on a published book. Ktab produces a 30-second, 16:9, 1080p MP4 trailer with no on-screen text: Higgsfield video, an ElevenLabs voice-over and music. The MP4 is saved in Ktab's R2 bucket and can be downloaded.

**Architecture:** This is the same flow the ChatGPT + Higgsfield + ElevenLabs session ran by hand, rebuilt as a server pipeline. The steps are:

1. Claude reads the book's first pages and writes the narration, six 5-second shot prompts and a music brief.
2. ElevenLabs speaks the narration and composes a 30 s instrumental.
3. Higgsfield renders the six shots.
4. Claude checks sampled frames of every shot for on-screen text, and a failing shot is re-rendered.
5. FFmpeg crops, concatenates, ducks and mixes the result into exactly 30.000 s at 1920×1080.
6. The MP4 is uploaded to R2.

Each `tbl_book_trailers` row is its own state machine. A scheduled worker advances it: rows are claimed with `FOR UPDATE SKIP LOCKED` plus a lease, so the pipeline survives restarts and runs safely on several instances.

**Tech Stack:** Spring Boot 3.5, Postgres + Flyway, Spring `RestClient` (Higgsfield REST, ElevenLabs REST), `com.anthropic:anthropic-java` (already in the pom), AWS SDK S3 client on Cloudflare R2 (already configured), and the system `ffmpeg`/`ffprobe` binaries (added to the Docker image).

**Spec:** the requirements in this document's next section. They combine the user's request ("a 30 sec trailer for author, admin, librarian for their published books … I can download it and save it in my bucket") with the reference ChatGPT session: 16:9, ElevenLabs voice-over, no Arabic text in the video.

## Requirements

- R1. Roles: an `AUTHOR` (their own books), a `LIBRARIAN` or `ADMIN_LIBRARIAN` (books of their library organization), or an `ADMIN` (any book) can request a trailer. The book must be `PUBLISHED`.
- R2. The output is one MP4: 30 s long (±0.5 s), 16:9, 1920×1080, 30 fps, H.264 + AAC.
- R3. Narration is an ElevenLabs voice-over in the book's language (MSA for Arabic books) and ends before the 30 s mark.
- R4. The video shows no text of any kind: no subtitles, no titles, no book-cover lettering, no signage, no pseudo-letters.
- R5. The video never depicts an identifiable real person. Political and biographical books use symbolic imagery.
- R6. The final MP4 is stored in Ktab's R2 bucket. Authorized users can download it through a presigned URL.
- R7. Generation is asynchronous. The user can poll the status, and can cancel while the trailer is still generating.

## How this maps to "Higgsfield + Claude MCP"

The Higgsfield and ElevenLabs "apps" in ChatGPT, and the MCP connectors in Claude, are for **chat assistants**. A backend pipeline must not depend on a chat session. Ktab calls the **same models** through Higgsfield's public REST API (`https://api.higgsfield.ai`, `Authorization: Key {id}:{secret}`, submit → poll `/requests/{id}/status`) and through ElevenLabs' REST API. Claude does the creative-director work that ChatGPT did in the transcript: it reads the book, writes the script and plans the shots.

## Global Constraints

- Package: `com.doova.ktab.features.trailer`. Tables: `tbl_book_trailers` and `tbl_book_trailer_shots`, with `col_*` columns and BaseEntity audit columns. Migration: `V21__book_trailers.sql` (latest existing is V20).
- Feature flag: `ktab.trailer.enabled`, default `false`. When it is off, no worker, no controller and no scheduling.
- Secrets come from the environment only: `HF_API_KEY_ID`, `HF_API_KEY_SECRET`, `ANTHROPIC_API_KEY` and the existing `elevenlabs.api-key`. Never commit keys.
- Controllers: `@ApiVersion(1)`, responses via `ResponseUtils.success`, messages via `ApiMessageKey` + Arabic `messages.properties`, errors via `KtabException` subclasses.
- The trailer feature must not import `com.doova.ktab.features.storybook..` in main code. Test code may reuse `storybook.support.StorybookJpaIT` and `UserFixtures`.
- Claude model: `claude-sonnet-5`, structured outputs via `outputConfig(Class)`, copying the SDK usage already proven in `features/storybook/llm/AnthropicLlmGateway.java`. Do not create a second `AnthropicClient` **bean**, because storybook injects that type by type. Build the client inside the trailer component instead.
- Higgsfield shot model (confirmed by the Task 2 spike): `POST /kling-video/v2.5-turbo/pro/text-to-video` with body `{prompt, duration: 5, cfg_scale, negative_prompt}`. The endpoint and any extra body params are configuration, so swapping models needs no code change.
- Higgsfield output URLs expire after about 7 days. Always copy the output to R2 immediately.
- Shape: 6 shots × 5 s = 30 s. The narration must be ≤ 27.5 s, because it starts at 1.0 s and needs a tail.

## Decisions

- **D1 Text-to-video, not keyframe image-to-video.** It is one call per shot. FFmpeg's `scale…increase,crop=1920:1080` guarantees 16:9 even if the model returns another ratio. The Task 2 spike records the model's native ratio. If it is not 16:9, set `ktab.trailer.higgsfield.extra-params.aspect_ratio=16:9`, or pick a model that accepts it; the change is configuration only.
- **D2 No text, three layers:**
  - The shot prompts ban text.
  - Higgsfield's `negative_prompt` lists text-like artifacts.
  - Claude vision checks 5 sampled frames of every shot. A shot with text is re-rendered, up to 3 attempts per shot, then the trailer fails with a clear error.
- **D3 No real people.** Claude lists every real person named in the excerpt (`realPeopleNamed`). `TrailerScriptRules` rejects any shot prompt that mentions one of them. Higgsfield `nsfw` or `failed` results are retried with a stronger safety suffix.
- **D4 Audio:**
  - Narration uses ElevenLabs `POST /v1/text-to-speech/{voice}/with-timestamps` with `eleven_multilingual_v2`. The duration is the last `character_end_times_seconds`.
  - If the narration exceeds 27.5 s, it is re-spoken once at `speed = min(1.15, seconds/27.5 + 0.02)`. If it is still too long, the trailer fails.
  - Music uses `POST /v1/music` with `force_instrumental=true` and `music_length_ms=30000`.
- **D5 Mixing:** the voice starts at 1.0 s. The music runs at 25% volume and is side-chain ducked under the voice, with a 1 s fade-in and a 2 s fade-out. The mix is loudness-normalized to −14 LUFS, then the video is hard-trimmed to 30 s.
- **D6 Limits:**
  - One active trailer per book, enforced by a partial unique index.
  - At most `ktab.trailer.limits.per-book-per-30-days=3` non-failed trailers per book. `ADMIN` is exempt.
  - Global cap: `ktab.trailer.higgsfield.max-in-flight=6` Higgsfield requests at a time.
- **D7 Idempotency:** R2 keys are deterministic (`trailers/{bookId}/{trailerId}/…`). Every step checks `store.exists(key)` before paying a provider again. Higgsfield has no idempotency key: a crash between "submit" and "save request id" can pay for one extra 5 s shot. That is accepted and documented.
- **D8 Polling, not webhooks.** Higgsfield webhooks are unsigned and need a public HTTPS endpoint, and Ktab dev machines are not public. Polling starts at 10 s intervals (`ktab.trailer.worker.poll-interval`).

## Review Focus

1. A book with **no extracted page text** (a DIGITAL-route book, or OCR still pending) still gets a trailer written from its title and description, not a failure. Pinned in Task 6: `scriptFallsBackToTitleAndDescriptionWhenThereIsNoPageText`.
2. For a **political or biographical book**, Higgsfield may return `nsfw`. The shot is retried with the safety suffix. When attempts run out, the trailer ends `FAILED` with a readable reason, and a failed trailer does not count against the 30-day limit. Pinned in Task 6: `nsfwShotIsRetriedThenFailsTheTrailer`, and in Task 7: `failedTrailersDoNotCountTowardTheLimit`.
3. **Cancel while the worker is mid-step.** The worker's later save must not resurrect the trailer. `@Version` optimistic locking rejects the stale save, and the next tick sees `CANCELLED` and does nothing. Pinned in Task 6: `cancelledTrailerIsNeverAdvanced`.
4. **Narration too long for 30 s.** The pipeline speeds it up once, then fails loudly. It never ships a trailer whose voice is cut off. Pinned in Task 6: `longNarrationIsSpedUpOnce` and `narrationStillTooLongFailsTheTrailer`.
5. **Double-click "generate" or two app instances.** Only one active trailer can exist per book: the DB index returns 409, not two paid pipelines. Pinned in Task 1: `onlyOneActiveTrailerPerBook`.

---

### Task 1: Schema, properties, entities, repositories

**Files:**
- Create: `src/main/resources/db/migration/V21__book_trailers.sql`
- Create: `src/main/java/com/doova/ktab/features/trailer/config/TrailerProperties.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/enums/TrailerStatus.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/enums/ShotStatus.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/model/BookTrailer.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/model/BookTrailerShot.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/repository/BookTrailerRepository.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/repository/BookTrailerShotRepository.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/support/TrailerFixtures.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/repository/BookTrailerRepositoryIT.java`

**Interfaces:**
- Produces:
  - `TrailerStatus { QUEUED, VOICING, SHOOTING, ASSEMBLING, READY, FAILED, CANCELLED }` with `static final Set<TrailerStatus> ACTIVE` and `boolean isActive()`.
  - `ShotStatus { PENDING, SUBMITTED, PASSED }`.
  - `BookTrailer` getters and setters: `bookId`, `requestedById`, `status`, `scriptJson`, `voiceoverKey`, `voiceoverSeconds`, `musicKey`, `videoKey`, `error`, `attempts`, `nextRunAt`, `lockedBy`, `lockedUntil`, `finishedAt`.
  - `BookTrailerShot(Long trailerId, int shotIndex, String prompt)` with `status`, `attempt`, `requestId`, `videoKey`, `lastError`.
  - `BookTrailerRepository`:
    - `findByBookIdOrderByIdDesc(Long)`
    - `boolean existsByBookIdAndStatusIn(Long, Collection<TrailerStatus>)`
    - `long countByBookIdAndCreatedAtAfterAndStatusNotIn(Long, LocalDateTime, Collection<TrailerStatus>)`
    - `List<Long> lockDueIds(int limit)`
    - `int lease(List<Long> ids, String worker, Instant until)`
  - `BookTrailerShotRepository`:
    - `findByTrailerIdOrderByShotIndexAsc(Long)`
    - `long countByStatus(ShotStatus)`
  - `TrailerProperties`, with the nested groups `higgsfield`, `elevenlabs`, `claude`, `ffmpeg`, `worker`, `limits` (fields listed in the code below).

- [ ] **Step 1: Write the failing repository IT**

```java
// src/test/java/com/doova/ktab/features/trailer/support/TrailerFixtures.java
package com.doova.ktab.features.trailer.support;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

public final class TrailerFixtures {

    private TrailerFixtures() {
    }

    public static Book publishedBook(TestEntityManager em, User author) {
        Book book = new Book();
        book.setTitle("ثورة دونالد ترامب");
        book.setDescription("قراءة ألكسندر دوغين لعودة ترامب.");
        book.setLanguage("ar");
        book.setAuthor(author);
        book.setStatus(BookStatus.PUBLISHED);
        return em.persistAndFlush(book);
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/repository/BookTrailerRepositoryIT.java
package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.support.TrailerFixtures;
import com.doova.ktab.model.book.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTrailerRepositoryIT extends StorybookJpaIT {

    @Autowired BookTrailerRepository trailers;

    private BookTrailer trailer(Book book, TrailerStatus status) {
        BookTrailer t = new BookTrailer();
        t.setBookId(book.getId());
        t.setStatus(status);
        return t;
    }

    @Test
    void onlyOneActiveTrailerPerBook() {
        Book book = TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "author-" + System.nanoTime() + "@x.com"));
        trailers.saveAndFlush(trailer(book, TrailerStatus.SHOOTING));
        trailers.saveAndFlush(trailer(book, TrailerStatus.READY)); // finished ones don't count

        assertThatThrownBy(() -> trailers.saveAndFlush(trailer(book, TrailerStatus.QUEUED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countsRecentNonFailedTrailers() {
        Book book = TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "author-" + System.nanoTime() + "@x.com"));
        trailers.saveAndFlush(trailer(book, TrailerStatus.READY));
        trailers.saveAndFlush(trailer(book, TrailerStatus.FAILED));

        long n = trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(book.getId(), LocalDateTime.now().minusDays(30),
                EnumSet.of(TrailerStatus.FAILED, TrailerStatus.CANCELLED));
        assertThat(n).isEqualTo(1);
    }

    @Test
    void lockDueIdsReturnsOnlyDueActiveUnleasedRows() {
        Book book = TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "author-" + System.nanoTime() + "@x.com"));
        BookTrailer due = trailers.saveAndFlush(trailer(book, TrailerStatus.QUEUED));
        Book other = TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "author-" + System.nanoTime() + "@x.com"));
        BookTrailer future = trailer(other, TrailerStatus.SHOOTING);
        future.setNextRunAt(Instant.now().plusSeconds(600));
        trailers.saveAndFlush(future);

        List<Long> ids = trailers.lockDueIds(10);

        assertThat(ids).contains(due.getId()).doesNotContain(future.getId());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `STORYBOOK_IT_DB_URL=jdbc:postgresql://localhost:5432/ktab_storybook_it STORYBOOK_IT_DB_PASSWORD=123456 mvn -o verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BookTrailerRepositoryIT`
Expected: compilation failure — `BookTrailerRepository` does not exist.

- [ ] **Step 3: Write the migration, enums, properties, entities and repositories**

```sql
-- src/main/resources/db/migration/V21__book_trailers.sql
-- ============================================================================
-- Flyway Migration V21: 30-second book trailers (Higgsfield video + ElevenLabs audio)
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_book_trailers (
    col_id                BIGSERIAL    PRIMARY KEY,
    col_book_id           BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_requested_by      BIGINT       REFERENCES tbl_users (col_id) ON DELETE SET NULL,
    col_status            VARCHAR(20)  NOT NULL,
    col_script            TEXT,
    col_voiceover_key     VARCHAR(300),
    col_voiceover_seconds NUMERIC(6, 2),
    col_music_key         VARCHAR(300),
    col_video_key         VARCHAR(300),
    col_error             TEXT,
    col_attempts          INTEGER      NOT NULL DEFAULT 0,
    col_next_run_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_locked_by         VARCHAR(100),
    col_locked_until      TIMESTAMPTZ,
    col_finished_at       TIMESTAMPTZ,
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0
);

-- D6: one active trailer per book, race-safe across instances and double clicks
CREATE UNIQUE INDEX IF NOT EXISTS uq_book_trailer_active ON tbl_book_trailers (col_book_id)
    WHERE col_status IN ('QUEUED', 'VOICING', 'SHOOTING', 'ASSEMBLING');

CREATE INDEX IF NOT EXISTS idx_book_trailer_due ON tbl_book_trailers (col_next_run_at)
    WHERE col_status IN ('QUEUED', 'VOICING', 'SHOOTING', 'ASSEMBLING');

CREATE INDEX IF NOT EXISTS idx_book_trailer_book ON tbl_book_trailers (col_book_id, created_at);

CREATE TABLE IF NOT EXISTS tbl_book_trailer_shots (
    col_id                BIGSERIAL    PRIMARY KEY,
    col_trailer_id        BIGINT       NOT NULL REFERENCES tbl_book_trailers (col_id) ON DELETE CASCADE,
    col_shot_index        SMALLINT     NOT NULL,
    col_prompt            TEXT         NOT NULL,
    col_status            VARCHAR(20)  NOT NULL,
    col_attempt           INTEGER      NOT NULL DEFAULT 1,
    col_request_id        VARCHAR(64),
    col_video_key         VARCHAR(300),
    col_last_error        TEXT,
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_book_trailer_shot UNIQUE (col_trailer_id, col_shot_index)
);

CREATE INDEX IF NOT EXISTS idx_book_trailer_shot_status ON tbl_book_trailer_shots (col_status);
```

```java
// src/main/java/com/doova/ktab/features/trailer/enums/TrailerStatus.java
package com.doova.ktab.features.trailer.enums;

import java.util.EnumSet;
import java.util.Set;

public enum TrailerStatus {
    QUEUED, VOICING, SHOOTING, ASSEMBLING, READY, FAILED, CANCELLED;

    /** Must match the partial index predicate in V21__book_trailers.sql. */
    public static final Set<TrailerStatus> ACTIVE = EnumSet.of(QUEUED, VOICING, SHOOTING, ASSEMBLING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/enums/ShotStatus.java
package com.doova.ktab.features.trailer.enums;

public enum ShotStatus { PENDING, SUBMITTED, PASSED }
```

```java
// src/main/java/com/doova/ktab/features/trailer/config/TrailerProperties.java
package com.doova.ktab.features.trailer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "ktab.trailer")
public class TrailerProperties {

    private boolean enabled = false;
    private int shotCount = 6;
    private int shotSeconds = 5;
    private double targetSeconds = 30.0;
    /** Voice starts at 1.0 s and needs a tail before the fade-out (D4). */
    private double maxNarrationSeconds = 27.5;
    private int maxShotAttempts = 3;
    private int excerptPages = 40;
    private int excerptMaxChars = 40_000;

    private Higgsfield higgsfield = new Higgsfield();
    private ElevenLabs elevenlabs = new ElevenLabs();
    private Claude claude = new Claude();
    private Ffmpeg ffmpeg = new Ffmpeg();
    private Worker worker = new Worker();
    private Limits limits = new Limits();

    @Getter
    @Setter
    public static class Higgsfield {
        private String baseUrl = "https://api.higgsfield.ai";
        private String apiKeyId;
        private String apiKeySecret;
        private String videoEndpoint = "/kling-video/v2.5-turbo/pro/text-to-video";
        private double cfgScale = 0.5;
        private String negativePrompt = "text, letters, words, writing, subtitles, captions, title card, logo, "
                + "watermark, signage, poster, banner, newspaper, book cover lettering, calligraphy, arabic script, "
                + "numbers, typography, recognizable celebrity, real politician, blurry, distorted faces";
        /** Extra body params per model, e.g. aspect_ratio=16:9 (D1). */
        private Map<String, Object> extraParams = new LinkedHashMap<>();
        private int maxInFlight = 6;
        private Duration timeout = Duration.ofSeconds(60);
    }

    @Getter
    @Setter
    public static class ElevenLabs {
        private String baseUrl = "https://api.elevenlabs.io";
        private String apiKey;
        private String voiceId;
        private String ttsModelId = "eleven_multilingual_v2";
        private String musicModelId = "music_v1";
        private double maxSpeed = 1.15;
        private Duration timeout = Duration.ofSeconds(120);
    }

    @Getter
    @Setter
    public static class Claude {
        private String apiKey;
        private String model = "claude-sonnet-5";
        private Duration timeout = Duration.ofSeconds(120);
    }

    @Getter
    @Setter
    public static class Ffmpeg {
        private String path = "ffmpeg";
        private String ffprobePath = "ffprobe";
        private Duration timeout = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Worker {
        private int concurrency = 2;
        private Duration lease = Duration.ofMinutes(10);
        private Duration pollInterval = Duration.ofSeconds(10);
        private int maxStepAttempts = 5;
    }

    @Getter
    @Setter
    public static class Limits {
        private int perBookPer30Days = 3;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/model/BookTrailer.java
package com.doova.ktab.features.trailer.model;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tbl_book_trailers")
@Getter
@Setter
public class BookTrailer extends BaseEntity {

    /** Plain ids, not relations: the worker never needs the book or user graph. */
    @Column(name = "col_book_id", nullable = false)
    private Long bookId;

    @Column(name = "col_requested_by")
    private Long requestedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private TrailerStatus status = TrailerStatus.QUEUED;

    @Column(name = "col_script", columnDefinition = "TEXT")
    private String scriptJson;

    @Column(name = "col_voiceover_key", length = 300)
    private String voiceoverKey;

    @Column(name = "col_voiceover_seconds", precision = 6, scale = 2)
    private BigDecimal voiceoverSeconds;

    @Column(name = "col_music_key", length = 300)
    private String musicKey;

    @Column(name = "col_video_key", length = 300)
    private String videoKey;

    @Column(name = "col_error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "col_attempts", nullable = false)
    private int attempts;

    @Column(name = "col_next_run_at", nullable = false)
    private Instant nextRunAt = Instant.now();

    @Column(name = "col_locked_by", length = 100)
    private String lockedBy;

    @Column(name = "col_locked_until")
    private Instant lockedUntil;

    @Column(name = "col_finished_at")
    private Instant finishedAt;
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/model/BookTrailerShot.java
package com.doova.ktab.features.trailer.model;

import com.doova.ktab.features.trailer.enums.ShotStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_book_trailer_shots")
@Getter
@Setter
@NoArgsConstructor
public class BookTrailerShot extends BaseEntity {

    @Column(name = "col_trailer_id", nullable = false)
    private Long trailerId;

    @Column(name = "col_shot_index", nullable = false)
    private short shotIndex;

    @Column(name = "col_prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private ShotStatus status = ShotStatus.PENDING;

    @Column(name = "col_attempt", nullable = false)
    private int attempt = 1;

    @Column(name = "col_request_id", length = 64)
    private String requestId;

    @Column(name = "col_video_key", length = 300)
    private String videoKey;

    @Column(name = "col_last_error", columnDefinition = "TEXT")
    private String lastError;

    public BookTrailerShot(Long trailerId, int shotIndex, String prompt) {
        this.trailerId = trailerId;
        this.shotIndex = (short) shotIndex;
        this.prompt = prompt;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/repository/BookTrailerRepository.java
package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookTrailerRepository extends JpaRepository<BookTrailer, Long> {

    List<BookTrailer> findByBookIdOrderByIdDesc(Long bookId);

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<TrailerStatus> statuses);

    long countByBookIdAndCreatedAtAfterAndStatusNotIn(Long bookId, LocalDateTime after, Collection<TrailerStatus> excluded);

    @Query(nativeQuery = true, value = """
            SELECT col_id FROM tbl_book_trailers
            WHERE col_status IN ('QUEUED', 'VOICING', 'SHOOTING', 'ASSEMBLING')
              AND col_next_run_at <= now()
              AND (col_locked_until IS NULL OR col_locked_until < now())
            ORDER BY col_next_run_at, col_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    List<Long> lockDueIds(@Param("limit") int limit);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE tbl_book_trailers SET col_locked_by = :worker, col_locked_until = :until
            WHERE col_id IN (:ids)
            """)
    int lease(@Param("ids") List<Long> ids, @Param("worker") String worker, @Param("until") Instant until);
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/repository/BookTrailerShotRepository.java
package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.trailer.enums.ShotStatus;
import com.doova.ktab.features.trailer.model.BookTrailerShot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookTrailerShotRepository extends JpaRepository<BookTrailerShot, Long> {

    List<BookTrailerShot> findByTrailerIdOrderByShotIndexAsc(Long trailerId);

    long countByStatus(ShotStatus status);
}
```

- [ ] **Step 4: Run it to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 3, Failures: 0, Errors: 0`. Flyway applies V21 and `ddl-auto=validate` accepts the entities.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V21__book_trailers.sql src/main/java/com/doova/ktab/features/trailer src/test/java/com/doova/ktab/features/trailer
git commit -m "feat(trailer): add trailer schema, entities and repositories"
```

---

### Task 2: Higgsfield client + live spike

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/TrailerStepException.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldClient.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldStatus.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldClientTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldLiveSpikeTest.java`

**Interfaces:**
- Consumes: `TrailerProperties.Higgsfield`.
- Produces:
  - `TrailerStepException(String message, boolean retryable)` and `(String, boolean, Throwable)`, with `boolean retryable()`.
  - `HiggsfieldClient(TrailerProperties)` (Spring, with timeouts) and a package-private `HiggsfieldClient(RestClient.Builder, TrailerProperties)` (tests):
    - `String submit(String prompt)` returns the request id.
    - `HiggsfieldStatus status(String requestId)`
    - `void cancel(String requestId)` is best effort.
    - `byte[] download(String url)`
  - `HiggsfieldStatus(String status, String videoUrl, String error)` with `boolean completed()`, `boolean pending()`, `boolean rejected()`. `rejected()` is true for failed, nsfw and canceled.

- [ ] **Step 1: Write the failing client test**

```java
// src/test/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldClientTest.java
package com.doova.ktab.features.trailer.higgsfield;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HiggsfieldClientTest {

    private MockRestServiceServer server;
    private HiggsfieldClient client;

    @BeforeEach
    void setUp() {
        TrailerProperties props = new TrailerProperties();
        props.getHiggsfield().setApiKeyId("id");
        props.getHiggsfield().setApiKeySecret("secret");
        props.getHiggsfield().getExtraParams().put("aspect_ratio", "16:9");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HiggsfieldClient(builder, props);
    }

    @Test
    void submitSendsKeyAuthFiveSecondsNegativePromptAndExtraParams() {
        server.expect(requestTo("https://api.higgsfield.ai/kling-video/v2.5-turbo/pro/text-to-video"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Key id:secret"))
                .andExpect(jsonPath("$.duration").value(5))
                .andExpect(jsonPath("$.aspect_ratio").value("16:9"))
                .andExpect(jsonPath("$.negative_prompt").isNotEmpty())
                .andRespond(withSuccess("{\"status\":\"queued\",\"request_id\":\"r-1\"}", MediaType.APPLICATION_JSON));

        assertThat(client.submit("A storm over a desert city at dusk")).isEqualTo("r-1");
        server.verify();
    }

    @Test
    void statusParsesCompletedVideoUrl() {
        server.expect(requestTo("https://api.higgsfield.ai/requests/r-1/status"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"video\":{\"url\":\"https://cdn/x.mp4\"}}",
                        MediaType.APPLICATION_JSON));

        HiggsfieldStatus s = client.status("r-1");

        assertThat(s.completed()).isTrue();
        assertThat(s.videoUrl()).isEqualTo("https://cdn/x.mp4");
    }

    @Test
    void nsfwIsARejection() {
        server.expect(requestTo("https://api.higgsfield.ai/requests/r-2/status"))
                .andRespond(withSuccess("{\"status\":\"nsfw\",\"error\":\"moderated\"}", MediaType.APPLICATION_JSON));

        HiggsfieldStatus s = client.status("r-2");

        assertThat(s.rejected()).isTrue();
        assertThat(s.error()).isEqualTo("moderated");
    }

    @Test
    void rateLimitIsRetryableAndBadRequestIsNot() {
        server.expect(requestTo("https://api.higgsfield.ai/kling-video/v2.5-turbo/pro/text-to-video"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.submit("x"))
                .isInstanceOfSatisfying(TrailerStepException.class, e -> assertThat(e.retryable()).isTrue());

        server.reset();
        server.expect(requestTo("https://api.higgsfield.ai/kling-video/v2.5-turbo/pro/text-to-video"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThatThrownBy(() -> client.submit("x"))
                .isInstanceOfSatisfying(TrailerStepException.class, e -> assertThat(e.retryable()).isFalse());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=HiggsfieldClientTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `HiggsfieldClient` does not exist.

- [ ] **Step 3: Implement**

```java
// src/main/java/com/doova/ktab/features/trailer/TrailerStepException.java
package com.doova.ktab.features.trailer;

/** A pipeline step failed. Retryable ones are tried again with backoff; the rest fail the trailer. */
public class TrailerStepException extends RuntimeException {

    private final boolean retryable;

    public TrailerStepException(String message, boolean retryable) {
        this(message, retryable, null);
    }

    public TrailerStepException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldStatus.java
package com.doova.ktab.features.trailer.higgsfield;

public record HiggsfieldStatus(String status, String videoUrl, String error) {

    public boolean completed() {
        return "completed".equals(status);
    }

    public boolean pending() {
        return "queued".equals(status) || "in_progress".equals(status);
    }

    /** failed, nsfw and canceled are terminal and are not charged (Higgsfield billing docs). */
    public boolean rejected() {
        return "failed".equals(status) || "nsfw".equals(status) || "canceled".equals(status);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldClient.java
package com.doova.ktab.features.trailer.higgsfield;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Higgsfield REST: submit → poll /requests/{id}/status. https://docs.higgsfield.ai */
@Component
@Slf4j
public class HiggsfieldClient {

    private final RestClient http;
    private final RestClient downloads;
    private final TrailerProperties.Higgsfield cfg;

    /** Production: a private builder with our own timeouts (doesn't touch Boot's shared RestClient.Builder). */
    @Autowired
    public HiggsfieldClient(TrailerProperties properties) {
        this(RestClient.builder().requestFactory(timeouts(properties.getHiggsfield().getTimeout())), properties);
    }

    /** Tests bind MockRestServiceServer to the builder, so this constructor must not replace its request factory. */
    HiggsfieldClient(RestClient.Builder builder, TrailerProperties properties) {
        this.cfg = properties.getHiggsfield();
        this.http = builder.clone()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Key " + cfg.getApiKeyId() + ":" + cfg.getApiKeySecret())
                .build();
        // CDN downloads must not carry our API key.
        this.downloads = builder.clone().build();
    }

    public String submit(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put("duration", 5);
        body.put("cfg_scale", cfg.getCfgScale());
        body.put("negative_prompt", cfg.getNegativePrompt());
        body.putAll(cfg.getExtraParams());
        JsonNode root = call("submit", () -> http.post().uri(cfg.getVideoEndpoint())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        String id = root == null ? null : root.path("request_id").asText(null);
        if (id == null) {
            throw new TrailerStepException("Higgsfield submit returned no request_id", true);
        }
        return id;
    }

    public HiggsfieldStatus status(String requestId) {
        JsonNode root = call("status", () -> http.get().uri("/requests/{id}/status", requestId)
                .retrieve().body(JsonNode.class));
        if (root == null) {
            throw new TrailerStepException("Higgsfield status returned no body", true);
        }
        String url = root.path("video").path("url").asText(null);
        String error = root.hasNonNull("error") ? root.get("error").asText() : null;
        return new HiggsfieldStatus(root.path("status").asText(""), url, error);
    }

    public void cancel(String requestId) {
        try {
            http.post().uri("/requests/{id}/cancel", requestId).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.info("higgsfield cancel of {} ignored: {}", requestId, e.getMessage()); // only queued requests cancel
        }
    }

    public byte[] download(String url) {
        byte[] bytes = call("download", () -> downloads.get().uri(url).retrieve().body(byte[].class));
        if (bytes == null || bytes.length == 0) {
            throw new TrailerStepException("Higgsfield video download was empty", true);
        }
        return bytes;
    }

    static SimpleClientHttpRequestFactory timeouts(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    private <T> T call(String what, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            boolean retryable = code == 429 || code >= 500;
            throw new TrailerStepException("Higgsfield " + what + " failed: HTTP " + code + " " + e.getResponseBodyAsString(),
                    retryable, e);
        } catch (ResourceAccessException e) {
            throw new TrailerStepException("Higgsfield " + what + " I/O error: " + e.getMessage(), true, e);
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 5: Write the live spike (the go/no-go for D1)**

```java
// src/test/java/com/doova/ktab/features/trailer/higgsfield/HiggsfieldLiveSpikeTest.java
package com.doova.ktab.features.trailer.higgsfield;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Spends real Higgsfield credits (one 5 s shot). Run: HIGGSFIELD_LIVE=true HF_API_KEY_ID=… HF_API_KEY_SECRET=… */
@EnabledIfEnvironmentVariable(named = "HIGGSFIELD_LIVE", matches = "true")
class HiggsfieldLiveSpikeTest {

    @Test
    void rendersOneShotAndSavesItForManualReview() throws Exception {
        TrailerProperties props = new TrailerProperties();
        props.getHiggsfield().setApiKeyId(System.getenv("HF_API_KEY_ID"));
        props.getHiggsfield().setApiKeySecret(System.getenv("HF_API_KEY_SECRET"));
        HiggsfieldClient client = new HiggsfieldClient(RestClient.builder(), props);

        String id = client.submit("Cinematic slow dolly-in over an empty marble chess board on a desk at night, "
                + "warm lamp light, drifting dust, dramatic shadows, photorealistic, no text, no people");
        HiggsfieldStatus s;
        long deadline = System.currentTimeMillis() + 15 * 60_000;
        do {
            Thread.sleep(10_000);
            s = client.status(id);
        } while (s.pending() && System.currentTimeMillis() < deadline);

        assertThat(s.completed()).as("status=%s error=%s", s.status(), s.error()).isTrue();
        Path out = Path.of("target/higgsfield-spike.mp4");
        Files.write(out, client.download(s.videoUrl()));
        System.out.println("Spike video: " + out.toAbsolutePath());
    }
}
```

- [ ] **Step 6: Run the spike and record the result**

Run: `HIGGSFIELD_LIVE=true HF_API_KEY_ID=… HF_API_KEY_SECRET=… mvn -o test -Dtest=HiggsfieldLiveSpikeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, and `target/higgsfield-spike.mp4` exists. Open it and check three things:
1. Is it 16:9? If not, set `ktab.trailer.higgsfield.extra-params.aspect_ratio=16:9`, or change `video-endpoint` to a model whose console schema accepts an aspect ratio, and re-run.
2. Is it about 5 s long?
3. Is there no text on screen?

Write the model choice and the result into the D1 line of this plan.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer src/test/java/com/doova/ktab/features/trailer
git commit -m "feat(trailer): add Higgsfield REST client and live spike"
```

---

### Task 3: ElevenLabs voice-over and music client

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/audio/TrailerAudioClient.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/audio/Voiceover.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/audio/TrailerAudioClientTest.java`

**Interfaces:**
- Consumes: `TrailerProperties.ElevenLabs` and `TrailerStepException`.
- Produces:
  - `Voiceover(byte[] mp3, double seconds)`
  - `TrailerAudioClient(TrailerProperties)` (Spring) and a package-private `TrailerAudioClient(RestClient.Builder, TrailerProperties)` (tests):
    - `Voiceover speak(String text, double speed)`
    - `byte[] music(String prompt, int lengthMs)`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/doova/ktab/features/trailer/audio/TrailerAudioClientTest.java
package com.doova.ktab.features.trailer.audio;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TrailerAudioClientTest {

    private MockRestServiceServer server;
    private TrailerAudioClient client;

    @BeforeEach
    void setUp() {
        TrailerProperties props = new TrailerProperties();
        props.getElevenlabs().setApiKey("xi");
        props.getElevenlabs().setVoiceId("voice-1");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TrailerAudioClient(builder, props);
    }

    @Test
    void speakDecodesAudioAndTakesDurationFromTheLastCharacter() {
        String audio = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        server.expect(requestTo("https://api.elevenlabs.io/v1/text-to-speech/voice-1/with-timestamps?output_format=mp3_44100_128"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("xi-api-key", "xi"))
                .andExpect(jsonPath("$.model_id").value("eleven_multilingual_v2"))
                .andExpect(jsonPath("$.voice_settings.speed").value(1.1))
                .andRespond(withSuccess("{\"audio_base64\":\"" + audio + "\",\"alignment\":{"
                        + "\"characters\":[\"a\",\"b\"],\"character_start_times_seconds\":[0.0,12.0],"
                        + "\"character_end_times_seconds\":[0.5,26.4]}}", MediaType.APPLICATION_JSON));

        Voiceover vo = client.speak("نص", 1.1);

        assertThat(vo.mp3()).containsExactly(1, 2, 3);
        assertThat(vo.seconds()).isEqualTo(26.4);
    }

    @Test
    void musicIsInstrumentalAndThirtySeconds() {
        server.expect(requestTo("https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128"))
                .andExpect(jsonPath("$.force_instrumental").value(true))
                .andExpect(jsonPath("$.music_length_ms").value(30000))
                .andRespond(withSuccess(new byte[]{9, 9}, MediaType.parseMediaType("audio/mpeg")));

        assertThat(client.music("tense cinematic strings", 30_000)).containsExactly(9, 9);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=TrailerAudioClientTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `TrailerAudioClient` does not exist.

- [ ] **Step 3: Implement**

```java
// src/main/java/com/doova/ktab/features/trailer/audio/Voiceover.java
package com.doova.ktab.features.trailer.audio;

public record Voiceover(byte[] mp3, double seconds) {
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/audio/TrailerAudioClient.java
package com.doova.ktab.features.trailer.audio;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;

/** ElevenLabs REST: TTS with timestamps (for the duration) and Music compose. */
@Component
public class TrailerAudioClient {

    private final RestClient http;
    private final TrailerProperties.ElevenLabs cfg;

    @Autowired
    public TrailerAudioClient(TrailerProperties properties) {
        this(RestClient.builder().requestFactory(timeouts(properties.getElevenlabs().getTimeout())), properties);
    }

    /** Tests bind MockRestServiceServer to the builder, so this constructor must not replace its request factory. */
    TrailerAudioClient(RestClient.Builder builder, TrailerProperties properties) {
        this.cfg = properties.getElevenlabs();
        this.http = builder.clone()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("xi-api-key", cfg.getApiKey() == null ? "" : cfg.getApiKey())
                .build();
    }

    public Voiceover speak(String text, double speed) {
        Map<String, Object> body = Map.of(
                "text", text,
                "model_id", cfg.getTtsModelId(),
                "voice_settings", Map.of("stability", 0.55, "similarity_boost", 0.8, "style", 0.35, "speed", speed));
        JsonNode root = call("tts", () -> http.post()
                .uri("/v1/text-to-speech/{voice}/with-timestamps?output_format=mp3_44100_128", cfg.getVoiceId())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        if (root == null || !root.hasNonNull("audio_base64")) {
            throw new TrailerStepException("ElevenLabs TTS returned no audio", true);
        }
        JsonNode ends = root.path("alignment").path("character_end_times_seconds");
        if (!ends.isArray() || ends.isEmpty()) {
            throw new TrailerStepException("ElevenLabs TTS returned no alignment", true);
        }
        double seconds = ends.get(ends.size() - 1).asDouble();
        return new Voiceover(Base64.getDecoder().decode(root.get("audio_base64").asText()), seconds);
    }

    public byte[] music(String prompt, int lengthMs) {
        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "music_length_ms", lengthMs,
                "model_id", cfg.getMusicModelId(),
                "force_instrumental", true);
        byte[] bytes = call("music", () -> http.post().uri("/v1/music?output_format=mp3_44100_128")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(byte[].class));
        if (bytes == null || bytes.length == 0) {
            throw new TrailerStepException("ElevenLabs music returned no audio", true);
        }
        return bytes;
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    private <T> T call(String what, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            throw new TrailerStepException("ElevenLabs " + what + " failed: HTTP " + code + " " + e.getResponseBodyAsString(),
                    code == 429 || code >= 500, e);
        } catch (ResourceAccessException e) {
            throw new TrailerStepException("ElevenLabs " + what + " I/O error: " + e.getMessage(), true, e);
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 2, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/audio src/test/java/com/doova/ktab/features/trailer/audio
git commit -m "feat(trailer): add ElevenLabs voice-over and music client"
```

---

### Task 4: Script writer (Claude) and script rules

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/script/TrailerBrief.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/script/TrailerScript.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/script/TrailerScriptRules.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/script/TrailerClaude.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/script/TrailerScriptWriter.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/script/TrailerScriptRulesTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/script/TrailerScriptWriterTest.java`

**Interfaces:**
- Consumes: `TrailerProperties.Claude` and `TrailerStepException`.
- Produces:
  - `TrailerBrief(String title, String description, String authorName, String language, String excerpt)`
  - `TrailerScript(String narration, List<Shot> shots, String musicPrompt, List<String> realPeopleNamed)` with nested `record Shot(String visualPrompt)`.
  - `TrailerScriptRules.problems(TrailerScript, int shotCount)`, which returns `List<String>`.
  - `TrailerClaude`:
    - `<T> T structured(String system, String user, List<byte[]> jpegFrames, Class<T> type, int maxTokens)`
  - `TrailerScriptWriter(TrailerClaude, TrailerProperties)`:
    - `TrailerScript write(TrailerBrief brief)`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/doova/ktab/features/trailer/script/TrailerScriptRulesTest.java
package com.doova.ktab.features.trailer.script;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TrailerScriptRulesTest {

    static String words(int n) {
        return String.join(" ", IntStream.range(0, n).mapToObj(i -> "كلمة").toList());
    }

    static List<TrailerScript.Shot> shots(String... prompts) {
        return java.util.Arrays.stream(prompts).map(TrailerScript.Shot::new).toList();
    }

    static TrailerScript valid() {
        return new TrailerScript(words(55), shots("storm clouds over a desert", "a chess board, pieces falling",
                "an empty parliament hall", "a lone eagle over mountains", "waves crashing on rocks",
                "dawn light over a city skyline"), "tense cinematic strings, no vocals", List.of("Donald Trump", "ترامب"));
    }

    @Test
    void aValidScriptHasNoProblems() {
        assertThat(TrailerScriptRules.problems(valid(), 6)).isEmpty();
    }

    @Test
    void wrongShotCountAndNarrationLengthAreReported() {
        TrailerScript s = new TrailerScript(words(120), shots("a", "b"), "music", List.of());
        assertThat(TrailerScriptRules.problems(s, 6))
                .anyMatch(p -> p.contains("6 shots"))
                .anyMatch(p -> p.contains("words"));
    }

    @Test
    void shotsThatInviteTextOrNameRealPeopleAreRejected() {
        TrailerScript s = new TrailerScript(words(55), shots("a newspaper headline on a desk", "Donald Trump waving",
                "c", "d", "e", "f"), "music", List.of("Donald Trump"));
        List<String> problems = TrailerScriptRules.problems(s, 6);
        assertThat(problems).anyMatch(p -> p.contains("Shot 1") && p.contains("newspaper"));
        assertThat(problems).anyMatch(p -> p.contains("Shot 2") && p.contains("Donald Trump"));
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/script/TrailerScriptWriterTest.java
package com.doova.ktab.features.trailer.script;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerScriptWriterTest {

    private final TrailerClaude claude = mock(TrailerClaude.class);
    private final TrailerScriptWriter writer = new TrailerScriptWriter(claude, new TrailerProperties());
    private final TrailerBrief brief = new TrailerBrief("ثورة دونالد ترامب", "وصف", "ألكسندر دوغين", "ar", "نص الكتاب");

    @Test
    void asksAgainWithTheProblemsWhenTheFirstScriptBreaksTheRules() {
        TrailerScript bad = new TrailerScript("قصير", List.of(), "m", List.of());
        when(claude.structured(anyString(), anyString(), anyList(), eq(TrailerScript.class), anyInt()))
                .thenReturn(bad, TrailerScriptRulesTest.valid());

        assertThat(writer.write(brief)).isEqualTo(TrailerScriptRulesTest.valid());
        verify(claude).structured(anyString(), contains("Fix these problems"), anyList(), eq(TrailerScript.class), anyInt());
    }

    @Test
    void givesUpAfterTheRewriteStillBreaksTheRules() {
        TrailerScript bad = new TrailerScript("قصير", List.of(), "m", List.of());
        when(claude.structured(anyString(), anyString(), anyList(), eq(TrailerScript.class), anyInt())).thenReturn(bad);

        assertThatThrownBy(() -> writer.write(brief))
                .isInstanceOfSatisfying(TrailerStepException.class, e -> assertThat(e.retryable()).isFalse());
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o test -Dtest='TrailerScript*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `TrailerScript` does not exist.

- [ ] **Step 3: Implement**

```java
// src/main/java/com/doova/ktab/features/trailer/script/TrailerBrief.java
package com.doova.ktab.features.trailer.script;

/** Everything Claude needs about the book; built inside a read-only transaction. */
public record TrailerBrief(String title, String description, String authorName, String language, String excerpt) {
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/script/TrailerScript.java
package com.doova.ktab.features.trailer.script;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record TrailerScript(
        @JsonPropertyDescription("Voice-over narration in the book's language (Modern Standard Arabic for Arabic books), "
                + "45 to 65 words, spoken over about 26 seconds. Plain sentences only: no stage directions, no emojis.")
        String narration,
        @JsonPropertyDescription("Exactly six shots in viewing order; each is one continuous 5-second shot.")
        List<Shot> shots,
        @JsonPropertyDescription("One-line brief in English for an instrumental score, e.g. 'slow-building tense strings, "
                + "deep percussion, no vocals'.")
        String musicPrompt,
        @JsonPropertyDescription("Every real, living or historical person named in the excerpt, both as written in the "
                + "book and in English transliteration. Empty if none.")
        List<String> realPeopleNamed
) {
    public record Shot(
            @JsonPropertyDescription("English prompt for a text-to-video model, at most 60 words: subject, setting, "
                    + "camera movement, light, mood. Symbolic imagery only; no identifiable real people; nothing "
                    + "that carries writing (no signs, screens, books, papers, flags with text).")
            String visualPrompt) {
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/script/TrailerScriptRules.java
package com.doova.ktab.features.trailer.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic checks run on every script before any paid generation (D2, D3). */
public final class TrailerScriptRules {

    static final int MIN_WORDS = 40;
    static final int MAX_WORDS = 70;
    static final int MAX_PROMPT_WORDS = 70;

    /** Things video models render with (garbled) writing on them. */
    static final List<String> TEXT_BEARING = List.of("text", "title", "caption", "subtitle", "letter", "word",
            "writing", "written", "sign", "signage", "poster", "banner", "billboard", "newspaper", "headline",
            "book cover", "logo", "label", "calligraphy", "screen showing", "typography", "graffiti");

    private TrailerScriptRules() {
    }

    public static List<String> problems(TrailerScript s, int shotCount) {
        List<String> problems = new ArrayList<>();
        int words = s.narration() == null ? 0 : s.narration().strip().split("\\s+").length;
        if (words < MIN_WORDS || words > MAX_WORDS) {
            problems.add("The narration has " + words + " words; it must have " + MIN_WORDS + " to " + MAX_WORDS
                    + " so it fits 26 seconds.");
        }
        if (s.musicPrompt() == null || s.musicPrompt().isBlank()) {
            problems.add("The music brief is empty.");
        }
        List<TrailerScript.Shot> shots = s.shots() == null ? List.of() : s.shots();
        if (shots.size() != shotCount) {
            problems.add("There are " + shots.size() + " shots; there must be exactly " + shotCount + " shots.");
        }
        List<String> people = s.realPeopleNamed() == null ? List.of() : s.realPeopleNamed();
        for (int i = 0; i < shots.size(); i++) {
            String prompt = shots.get(i).visualPrompt() == null ? "" : shots.get(i).visualPrompt();
            String lower = prompt.toLowerCase(Locale.ROOT);
            String label = "Shot " + (i + 1);
            if (prompt.isBlank()) {
                problems.add(label + " has no prompt.");
                continue;
            }
            if (prompt.strip().split("\\s+").length > MAX_PROMPT_WORDS) {
                problems.add(label + " is longer than " + MAX_PROMPT_WORDS + " words.");
            }
            for (String t : TEXT_BEARING) {
                if (lower.matches(".*\\b" + java.util.regex.Pattern.quote(t) + "s?\\b.*")) {
                    problems.add(label + " mentions '" + t + "', which makes the model draw writing; show it visually instead.");
                }
            }
            for (String person : people) {
                if (!person.isBlank() && lower.contains(person.toLowerCase(Locale.ROOT))) {
                    problems.add(label + " names the real person '" + person + "'; use symbolic imagery instead.");
                }
            }
        }
        return problems;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/script/TrailerClaude.java
package com.doova.ktab.features.trailer.script;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Claude for the trailer feature. Deliberately not an AnthropicClient bean: storybook injects that type,
 * and a second bean would make its injection ambiguous. SDK usage mirrors storybook's AnthropicLlmGateway.
 */
@Component
public class TrailerClaude {

    private final AnthropicClient client;
    private final String model;

    public TrailerClaude(TrailerProperties properties) {
        TrailerProperties.Claude cfg = properties.getClaude();
        this.model = cfg.getModel();
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(cfg.getApiKey() == null || cfg.getApiKey().isBlank() ? "missing-anthropic-api-key" : cfg.getApiKey())
                .timeout(cfg.getTimeout())
                .maxRetries(2)
                .build();
    }

    public <T> T structured(String system, String user, List<byte[]> jpegFrames, Class<T> type, int maxTokens) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (byte[] jpeg : jpegFrames) {
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(jpeg))
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                            .build())
                    .build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(user).build()));

        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(type)
                .build();

        StructuredMessage<T> message;
        try {
            message = client.messages().create(params);
        } catch (RateLimitException | InternalServerException | AnthropicIoException e) {
            throw new TrailerStepException("Claude call failed transiently: " + e.getMessage(), true, e);
        } catch (RuntimeException e) {
            throw new TrailerStepException("Claude call failed: " + e.getMessage(), false, e);
        }
        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new TrailerStepException("Claude refused the trailer request", false);
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new TrailerStepException("Claude output was truncated at max_tokens", false);
        }
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new TrailerStepException("Claude returned no structured output", true));
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/script/TrailerScriptWriter.java
package com.doova.ktab.features.trailer.script;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TrailerScriptWriter {

    static final String SYSTEM = """
            You are the creative director of 30-second cinematic book trailers for Ktab, an Arabic reading platform.
            You receive a book's metadata and the opening pages. Produce:
            1. A voice-over narration in the book's language (Modern Standard Arabic for Arabic books) that hooks the
               viewer with the book's central question and ends on an invitation to read. It is spoken over about 26
               seconds, so 45 to 65 words. Present the book's arguments as the author's view, never as your own.
            2. Exactly six 5-second shots, in English, for a text-to-video model. Premium documentary style, dynamic
               camera moves, one idea per shot, building to a closing image.
            Hard rules for the shots:
            - Never depict an identifiable real person (politicians, celebrities, the author). Use symbolic imagery:
              landscapes, architecture, silhouettes seen from behind, objects, weather, light.
            - Nothing that carries writing: no signs, screens, papers, books, flags with emblems, maps with labels,
              headlines. The finished video must contain no text of any kind.
            - No violence, gore or sexual content.
            3. A one-line instrumental music brief in English.
            4. The list of real people the excerpt names, so the shots can be checked against it.
            """;

    private final TrailerClaude claude;
    private final TrailerProperties properties;

    public TrailerScript write(TrailerBrief brief) {
        String user = userMessage(brief);
        TrailerScript script = claude.structured(SYSTEM, user, List.of(), TrailerScript.class, 4000);
        List<String> problems = TrailerScriptRules.problems(script, properties.getShotCount());
        if (problems.isEmpty()) {
            return script;
        }
        String retry = user + "\n\nFix these problems in your previous answer and return the full corrected result:\n- "
                + String.join("\n- ", problems);
        script = claude.structured(SYSTEM, retry, List.of(), TrailerScript.class, 4000);
        problems = TrailerScriptRules.problems(script, properties.getShotCount());
        if (!problems.isEmpty()) {
            throw new TrailerStepException("Trailer script still breaks the rules: " + String.join(" ", problems), false);
        }
        return script;
    }

    private static String userMessage(TrailerBrief b) {
        String excerpt = b.excerpt() == null || b.excerpt().isBlank()
                ? "(No page text is available; rely on the title and description.)"
                : b.excerpt();
        return "Title: " + b.title() + "\nAuthor: " + b.authorName() + "\nLanguage: " + b.language()
                + "\nDescription: " + (b.description() == null ? "" : b.description())
                + "\n\n<book_excerpt>\n" + excerpt + "\n</book_excerpt>";
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run the Step 2 command again. Expected: `Tests run: 5, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/script src/test/java/com/doova/ktab/features/trailer/script
git commit -m "feat(trailer): add Claude script writer with no-text and no-real-people rules"
```

---

### Task 5: FFmpeg runner, assembler and frame-text checker

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/media/FfmpegRunner.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/media/TrailerAssembler.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/media/FrameTextVerdict.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/media/FrameTextChecker.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/media/TrailerAssemblerTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/media/FfmpegAssemblyIT.java`

**Interfaces:**
- Consumes: `TrailerProperties`, `TrailerClaude` and `TrailerStepException`.
- Produces:
  - `FfmpegRunner(TrailerProperties)`:
    - `void run(List<String> args)` throws a non-retryable `TrailerStepException` on a non-zero exit or a timeout.
    - `double durationSeconds(Path media)`
  - `TrailerAssembler(TrailerProperties)`:
    - `List<String> frameArgs(Path video, Path outDir)` samples 5 frames at 1 fps into `frame-%02d.jpg`.
    - `List<String> assembleArgs(List<Path> shots, Path voice, Path music, Path out)`
  - `FrameTextVerdict(boolean anyText, String note)`
  - `FrameTextChecker(FfmpegRunner, TrailerAssembler, TrailerClaude)`:
    - `FrameTextVerdict check(byte[] mp4)`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/doova/ktab/features/trailer/media/TrailerAssemblerTest.java
package com.doova.ktab.features.trailer.media;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TrailerAssemblerTest {

    private final TrailerAssembler assembler = new TrailerAssembler(new TrailerProperties());

    @Test
    void assemblyCropsToExact1080p16x9TrimsToThirtySecondsAndDucksMusicUnderTheVoice() {
        List<Path> shots = IntStream.range(0, 6).mapToObj(i -> Path.of("s" + i + ".mp4")).toList();

        List<String> args = assembler.assembleArgs(shots, Path.of("vo.mp3"), Path.of("mu.mp3"), Path.of("out.mp4"));
        String filter = args.get(args.indexOf("-filter_complex") + 1);

        assertThat(args).containsSubsequence("-i", "s0.mp4").containsSubsequence("-i", "vo.mp3")
                .containsSubsequence("-i", "mu.mp3").containsSubsequence("-t", "30");
        assertThat(filter).contains("scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080")
                .contains("concat=n=6:v=1:a=0")
                .contains("adelay=1000|1000")
                .contains("sidechaincompress")
                .contains("loudnorm=I=-14");
        assertThat(args).containsSubsequence("-c:v", "libx264").containsSubsequence("-movflags", "+faststart")
                .endsWith("out.mp4");
    }

    @Test
    void frameSamplingTakesFiveFrames() {
        List<String> args = assembler.frameArgs(Path.of("shot.mp4"), Path.of("frames"));
        assertThat(args).containsSubsequence("-frames:v", "5");
        assertThat(args.get(args.size() - 1)).endsWith("frame-%02d.jpg");
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/media/FfmpegAssemblyIT.java
package com.doova.ktab.features.trailer.media;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Needs ffmpeg/ffprobe on PATH (the Docker image has them). Run: TRAILER_FFMPEG_TESTS=true */
@EnabledIfEnvironmentVariable(named = "TRAILER_FFMPEG_TESTS", matches = "true")
class FfmpegAssemblyIT {

    @Test
    void sixSquareShotsBecomeAThirtySecond1080pTrailer(@TempDir Path dir) {
        TrailerProperties props = new TrailerProperties();
        FfmpegRunner ffmpeg = new FfmpegRunner(props);
        TrailerAssembler assembler = new TrailerAssembler(props);

        List<Path> shots = new ArrayList<>();
        for (int i = 0; i < 6; i++) { // square and slightly short on purpose: crop + tpad must fix both
            Path shot = dir.resolve("s" + i + ".mp4");
            ffmpeg.run(List.of("-y", "-f", "lavfi", "-i", "testsrc=size=1024x1024:rate=24:duration=4.8",
                    "-pix_fmt", "yuv420p", shot.toString()));
            shots.add(shot);
        }
        Path vo = dir.resolve("vo.mp3");
        ffmpeg.run(List.of("-y", "-f", "lavfi", "-i", "sine=frequency=300:duration=26", vo.toString()));
        Path mu = dir.resolve("mu.mp3");
        ffmpeg.run(List.of("-y", "-f", "lavfi", "-i", "sine=frequency=110:duration=30", mu.toString()));
        Path out = dir.resolve("trailer.mp4");

        ffmpeg.run(assembler.assembleArgs(shots, vo, mu, out));

        assertThat(ffmpeg.durationSeconds(out)).isCloseTo(30.0, within(0.5));
    }
}
```

- [ ] **Step 2: Run the unit test to verify it fails**

Run: `mvn -o test -Dtest=TrailerAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `TrailerAssembler` does not exist.

- [ ] **Step 3: Implement**

```java
// src/main/java/com/doova/ktab/features/trailer/media/FfmpegRunner.java
package com.doova.ktab.features.trailer.media;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegRunner {

    private final TrailerProperties.Ffmpeg cfg;

    public FfmpegRunner(TrailerProperties properties) {
        this.cfg = properties.getFfmpeg();
    }

    public void run(List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(cfg.getPath());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.addAll(args);
        exec(command);
    }

    public double durationSeconds(Path media) {
        String out = exec(List.of(cfg.getFfprobePath(), "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", media.toString())).strip();
        try {
            return Double.parseDouble(out);
        } catch (NumberFormatException e) {
            throw new TrailerStepException("ffprobe returned no duration for " + media + ": " + out, false);
        }
    }

    private String exec(List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new TrailerStepException("Cannot start " + command.get(0) + " (is ffmpeg installed?)", false, e);
        }
        // Drain output on another thread so a chatty ffmpeg never blocks on a full pipe.
        StringBuilder output = new StringBuilder();
        Thread drain = Thread.ofVirtual().start(() -> {
            try (InputStream in = process.getInputStream()) {
                output.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // process died; exit code below reports it
            }
        });
        try {
            if (!process.waitFor(cfg.getTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new TrailerStepException(command.get(0) + " timed out after " + cfg.getTimeout(), false);
            }
            drain.join(5_000);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new TrailerStepException(command.get(0) + " was interrupted", true, e);
        }
        if (process.exitValue() != 0) {
            String tail = output.length() > 2000 ? output.substring(output.length() - 2000) : output.toString();
            throw new TrailerStepException(command.get(0) + " exited " + process.exitValue() + ": " + tail, false);
        }
        return output.toString();
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/media/TrailerAssembler.java
package com.doova.ktab.features.trailer.media;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds ffmpeg argument lists; pure, so the filter graph is unit-tested without ffmpeg installed. */
@Component
public class TrailerAssembler {

    private final TrailerProperties properties;

    public TrailerAssembler(TrailerProperties properties) {
        this.properties = properties;
    }

    public List<String> frameArgs(Path video, Path outDir) {
        return List.of("-y", "-i", video.toString(), "-vf", "fps=1,scale=768:-2", "-frames:v", "5",
                outDir.resolve("frame-%02d.jpg").toString());
    }

    public List<String> assembleArgs(List<Path> shots, Path voice, Path music, Path out) {
        int n = shots.size();
        int seconds = properties.getShotSeconds();
        String total = String.valueOf((int) properties.getTargetSeconds());
        List<String> args = new ArrayList<>(List.of("-y"));
        for (Path shot : shots) {
            args.add("-i");
            args.add(shot.toString());
        }
        args.addAll(List.of("-i", voice.toString(), "-i", music.toString()));

        StringBuilder f = new StringBuilder();
        for (int i = 0; i < n; i++) {
            // D1: whatever ratio the model returns, crop to exact 1920x1080; pad short shots by cloning the last frame.
            f.append("[").append(i).append(":v]")
                    .append("scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,fps=30,setsar=1,")
                    .append("tpad=stop_mode=clone:stop_duration=1,trim=duration=").append(seconds)
                    .append(",setpts=PTS-STARTPTS[v").append(i).append("];");
        }
        for (int i = 0; i < n; i++) {
            f.append("[v").append(i).append("]");
        }
        f.append("concat=n=").append(n).append(":v=1:a=0,")
                .append("fade=t=in:st=0:d=0.5,fade=t=out:st=").append(Integer.parseInt(total) - 1).append(":d=1[vout];");
        // D5: voice at +1.0 s; music at 25%, faded, ducked under the voice; loudness to -14 LUFS.
        f.append("[").append(n).append(":a]aresample=48000,adelay=1000|1000,asplit=2[vo1][vo2];")
                .append("[").append(n + 1).append(":a]aresample=48000,atrim=0:").append(total)
                .append(",volume=0.25,afade=t=in:st=0:d=1,afade=t=out:st=").append(Integer.parseInt(total) - 2)
                .append(":d=2[mu];")
                .append("[mu][vo1]sidechaincompress=threshold=0.05:ratio=8:attack=20:release=400[duck];")
                .append("[duck][vo2]amix=inputs=2:duration=longest:normalize=0,")
                .append("loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000,apad,atrim=0:").append(total).append("[aout]");

        args.addAll(List.of("-filter_complex", f.toString(), "-map", "[vout]", "-map", "[aout]",
                "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-r", "30",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-t", total, "-movflags", "+faststart",
                out.toString()));
        return args;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/media/FrameTextVerdict.java
package com.doova.ktab.features.trailer.media;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record FrameTextVerdict(
        @JsonPropertyDescription("True if ANY frame shows letters, words, numbers, subtitles, logos, signage, "
                + "calligraphy or writing-like marks in any script, even blurred or garbled.")
        boolean anyText,
        @JsonPropertyDescription("One short sentence describing where the text is; empty if none.")
        String note) {
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/media/FrameTextChecker.java
package com.doova.ktab.features.trailer.media;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.script.TrailerClaude;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** D2 layer 3: Claude looks at sampled frames of every shot for on-screen text. */
@Component
@RequiredArgsConstructor
public class FrameTextChecker {

    static final String SYSTEM = "You inspect frames from a video shot. Report whether any visible text appears. "
            + "Count garbled pseudo-letters, subtitles, logos, watermarks, signage, and writing on objects as text.";

    private final FfmpegRunner ffmpeg;
    private final TrailerAssembler assembler;
    private final TrailerClaude claude;

    public FrameTextVerdict check(byte[] mp4) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("trailer-frames-");
            Path video = dir.resolve("shot.mp4");
            Files.write(video, mp4);
            ffmpeg.run(assembler.frameArgs(video, dir));
            List<byte[]> frames = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(p -> p.getFileName().toString().startsWith("frame-")).sorted().toList()) {
                    frames.add(Files.readAllBytes(p));
                }
            }
            if (frames.isEmpty()) {
                throw new TrailerStepException("No frames could be extracted from the shot", false);
            }
            return claude.structured(SYSTEM, "Here are " + frames.size() + " frames sampled one second apart.",
                    frames, FrameTextVerdict.class, 500);
        } catch (IOException e) {
            throw new TrailerStepException("Frame check I/O failed: " + e.getMessage(), true, e);
        } finally {
            if (dir != null) {
                FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -o test -Dtest=TrailerAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 2, Failures: 0`.

On a machine with ffmpeg (Windows: `winget install Gyan.FFmpeg`), run:
`TRAILER_FFMPEG_TESTS=true mvn -o verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FfmpegAssemblyIT`
Expected: `Tests run: 1, Failures: 0`, and the output duration is 30.0 ± 0.5 s.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/media src/test/java/com/doova/ktab/features/trailer/media
git commit -m "feat(trailer): add ffmpeg assembly (1080p 16:9, 30s, ducked mix) and frame text check"
```

---

### Task 6: Pipeline, asset store, claimer and worker

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerAssetStore.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerBookReader.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerPipeline.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerClaimer.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerWorker.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/config/TrailerConfig.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerPipelineTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerClaimerIT.java`

**Interfaces:**
- Consumes: everything from Tasks 1–5, plus `BookRepository` and `BookPageRepository.findByBookIdAndPageNumberBetweenOrderByPageNumberAsc(Long, int, int)`.
- Produces:
  - `TrailerAssetStore`:
    - `static String prefix(long bookId, long trailerId)`
    - `put(String key, byte[], String type)`
    - `putFile(String key, Path, String type)`
    - `byte[] get(String key)`
    - `boolean exists(String key)`
    - `void deletePrefix(String prefix)`
  - `TrailerBookReader.brief(Long bookId, int pages, int maxChars)`, which returns a `TrailerBrief`.
  - `TrailerPipeline.advance(Long trailerId)`, which returns the `Instant` at which the trailer should run again.
  - `TrailerClaimer`:
    - `List<Long> claim(String worker, int limit)`
    - `void release(Long id, Instant nextRunAt)`
    - `void retryLater(Long id, String reason)`
    - `void fail(Long id, String reason)`
  - `TrailerWorker.tick()`

- [ ] **Step 1: Write the failing pipeline test**

```java
// src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerPipelineTest.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.audio.TrailerAudioClient;
import com.doova.ktab.features.trailer.audio.Voiceover;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.ShotStatus;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.higgsfield.HiggsfieldClient;
import com.doova.ktab.features.trailer.higgsfield.HiggsfieldStatus;
import com.doova.ktab.features.trailer.media.FfmpegRunner;
import com.doova.ktab.features.trailer.media.FrameTextChecker;
import com.doova.ktab.features.trailer.media.FrameTextVerdict;
import com.doova.ktab.features.trailer.media.TrailerAssembler;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.model.BookTrailerShot;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.features.trailer.repository.BookTrailerShotRepository;
import com.doova.ktab.features.trailer.script.TrailerBrief;
import com.doova.ktab.features.trailer.script.TrailerScript;
import com.doova.ktab.features.trailer.script.TrailerScriptWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerPipelineTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final BookTrailerShotRepository shots = mock(BookTrailerShotRepository.class);
    final TrailerBookReader reader = mock(TrailerBookReader.class);
    final TrailerScriptWriter writer = mock(TrailerScriptWriter.class);
    final TrailerAudioClient audio = mock(TrailerAudioClient.class);
    final HiggsfieldClient higgsfield = mock(HiggsfieldClient.class);
    final FrameTextChecker frames = mock(FrameTextChecker.class);
    final FfmpegRunner ffmpeg = mock(FfmpegRunner.class);
    final TrailerAssetStore store = mock(TrailerAssetStore.class);
    final TrailerProperties props = new TrailerProperties();
    final ObjectMapper json = new ObjectMapper();
    final TrailerPipeline pipeline = new TrailerPipeline(trailers, shots, reader, writer, audio, higgsfield, frames,
            ffmpeg, new TrailerAssembler(props), store, props, json);

    final BookTrailer trailer = new BookTrailer();

    static TrailerScript script() {
        return new TrailerScript("سرد", IntStream.range(0, 6).mapToObj(i -> new TrailerScript.Shot("shot " + i)).toList(),
                "strings", List.of());
    }

    @BeforeEach
    void setUp() throws Exception {
        trailer.setId(7L);
        trailer.setBookId(3L);
        when(trailers.findById(7L)).thenReturn(Optional.of(trailer));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(shots.save(any())).thenAnswer(i -> i.getArgument(0));
        trailer.setScriptJson(json.writeValueAsString(script()));
    }

    @Test
    void scriptFallsBackToTitleAndDescriptionWhenThereIsNoPageText() {
        trailer.setStatus(TrailerStatus.QUEUED);
        trailer.setScriptJson(null);
        TrailerBrief noText = new TrailerBrief("عنوان", "وصف", "مؤلف", "ar", "");
        when(reader.brief(eq(3L), anyInt(), anyInt())).thenReturn(noText);
        when(writer.write(noText)).thenReturn(script());

        pipeline.advance(7L);

        assertThat(trailer.getStatus()).isEqualTo(TrailerStatus.VOICING);
        verify(shots, times(6)).save(any(BookTrailerShot.class));
    }

    @Test
    void longNarrationIsSpedUpOnce() {
        trailer.setStatus(TrailerStatus.VOICING);
        when(audio.speak(anyString(), eq(1.0))).thenReturn(new Voiceover(new byte[]{1}, 29.0));
        when(audio.speak(anyString(), doubleThat(s -> s > 1.0 && s <= 1.15))).thenReturn(new Voiceover(new byte[]{2}, 26.9));
        when(audio.music(anyString(), eq(30_000))).thenReturn(new byte[]{3});

        pipeline.advance(7L);

        assertThat(trailer.getStatus()).isEqualTo(TrailerStatus.SHOOTING);
        verify(store).put(endsWith("voiceover.mp3"), eq(new byte[]{2}), eq("audio/mpeg"));
    }

    @Test
    void narrationStillTooLongFailsTheTrailer() {
        trailer.setStatus(TrailerStatus.VOICING);
        when(audio.speak(anyString(), anyDouble())).thenReturn(new Voiceover(new byte[]{1}, 40.0));

        assertThatThrownBy(() -> pipeline.advance(7L))
                .isInstanceOfSatisfying(TrailerStepException.class, e -> assertThat(e.retryable()).isFalse());
    }

    private List<BookTrailerShot> sixShots(ShotStatus status) {
        List<BookTrailerShot> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            BookTrailerShot s = new BookTrailerShot(7L, i, "shot " + i);
            s.setStatus(status);
            s.setRequestId(status == ShotStatus.SUBMITTED ? "r" + i : null);
            s.setVideoKey(status == ShotStatus.PASSED ? "k" + i : null);
            list.add(s);
        }
        return list;
    }

    @Test
    void aShotWithTextOnScreenIsRenderedAgain() {
        trailer.setStatus(TrailerStatus.SHOOTING);
        List<BookTrailerShot> list = sixShots(ShotStatus.PASSED);
        BookTrailerShot first = list.get(0);
        first.setStatus(ShotStatus.SUBMITTED);
        first.setRequestId("r0");
        when(shots.findByTrailerIdOrderByShotIndexAsc(7L)).thenReturn(list);
        when(higgsfield.status("r0")).thenReturn(new HiggsfieldStatus("completed", "https://cdn/0.mp4", null));
        when(higgsfield.download("https://cdn/0.mp4")).thenReturn(new byte[]{5});
        when(frames.check(any())).thenReturn(new FrameTextVerdict(true, "garbled letters on a wall"));

        pipeline.advance(7L);

        assertThat(first.getStatus()).isEqualTo(ShotStatus.PENDING);
        assertThat(first.getAttempt()).isEqualTo(2);
        assertThat(first.getLastError()).contains("garbled letters");
        assertThat(trailer.getStatus()).isEqualTo(TrailerStatus.SHOOTING);
    }

    @Test
    void nsfwShotIsRetriedThenFailsTheTrailer() {
        trailer.setStatus(TrailerStatus.SHOOTING);
        List<BookTrailerShot> list = sixShots(ShotStatus.PASSED);
        BookTrailerShot first = list.get(0);
        first.setStatus(ShotStatus.SUBMITTED);
        first.setRequestId("r0");
        first.setAttempt(3); // last allowed attempt
        when(shots.findByTrailerIdOrderByShotIndexAsc(7L)).thenReturn(list);
        when(higgsfield.status("r0")).thenReturn(new HiggsfieldStatus("nsfw", null, "moderated"));

        assertThatThrownBy(() -> pipeline.advance(7L))
                .isInstanceOfSatisfying(TrailerStepException.class, e -> {
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.getMessage()).contains("Shot 1").contains("nsfw");
                });
    }

    @Test
    void pendingShotsAreSubmittedWithTheSafetySuffixUnderTheGlobalCap() {
        trailer.setStatus(TrailerStatus.SHOOTING);
        when(shots.findByTrailerIdOrderByShotIndexAsc(7L)).thenReturn(sixShots(ShotStatus.PENDING));
        when(shots.countByStatus(ShotStatus.SUBMITTED)).thenReturn(4L); // cap is 6 → only 2 may start
        when(higgsfield.submit(anyString())).thenReturn("new");

        pipeline.advance(7L);

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(higgsfield, times(2)).submit(prompts.capture());
        assertThat(prompts.getValue()).contains("no text");
    }

    @Test
    void allPassedShotsMoveToAssembling() {
        trailer.setStatus(TrailerStatus.SHOOTING);
        when(shots.findByTrailerIdOrderByShotIndexAsc(7L)).thenReturn(sixShots(ShotStatus.PASSED));

        pipeline.advance(7L);

        assertThat(trailer.getStatus()).isEqualTo(TrailerStatus.ASSEMBLING);
    }

    @Test
    void cancelledTrailerIsNeverAdvanced() {
        trailer.setStatus(TrailerStatus.CANCELLED);

        pipeline.advance(7L);

        verifyNoInteractions(writer, audio, higgsfield, ffmpeg);
        verify(trailers, never()).save(any());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=TrailerPipelineTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `TrailerPipeline` does not exist.

- [ ] **Step 3: Implement the store, book reader and pipeline**

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerAssetStore.java
package com.doova.ktab.features.trailer.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Path;
import java.util.List;

/** R2 with deterministic keys, so a retried step finds what it already paid for (D7). */
@Component
public class TrailerAssetStore {

    private final S3Client s3;
    private final String bucket;

    public TrailerAssetStore(S3Client s3, @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public static String prefix(long bookId, long trailerId) {
        return "trailers/" + bookId + "/" + trailerId + "/";
    }

    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    public void putFile(String key, Path file, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromFile(file));
    }

    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
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
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerBookReader.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.script.TrailerBrief;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.stream.Collectors;

/** Reads lazy book relations inside a transaction; the worker thread has none of its own. */
@Component
@RequiredArgsConstructor
public class TrailerBookReader {

    private final BookRepository books;
    private final BookPageRepository pages;

    @Transactional(readOnly = true)
    public TrailerBrief brief(Long bookId, int pageCount, int maxChars) {
        Book book = books.findById(bookId).orElseThrow();
        String excerpt = pages.findByBookIdAndPageNumberBetweenOrderByPageNumberAsc(bookId, 1, pageCount).stream()
                .map(BookPage::getMarkdownContent)
                .filter(Objects::nonNull) // DIGITAL-route or pending-OCR books have no page text (Review Focus 1)
                .collect(Collectors.joining("\n\n"));
        if (excerpt.length() > maxChars) {
            excerpt = excerpt.substring(0, maxChars);
        }
        return new TrailerBrief(book.getTitle(), book.getDescription(), authorName(book),
                book.getLanguage() == null ? "ar" : book.getLanguage(), excerpt);
    }

    private static String authorName(Book book) {
        if (book.getCustomAuthorName() != null && !book.getCustomAuthorName().isBlank()) {
            return book.getCustomAuthorName();
        }
        User author = book.getAuthor();
        if (author == null) {
            return "";
        }
        return (author.getFirstName() + " " + (author.getLastName() == null ? "" : author.getLastName())).strip();
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerPipeline.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.audio.TrailerAudioClient;
import com.doova.ktab.features.trailer.audio.Voiceover;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.ShotStatus;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.higgsfield.HiggsfieldClient;
import com.doova.ktab.features.trailer.higgsfield.HiggsfieldStatus;
import com.doova.ktab.features.trailer.media.FfmpegRunner;
import com.doova.ktab.features.trailer.media.FrameTextChecker;
import com.doova.ktab.features.trailer.media.FrameTextVerdict;
import com.doova.ktab.features.trailer.media.TrailerAssembler;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.model.BookTrailerShot;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.features.trailer.repository.BookTrailerShotRepository;
import com.doova.ktab.features.trailer.script.TrailerScript;
import com.doova.ktab.features.trailer.script.TrailerScriptWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * One step per call. External calls happen outside transactions; each save is its own short transaction and
 * @Version rejects a save that races a user's cancel (Review Focus 3).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerPipeline {

    static final String SAFETY_SUFFIX = " Cinematic, photorealistic, 16:9 widescreen. Absolutely no text, no letters, "
            + "no subtitles, no logos, no signage. No identifiable real people.";
    static final String RETRY_SUFFIX = " Keep every surface plain and free of any writing or symbols.";

    private final BookTrailerRepository trailers;
    private final BookTrailerShotRepository shots;
    private final TrailerBookReader reader;
    private final TrailerScriptWriter writer;
    private final TrailerAudioClient audio;
    private final HiggsfieldClient higgsfield;
    private final FrameTextChecker frameChecker;
    private final FfmpegRunner ffmpeg;
    private final TrailerAssembler assembler;
    private final TrailerAssetStore store;
    private final TrailerProperties properties;
    private final ObjectMapper json;

    public Instant advance(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        return switch (t.getStatus()) {
            case QUEUED -> script(t);
            case VOICING -> voice(t);
            case SHOOTING -> shoot(t);
            case ASSEMBLING -> assemble(t);
            case READY, FAILED, CANCELLED -> Instant.now().plus(3650, ChronoUnit.DAYS);
        };
    }

    private Instant script(BookTrailer t) {
        if (t.getScriptJson() == null) {
            TrailerScript s = writer.write(reader.brief(t.getBookId(), properties.getExcerptPages(),
                    properties.getExcerptMaxChars()));
            t.setScriptJson(write(s));
        }
        if (shots.findByTrailerIdOrderByShotIndexAsc(t.getId()).isEmpty()) {
            List<TrailerScript.Shot> planned = read(t).shots();
            for (int i = 0; i < planned.size(); i++) {
                shots.save(new BookTrailerShot(t.getId(), i, planned.get(i).visualPrompt()));
            }
        }
        t.setStatus(TrailerStatus.VOICING);
        trailers.save(t);
        return Instant.now();
    }

    private Instant voice(BookTrailer t) {
        TrailerScript s = read(t);
        String prefix = TrailerAssetStore.prefix(t.getBookId(), t.getId());
        String voKey = prefix + "voiceover.mp3";
        if (!store.exists(voKey)) {
            double max = properties.getMaxNarrationSeconds();
            Voiceover vo = audio.speak(s.narration(), 1.0);
            if (vo.seconds() > max) {
                double speed = Math.min(properties.getElevenlabs().getMaxSpeed(), vo.seconds() / max + 0.02);
                vo = audio.speak(s.narration(), speed);
                if (vo.seconds() > max) {
                    throw new TrailerStepException(String.format("Narration runs %.1fs, over the %.1fs limit even at "
                            + "speed %.2f", vo.seconds(), max, speed), false);
                }
            }
            store.put(voKey, vo.mp3(), "audio/mpeg");
            t.setVoiceoverSeconds(BigDecimal.valueOf(vo.seconds()).setScale(2, RoundingMode.HALF_UP));
        }
        String muKey = prefix + "music.mp3";
        if (!store.exists(muKey)) {
            store.put(muKey, audio.music(s.musicPrompt(), (int) (properties.getTargetSeconds() * 1000)), "audio/mpeg");
        }
        t.setVoiceoverKey(voKey);
        t.setMusicKey(muKey);
        t.setStatus(TrailerStatus.SHOOTING);
        trailers.save(t);
        return Instant.now();
    }

    private Instant shoot(BookTrailer t) {
        List<BookTrailerShot> list = shots.findByTrailerIdOrderByShotIndexAsc(t.getId());
        long inFlight = shots.countByStatus(ShotStatus.SUBMITTED);
        for (BookTrailerShot shot : list) {
            if (shot.getStatus() == ShotStatus.PENDING && inFlight < properties.getHiggsfield().getMaxInFlight()) {
                String prompt = shot.getPrompt() + (shot.getAttempt() > 1 ? RETRY_SUFFIX : "") + SAFETY_SUFFIX;
                shot.setRequestId(higgsfield.submit(prompt));
                shot.setStatus(ShotStatus.SUBMITTED);
                shots.save(shot);
                inFlight++;
            } else if (shot.getStatus() == ShotStatus.SUBMITTED) {
                poll(t, shot);
            }
        }
        if (list.stream().allMatch(s -> s.getStatus() == ShotStatus.PASSED)) {
            t.setStatus(TrailerStatus.ASSEMBLING);
            trailers.save(t);
            return Instant.now();
        }
        return Instant.now().plus(properties.getWorker().getPollInterval());
    }

    private void poll(BookTrailer t, BookTrailerShot shot) {
        HiggsfieldStatus st = higgsfield.status(shot.getRequestId());
        if (st.pending()) {
            return;
        }
        if (st.rejected()) {
            retryShot(shot, st.status() + (st.error() == null ? "" : ": " + st.error()));
            return;
        }
        if (st.completed()) {
            byte[] video = higgsfield.download(st.videoUrl()); // URLs expire after ~7 days: copy now
            String key = TrailerAssetStore.prefix(t.getBookId(), t.getId())
                    + "shots/" + shot.getShotIndex() + "-a" + shot.getAttempt() + ".mp4";
            store.put(key, video, "video/mp4");
            FrameTextVerdict verdict = frameChecker.check(video);
            if (verdict.anyText()) {
                retryShot(shot, "text on screen: " + verdict.note());
                return;
            }
            shot.setVideoKey(key);
            shot.setStatus(ShotStatus.PASSED);
            shots.save(shot);
        }
    }

    private void retryShot(BookTrailerShot shot, String reason) {
        if (shot.getAttempt() >= properties.getMaxShotAttempts()) {
            throw new TrailerStepException("Shot " + (shot.getShotIndex() + 1) + " failed after "
                    + shot.getAttempt() + " attempts: " + reason, false);
        }
        log.info("trailer shot {} attempt {} rejected: {}", shot.getId(), shot.getAttempt(), reason);
        shot.setAttempt(shot.getAttempt() + 1);
        shot.setRequestId(null);
        shot.setLastError(reason);
        shot.setStatus(ShotStatus.PENDING);
        shots.save(shot);
    }

    private Instant assemble(BookTrailer t) {
        String key = TrailerAssetStore.prefix(t.getBookId(), t.getId()) + "trailer.mp4";
        if (!store.exists(key)) {
            Path dir = null;
            try {
                dir = Files.createTempDirectory("trailer-" + t.getId() + "-");
                List<Path> clips = new ArrayList<>();
                for (BookTrailerShot shot : shots.findByTrailerIdOrderByShotIndexAsc(t.getId())) {
                    Path clip = dir.resolve("s" + shot.getShotIndex() + ".mp4");
                    Files.write(clip, store.get(shot.getVideoKey()));
                    clips.add(clip);
                }
                Path voice = Files.write(dir.resolve("vo.mp3"), store.get(t.getVoiceoverKey()));
                Path music = Files.write(dir.resolve("mu.mp3"), store.get(t.getMusicKey()));
                Path out = dir.resolve("trailer.mp4");
                ffmpeg.run(assembler.assembleArgs(clips, voice, music, out));
                double seconds = ffmpeg.durationSeconds(out);
                if (Math.abs(seconds - properties.getTargetSeconds()) > 0.5) {
                    throw new TrailerStepException(String.format("Assembled trailer is %.2fs, not %.0fs", seconds,
                            properties.getTargetSeconds()), false);
                }
                store.putFile(key, out, "video/mp4");
            } catch (IOException e) {
                throw new TrailerStepException("Assembly I/O failed: " + e.getMessage(), true, e);
            } finally {
                if (dir != null) {
                    FileSystemUtils.deleteRecursively(dir.toFile());
                }
            }
        }
        t.setVideoKey(key);
        t.setStatus(TrailerStatus.READY);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
        return Instant.now().plus(3650, ChronoUnit.DAYS);
    }

    private TrailerScript read(BookTrailer t) {
        try {
            return json.readValue(t.getScriptJson(), TrailerScript.class);
        } catch (JsonProcessingException e) {
            throw new TrailerStepException("Stored trailer script is unreadable", false, e);
        }
    }

    private String write(TrailerScript s) {
        try {
            return json.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new TrailerStepException("Cannot serialize trailer script", false, e);
        }
    }
}
```

- [ ] **Step 4: Run the pipeline test to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 8, Failures: 0`.

- [ ] **Step 5: Write the failing claimer IT**

```java
// src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerClaimerIT.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.features.trailer.support.TrailerFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// TrailerConfig registers TrailerProperties; its scheduling part stays off because ktab.trailer.enabled is unset.
@Import({TrailerClaimer.class, com.doova.ktab.features.trailer.config.TrailerConfig.class})
class TrailerClaimerIT extends StorybookJpaIT {

    @Autowired TrailerClaimer claimer;
    @Autowired BookTrailerRepository trailers;

    private BookTrailer queued() {
        BookTrailer t = new BookTrailer();
        t.setBookId(TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "a-" + System.nanoTime() + "@x.com")).getId());
        t.setStatus(TrailerStatus.QUEUED);
        return trailers.saveAndFlush(t);
    }

    @Test
    void aLeasedTrailerIsNotClaimedAgainUntilReleased() {
        BookTrailer t = queued();

        List<Long> first = claimer.claim("w1", 10);
        List<Long> second = claimer.claim("w2", 10);

        assertThat(first).contains(t.getId());
        assertThat(second).doesNotContain(t.getId());

        claimer.release(t.getId(), java.time.Instant.now().minusSeconds(1));
        assertThat(claimer.claim("w2", 10)).contains(t.getId());
    }

    @Test
    void failMarksTheTrailerFailedWithTheReason() {
        BookTrailer t = queued();

        claimer.fail(t.getId(), "Shot 1 failed after 3 attempts: nsfw");

        BookTrailer reloaded = trailers.findById(t.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(reloaded.getError()).contains("nsfw");
        assertThat(reloaded.getFinishedAt()).isNotNull();
    }
}
```

- [ ] **Step 6: Implement the claimer, worker and config**

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerClaimer.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class TrailerClaimer {

    private final BookTrailerRepository trailers;
    private final TrailerProperties properties;

    @Transactional
    public List<Long> claim(String worker, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Long> ids = trailers.lockDueIds(limit);
        if (!ids.isEmpty()) {
            trailers.lease(ids, worker, Instant.now().plus(properties.getWorker().getLease()));
        }
        return ids;
    }

    @Transactional
    public void release(Long id, Instant nextRunAt) {
        trailers.findById(id).ifPresent(t -> {
            t.setLockedBy(null);
            t.setLockedUntil(null);
            t.setNextRunAt(nextRunAt);
            t.setAttempts(0);
        });
    }

    @Transactional
    public void retryLater(Long id, String reason) {
        trailers.findById(id).ifPresent(t -> {
            int attempts = t.getAttempts() + 1;
            if (attempts >= properties.getWorker().getMaxStepAttempts()) {
                failNow(t, "Gave up after " + attempts + " attempts: " + reason);
                return;
            }
            long backoff = Math.min(600, (long) Math.pow(2, attempts) * 15) + ThreadLocalRandom.current().nextLong(10);
            t.setAttempts(attempts);
            t.setError(reason);
            t.setNextRunAt(Instant.now().plus(Duration.ofSeconds(backoff)));
            t.setLockedBy(null);
            t.setLockedUntil(null);
        });
    }

    @Transactional
    public void fail(Long id, String reason) {
        trailers.findById(id).ifPresent(t -> failNow(t, reason));
    }

    private static void failNow(BookTrailer t, String reason) {
        if (!t.getStatus().isActive()) {
            return; // already cancelled/finished by someone else
        }
        t.setStatus(TrailerStatus.FAILED);
        t.setError(reason);
        t.setFinishedAt(Instant.now());
        t.setLockedBy(null);
        t.setLockedUntil(null);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerWorker.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.TrailerStepException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class TrailerWorker {

    private final TrailerClaimer claimer;
    private final TrailerPipeline pipeline;
    private final TaskExecutor executor;
    private final TrailerProperties properties;
    private final String workerId = "trailer-" + UUID.randomUUID().toString().substring(0, 8);
    private final AtomicInteger inFlight = new AtomicInteger();

    public TrailerWorker(TrailerClaimer claimer, TrailerPipeline pipeline,
                         @Qualifier("trailerExecutor") TaskExecutor executor, TrailerProperties properties) {
        this.claimer = claimer;
        this.pipeline = pipeline;
        this.executor = executor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ktab.trailer.worker.tick:5s}")
    public void tick() {
        int free = properties.getWorker().getConcurrency() - inFlight.get();
        List<Long> ids = claimer.claim(workerId, free);
        for (Long id : ids) {
            inFlight.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        runOne(id);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            } catch (RuntimeException rejected) {
                inFlight.decrementAndGet();
                claimer.release(id, Instant.now().plusSeconds(5));
            }
        }
    }

    void runOne(Long id) {
        try {
            claimer.release(id, pipeline.advance(id));
        } catch (TrailerStepException e) {
            log.warn("trailer {} step failed (retryable={}): {}", id, e.retryable(), e.getMessage());
            if (e.retryable()) {
                claimer.retryLater(id, e.getMessage());
            } else {
                claimer.fail(id, e.getMessage());
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            // A user cancelled mid-step; the next tick sees CANCELLED and does nothing (Review Focus 3).
            claimer.release(id, Instant.now());
        } catch (RuntimeException e) {
            log.error("trailer {} step threw", id, e);
            claimer.retryLater(id, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/config/TrailerConfig.java
package com.doova.ktab.features.trailer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(TrailerProperties.class)
public class TrailerConfig {

    /** Scheduling is only switched on with the feature, so a disabled feature starts no worker. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
    static class Scheduling {

        @Bean(name = "trailerExecutor")
        ThreadPoolTaskExecutor trailerExecutor(TrailerProperties properties) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            int n = properties.getWorker().getConcurrency();
            executor.setCorePoolSize(n);
            executor.setMaxPoolSize(n);
            executor.setQueueCapacity(0);
            executor.setThreadNamePrefix("trailer-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(60);
            executor.initialize();
            return executor;
        }
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -o test -Dtest=TrailerPipelineTest -Dsurefire.failIfNoSpecifiedTests=false`, then
`STORYBOOK_IT_DB_URL=jdbc:postgresql://localhost:5432/ktab_storybook_it STORYBOOK_IT_DB_PASSWORD=123456 mvn -o verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TrailerClaimerIT`
Expected: 8 and 2 tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer src/test/java/com/doova/ktab/features/trailer
git commit -m "feat(trailer): add resumable trailer pipeline, claimer and scheduled worker"
```

---

### Task 7: Access rules, limits, REST API and messages

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerAccess.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerService.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerController.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerView.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerConflictException.java`
- Modify: `src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java`. Add the new keys after `STORYBOOK_NOT_READY(...), STORYBOOK_INSUFFICIENT_CREDITS(...),`.
- Modify: `src/main/resources/messages.properties`. Append the keys at the end.
- Test: `src/test/java/com/doova/ktab/features/trailer/web/TrailerServiceTest.java`

**Interfaces:**
- Consumes:
  - `BookRepository.findById`, `findByIdAndAuthor(Long, User)` and `findByIdAndLibraryOrganizationId(Long, Long)`.
  - `UserRepository.findById`.
  - `UserRole` codes.
  - `FileStorageService.getPreSignedDownloadUrl(String, Duration, String)`.
  - `HiggsfieldClient.cancel`, `BookTrailerRepository` and `BookTrailerShotRepository`.
- Produces:
  - `TrailerAccess.requireBook(User, Long bookId)`, which returns a `Book`.
  - `TrailerService`:
    - `TrailerView create(User, Long bookId)`
    - `List<TrailerView> list(User, Long bookId)`
    - `TrailerView get(User, Long trailerId)`
    - `String downloadUrl(User, Long trailerId)`
    - `void cancel(User, Long trailerId)`
  - REST under `/api/v1/trailers`.

- [ ] **Step 1: Write the failing service test**

```java
// src/test/java/com/doova/ktab/features/trailer/web/TrailerServiceTest.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.ShotStatus;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.higgsfield.HiggsfieldClient;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.model.BookTrailerShot;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.features.trailer.repository.BookTrailerShotRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerServiceTest {

    final BookRepository books = mock(BookRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final BookTrailerShotRepository shots = mock(BookTrailerShotRepository.class);
    final HiggsfieldClient higgsfield = mock(HiggsfieldClient.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerAccess access = new TrailerAccess(books, users);
    final TrailerService service = new TrailerService(access, trailers, shots, higgsfield, storage, props);

    static User user(UserRole role, long id) {
        User u = new User();
        u.setId(id);
        u.setRole(role.getCode());
        return u;
    }

    static Book book(BookStatus status) {
        Book b = new Book();
        b.setId(3L);
        b.setStatus(status);
        return b;
    }

    @Test
    void authorCanOnlyUseTheirOwnPublishedBook() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(ResourceNotFoundException.class);

        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.DRAFT)));
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(TrailerConflictException.class);
    }

    @Test
    void librarianIsScopedToTheirOrganization() {
        User librarian = user(UserRole.LIBRARIAN, 2);
        LibraryOrganization org = new LibraryOrganization();
        org.setId(50L);
        User managed = user(UserRole.LIBRARIAN, 2);
        managed.setLibraryOrganization(org);
        when(users.findById(2L)).thenReturn(Optional.of(managed));
        when(books.findByIdAndLibraryOrganizationId(3L, 50L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));

        TrailerView view = service.create(librarian, 3L);

        assertThat(view.status()).isEqualTo(TrailerStatus.QUEUED);
    }

    @Test
    void aSecondActiveTrailerIsAConflict() {
        User admin = user(UserRole.ADMIN, 9);
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.existsByBookIdAndStatusIn(eq(3L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.create(admin, 3L)).isInstanceOf(TrailerConflictException.class);
    }

    @Test
    void failedTrailersDoNotCountTowardTheLimitButOthersDo() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(eq(3L), any(), argThat(c ->
                c.contains(TrailerStatus.FAILED) && c.contains(TrailerStatus.CANCELLED)))).thenReturn(3L);

        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminIsExemptFromTheMonthlyLimit() {
        User admin = user(UserRole.ADMIN, 9);
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(anyLong(), any(), anyCollection())).thenReturn(99L);
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(admin, 3L).status()).isEqualTo(TrailerStatus.QUEUED);
    }

    @Test
    void cancelStopsTheTrailerAndCancelsSubmittedShots() {
        User admin = user(UserRole.ADMIN, 9);
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.SHOOTING);
        BookTrailerShot submitted = new BookTrailerShot(7L, 0, "p");
        submitted.setStatus(ShotStatus.SUBMITTED);
        submitted.setRequestId("r0");
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(shots.findByTrailerIdOrderByShotIndexAsc(7L)).thenReturn(List.of(submitted));

        service.cancel(admin, 7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.CANCELLED);
        verify(higgsfield).cancel("r0");
    }

    @Test
    void downloadNeedsAReadyTrailer() {
        User admin = user(UserRole.ADMIN, 9);
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.SHOOTING);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.downloadUrl(admin, 7L)).isInstanceOf(TrailerConflictException.class);

        t.setStatus(TrailerStatus.READY);
        t.setVideoKey("trailers/3/7/trailer.mp4");
        when(storage.getPreSignedDownloadUrl(eq("trailers/3/7/trailer.mp4"), any(), eq("trailer-3.mp4"))).thenReturn("https://signed");
        assertThat(service.downloadUrl(admin, 7L)).isEqualTo("https://signed");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=TrailerServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `TrailerService` does not exist.

- [ ] **Step 3: Add the message keys**

In `ApiMessageKey.java`, insert this block right after the line `STORYBOOK_NOT_READY("storybook.not.ready"), STORYBOOK_INSUFFICIENT_CREDITS("storybook.insufficient.credits"),`:

```java
    // ===== BOOK TRAILER =====
    TRAILER_CREATED("trailer.created"),
    TRAILER_FETCHED("trailer.fetched"),
    TRAILER_NOT_FOUND("trailer.not.found"),
    TRAILER_BOOK_NOT_PUBLISHED("trailer.book.not.published"),
    TRAILER_ALREADY_RUNNING("trailer.already.running"),
    TRAILER_LIMIT_REACHED("trailer.limit.reached"),
    TRAILER_NOT_READY("trailer.not.ready"),
    TRAILER_CANCELLED("trailer.cancelled"),
```

Append to `messages.properties`:

```properties
trailer.created=بدأنا بإنشاء الإعلان التشويقي للكتاب.
trailer.fetched=تم جلب الإعلان التشويقي.
trailer.not.found=الإعلان التشويقي غير موجود.
trailer.book.not.published=يمكن إنشاء إعلان تشويقي للكتب المنشورة فقط.
trailer.already.running=يوجد إعلان تشويقي قيد الإنشاء لهذا الكتاب.
trailer.limit.reached=وصلت إلى الحد الأقصى من الإعلانات التشويقية لهذا الكتاب خلال 30 يومًا.
trailer.not.ready=الإعلان التشويقي غير جاهز بعد.
trailer.cancelled=تم إلغاء الإعلان التشويقي.
```

- [ ] **Step 4: Implement access, service, view, exception and controller**

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerConflictException.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.KtabException;
import org.springframework.http.HttpStatus;

public class TrailerConflictException extends KtabException {

    public TrailerConflictException(ApiMessageKey key) {
        super(key);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerView.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;

import java.math.BigDecimal;
import java.time.Instant;

public record TrailerView(Long id, Long bookId, TrailerStatus status, BigDecimal voiceoverSeconds, String error,
                          Instant finishedAt) {

    static TrailerView of(BookTrailer t) {
        return new TrailerView(t.getId(), t.getBookId(), t.getStatus(), t.getVoiceoverSeconds(), t.getError(),
                t.getFinishedAt());
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerAccess.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** R1: author → own books, librarian/admin-librarian → their organization's books, admin → any book. */
@Component
@RequiredArgsConstructor
public class TrailerAccess {

    private final BookRepository books;
    private final UserRepository users;

    public Book requireBook(User user, Long bookId) {
        String role = user.getRole();
        Optional<Book> book;
        if (UserRole.ADMIN.getCode().equals(role)) {
            book = books.findById(bookId);
        } else if (UserRole.AUTHOR.getCode().equals(role)) {
            book = books.findByIdAndAuthor(bookId, user);
        } else if (UserRole.LIBRARIAN.getCode().equals(role) || UserRole.ADMIN_LIBRARIAN.getCode().equals(role)) {
            // The principal may be detached; reload it to read the organization (same as LibrarianBookServiceImpl).
            User managed = users.findById(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));
            if (managed.getLibraryOrganization() == null) {
                throw new BadRequestException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_ASSOCIATED);
            }
            book = books.findByIdAndLibraryOrganizationId(bookId, managed.getLibraryOrganization().getId());
        } else {
            book = Optional.empty();
        }
        return book.orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
    }

    public boolean isAdmin(User user) {
        return UserRole.ADMIN.getCode().equals(user.getRole());
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerService.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.ShotStatus;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.higgsfield.HiggsfieldClient;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.model.BookTrailerShot;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.features.trailer.repository.BookTrailerShotRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrailerService {

    private final TrailerAccess access;
    private final BookTrailerRepository trailers;
    private final BookTrailerShotRepository shots;
    private final HiggsfieldClient higgsfield;
    private final FileStorageService storage;
    private final TrailerProperties properties;

    @Transactional
    public TrailerView create(User user, Long bookId) {
        Book book = access.requireBook(user, bookId);
        if (book.getStatus() != BookStatus.PUBLISHED) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_BOOK_NOT_PUBLISHED);
        }
        if (trailers.existsByBookIdAndStatusIn(bookId, TrailerStatus.ACTIVE)) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
        if (!access.isAdmin(user)) {
            long recent = trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(bookId,
                    LocalDateTime.now().minusDays(30), EnumSet.of(TrailerStatus.FAILED, TrailerStatus.CANCELLED));
            if (recent >= properties.getLimits().getPerBookPer30Days()) {
                throw new BadRequestException(ApiMessageKey.TRAILER_LIMIT_REACHED);
            }
        }
        BookTrailer t = new BookTrailer();
        t.setBookId(bookId);
        t.setRequestedById(user.getId());
        t.setStatus(TrailerStatus.QUEUED);
        try {
            return TrailerView.of(trailers.save(t));
        } catch (DataIntegrityViolationException raced) { // uq_book_trailer_active (Review Focus 5)
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
    }

    @Transactional(readOnly = true)
    public List<TrailerView> list(User user, Long bookId) {
        access.requireBook(user, bookId);
        return trailers.findByBookIdOrderByIdDesc(bookId).stream().map(TrailerView::of).toList();
    }

    @Transactional(readOnly = true)
    public TrailerView get(User user, Long trailerId) {
        return TrailerView.of(owned(user, trailerId));
    }

    @Transactional(readOnly = true)
    public String downloadUrl(User user, Long trailerId) {
        BookTrailer t = owned(user, trailerId);
        if (t.getStatus() != TrailerStatus.READY || t.getVideoKey() == null) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        return storage.getPreSignedDownloadUrl(t.getVideoKey(), Duration.ofMinutes(10), "trailer-" + t.getBookId() + ".mp4");
    }

    @Transactional
    public void cancel(User user, Long trailerId) {
        BookTrailer t = owned(user, trailerId);
        if (!t.getStatus().isActive()) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        t.setStatus(TrailerStatus.CANCELLED);
        t.setFinishedAt(Instant.now());
        for (BookTrailerShot shot : shots.findByTrailerIdOrderByShotIndexAsc(t.getId())) {
            if (shot.getStatus() == ShotStatus.SUBMITTED && shot.getRequestId() != null) {
                higgsfield.cancel(shot.getRequestId()); // best effort; only queued requests are refundable
            }
        }
    }

    private BookTrailer owned(User user, Long trailerId) {
        BookTrailer t = trailers.findById(trailerId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
        access.requireBook(user, t.getBookId());
        return t;
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerController.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/trailers", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN','AUTHOR','LIBRARIAN','ADMIN_LIBRARIAN')")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class TrailerController {

    private final TrailerService service;
    private final MessageSource messageSource;

    @PostMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<TrailerView>> create(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.create(user, bookId),
                ApiMessageKey.TRAILER_CREATED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<List<TrailerView>>> list(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.list(user, bookId),
                ApiMessageKey.TRAILER_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/{trailerId}")
    public ResponseEntity<ApiResponse<TrailerView>> get(@CurrentUser User user, @PathVariable Long trailerId) {
        return ResponseUtils.success(service.get(user, trailerId),
                ApiMessageKey.TRAILER_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @GetMapping("/{trailerId}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> download(@CurrentUser User user, @PathVariable Long trailerId) {
        return ResponseUtils.success(Map.of("url", service.downloadUrl(user, trailerId)),
                ApiMessageKey.TRAILER_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @PostMapping("/{trailerId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@CurrentUser User user, @PathVariable Long trailerId) {
        service.cancel(user, trailerId);
        return ResponseUtils.success(null, ApiMessageKey.TRAILER_CANCELLED.getMessage(messageSource), HttpStatus.OK);
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 7, Failures: 0`. Then run `mvn -o test -Dtest=StorybookMessagesTest` to confirm that no existing message-key test breaks.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/web src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java src/main/resources/messages.properties src/test/java/com/doova/ktab/features/trailer/web
git commit -m "feat(trailer): add role-scoped trailer API with limits, cancel and download"
```

---

### Task 8: Configuration, Docker ffmpeg, live end-to-end run

**Files:**
- Modify: `src/main/resources/application.properties` (append the block below).
- Modify: `Dockerfile`. Change the first runtime `apt-get install` line to add `ffmpeg`.
- Create: `docs/trailer/launch-checklist.md`

**Interfaces:**
- Consumes: everything above.
- Produces: a deployable, feature-flagged trailer pipeline.

- [ ] **Step 1: Append the configuration**

```properties
# ===== Book trailers (Higgsfield video + ElevenLabs audio) =====
ktab.trailer.enabled=${KTAB_TRAILER_ENABLED:false}
ktab.trailer.higgsfield.base-url=https://api.higgsfield.ai
ktab.trailer.higgsfield.api-key-id=${HF_API_KEY_ID:}
ktab.trailer.higgsfield.api-key-secret=${HF_API_KEY_SECRET:}
ktab.trailer.higgsfield.video-endpoint=/kling-video/v2.5-turbo/pro/text-to-video
ktab.trailer.higgsfield.max-in-flight=6
ktab.trailer.elevenlabs.api-key=${elevenlabs.api-key:}
ktab.trailer.elevenlabs.voice-id=${KTAB_TRAILER_VOICE_ID:}
ktab.trailer.claude.api-key=${ANTHROPIC_API_KEY:}
ktab.trailer.claude.model=claude-sonnet-5
ktab.trailer.ffmpeg.path=ffmpeg
ktab.trailer.ffmpeg.ffprobe-path=ffprobe
ktab.trailer.limits.per-book-per-30-days=3
```

- [ ] **Step 2: Add ffmpeg to the runtime image**

In `Dockerfile`, replace:

```dockerfile
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl && \
```

with:

```dockerfile
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl ffmpeg && \
```

- [ ] **Step 3: Verify that the flag-off app still boots and all tests pass**

Run: `mvn -o clean test` (the flag is off by default).
Expected: BUILD SUCCESS, and no `TrailerWorker` or `TrailerController` bean exists.

Then run: `STORYBOOK_IT_DB_URL=… STORYBOOK_IT_DB_PASSWORD=… mvn -o verify -Dit.test='com.doova.ktab.features.trailer.**.*IT' -Dfailsafe.failIfNoSpecifiedTests=false -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
Expected: all trailer ITs pass.

- [ ] **Step 4: Run one real trailer end to end (costs real credits)**

1. Start the app with `KTAB_TRAILER_ENABLED=true`, `HF_API_KEY_ID`, `HF_API_KEY_SECRET`, `ANTHROPIC_API_KEY`, `KTAB_TRAILER_VOICE_ID` (an Arabic-capable ElevenLabs voice) and the existing ElevenLabs key. Make sure ffmpeg is on PATH.
2. As the author of a published book, call `POST /api/v1/trailers/books/{bookId}`. Expected: HTTP 202 with `status=QUEUED`.
3. Poll `GET /api/v1/trailers/{id}`. The status should go `QUEUED → VOICING → SHOOTING → ASSEMBLING → READY` within about 5–15 minutes.
4. Call `GET /api/v1/trailers/{id}/download` and open the URL. Check that:
   - the video is 30 s, 1920×1080, 16:9;
   - the Arabic narration is clear and ends before the fade;
   - the music sits under the voice;
   - **no text appears anywhere**;
   - no identifiable real person appears.
5. Confirm that `trailers/{bookId}/{id}/trailer.mp4` exists in the R2 bucket.

- [ ] **Step 5: Write the launch checklist**

```markdown
<!-- docs/trailer/launch-checklist.md -->
# Book trailer launch checklist

- [ ] Task 2 spike run; model + aspect-ratio choice recorded in plan D1.
- [ ] Secrets set in the deploy environment only: HF_API_KEY_ID, HF_API_KEY_SECRET, ANTHROPIC_API_KEY,
      KTAB_TRAILER_VOICE_ID, elevenlabs.api-key. None in git.
- [ ] Docker image rebuilt; `ffmpeg -version` works inside the container as user `ktab`.
- [ ] One real trailer generated per book type: Arabic political (real people named), Arabic children's, English novel.
      Each: 30 s, 16:9, no on-screen text, no real-person likeness, narration inside 30 s.
- [ ] Credits/budget: confirm Higgsfield credit cost of 6 Kling Pro 5 s shots; set a Higgsfield account spend alert.
- [ ] ElevenLabs Music commercial-use terms confirmed for the plan tier in use.
- [ ] Rights: authors confirm (ToS) they may promote their book with AI-generated media.
- [ ] Flip KTAB_TRAILER_ENABLED=true for one instance first; watch logs for "trailer … step failed".
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.properties Dockerfile docs/trailer/launch-checklist.md
git commit -m "feat(trailer): configure trailer feature, add ffmpeg to image and launch checklist"
```

---

## Out of scope (follow-ups, not built here)

- Showing the latest READY trailer in the book response and the reader app (a `BookResponseDto` field).
- Automatic re-generation of a single weak shot chosen by the user. Today the user regenerates the whole trailer, within the 30-day limit.
- Higgsfield webhooks, which need a public HTTPS endpoint and a way to verify an unsigned payload (D8).
- Billing trailers through the storybook credits port or the future subscription module.

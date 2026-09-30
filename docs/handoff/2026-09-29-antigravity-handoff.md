# Ktab Backend: handoff to Antigravity (2026-09-29)

This document covers the work done with Claude Code on the Ktab backend up to 2026-09-29: what is built, what is only planned, where the code has fallen behind its plan, and what to do next. Every claim was checked against the working tree on 2026-09-29 unless marked otherwise.

**Read first:**
- §4, the gaps between the trailer code and its plan. Priority 1 is a live bug.
- §3, local build constraints. There is no Docker and no network Maven.

---

## 1. Project snapshot

| | |
|---|---|
| Repo | `C:\Users\PC\IdeaProjects\Ktab-Backend`, branch `master` |
| Stack | Spring Boot 3.5.7, Java 21, Postgres + Flyway, Cloudflare R2 through the AWS S3 SDK, Thymeleaf, Playwright (PDF), `com.anthropic:anthropic-java:2.34.0` |
| Latest migration | `V21__book_trailers.sql`. **Already applied** to the local dev DB `ktab` (`flyway_schema_history` shows V21 with success = t). It is still **untracked in git**. |
| Conventions | <ul><li>Tables and columns: `tbl_*` / `col_*`, plus BaseEntity (`col_id`, `col_created_by`, `col_last_modified_by`, `created_at`, `updated_at`, `version` with `@Version`).</li><li>Controllers: `@ApiVersion(1)` (maps to `/api/v1`), responses via `ResponseUtils.success(data, message, status)`.</li><li>Messages: `ApiMessageKey` + Arabic `messages.properties`.</li><li>Errors: subclasses of `KtabException`.</li><li>Current user: `@CurrentUser User`.</li><li>Public endpoints live under the existing `permitAll` `/api/v1/public/**`.</li></ul> |
| Secrets | Environment variables only. `gcp-credentials.json` and `*.env` are git-ignored and untracked; keep it that way. Never put API keys in prompts, plans or commits. |

## 2. What exists: feature status

### 2.1 Personalized storybook (`features/storybook`)

**Status:** sub-plans 01–07 are **implemented and committed** (see `git log`). The overview is `docs/superpowers/plans/2026-09-24-storybook-00-overview.md`, and the spec is `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`.

> **Both of those files are listed in `.gitignore`** (lines around 52–77 of the locally modified `.gitignore`). Edits to them exist on disk but are not tracked.

**Uncommitted work from this session**, all tests green:

1. **Test infrastructure**
   - `pom.xml`: added `maven-failsafe-plugin`, so `*IT` classes run on `mvn verify`. Before this they never ran.
   - `support/StorybookJpaIT.java`: tests use Testcontainers when Docker is available. Otherwise they fall back to a local Postgres named by `STORYBOOK_IT_DB_URL`, and the database name **must end in `_it`** as a safety guard. With neither available, the tests skip.
     - The skip uses a custom `ExecutionCondition` registered through `@ExtendWith`. `@EnabledIf` is **not** `@Inherited` and silently stopped applying to subclasses; this was reproduced and fixed.
   - `support/UserFixtures.java`: sets `lastName`. `tbl_users.col_last_name` is NOT NULL, and without it 34 of 39 ITs failed.
2. **PDF render pipeline fixes** (in `render/`)
   - `RenderPdfHandler`: a COVER or STORY page with no image returns `StepOutcome.fail` instead of silently rendering a blank page. It uses an exhaustive `switch` on `PageKind`. The regression test is `RenderPdfHandlerTest.refusesToRenderWhenAStoryPageHasNoImage`.
   - `PlaywrightPdfRenderer`: relaunches Chromium when it is disconnected. It drops the dead reference **without** closing it, so no in-flight render thread is raced, and keeps the Playwright driver. A failed launch no longer leaks a driver process.
   - `ReaderService`: logs `log.error` when a READY book's page has no image key instead of failing silently. The reader still degrades gracefully.
3. **Sample output:** `scratch/storybook-sample.pdf` is a real render. A render at 70 words, the top age band's limit, fits the text box.

**Planned, NOT implemented: sub-plan 08.** It is in `docs/superpowers/plans/2026-09-28-storybook-08-custom-blueprints-and-text-editing.md` (7 tasks, TDD, full code). It covers:
- **Parent-written blueprints:** a title plus 10, 12 or 15 beats, moderated when saved, saved for reuse, and snapshotted into the book as `custom-{id}`. Beats are fenced in the prompt as story content, not instructions.
- **Editable story text:** the parent can view and edit page text and the title in `STORY_READY`, `CHARACTER_READY`, `ILLUSTRATING`, `QA` and `READY`, and can revert to the AI original.
  - Edits are moderated.
  - MSA text is vocalized by a guarded Claude call that must not change a single letter.
  - Edits are limited to 60 per book.
  - An edit in `READY` re-renders the PDF only, through a new `READY → RENDERING` edge and a unified `renderRevision` counter that replaces `pageRegenerations` in PDF keys.
- Overview decisions D13–D16 and the spec's "Amendment 2" describe it.
- **Migration number: use `V23__…`, not V22** (see §4, priority 3). The plan text says V22; rename it when implementing.

### 2.2 Book trailer: Claude Managed Agents (`features/trailer`)

**Plan:** `docs/superpowers/plans/2026-09-28-trailer-agent.md`. This is the plan to follow. `docs/superpowers/plans/2026-09-28-book-trailer.md`, the backend-orchestrated pipeline, is **superseded**; don't implement it.

**Architecture:**
- **Control plane.** It is created once from `ops/trailer-agent/`:
  - a cloud environment with ffmpeg, poppler-utils, jq and fonts;
  - an agent with the full toolset and the Higgsfield MCP (`https://mcp.higgsfield.ai/mcp`) under the `always_allow` permission policy;
  - a vault holding the ElevenLabs key as an `environment_variable` credential and the Higgsfield `mcp_oauth` credential, which an admin connects once.
- **Spring, per trailer.** The data plane:
  1. uploads the book PDF;
  2. creates a session with the PDF mounted at `/workspace/book.pdf`, the vault, a budget, and a `user.define_outcome` kickoff with a rubric;
  3. a reconciler polls the session, and a signed webhook pulls the next check forward;
  4. a harvester downloads `/mnt/session/outputs/*`, re-verifies the MP4 with ffprobe, and uploads to R2.
- **Statuses:** `QUEUED → RUNNING → HARVESTING → READY | NEEDS_REVIEW | FAILED | CANCELLED`.

**Status:** **implemented but untracked.** It was built from the *original* plan (before 2026-09-29), plus extras not in the plan:
- `TrailerNotifier`, which sends the email `templates/emails/trailer-ready.html`;
- `TrailerEtaService`, a rolling-average ETA;
- the property `max-concurrent-runs`.

The unit tests pass (53 trailer tests).

The control plane is live:
- `agent.json` uses model `claude-opus-5-5` with effort `high`;
- `application.properties` defaults `KTAB_TRAILER_AGENT_VERSION` to `7`;
- `ops/trailer-agent/connect-higgsfield.mjs` and `update-agent.mjs` exist;
- `scratch/*.mjs` holds probe scripts for Higgsfield tools, jobs and sessions.

**First real run (qc_report provided by the product owner):**
- **Passed:** 30.0 s, 1920×1080, 30 fps, h264/aac; eleven_v3 Arabic narration of 24.61 s, ending at 25.5 s before a fade at 27.8 s; music_v2 at 30 s; −14.5 LUFS; forced-alignment captions; frame check clean.
- **Waste:** 7 Seedance 2.5 jobs from 11 generate calls. 1 job was discarded (a lecture-hall shot showed a globe with map labels and a plaque), 2 calls were concurrency/429 rejections, and 2 calls came back as preset suggestions. 11 of 12 allowed calls were used; the old cap counted *calls*.

**Product decisions made after that run (2026-09-29):**
1. **Captions are Arabic only.** They are burned into `trailer.mp4` in a proper Arabic font, right-to-left, with each cue starting with U+200F. **No English captions anywhere**: none generated, burned in or stored.
2. **Storage keeps only the Arabic captions:** R2 stores `trailer.mp4`, `trailer_clean.mp4`, `captions_ar.srt`, `script.md` and `qc_report.json`.
3. **Spend cap counts jobs, not calls.**
   - Pair each generate `agent.mcp_tool_use` with its `agent.mcp_tool_result` through `mcp_tool_use_id`.
   - A **job** is a call whose result is not an error and whose text matches `higgsfield-job-pattern`.
   - The limits are **8 jobs** (the money limit) and **24 calls** (a runaway guard).
4. **Waste prevention in the agent prompt:**
   - at most **2 generations in flight**;
   - after a concurrency or 429 rejection, **wait 60, then 120, then 240 seconds**, never retrying immediately;
   - call `generate_video` with **pinned exact arguments**, passed in the task message;
   - **never plan text-bearing shots** (classrooms and lecture halls, maps and globes, plaques, signs, screens, bookshelves, newspapers, flags).

The plan file already contains all of this, as tests and code. **The implemented code does not yet** (§4).

### 2.3 Other items in the working tree (not from this session; review before committing)

| File | What |
|---|---|
| `config/s3/S3Config.java` | S3/R2 timeouts made configurable: `cloudflare.r2.apiCallTimeoutSeconds` defaults to 600 (was 30), `apiCallAttemptTimeoutSeconds` defaults to 300 (was 10). Presumably for large trailer uploads. |
| `service/user/impl/UserServiceImpl.java`, `event/listener/BookEditorialNotificationListener.java`, `templates/emails/*.html` (5 files) | Email changes. Unknown scope; review the diff. |
| `storybook/blueprints/*.v1.json` (4 new) | New catalogue blueprints (`secret-of-the-ancient-library`, `the-lantern-of-eid`, `the-lost-little-kitten`, `the-young-inventors-challenge`). `BlueprintCatalog` validates them on startup; the unit tests pass. |
| `test/.../SendTrailerReadyEmailLiveTest.java` | Live email test for the trailer notifier. |
| `docs/storybook/client-pitch-brief.md`, `docs/trailer/launch-checklist.md` | Docs. |
| `.gitignore` | Many added ignore lines, **including the storybook overview and spec**. Decide whether that is intended. |

**Earlier, older work (context only):** OCR Engine v3, which covers PDF classification, DIGITAL / SCANNED routing and the ElevenLabs Studio audiobook pipeline in `features/studio`. See `docs/ocr_engine_v3.md`. Its phases 0–3 were partly implemented in earlier sessions; this handoff did not re-verify that work.

## 3. Local environment and commands (important)

- **Maven:** the wrapper cannot download (no network access to Maven Central). Run the cached Maven directly, **offline**:
  `MVN=/c/Users/PC/.m2/wrapper/dists/apache-maven-3.9.11/03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc/bin/mvn`
  - Unit tests: `$MVN -o test -Dtest='com.doova.ktab.features.trailer.**.*Test,com.doova.ktab.features.storybook.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false` (on 2026-09-29: **307 run, 0 failures, 3 skipped**).
  - Integration tests (no Docker on this machine): create a throwaway database, then run failsafe:
    ```bash
    PGPASSWORD=123456 psql -h localhost -U postgres -c "DROP DATABASE IF EXISTS ktab_storybook_it" -c "CREATE DATABASE ktab_storybook_it"
    STORYBOOK_IT_DB_URL=jdbc:postgresql://localhost:5432/ktab_storybook_it STORYBOOK_IT_DB_PASSWORD=123456 \
      $MVN -o verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='com.doova.ktab.features.**.*IT' -Dfailsafe.failIfNoSpecifiedTests=false
    ```
    If `DROP DATABASE` complains about open sessions, first run `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='ktab_storybook_it' AND pid <> pg_backend_pid();`. Otherwise stale rows break `JobClaimerIT`.
  - The PDF render IT needs `STORYBOOK_RENDER_TESTS=true PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`, because Chromium is already cached in `%LOCALAPPDATA%\ms-playwright`.
  - Stale incremental compiles cause bogus "unresolved compilation problem" errors; run `$MVN -o clean` when that happens.
- **Postgres:** local PG 18 on `localhost:5432` with user `postgres` and password `123456`. The dev DB is `ktab`. **Never point tests at `ktab`.**
- **ffmpeg/ffprobe: not installed** locally (`winget install Gyan.FFmpeg`). The trailer harvester's `MediaProbe` needs ffprobe at runtime. The Dockerfile installs ffmpeg for production.
- **Python:** `C:\Users\PC\AppData\Local\Programs\Python\Python311\python.exe`. The `python` on PATH is a Store stub.
- **JDK tools:** `C:\Users\PC\.jdks\ms-21.0.11\bin`. The anthropic-java jar is at `~/.m2/repository/com/anthropic/anthropic-java-core/2.34.0/` and is useful for `javap` checks.
- **anthropic-java 2.34.0 gaps**, verified with javap:
  - no typed session `budget` or `initial_events`: send them with `SessionCreateParams.Builder.putAdditionalBodyProperty`;
  - no typed `environment_variable` vault credential: create it with curl in `ops/trailer-agent/setup.sh`;
  - output files are listed with `FileListParams.scopeId(sessionId).addBeta("managed-agents-2026-04-01")`.

## 4. Gaps: trailer code vs. its updated plan (do these in order)

**Priority 1: live bug, caption file mismatch.**
- `ops/trailer-agent/system-prompt.md` and `src/main/resources/trailer-agent/rubric.md` **already** tell the agent to produce `captions_ar.srt`, the Arabic captions.
- But `pipeline/TrailerHarvester.java` still maps and **requires `captions_en.srt`** (lines around 37, 75, 108–138).
- **Effect:** every real trailer ends as `NEEDS_REVIEW` with "captions_en.srt is missing", and the Arabic captions are never stored in R2.
- **Fix:**
  1. The harvester stores `captions_ar.srt` into `captionsKey`, validates it with `checkSrt`, and requires `qc_report.captions.language == "ar"`. The plan's Task 6 has the exact code.
  2. Remove every `captions_en` reference: TYPES, the switch, the checks, and the test fixtures in `TrailerHarvesterTest`.
  3. Add the test `nonArabicCaptionsAreNeedsReview`.
  4. In `TrailerService.downloadUrls`, the `captions` link should name the file `trailer-{bookId}-ar.srt`.

**Priority 2: job-based spend cap and waste prevention** (plan decision D5, Tasks 1–5).
- `SessionSnapshot` gets `int higgsfieldCalls`.
- `SessionEvents.interpret(..., Pattern jobPattern)` counts jobs by pairing each generate `agent.mcp_tool_use` (by its `id`) with the `agent.mcp_tool_result` (`mcp_tool_use_id`, `is_error`, `content`). The tests are `countsOnlyCallsThatCreatedAJob`, `aLoopOfRejectedCallsHitsTheCallCap` and `rejectedCallsBelowTheCapDoNotStopAGoodRun`.
- `TrailerProperties` gets:
  - `maxHiggsfieldGenerations = 8` (jobs);
  - `maxHiggsfieldCalls = 24`;
  - `higgsfieldJobPattern`;
  - `higgsfieldGenerateArgs`;
  - `higgsfieldMaxInFlight = 2`.

  Wire each in `application.properties` (`KTAB_TRAILER_MAX_HIGGSFIELD_GENERATIONS` currently defaults to 12; change it to 8).
- `TrailerReconciler` enforces both caps. `TrailerTask.describe(title, author, language, voiceId, maxJobs, maxInFlight, generateArgs)` tells the agent the job allowance, the concurrency and the exact `generate_video` arguments, and says "Arabic only".
- `system-prompt.md` gets three new rule groups: the concurrency-and-backoff rules, "call `generate_video` with exactly the arguments in the task message", and "Never put text-bearing things in a shot". Keep the existing Amiri-font caption rules. The plan uses Noto Naskh Arabic, but Amiri from `fonts-hosny-amiri` is already live and fine. Then run `ops/trailer-agent/update-agent.sh` (or `.mjs`) to publish a new agent version, and bump `KTAB_TRAILER_AGENT_VERSION`.
- **Values to capture from a real run** before relying on the caps:
  - the exact job-creating `generate_video` arguments, which go into `KTAB_TRAILER_HIGGSFIELD_GENERATE_ARGS` (e.g. `model=seedance_2_5, aspect_ratio=16:9, duration=5`);
  - a regex that matches the job-created reply text, which goes into `KTAB_TRAILER_HIGGSFIELD_JOB_PATTERN`;
  - the account's concurrency limit, from the Higgsfield Console.

  `scratch/list-higgsfield-tools.mjs` and `scratch/test-jobs-tools.mjs` are a good starting point.

**Priority 3: migration numbering.**
- `V21__book_trailers.sql` is already applied locally. **Do not edit it**, because Flyway would fail the checksum. It lacks `col_higgsfield_calls`, which the updated plan adds.
- Put the trailer follow-up in a **new `V22__book_trailer_higgsfield_calls.sql`**: `ALTER TABLE tbl_book_trailers ADD COLUMN IF NOT EXISTS col_higgsfield_calls INTEGER NOT NULL DEFAULT 0;`. Add the `higgsfieldCalls` field to `BookTrailer`.
- The storybook 08 migration then becomes **V23**. That plan's text says V22; rename it, and adjust its Global Constraints line.
- The trailer plan's own Task 2 shows the column inside V21 because it was written before V21 shipped. Follow this handoff instead.

**Priority 4: commit hygiene.** Nothing from this session is committed, and the trailer feature, `ops/` and V21 are untracked. Suggested separate commits:
1. storybook test infrastructure (pom, StorybookJpaIT, UserFixtures);
2. storybook render fixes (RenderPdfHandler, PlaywrightPdfRenderer, ReaderService, test);
3. the trailer feature with V21 and `ops/`;
4. the trailer fixes from priorities 1–3;
5. the other email and S3 changes, after review.

End each commit message with the attribution line the team uses. Check `git status` for secrets before every commit.

**Priority 5: implement storybook sub-plan 08** (§2.1) with V23.

## 5. Key decisions and why (do not relitigate without the product owner)

| Decision | Why |
|---|---|
| Trailer runs on **Claude Managed Agents**, not the OpenAI Agents API design the owner first pasted | Ktab already ships anthropic-java, so every endpoint could be verified against the docs and the jar. It adds hard per-session dollar budgets, a rubric-graded outcome loop (grade, then revise), and vault credentials that are never visible in the sandbox. |
| Spring owns access, limits, storage and an **independent ffprobe re-check**; the agent owns creative work and QC | The agent's self-reported QC is not trusted alone. A 24-second or 720p file is `NEEDS_REVIEW`, never `READY`. |
| MCP toolset `permission_policy: always_allow` | MCP defaults to `always_ask`, which would stall every session at `requires_action`. `ControlPlaneFilesTest` guards this. |
| Polling reconciler plus webhook nudge | Anthropic webhooks are thin, unordered and droppable. |
| Higgsfield OAuth: dynamic client registration + PKCE, tokens stored in the vault with a refresh block (auth `none`) | Verified live from `https://mcp.higgsfield.ai/.well-known/oauth-authorization-server`. Anthropic refreshes the token after that. The `vault_credential.refresh_failed` webhook tells an admin to reconnect. |
| Roles | An AUTHOR can request trailers for their own books, a LIBRARIAN or ADMIN_LIBRARIAN for their organization's books, and an ADMIN for any book. The book must be PUBLISHED. One active trailer per book is enforced by a partial unique index, plus 3 per book per 30 days (ADMIN exempt). |
| Storybook text edits never regenerate images | The images follow `sceneEn`. Re-rendering only the PDF is cheap and predictable. |

## 6. File index

- **Plans:**
  - `docs/superpowers/plans/2026-09-28-trailer-agent.md` (the current trailer plan, updated 2026-09-29);
  - `docs/superpowers/plans/2026-09-28-storybook-08-custom-blueprints-and-text-editing.md`;
  - `docs/superpowers/plans/2026-09-24-storybook-0[0-7]-*.md`;
  - `docs/superpowers/plans/2026-09-28-book-trailer.md` (**superseded**).
- **Trailer control plane:** `ops/trailer-agent/` (`agent.json`, `environment.json`, `system-prompt.md`, `setup.sh`, `update-agent.sh`/`.mjs`, `connect-higgsfield.mjs`, `README.md`).
- **Trailer code:** `src/main/java/com/doova/ktab/features/trailer/**`, `src/main/resources/trailer-agent/rubric.md`, `src/main/resources/db/migration/V21__book_trailers.sql`, tests under `src/test/java/com/doova/ktab/features/trailer/**`.
- **Storybook code:** `src/main/java/com/doova/ktab/features/storybook/**`.
- **Checklists:** `docs/trailer/launch-checklist.md`, `docs/storybook/launch-checklist.md`.

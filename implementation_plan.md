# Implementation Plan: OCR Engine v3 — Studio Ingestion & Audiobook Completion

## Overview
Phase 0 (enums, properties, migrations V11-V13), Phase 1 (PDF type classification), Phase 2 (routing & purging), Phase 3 (scaffolding entities, repositories, two-tier sync service, and projector service), and the core Phase 4 batch tasklets have been created and compile cleanly.

This plan details the remaining components required to complete the `docs/ocr_engine_v3.md` specification:
1. **Post-Projection Renumbering (`StudioRenumberTasklet` / Service)**: contiguous `col_page_number` assignments, updating `col_start_page`/`col_end_page` on sections, and setting `tbl_books.col_page_count`.
2. **Projection Quality Gate (`ProjectionGateTasklet`)**: §3.9 assertion rules on chapter counts, ratios, character length tolerances, and fallback to `ocrJob` on failure.
3. **Push Path for OCR Books (`PushChaptersTasklet`)**: §4.1 creating chapters at ElevenLabs Studio from `col_markdown_clean` for scanned/hybrid books.
4. **Spring Batch Job Configurations (`StudioBatchConfig`)**: defining `studioIngestionJob` and `studioAudiobookJob`, and wiring `studioIngestionJob` into `IngestionRouter`.
5. **Orphan Reconciler (`StudioOrphanReconciler`)**: §4.5 hourly scheduled background task to reap unreferenced or orphaned ElevenLabs projects.
6. **Verification & Tests**: Layer 3 contract test (§3.8), Projection idempotency, and batch job integration tests.

---

## User Review Required
> [!IMPORTANT]
> The Studio ingestion route for digital PDFs and the audiobook generation route make live HTTP calls to the ElevenLabs REST API when not running in dry-run mode (`ktab.studio.audiobook.dry-run=false`). The implementation ensures safe defaults:
> - `ktab.ingestion.classification.shadow-mode=true` remains default so that in production, incoming books continue routing to OCR while recording classification telemetry.
> - Dry-run mode skips costly `/convert` calls while exercising data pipelines.

---

## Proposed Changes

### 1. Studio Batch & Renumbering Layer
`src/main/java/com/doova/ktab/features/studio/batch/`

#### [NEW] [StudioRenumberTasklet.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/main/java/com/doova/ktab/features/studio/batch/StudioRenumberTasklet.java)
- Runs after all chapters are projected.
- Queries `StudioChapter` ordered by `col_order_index ASC`.
- Sequentially numbers `BookPage` rows across all chapters to assign 1..N contiguous `col_page_number`.
- Updates `col_start_page` and `col_end_page` on each `BookSection`.
- Updates `tbl_books.col_page_count = N`.

#### [NEW] [ProjectionGateTasklet.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/main/java/com/doova/ktab/features/studio/batch/ProjectionGateTasklet.java)
- Enforces the quality rules in `docs/ocr_engine_v3.md` §3.9:
  - Chapter count within `[minChapters, maxChapters]` (default 2 to 500)
  - No chapter exceeding `maxChapterCharRatio` (default 0.15) of total chars
  - No empty chapters
  - Total characters within `charCountToleranceRatio` (default 0.20) of PDF text layer count
- On failure: purges generated content via `BookContentPurger`, sets book audit/warning, and launches fallback `ocrJob`.

#### [NEW] [PushChaptersTasklet.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/main/java/com/doova/ktab/features/studio/batch/PushChaptersTasklet.java)
- Implements §4.1 for OCR'd books being turned into audiobooks:
  - Iterates through `BookSection` rows of the book.
  - Formats content from `BookPage.col_markdown_clean`.
  - Calls `ElevenLabsStudioClient.createChapter(externalProjectId, title, content)`.
  - Links `col_external_chapter_id` to the `BookSection` and saves a `StudioChapter` row with origin `KTAB_PUSHED`.

---

### 2. Spring Batch Job Orchestration
`src/main/java/com/doova/ktab/features/studio/config/`

#### [NEW] [StudioBatchConfig.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/main/java/com/doova/ktab/features/studio/config/StudioBatchConfig.java)
- Declares dedicated `studioTaskExecutor` with configured concurrency caps.
- Builds `studioIngestionJob`:
  - `createProjectStep` &rarr; `syncContentStep` &rarr; `renumberStep` &rarr; `projectionGateStep` (&rarr; optional `cleanupStep` if ingestion-only).
- Builds `studioAudiobookJob`:
  - `estimateStep` &rarr; `createProjectStep` &rarr; `syncOrPushStep` &rarr; `convertStep` &rarr; `pollStep` &rarr; `downloadStep` &rarr; `timingIndexStep` &rarr; `finalizeStep` &rarr; `cleanupStep`.
- Does **not** import anything from `features.ocr`.

#### [MODIFY] [IngestionRouter.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/main/java/com/doova/ktab/features/ingestion/routing/IngestionRouter.java)
- Injects `@Qualifier("studioIngestionJob") Job studioIngestionJob`.
- Replaces `throw new UnsupportedOperationException(...)` with `launchStudio(bookId, pdfKey)`:
  - Builds `JobParameters` with `bookId`, `pdfKey`, and `run.id`.
  - Launches `studioIngestionJob` via `jobLauncher`.

---

### 3. Orphan Reconciler
`src/main/java/com/doova/ktab/features/studio/reconciler/`

#### [NEW] [StudioOrphanReconciler.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/main/java/com/doova/ktab/features/studio/reconciler/StudioOrphanReconciler.java)
- Runs on `@Scheduled(cron = "${ktab.studio.orphan-reconciler.cron:0 0 * * * *}")`.
- Fetches all remote projects via `client.listProjects()`.
- Identifies projects missing from `tbl_studio_projects` or tied to terminal status older than 2 hours without `col_project_deleted_at`.
- Calls `client.deleteProject(projectId)` and records `studio.orphans.reclaimed`.

---

### 4. Testing & Verification
`src/test/java/com/doova/ktab/features/studio/`

#### [NEW] [StudioContractTest.java](file:///c:/Users/PC/IdeaProjects/Ktab-Backend/src/test/java/com/doova/ktab/features/studio/StudioContractTest.java)
- Layer 3 contract test (§3.8) verifying:
  - `col_page_number` contiguous 1..N without gaps.
  - `col_markdown_clean` non-null on every body page.
  - Every page's `section` is non-null.
  - Section `startPage`/`endPage` span the book without gaps.
  - `tbl_books.col_page_count` matches the total page count.

---

## Verification Plan

### Automated Tests
1. `.\mvnw.cmd test-compile -DskipTests` to confirm whole-project clean compilation.
2. `.\mvnw.cmd test -Dtest=StudioIndependenceTest` to assert zero imports from `features.ocr`.
3. `.\mvnw.cmd test -Dtest=StudioContractTest` to validate Layer 3 contract assertions.
4. `.\mvnw.cmd test -Dtest=*Studio*` to run all studio-specific unit/integration tests.

### Manual / Integration Verification
- Verify Spring context startup (`spring-boot:run` or integration test).
- Test `/api/v1/ingestion/books/{id}/classify` escape hatch endpoint.

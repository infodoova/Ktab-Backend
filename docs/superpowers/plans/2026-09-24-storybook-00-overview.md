# Personalized Storybook (Ktab feature) — Implementation Plan Overview

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This file is the index; the tasks live in sub-plans `01`–`07`. Read this file first, then the sub-plan you are executing.

**Goal:** Add a personalized Arabic picture-book feature to Ktab: a parent enters their child's details, approves a generated Arabic story, approves a generated character look, and receives a 21×21 cm RTL PDF book plus an in-app flipbook.

**Architecture:** A new `com.doova.ktab.features.storybook` package inside the existing Ktab Spring Boot app. A Postgres job table polled by in-process workers drives each book through a state machine. Claude Sonnet 5 (official Anthropic Java SDK) writes and checks the Arabic text and does visual QA; Nano Banana 2 on Vertex AI (existing `google-genai` client) draws the illustrations; headless Chromium via Playwright for Java renders the PDF. Everything is stored in Ktab's Postgres and Cloudflare R2.

**Tech Stack:** Java 21, Spring Boot 3.5.7, Spring Data JPA/Hibernate 6, Flyway, PostgreSQL, Cloudflare R2 (S3 SDK), `com.anthropic:anthropic-java`, `com.google.genai:google-genai`, `com.microsoft.playwright:playwright`, Thymeleaf, JUnit 5, Mockito, AssertJ, Testcontainers (Postgres).

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md` (the product spec, with the "feature inside Ktab" amendment at the top).

---

## How the Ktab amendment changes the spec

The spec was written for a standalone product. The product owner has since decided it ships inside Ktab. That changes these parts of the design; everything else in the spec is implemented as written.

| Spec says | Inside Ktab it becomes | Why |
|---|---|---|
| Standalone app with its own accounts | Parents use their existing Ktab account; endpoints need `isAuthenticated()` plus an ownership check | Ktab already has JWT auth and `@CurrentUser User` |
| Tables `book`, `page`, `character`, … | `tbl_storybook_*` tables, entities named `Storybook`, `StorybookPage`, … | `tbl_books` / `tbl_book_pages` and `Book` / `BookPage` already exist in Ktab and mean something else |
| Its own Flyway history | Ktab's Flyway history; the storybook migration is the next free `V<n>` (V14 at the time of writing — re-check `src/main/resources/db/migration` before creating it) | One schema, one migration sequence |
| Next.js frontend | The Ktab web client (separate repo). This repo delivers the REST contract below; the frontend work is listed at the end as its own track | Backend repo only |
| "Spring AI" for the LLM adapter | Official Anthropic Java SDK (`com.anthropic:anthropic-java`) for the Claude calls; Spring AI stays in Ktab for its existing Gemini/OpenAI features | The official SDK exposes structured outputs (`outputConfig(Class)`), image input, adaptive thinking and effort directly |
| Its own payments | A `StorybookCreditPort` in the feature; the first adapter uses admin-granted credits; it is re-pointed at Ktab's `EntitlementService` when `features.subscription` ships | Ktab's subscription/entitlement module is designed (`docs/Ktab_Subscription_Entitlement_Architecture_Final.md`) but not implemented, and the spec's payment provider and price are ticked but have no values |
| Its own image client | A new `GeminiImageProvider` using Ktab's existing `vertexGenAiClient` bean | The existing `features.story.image.VertexImageClient` sets **every** Gemini safety filter to `OFF`; it must not be used for children's content |
| Own deployment | Same Docker image; the runtime stage gains Chromium | One deployable |

## Findings in the current Ktab codebase that the plans act on

1. **Scheduling is not enabled.** No `@EnableScheduling` exists anywhere, so `features.studio.reconciler.StudioOrphanReconciler`'s `@Scheduled` method never fires today. Sub-plan 03 enables scheduling for the storybook worker; that also wakes up the Studio reconciler. Confirm with whoever owns Studio that it should run (its cron is `ktab.studio.orphan-reconciler.cron`, default hourly).
2. **`google-genai` 1.3.0 has no `ImageConfig`**, so it cannot request 2K square images. Sub-plan 01 upgrades it and re-runs the OCR and interactive-story tests that use the same SDK.
3. **The Anthropic SDK, Playwright and Testcontainers are not in the build.** Sub-plans 01, 03 and 06 add them. The build machine needs Maven Central access for that step.
4. **Ktab's latest migration is `V13__align_audio_chapter_hash_types.sql`.** Other work is landing migrations concurrently; always take the next free number at the moment you create the file.

## Decisions this plan makes where the spec is silent or ambiguous

Each is marked **[confirm]** where a product owner should agree before launch. The code isolates each one behind config or a single class so changing it is cheap.

| # | Decision | Where |
|---|---|---|
| D1 | Blueprints are versioned JSON files on the classpath (`storybook/blueprints/<key>.v<version>.json`), edited through pull requests, not a DB table. Each book stores a snapshot of the beats it was written from, which is what the spec's `blueprint_version` column is for. **[confirm]** | 01 |
| D2 | Words per page by age band: 3–5 → at most 25, 6–8 → at most 45, 9–10 → at most 70. Starting values for Phase 0 to tune. **[confirm]** | 01 |
| D3 | MSA text is always generated fully vocalized and stored that way; the tashkeel level is applied when rendering, so changing it never needs a new LLM call. `PARTIAL` keeps shadda and tanween only. **[confirm with the native editor in Phase 0]** | 01 |
| D4 | Choosing a dialect forces tashkeel `NONE`; any other combination is rejected with 400 (spec: dialects have "no tashkeel"; the tashkeel input is "MSA only"). | 02 |
| D5 | At most one companion in the MVP (spec allows "max 1–2"; each extra character costs consistency). | 02 |
| D6 | Visual QA: first generation plus up to 3 retries. Generations 1–2 use Nano Banana 2, generations 3–4 use Nano Banana Pro (spec: Pro is "the fallback for pages that fail QA"). After the 4th failure the page is flagged for manual review. | 05 |
| D7 | The uploaded photo is deleted as soon as the character sheet image has been generated from it (spec: "deletion right after the character sheet is made"). If the parent asks for a different look afterwards, the new sheet is generated from the previous sheet plus edited attributes, not from the photo. | 05 |
| D8 | Credits are reserved when the parent approves the story (just before the first image is paid for), committed when the book reaches `READY`, and released if the book is cancelled or permanently fails. Drafting stories is free but rate-limited (3 drafts per user per day). **[confirm]** | 07 |
| D9 | Final-review page regeneration happens after `READY`: `READY → ILLUSTRATING → … → RENDERING → READY`, capped at 3 regenerations per book. The spec's state diagram has no edge out of `READY`; this adds one. **[confirm]** | 03, 05 |
| D10 | Phase 0 benchmarks Nano Banana 2 against Nano Banana Pro only. FLUX.2 Pro and GPT Image 2.5 adapters are built only if Nano Banana fails the exit criteria. The spec's roadmap row says "3–4 image models" while its decision section says FLUX and GPT Image are alternatives "if Phase 0 shows consistency problems"; this plan follows the decision section. **[confirm]** | 01 |
| D11 | Phase 0 exit criteria (the spec gives none): automated identity QA passes on ≥ 85% of first-attempt pages and ≥ 97% after retries; human consistency score averages ≥ 4 / 5; native-editor Arabic grade averages ≥ 4 / 5 with zero gender-agreement errors in MSA stories; mean image cost ≤ $3.50 per 15-page book. **[confirm]** | 01 |
| D12 | Story-generation workers run inside the Ktab app on their own bounded executor (default 4 threads), separate from the OCR executors, and are switched off with `ktab.storybook.enabled=false`. | 03 |
| D13 | *(Amendment 2, 2026-09-28.)* Parents can write their own blueprint:<ul><li>a title of 2–80 characters;</li><li>exactly 10, 12 or 15 beats, one per page, each 10–300 characters;</li><li>at most 20 saved per parent.</li></ul>A book made from it must have `pageCount == beats`. Title and beats are moderated together when saved. The blueprint is snapshotted into the book as `custom-{id}` v`{revision}`, like catalogue blueprints (D1). In the story prompt the beats are fenced as parent-written story content, not instructions. **[confirm]** | 08 |
| D14 | *(Amendment 2.)* The parent sees the AI-written title and page text and may edit them in `STORY_READY`, `CHARACTER_READY`, `ILLUSTRATING`, `QA` and `READY`. Not in `DRAFT`, `RENDERING`, `FAILED` or `CANCELLED`. At most 60 edits per book. The AI original is kept, so it can be compared and restored. **[confirm]** | 08 |
| D15 | *(Amendment 2.)* Edited text must:<ul><li>stay within the age band's word limit (≤ 600 characters; ≤ 60 for the title);</li><li>pass moderation.</li></ul>Dialect text has its tashkeel stripped. MSA text is vocalized by Claude, and the result is accepted only if every letter is unchanged; otherwise the parent's exact text is kept. The child's name spelling is enforced. | 08 |
| D16 | *(Amendment 2.)* Text edits never regenerate illustrations. An edit in `READY` re-renders the PDF only, through a new `READY → RENDERING` edge. One `renderRevision` counter names every PDF render; it replaces `pageRegenerations`, so no PDF key is ever reused. | 08 |

## Sub-plans (execute in this order)

| # | File | Delivers | Depends on | Spec phase |
|---|---|---|---|---|
| 01 | `2026-09-24-storybook-01-ai-core-and-spike.md` | Dependencies, config, Claude gateway, Gemini image provider, cost calculator, blueprints, prompts, dialect guides, text checks, story/critic/QA/moderation services (all pure, no DB), and the two Phase 0 spike harnesses | — | Phase 0 |
| — | **Gate:** run both spikes, record results in `docs/storybook/phase-0-results.md`, go/no-go against D11 | | 01 | Phase 0 |
| 02 | `2026-09-24-storybook-02-domain-foundation.md` | Migration, enums, entities, repositories, AI-call ledger, child profile + storybook REST API with validation and ownership checks | 01 | MVP |
| 03 | `2026-09-24-storybook-03-job-orchestrator.md` | State machine, job table claiming with `SKIP LOCKED`, retries with backoff, idempotent enqueue, worker, resume | 02 | MVP |
| 04 | `2026-09-24-storybook-04-story-pipeline.md` | Story-plan and critic step handlers, per-page regeneration, story approval gate | 01–03 | MVP |
| 05 | `2026-09-24-storybook-05-illustration.md` | Photo vault, character sheet, look approval gate, page illustration, visual QA loop, fallback model, photo purge | 01–04 | MVP |
| 06 | `2026-09-24-storybook-06-render-and-reader.md` | RTL HTML template, Playwright PDF renderer, render step, reader manifest, download URL, Docker changes | 02, 03, 05 | MVP |
| 07 | `2026-09-24-storybook-07-credits-and-hardening.md` | Credit port + admin-granted adapter, draft rate limit, cost guard, admin review queue, metrics, launch checklist | 02–06 | MVP |
| 08 | `2026-09-28-storybook-08-custom-blueprints-and-text-editing.md` | *(Amendment 2)* Parent-written blueprints (save, edit, reuse) and editable, revertible story text with moderation, guarded vocalization and PDF-only re-render | 01–07 | MVP |

Each sub-plan ends with working, tested software: after 02 you can create profiles and books through the API; after 04 a book reaches `STORY_READY` with checked Arabic text; after 05 it reaches `QA`/`RENDERING`; after 06 it reaches `READY` with a downloadable PDF.

## Shared contracts

Every sub-plan uses these names. A task's implementer sees only their own sub-plan, so this table is the source of truth for names and types. Base package: `com.doova.ktab.features.storybook` (abbreviated `sb` below).

### Enums (`sb.enums`) — created in 01 (AI-facing) and 02 (domain)

| Enum | Values | Created in |
|---|---|---|
| `ChildGender` | `BOY, GIRL` | 01 |
| `AgeBand` | `AGE_3_5, AGE_6_8, AGE_9_10` — method `int maxWordsPerPage()` | 01 |
| `LanguageVariety` | `MSA, LEBANESE, EGYPTIAN, GULF` | 01 |
| `TashkeelLevel` | `FULL, PARTIAL, NONE` | 01 |
| `TextZone` | `TOP, BOTTOM` | 01 |
| `LlmPurpose` | `STORY_PLAN, STORY_PAGE_REWRITE, STORY_CRITIC, VISUAL_QA, MODERATION` | 01 |
| `Interest` | `FOOTBALL, CATS, DOGS, DINOSAURS, SPACE, SEA_CREATURES, DRAWING, MUSIC, CARS, HORSES, BOOKS, COOKING` — `String en()` | 01 |
| `StorySetting` | `BEIRUT, CAIRO, RIYADH, DUBAI, AMMAN, GENERIC_CITY, COUNTRYSIDE` — `String sceneEn()` | 01 |
| `ArtStyle` | `SOFT_WATERCOLOR` (one style in the MVP) | 02 |
| `StorybookStatus` | `DRAFT, STORY_READY, CHARACTER_READY, ILLUSTRATING, QA, RENDERING, READY, FAILED, CANCELLED` | 02 |
| `JobStep` | `STORY_PLAN, STORY_CRITIC, CHARACTER_SHEET, ILLUSTRATE_PAGE, QA_PAGE, RENDER_PDF, PURGE_PHOTO` | 02 |
| `JobStatus` | `PENDING, RUNNING, SUCCEEDED, DEAD` | 02 |
| `PageKind` | `COVER, STORY` | 02 |
| `PageImageStatus` | `GENERATED, QA_PASSED, QA_FAILED, FLAGGED, ACCEPTED_BY_ADMIN` | 02 |
| `CharacterKind` | `CHILD, COMPANION` | 02 |
| `CharacterSheetStatus` | `NOT_STARTED, GENERATED, APPROVED` | 02 |

### Key types

| Type | Package | Signature | Created in |
|---|---|---|---|
| `StorybookProperties` | `sb.config` | `@ConfigurationProperties("ktab.storybook")` | 01 |
| `LlmGateway` | `sb.llm` | `<T> LlmCall<T> call(LlmRequest<T> request)` | 01 |
| `LlmRequest<T>` | `sb.llm` | `record(LlmPurpose purpose, String system, String user, List<LlmImage> images, Class<T> responseType, int maxTokens)`; `static of(purpose, system, user, type)`; `withImages(List<LlmImage>)` | 01 |
| `LlmCallFailedException` | `sb.llm` | `RuntimeException` with `boolean retryable()` | 01 |
| `LlmImage` | `sb.llm` | `record(byte[] bytes, String mediaType)` | 01 |
| `LlmCall<T>` | `sb.llm` | `record(T value, String model, long inputTokens, long outputTokens, long latencyMs)` | 01 |
| `ImageProvider` | `sb.image` | `ImageResult generate(ImageRequest request)` | 01 |
| `ImageRequest` | `sb.image` | `record(String model, String prompt, List<ReferenceImage> references)` | 01 |
| `ReferenceImage` | `sb.image` | `record(byte[] bytes, String mimeType)` | 01 |
| `ImageResult` | `sb.image` | `record(byte[] bytes, String mimeType, String model, long latencyMs)` | 01 |
| `ImageGenerationException` | `sb.image` | `RuntimeException` with `boolean retryable()` | 01 |
| `ImageDownscaler` | `sb.image` | `static byte[] toJpeg(byte[] image, int maxSidePx)` | 01 |
| `PromptLibrary` | `sb.prompt` | `String get(String name)`, `String dialectGuide(LanguageVariety v)` | 01 |
| `ChildAppearance` | `sb.character` | `record(SkinTone, HairColor, HairStyle, EyeColor, boolean hijab, boolean glasses)` with nested enums; `String describeEn()` | 01 |
| `CompanionSpec` | `sb.character` | `record(CompanionType type, String nameAr, ChildAppearance siblingAppearance, PetColor petColor)`; `String describeEn()` | 01 |
| `CharacterPrompts` | `sb.character` | static `sheet(gender, band, appearance)`, `sheetFromPhoto(gender, band)`, `companionSheet(companion)`, `scene(sceneEn, zone, hasCompanion, hijab)`, `cover(coverSceneEn, hasCompanion, hijab)` | 01 |
| `CostCalculator` | `sb.cost` | `BigDecimal llmCostUsd(String model, long in, long out)`, `BigDecimal imageCostUsd(String model)` | 01 |
| `Blueprint`, `BlueprintBeat` | `sb.blueprint` | records; `List<BlueprintBeat> beatsFor(int pageCount)` | 01 |
| `BlueprintCatalog` | `sb.blueprint` | `Blueprint get(String key)`, `List<Blueprint> forAgeBand(AgeBand band)` | 01 |
| `StoryRequest` | `sb.story` | `record(String childNameAr, ChildGender gender, AgeBand ageBand, ChildAppearance appearance, LanguageVariety variety, Blueprint blueprint, int pageCount, List<Interest> interests, CompanionSpec companion, StorySetting setting)` | 01 |
| `StoryPlanResponse` | `sb.story` | `record(String titleAr, String coverSceneEn, List<PagePlan> pages)`; `withPage(PagePlan)` | 01 |
| `PagePlan` | `sb.story` | `record(int pageNumber, String textAr, String sceneEn, List<CharacterInScene> characters, TextZone textZone)`; `withText(String)` | 01 |
| `CharacterInScene` | `sb.story` | `record(String ref, String emotion)` — ref is `CHILD` or `COMPANION` | 01 |
| `StoryWriter` | `sb.story` | `LlmCall<StoryPlanResponse> writePlan(StoryRequest r)`, `LlmCall<PagePlan> rewritePage(StoryRequest r, PagePlan page, List<String> problems)` | 01 |
| `StoryPlanInvalidException` | `sb.story` | extends `LlmCallFailedException`, always retryable | 01 |
| `StoryCritic` | `sb.story` | `CriticReport review(StoryRequest r, StoryPlanResponse plan)` | 01 |
| `CriticReport` | `sb.story` | `record(StoryPlanResponse plan, Map<Integer, List<String>> problemsByPage, LlmCall<CriticResponse> llmCall)`; `allPass()`, `failingPages()`; page 0 = title | 01 |
| `TashkeelFilter` | `sb.story` | `static String apply(String text, TashkeelLevel level)` | 01 |
| `ArabicText`, `NameEnforcer`, `DeterministicTextChecks` | `sb.story` | static helpers; `NameEnforcer.enforce(String text, String typedName)` | 01 |
| `VisualQa` | `sb.illustration` | `LlmCall<VisualQaResponse> check(byte[] candidate, List<ReferenceImage> references, String sceneEn)` — references ordered CHILD sheet, style reference, COMPANION sheet | 01 |
| `VisualQaResponse` | `sb.illustration` | `record(boolean identityMatch, boolean strayText, boolean anatomyOk, boolean safeForChildren, List<String> problems)`; `passed()` | 01 |
| `ModerationService` | `sb.story` | `ModerationOutcome moderate(String text)`; `record ModerationOutcome(boolean allowed, String reason, LlmCall<ModerationResponse> llmCall)` (llmCall null when a rule rejected without the LLM) | 01 |
| `FakeLlmGateway`, `StoryFixtures` (test support) | `src/test/.../sb.support` | `enqueue(Object)`, `requests()`; `request(variety, gender, pageCount)`, `plan(pageCount, text)`, `APPEARANCE`, `CATALOG` | 01 |
| `Storybook`, `StorybookPage`, `StorybookPageImage`, `StorybookCharacter`, `ChildProfile`, `StorybookJob`, `StorybookAiCall` | `sb.model` | JPA entities extending `BaseEntity` | 02 |
| `AiCallLedger` | `sb.cost` | `BigDecimal recordLlm(Long bookId, Long jobId, LlmPurpose purpose, LlmCall<?> call)`, `BigDecimal recordImage(Long bookId, Long jobId, String purpose, ImageResult result)`, `void recordFailure(...)` — each `REQUIRES_NEW` | 02 |
| `StorybookEntityFixtures` (test support) | `src/test/.../sb.support` | `static Storybook newBook(TestEntityManager em, User owner)` | 02 |
| `StorybookAssetStore`, `StorybookKeys` | `sb.storage` | deterministic-key R2 access: `put/get/exists/delete`; `characterSheet(...)`, `pageImage(...)`, `photo(...)`, `pdf(...)` | 05 |
| `StorybookAccessGuard` | `sb.web` | `Storybook requireOwned(Long bookId, User user)` | 02 |
| `StorybookStateMachine` | `sb.orchestrator` | `void transition(Storybook book, StorybookStatus to)`, `void fail(Storybook book, String reason)`; `void cancel(Storybook book)` added in 07 | 03 |
| `JobEnqueuer` | `sb.orchestrator` | `boolean enqueue(Long bookId, JobStep step, int pageIndex, int generation)` — joins the caller's transaction (`MANDATORY`); key `book:STEP:page:generation` | 03 |
| `StepHandler` | `sb.orchestrator` | `JobStep step()`, `StepOutcome handle(StorybookJob job)` | 03 |
| `StepOutcome` | `sb.orchestrator` | `static success()`, `static retry(String reason)`, `static fail(String reason)` | 03 |
| `StorybookCreditPort` | `sb.billing` | `void reserve(Long userId, Long bookId, int units)` (402 when short), `void commit(Long bookId)`, `void release(Long bookId)`, `int balance(Long userId)`, `void grant(Long userId, int units)` | 07 |

### REST contract (paths are relative to the same base as Ktab's `controller/v1` controllers)

| Method + path | Who | Purpose | Sub-plan |
|---|---|---|---|
| `POST /storybook/children` | owner | Create a child profile | 02 |
| `GET /storybook/children` | owner | List my child profiles | 02 |
| `PUT /storybook/children/{childId}` | owner | Update a child profile | 02 |
| `DELETE /storybook/children/{childId}` | owner | Delete a child profile and its books | 02 |
| `GET /storybook/blueprints?ageBand=AGE_6_8` | any signed-in user | Blueprint catalogue for the wizard | 02 |
| `GET /storybook/custom-blueprints` | owner | My saved blueprints | 08 |
| `POST /storybook/custom-blueprints` | owner | Save a parent-written blueprint (title + 10/12/15 beats; moderated) | 08 |
| `PUT /storybook/custom-blueprints/{id}` | owner | Edit it (bumps its revision; existing books keep their snapshot) | 08 |
| `DELETE /storybook/custom-blueprints/{id}` | owner | Delete it (existing books unaffected) | 08 |
| `POST /storybook/books` | owner | Create a book; starts story generation. Body takes **exactly one** of `blueprintKey` and `customBlueprintId` (08) | 02, 04, 08 |
| `GET /storybook/books` | owner | List my books with status | 02 |
| `GET /storybook/books/{bookId}` | owner | Status, pages' text (from `STORY_READY`), sheet URL (from `CHARACTER_READY`) | 02, 04, 05 |
| `POST /storybook/books/{bookId}/story/approve` | owner | Approval gate 1 | 04 |
| `PUT /storybook/books/{bookId}/pages/{pageIndex}/text` | owner | Edit a page's Arabic text (D14–D16) | 08 |
| `POST /storybook/books/{bookId}/pages/{pageIndex}/text/revert` | owner | Restore the AI's text for that page | 08 |
| `PUT /storybook/books/{bookId}/title` | owner | Edit the book title | 08 |
| `POST /storybook/books/{bookId}/title/revert` | owner | Restore the AI's title | 08 |
| `POST /storybook/books/{bookId}/photo` (multipart `photo`, `consent=true`) | owner | Optional photo, before story approval | 05 |
| `POST /storybook/books/{bookId}/character/regenerate` | owner | New look, max 2 before approval | 05 |
| `POST /storybook/books/{bookId}/character/approve` | owner | Approval gate 2 | 05 |
| `POST /storybook/books/{bookId}/pages/{pageIndex}/regenerate` | owner | Final-review regeneration, max 3 per book | 05 |
| `GET /storybook/books/{bookId}/reader` | owner | Flipbook manifest | 06 |
| `GET /storybook/books/{bookId}/download` | owner | Short-lived PDF download URL | 06 |
| `POST /storybook/books/{bookId}/resume` | owner | Resume a `FAILED` book | 03 |
| `POST /storybook/books/{bookId}/cancel` | owner | Cancel before `ILLUSTRATING`; releases credits | 07 |
| `GET /admin/storybook/flagged-pages` | `ADMIN` | Pages that failed QA 4 times | 07 |
| `POST /admin/storybook/pages/{pageId}/accept` | `ADMIN` | Accept the latest image as-is | 07 |
| `POST /admin/storybook/pages/{pageId}/regenerate` | `ADMIN` | One more generation on the fallback model | 07 |
| `POST /admin/storybook/credits` | `ADMIN` | Grant credits to a user (beta) | 07 |

## Global Constraints

Every task in every sub-plan implicitly includes these. Values are copied from the spec.

- A book is: a cover, a dedication page, 10, 12 or 15 story pages, and a back page.
- Each story page has one illustration and 1–4 sentences.
- Output is a 21×21 cm square PDF; the web preview is an RTL flipbook.
- A 15-page book needs about 17 images: the character sheet, the cover and one per story page.
- Images are generated at 2K (2048 px), square, text-free, with an empty zone left for the text. No bleed.
- Arabic text is never drawn by the image model; it is typeset in an RTL HTML template.
- The PDF is rendered with headless Chromium through Playwright for Java. Do not use PDFBox, JasperReports or xhtml2pdf for rendering.
- Gender is required; grammatical agreement is checked on every page.
- Age bands: 3–5, 6–8, 9–10.
- Language varieties: MSA (default), Lebanese, Egyptian, Gulf. Dialects are written without tashkeel and follow their style guide.
- Tashkeel level (full, partial, none) applies to MSA only.
- The only free-text field is the dedication, and it is moderated. The child's name is Arabic script, optional tashkeel, rendered exactly as typed.
- Art style is chosen from a curated catalogue, never free-form.
- Claude model for text, critic, QA and moderation: `claude-sonnet-5`.
- Image models: primary `gemini-3.1-flash-image` (Nano Banana 2), fallback `gemini-3-pro-image` (Nano Banana Pro).
- A page failing visual QA is retried up to 3 times, then flagged for manual review.
- Every AI call is logged with model, cost and latency. Every step is idempotent, keyed by book + step + page, so a retry never charges twice.
- `FAILED` resumes from the last completed step rather than restarting the book.
- Child data is limited to first name, gender, age band and appearance. No surname, school or location beyond the chosen story setting.
- Photos: explicit parental consent, deleted right after the character sheet is made, never used for training, encrypted while stored.
- Religious themes are separate, opt-in blueprints.
- Ktab conventions: tables `tbl_storybook_*` with `col_*` columns and the `BaseEntity` audit block (`col_id`, `created_at`, `updated_at`, `col_created_by`, `col_last_modified_by`, `version`); entities extend `com.doova.ktab.model.base.BaseEntity`; controllers return `ApiResponse<T>`; errors are `KtabException` subclasses with an `ApiMessageKey`; files go through `FileStorageService`.

## Review Focus

The inputs and failure modes the spec implies but does not spell out, most likely to hurt a real parent first. Each has a test in the task that owns the code.

1. **A child's name typed with tashkeel (مُحَمَّد) or with a different alif (أحمد / احمد).** Expected: the book prints the name exactly as typed, and the name-consistency check compares names with tashkeel stripped, so a page is not rejected for vocalizing the name. Test: sub-plan 01, Task 9 (`DeterministicTextChecksTest`).
2. **Two Ktab instances polling the job table at once.** Expected: each job runs on exactly one worker. Test: sub-plan 03, Task 4 (`JobClaimerConcurrencyIT` with Testcontainers).
3. **The worker dies after the image provider has returned a paid image but before the job is marked done.** Expected: the re-run finds the stored image for that page and generation and does not call the provider again. Test: sub-plan 05, Task 6 (`PageIllustrationHandlerTest.doesNotRegenerateWhenVersionAlreadyStored`).
4. **A signed-in user requests another user's book, child profile or PDF by id.** Expected: 404, indistinguishable from a book that does not exist. Test: sub-plan 02, Task 6 (`StorybookAccessGuardTest`) and Task 5 (`ChildProfileServiceTest.otherUsersProfileIsNotFound`); sub-plan 06, Task 5 (`ReaderServiceTest.otherUsersBookIsNotFound`).
5. **A dedication containing HTML or script (`<img src=x onerror=…>`).** Expected: printed literally as text in the PDF and the reader; nothing executes in Chromium. Test: sub-plan 06, Task 2 (`StorybookHtmlBuilderTest.escapesDedication`).

## Non-code prerequisites (owners outside engineering)

These block launch, not development. Code loads them from fixed classpath locations and fails fast at startup if they are missing.

| Item | Owner | Needed by | Location in repo |
|---|---|---|---|
| Style reference image for `SOFT_WATERCOLOR` (square PNG, 1024–2048 px, no text) | Art director | Sub-plan 01 spike | `src/main/resources/storybook/styles/soft_watercolor.png` |
| 6–8 blueprints (MVP) following the schema in sub-plan 01 Task 7; Eid and Ramadan as separate blueprints with `"religious": true` | Editors | Sub-plan 04 launch | `src/main/resources/storybook/blueprints/` |
| Native reviewers for Lebanese, Egyptian, Gulf; sign-off on the dialect guides | Content lead | Phase 0 grading | `src/main/resources/storybook/dialects/` |
| Native MSA editor for Phase 0 grading and the `PARTIAL` tashkeel rule (D3) | Content lead | Phase 0 | — |
| Arabic book font licensed for embedding (plan uses Noto Naskh Arabic, SIL OFL) | Engineering | Sub-plan 06 | `src/main/resources/storybook/fonts/NotoNaskhArabic-Regular.ttf` |
| Photo consent wording, retention policy page, and legal review for GDPR, Saudi PDPL, UAE PDPL per launch market | Legal | Sub-plan 05 launch | — |
| Launch markets, payment provider and retail price (ticked in the spec, values not written down) | Product | Sub-plan 07 go-live | — |

## Frontend track (Ktab web client repo — not part of these sub-plans)

Screens the client builds against the REST contract above: child-profile form with the avatar builder (skin tone, hair colour and style, hijab, glasses, eye colour) and a live name preview; the book wizard (blueprint, interests, companion, setting, style, page count, variety, tashkeel, dedication), where the blueprint step offers "choose a ready story" or "write my own" (title plus 10/12/15 page beats, saved for reuse, 08); a "writing your story" progress state polling `GET /storybook/books/{id}`; story approval showing each page's text with inline editing, an "Edited" badge and "restore AI version" (08), and the same editing after the book is ready with a "PDF updating, pictures unchanged" banner; optional photo upload with the consent checkbox; look approval with one "try another look" action; a progress state while illustrating; the RTL flipbook reading the reader manifest; per-page "regenerate" during final review; and the download button.

## Self-review record

- Spec coverage: every spec section maps to a sub-plan task; the amendment table above records the Ktab-specific deviations.
- Placeholder scan: done on each sub-plan.
- Type consistency: names in "Shared contracts" were checked against each sub-plan's Interfaces blocks.
- Review Focus: the five items above each point to an owning task with a named test.

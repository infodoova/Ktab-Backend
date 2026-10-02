# Storybook 04 — Story Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Read `2026-09-24-storybook-00-overview.md` first — its "Shared contracts" and "Global Constraints" sections apply to every task here.

**Goal:** Turn a new `DRAFT` book into checked Arabic text the parent can read, then let the parent approve it (approval gate 1).

**Architecture:** Two step handlers plug into the orchestrator from sub-plan 03. `STORY_PLAN` calls `StoryWriter` (sub-plan 01) and stores the title, cover scene and pages. `STORY_CRITIC` runs `StoryCritic`, rewrites failing pages one at a time (up to 2 rounds), and either accepts the story (`DRAFT → STORY_READY`) or discards it and asks for a fresh plan (at most 3 plans per book). All LLM calls happen outside database transactions; every write goes through `StoryPersistence`, whose methods are short transactions that also enqueue the next job. Approving the story records the approval and enqueues `CHARACTER_SHEET` (handled in sub-plan 05).

**Tech Stack:** Spring Boot 3.5.7, Spring Data JPA, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-24-personalized-storybook-spec.md`

## Global Constraints

See the overview. Most relevant here:

- The story is approved before any image is paid for.
- A failing page is regenerated on its own (spec, "Arabic text and critic pass").
- Every AI call is logged with model, cost and latency (`AiCallLedger`, sub-plan 02).
- Handlers are idempotent: a re-run after a crash must not duplicate pages or redo finished work.

## Review Focus

- **The critic keeps failing the same page.** Expected: after 2 rewrite rounds the whole story is regenerated (fresh plan, generation +1); after the 3rd plan also fails, the book goes to `FAILED` with the critic's problems in `failureReason` — never an endless loop of paid calls. Test: Task 3, `StoryCriticHandlerTest.givesUpAfterThreePlans`.
- **The worker crashes after the plan was saved but before the job was marked done.** Expected: the re-run sees the stored pages, does not call the LLM again, and makes sure the critic job exists. Test: Task 2, `StoryPlanHandlerTest.doesNotRewriteAStoredPlan`.
- **The parent double-clicks "approve".** Expected: first call 202, second call 409, exactly one `CHARACTER_SHEET` job. Test: Task 4, `StoryApprovalServiceIT`.

## File structure

```
src/main/java/com/doova/ktab/features/storybook/story/pipeline/
├── StoryContext.java  StoryPersistence.java        (Task 1)
├── StorybookCreatedListener.java                   (Task 1)
├── StoryPlanHandler.java                           (Task 2)
├── StoryCriticHandler.java                         (Task 3)
└── StoryApprovalService.java                       (Task 4)
src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java  (Task 4: approve endpoint)
```

---

### Task 1: `StoryPersistence`, `StoryContext` and the created-book listener

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/pipeline/StoryContext.java`, `StoryPersistence.java`, `StorybookCreatedListener.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/pipeline/StoryPersistenceIT.java`, `StorybookCreatedListenerTest.java`

**Interfaces:**
- Consumes: `StorybookRepository`, `StorybookPageRepository`, `Storybook`, `StorybookPage`, `PageKind`, `StorybookStatus`, `JobStep`, `StorybookCreatedEvent` (02); `JobEnqueuer`, `StorybookStateMachine` (03); `StoryRequest`, `StoryPlanResponse`, `PagePlan`, `CharacterInScene` (01).
- Produces:
  - `record StoryContext(Long bookId, StorybookStatus status, StoryRequest request, StoryPlanResponse storedPlan)` — `storedPlan` is null when the book has no pages.
  - `StoryPersistence` (each method `@Transactional`):
    - `StoryContext load(Long bookId)`
    - `void savePlan(Long bookId, StoryPlanResponse plan, int generation)` — sets title and cover scene, replaces all pages (cover at index 0 + story pages 1..N), enqueues `STORY_CRITIC` with the same generation.
    - `void acceptStory(Long bookId, StoryPlanResponse checkedPlan)` — writes the name-enforced texts and title, clears `criticProblems`, `DRAFT → STORY_READY`.
    - `void restartPlan(Long bookId, int nextGeneration, Map<Integer, List<String>> problems)` — deletes the pages and enqueues `STORY_PLAN` for `nextGeneration`.
    - `void failStory(Long bookId, String reason)` — `StorybookStateMachine.fail`.
  - `StorybookCreatedListener.onCreated(StorybookCreatedEvent)` — synchronous `@EventListener`, runs inside the creating transaction, enqueues `STORY_PLAN` (page −1, generation 0). Job and book commit together.

- [ ] **Step 1: Write the failing tests**

`StorybookCreatedListenerTest.java`:
```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorybookCreatedListenerTest {

    @Test
    void enqueuesTheFirstStoryPlan() {
        JobEnqueuer enqueuer = mock(JobEnqueuer.class);
        new StorybookCreatedListener(enqueuer).onCreated(new StorybookCreatedEvent(42L));
        verify(enqueuer).enqueue(42L, JobStep.STORY_PLAN, -1, 0);
    }
}
```

`StoryPersistenceIT.java`:
```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import({StoryPersistence.class, JobEnqueuer.class, StorybookStateMachine.class})
class StoryPersistenceIT extends StorybookJpaIT {

    @Autowired StoryPersistence persistence;
    @Autowired StorybookPageRepository pages;
    @Autowired StorybookJobRepository jobs;
    @Autowired StorybookRepository books;

    @Test
    void savePlanStoresCoverAndPagesAndEnqueuesTheCritic() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p1@example.com"));

        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سامي."), 0);
        em.flush();
        em.clear();

        var stored = pages.findByStorybook_IdOrderByPageIndexAsc(book.getId());
        assertThat(stored).hasSize(11);
        assertThat(stored.get(0).getKind()).isEqualTo(PageKind.COVER);
        assertThat(stored.get(0).getTextAr()).isNull();
        assertThat(stored.get(0).getCharacters()).extracting(c -> c.ref()).containsExactly("CHILD", "COMPANION");
        assertThat(stored.get(10).getPageIndex()).isEqualTo((short) 10);
        assertThat(books.findById(book.getId()).orElseThrow().getTitleAr()).isEqualTo("يومي الأول");
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.STORY_CRITIC + ":0");
    }

    @Test
    void loadRebuildsTheStoredPlan() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p2@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سامي."), 0);
        em.flush();
        em.clear();

        StoryContext ctx = persistence.load(book.getId());
        assertThat(ctx.status()).isEqualTo(StorybookStatus.DRAFT);
        assertThat(ctx.request().childNameAr()).isEqualTo("سامي");
        assertThat(ctx.storedPlan().pages()).hasSize(10);
        assertThat(ctx.storedPlan().coverSceneEn()).isEqualTo("The CHILD at the school gate.");
    }

    @Test
    void acceptStoryMovesTheBookToStoryReady() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p3@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "ذَهَبَ سَامِي."), 0);
        StoryPlanResponse checked = StoryFixtures.plan(10, "ذَهَبَ سامي.");

        persistence.acceptStory(book.getId(), checked);
        em.flush();
        em.clear();

        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(StorybookStatus.STORY_READY);
        assertThat(pages.findByStorybook_IdAndPageIndex(book.getId(), 1).orElseThrow().getTextAr()).isEqualTo("ذَهَبَ سامي.");
    }

    @Test
    void restartDeletesPagesAndEnqueuesTheNextPlan() {
        Storybook book = StorybookEntityFixtures.newBook(em, UserFixtures.reader(em, "p4@example.com"));
        persistence.savePlan(book.getId(), StoryFixtures.plan(10, "x."), 0);

        persistence.restartPlan(book.getId(), 1, Map.of(3, List.of("bad")));
        em.flush();
        em.clear();

        assertThat(pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())).isEmpty();
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.STORY_CRITIC + ":0", JobStep.STORY_PLAN + ":1");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw -q test -Dtest='StorybookCreatedListenerTest,StoryPersistenceIT'`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`StoryContext.java`:
```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryRequest;

/** Plain snapshot handed to handlers so no entity is touched outside a transaction. */
public record StoryContext(Long bookId, StorybookStatus status, StoryRequest request, StoryPlanResponse storedPlan) {
}
```

`StorybookCreatedListener.java`:
```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronous on purpose: runs inside the creating transaction, so book and first job commit together. */
@Component
@RequiredArgsConstructor
public class StorybookCreatedListener {

    private final JobEnqueuer enqueuer;

    @EventListener
    public void onCreated(StorybookCreatedEvent event) {
        enqueuer.enqueue(event.storybookId(), JobStep.STORY_PLAN, -1, 0);
    }
}
```

`StoryPersistence.java`:
```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StoryPersistence {

    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public StoryContext load(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        List<StorybookPage> stored = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        StoryPlanResponse plan = null;
        if (!stored.isEmpty()) {
            String coverScene = stored.get(0).getKind() == PageKind.COVER ? stored.get(0).getSceneEn() : book.getCoverSceneEn();
            plan = new StoryPlanResponse(book.getTitleAr(), coverScene, stored.stream()
                    .filter(p -> p.getKind() == PageKind.STORY)
                    .map(p -> new PagePlan(p.getPageIndex(), p.getTextAr(), p.getSceneEn(), p.getCharacters(), p.getTextZone()))
                    .toList());
        }
        return new StoryContext(bookId, book.getStatus(),
                book.getInputs().toStoryRequest(book.getVariety(), book.getPageCount()), plan);
    }

    @Transactional
    public void savePlan(Long bookId, StoryPlanResponse plan, int generation) {
        Storybook book = books.findById(bookId).orElseThrow();
        book.setTitleAr(plan.titleAr());
        book.setCoverSceneEn(plan.coverSceneEn());
        pages.deleteByStorybook_Id(bookId);
        entityManager.flush();

        List<CharacterInScene> coverCast = new ArrayList<>(List.of(new CharacterInScene("CHILD", "happy")));
        if (book.getInputs().companion() != null) {
            coverCast.add(new CharacterInScene("COMPANION", "happy"));
        }
        pages.save(page(book, 0, PageKind.COVER, null, plan.coverSceneEn(), coverCast, TextZone.TOP));
        for (PagePlan p : plan.pages()) {
            pages.save(page(book, p.pageNumber(), PageKind.STORY, p.textAr(), p.sceneEn(),
                    p.characters() == null ? List.of() : p.characters(), p.textZone()));
        }
        enqueuer.enqueue(bookId, JobStep.STORY_CRITIC, -1, generation);
    }

    @Transactional
    public void acceptStory(Long bookId, StoryPlanResponse checkedPlan) {
        Storybook book = books.findById(bookId).orElseThrow();
        book.setTitleAr(checkedPlan.titleAr());
        for (PagePlan p : checkedPlan.pages()) {
            StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, p.pageNumber()).orElseThrow();
            page.setTextAr(p.textAr());
            page.setSceneEn(p.sceneEn());
            page.setTextZone(p.textZone());
            page.setCriticProblems(null);
        }
        stateMachine.transition(book, StorybookStatus.STORY_READY);
    }

    @Transactional
    public void restartPlan(Long bookId, int nextGeneration, Map<Integer, List<String>> problems) {
        pages.deleteByStorybook_Id(bookId);
        enqueuer.enqueue(bookId, JobStep.STORY_PLAN, -1, nextGeneration);
    }

    @Transactional
    public void failStory(Long bookId, String reason) {
        stateMachine.fail(books.findById(bookId).orElseThrow(), reason);
    }

    private static StorybookPage page(Storybook book, int index, PageKind kind, String text, String scene,
                                      List<CharacterInScene> cast, TextZone zone) {
        StorybookPage p = new StorybookPage();
        p.setStorybook(book);
        p.setPageIndex(index);
        p.setKind(kind);
        p.setTextAr(text);
        p.setSceneEn(scene);
        p.setCharacters(cast);
        p.setTextZone(zone);
        return p;
    }
}
```

`restartPlan` receives `problems` so the call site documents why; they are logged by the handler (Task 3), not stored, because the pages they refer to are deleted.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StorybookCreatedListenerTest,StoryPersistenceIT'`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/story/pipeline src/test/java/com/doova/ktab/features/storybook/story/pipeline
git commit -m "feat(storybook): persist story plans and enqueue story generation on book creation"
```

---

### Task 2: `StoryPlanHandler`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/pipeline/StoryPlanHandler.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/pipeline/StoryPlanHandlerTest.java`

**Interfaces:**
- Consumes: `StepHandler`, `StepOutcome` (03); `StoryWriter` (01); `StoryPersistence` (Task 1); `AiCallLedger` (02); `JobEnqueuer` (03).
- Produces: `StoryPlanHandler implements StepHandler` for `JobStep.STORY_PLAN`:
  1. `load(bookId)`. Status ≠ `DRAFT` → `success()` (stale job).
  2. Pages already stored → the plan for this generation was saved before a crash: make sure `STORY_CRITIC` exists (`enqueueCritic`) and return `success()` **without** calling the LLM.
  3. Otherwise `writer.writePlan(request)` → `ledger.recordLlm(bookId, jobId, STORY_PLAN, call)` → `savePlan(bookId, plan, job.getGeneration())` → `success()`.
  - Exceptions from `StoryWriter` propagate; the worker turns them into retry/fail (sub-plan 03).
  - `StoryPersistence` gains `@Transactional void enqueueCritic(Long bookId, int generation)`.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.story.StoryWriter;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoryPlanHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StoryPersistence persistence = mock(StoryPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final StoryPlanHandler handler = new StoryPlanHandler(new StoryWriter(llm, new PromptLibrary()), persistence, ledger);

    private static StorybookJob job(int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(7L);
        j.setStorybookId(42L);
        j.setStep(JobStep.STORY_PLAN);
        j.setGeneration(generation);
        return j;
    }

    private static StoryContext ctx(StorybookStatus status, boolean withPlan) {
        return new StoryContext(42L, status, StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                withPlan ? StoryFixtures.plan(10, "x.") : null);
    }

    @Test
    void writesAndStoresAPlan() {
        when(persistence.load(42L)).thenReturn(ctx(StorybookStatus.DRAFT, false));
        llm.enqueue(StoryFixtures.plan(10, "ذَهَبَ سامي."));

        StepOutcome outcome = handler.handle(job(1));

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(ledger).recordLlm(eq(42L), eq(7L), eq(LlmPurpose.STORY_PLAN), any());
        verify(persistence).savePlan(eq(42L), any(), eq(1));
    }

    @Test
    void doesNotRewriteAStoredPlan() {
        when(persistence.load(42L)).thenReturn(ctx(StorybookStatus.DRAFT, true));

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
        verify(persistence).enqueueCritic(42L, 0);
        verify(persistence, never()).savePlan(any(), any(), anyInt());
    }

    @Test
    void staleJobForAnApprovedBookDoesNothing() {
        when(persistence.load(42L)).thenReturn(ctx(StorybookStatus.STORY_READY, true));
        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
        verifyNoInteractions(ledger);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StoryPlanHandlerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

Add to `StoryPersistence`:
```java
    @Transactional
    public void enqueueCritic(Long bookId, int generation) {
        enqueuer.enqueue(bookId, JobStep.STORY_CRITIC, -1, generation);
    }
```

`StoryPlanHandler.java`:
```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoryPlanHandler implements StepHandler {

    private final StoryWriter writer;
    private final StoryPersistence persistence;
    private final AiCallLedger ledger;

    @Override
    public JobStep step() {
        return JobStep.STORY_PLAN;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        StoryContext ctx = persistence.load(job.getStorybookId());
        if (ctx.status() != StorybookStatus.DRAFT) {
            return StepOutcome.success();
        }
        if (ctx.storedPlan() != null) {
            persistence.enqueueCritic(ctx.bookId(), job.getGeneration());
            return StepOutcome.success();
        }
        LlmCall<StoryPlanResponse> call = writer.writePlan(ctx.request());
        ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.STORY_PLAN, call);
        persistence.savePlan(ctx.bookId(), call.value(), job.getGeneration());
        return StepOutcome.success();
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=StoryPlanHandlerTest`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/story/pipeline src/test/java/com/doova/ktab/features/storybook/story/pipeline/StoryPlanHandlerTest.java
git commit -m "feat(storybook): add idempotent story plan step handler"
```

---

### Task 3: `StoryCriticHandler`

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/pipeline/StoryCriticHandler.java`
- Test: `src/test/java/com/doova/ktab/features/storybook/story/pipeline/StoryCriticHandlerTest.java`

**Interfaces:**
- Consumes: `StoryCritic`, `StoryWriter`, `CriticReport`, `CriticResponse`, `PageVerdict` (01); `StoryPersistence` (Task 1); `AiCallLedger` (02); `StorybookProperties.getLimits().getCriticRewritesPerPage()` (01).
- Produces: `StoryCriticHandler implements StepHandler` for `JobStep.STORY_CRITIC`; `static final int MAX_PLANS = 3`.
  1. `load`. Status ≠ `DRAFT`, or no stored plan (a restart deleted it) → `success()`.
  2. `review` → record cost. While there are failing **story** pages and rounds < `criticRewritesPerPage` (default 2): rewrite each failing page with `writer.rewritePage(request, page, problems)` (record cost), put it into the plan, `review` again (record cost).
  3. All pass → `acceptStory(bookId, report.plan())` → `success()`.
  4. Still failing (including a failing title, page 0, which has no rewrite path) and `generation + 1 < MAX_PLANS` → `restartPlan(bookId, generation + 1, problems)` → `success()`.
  5. Still failing on the last plan → `failStory(bookId, "Story failed quality checks: " + problems)` → `success()` (the job itself worked; the book is what failed, and the parent can resume it).

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.story.CriticResponse;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.PageVerdict;
import com.doova.ktab.features.storybook.story.StoryCritic;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryWriter;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoryCriticHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PromptLibrary prompts = new PromptLibrary();
    private final StoryPersistence persistence = mock(StoryPersistence.class);
    private final StoryCriticHandler handler = new StoryCriticHandler(new StoryCritic(llm, prompts),
            new StoryWriter(llm, prompts), persistence, mock(AiCallLedger.class), new StorybookProperties());

    private static final String GOOD = "ذَهَبَ سامي إِلَى المَدْرَسَةِ.";

    private static StorybookJob job(int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(1L);
        j.setStorybookId(42L);
        j.setStep(JobStep.STORY_CRITIC);
        j.setGeneration(generation);
        return j;
    }

    private void stored(StoryPlanResponse plan) {
        when(persistence.load(42L)).thenReturn(new StoryContext(42L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), plan));
    }

    private static CriticResponse verdicts(int failingPage) {
        List<PageVerdict> v = new ArrayList<>(IntStream.rangeClosed(0, 10)
                .mapToObj(n -> new PageVerdict(n, true, List.of())).toList());
        if (failingPage >= 0) {
            v.set(failingPage, new PageVerdict(failingPage, false, List.of("gender agreement")));
        }
        return new CriticResponse(v);
    }

    @Test
    void cleanStoryIsAccepted() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(-1));

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).acceptStory(eq(42L), any());
    }

    @Test
    void aFailingPageIsRewrittenOnItsOwn() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(4));                                     // first review: page 4 fails
        llm.enqueue(new PagePlan(4, "ذَهَبَ سامي إِلَى البَيْتِ.", "scene", List.of(), com.doova.ktab.features.storybook.enums.TextZone.TOP)); // rewrite
        llm.enqueue(verdicts(-1));                                    // second review: all pass

        handler.handle(job(0));

        ArgumentCaptor<StoryPlanResponse> accepted = ArgumentCaptor.forClass(StoryPlanResponse.class);
        verify(persistence).acceptStory(eq(42L), accepted.capture());
        assertThat(accepted.getValue().pages().get(3).textAr()).isEqualTo("ذَهَبَ سامي إِلَى البَيْتِ.");
        assertThat(llm.requests()).hasSize(3);
    }

    @Test
    void persistentFailureStartsAFreshPlan() {
        stored(StoryFixtures.plan(10, GOOD));
        PagePlan rewrite = new PagePlan(4, GOOD, "scene", List.of(), com.doova.ktab.features.storybook.enums.TextZone.TOP);
        llm.enqueue(verdicts(4)); llm.enqueue(rewrite);   // round 1
        llm.enqueue(verdicts(4)); llm.enqueue(rewrite);   // round 2
        llm.enqueue(verdicts(4));                         // still failing

        handler.handle(job(0));

        verify(persistence).restartPlan(eq(42L), eq(1), anyMap());
        verify(persistence, never()).acceptStory(any(), any());
    }

    @Test
    void givesUpAfterThreePlans() {
        stored(StoryFixtures.plan(10, GOOD));
        PagePlan rewrite = new PagePlan(4, GOOD, "scene", List.of(), com.doova.ktab.features.storybook.enums.TextZone.TOP);
        llm.enqueue(verdicts(4)); llm.enqueue(rewrite);
        llm.enqueue(verdicts(4)); llm.enqueue(rewrite);
        llm.enqueue(verdicts(4));

        assertThat(handler.handle(job(2)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).failStory(eq(42L), contains("gender agreement"));
        verify(persistence, never()).restartPlan(any(), anyInt(), anyMap());
    }

    @Test
    void aFailingTitleCannotBeRewrittenSoTheStoryRestarts() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(0));

        handler.handle(job(0));

        verify(persistence).restartPlan(eq(42L), eq(1), anyMap());
    }

    @Test
    void staleJobDoesNothing() {
        when(persistence.load(42L)).thenReturn(new StoryContext(42L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), null));
        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StoryCriticHandlerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.story.CriticReport;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryCritic;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoryCriticHandler implements StepHandler {

    /** Plans per book: the first plus two fresh restarts. */
    public static final int MAX_PLANS = 3;

    private final StoryCritic critic;
    private final StoryWriter writer;
    private final StoryPersistence persistence;
    private final AiCallLedger ledger;
    private final StorybookProperties properties;

    @Override
    public JobStep step() {
        return JobStep.STORY_CRITIC;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        StoryContext ctx = persistence.load(job.getStorybookId());
        if (ctx.status() != StorybookStatus.DRAFT || ctx.storedPlan() == null) {
            return StepOutcome.success();
        }

        CriticReport report = review(ctx, ctx.storedPlan(), job);
        int rounds = 0;
        while (!report.allPass() && !report.problemsByPage().containsKey(0)
                && rounds < properties.getLimits().getCriticRewritesPerPage()) {
            StoryPlanResponse plan = report.plan();
            for (int pageNumber : report.failingPages()) {
                PagePlan current = plan.pages().get(pageNumber - 1);
                LlmCall<PagePlan> rewrite = writer.rewritePage(ctx.request(), current, report.problemsByPage().get(pageNumber));
                ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.STORY_PAGE_REWRITE, rewrite);
                plan = plan.withPage(rewrite.value());
            }
            report = review(ctx, plan, job);
            rounds++;
        }

        if (report.allPass()) {
            persistence.acceptStory(ctx.bookId(), report.plan());
        } else if (job.getGeneration() + 1 < MAX_PLANS) {
            log.info("storybook {} story plan {} failed checks, starting a fresh plan: {}",
                    ctx.bookId(), job.getGeneration(), report.problemsByPage());
            persistence.restartPlan(ctx.bookId(), job.getGeneration() + 1, report.problemsByPage());
        } else {
            persistence.failStory(ctx.bookId(), "Story failed quality checks: " + report.problemsByPage());
        }
        return StepOutcome.success();
    }

    private CriticReport review(StoryContext ctx, StoryPlanResponse plan, StorybookJob job) {
        CriticReport report = critic.review(ctx.request(), plan);
        ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.STORY_CRITIC, report.llmCall());
        return report;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw -q test -Dtest=StoryCriticHandlerTest`
Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook/story/pipeline/StoryCriticHandler.java src/test/java/com/doova/ktab/features/storybook/story/pipeline/StoryCriticHandlerTest.java
git commit -m "feat(storybook): add critic step with per-page rewrites and bounded fresh plans"
```

---

### Task 4: Story approval gate

**Files:**
- Create: `src/main/java/com/doova/ktab/features/storybook/story/pipeline/StoryApprovalService.java`
- Modify: `src/main/java/com/doova/ktab/features/storybook/web/StorybookController.java`
- Modify: `src/test/java/com/doova/ktab/features/storybook/web/StorybookControllerTest.java` (new constructor argument)
- Test: `src/test/java/com/doova/ktab/features/storybook/story/pipeline/StoryApprovalServiceIT.java`

**Interfaces:**
- Consumes: `StorybookAccessGuard` (02), `JobEnqueuer` (03), `StorybookStatus`, `JobStep` (02).
- Produces:
  - `StoryApprovalService.approveStory(User owner, Long bookId)` — `@Transactional`; book must be `STORY_READY` with `storyApprovedAt == null`, else `StorybookStateConflictException` (409); sets `storyApprovedAt = now` and enqueues `CHARACTER_SHEET` (page −1, generation 1). The status stays `STORY_READY` until the sheet exists; sub-plan 05 moves it to `CHARACTER_READY`. Sub-plan 07 inserts the credit reservation at the top of this method.
  - Endpoint: `POST /storybook/books/{bookId}/story/approve` → 202 `STORYBOOK_ACTION_ACCEPTED`.

- [ ] **Step 1: Write the failing test**

```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.support.StorybookEntityFixtures;
import com.doova.ktab.features.storybook.support.StorybookJpaIT;
import com.doova.ktab.features.storybook.support.UserFixtures;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({StoryApprovalService.class, StorybookAccessGuard.class, JobEnqueuer.class})
class StoryApprovalServiceIT extends StorybookJpaIT {

    @Autowired StoryApprovalService approvals;
    @Autowired StorybookJobRepository jobs;
    @Autowired StorybookRepository books;

    @Test
    void approvingTwiceIsAConflictAndEnqueuesOneSheetJob() {
        User owner = UserFixtures.reader(em, "approve@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        book.setStatus(StorybookStatus.STORY_READY);
        em.flush();

        approvals.approveStory(owner, book.getId());
        assertThatThrownBy(() -> approvals.approveStory(owner, book.getId()))
                .isInstanceOf(StorybookStateConflictException.class);

        assertThat(books.findById(book.getId()).orElseThrow().getStoryApprovedAt()).isNotNull();
        assertThat(jobs.findByStorybookIdOrderByIdAsc(book.getId()))
                .extracting(j -> j.getStep() + ":" + j.getGeneration())
                .containsExactly(JobStep.CHARACTER_SHEET + ":1");
    }

    @Test
    void cannotApproveADraft() {
        User owner = UserFixtures.reader(em, "approve2@example.com");
        Storybook book = StorybookEntityFixtures.newBook(em, owner);
        assertThatThrownBy(() -> approvals.approveStory(owner, book.getId()))
                .isInstanceOf(StorybookStateConflictException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q test -Dtest=StoryApprovalServiceIT`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement the service**

```java
package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StoryApprovalService {

    private final StorybookAccessGuard guard;
    private final JobEnqueuer enqueuer;

    /** Approval gate 1. The first paid image (the character sheet) is enqueued only after this. */
    @Transactional
    public void approveStory(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        if (book.getStatus() != StorybookStatus.STORY_READY || book.getStoryApprovedAt() != null) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
        book.setStoryApprovedAt(Instant.now());
        enqueuer.enqueue(bookId, JobStep.CHARACTER_SHEET, -1, 1);
    }
}
```

- [ ] **Step 4: Add the endpoint**

In `StorybookController`, add the field `private final com.doova.ktab.features.storybook.story.pipeline.StoryApprovalService storyApprovalService;` (after `resumeService`) and:
```java
    @PostMapping("/books/{bookId}/story/approve")
    public ResponseEntity<ApiResponse<Void>> approveStory(@CurrentUser User user, @PathVariable Long bookId) {
        storyApprovalService.approveStory(user, bookId);
        return ResponseUtils.success(null, ApiMessageKey.STORYBOOK_ACTION_ACCEPTED.getMessage(messageSource), HttpStatus.ACCEPTED);
    }
```

In `StorybookControllerTest.setUp()`, construct the controller as
`new StorybookController(service, messages, mock(StorybookResumeService.class), mock(StoryApprovalService.class))`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -q test -Dtest='StoryApprovalServiceIT,StorybookControllerTest'`
Expected: all PASS.

- [ ] **Step 6: End-to-end check with a live model (manual, optional)**

With `ktab.storybook.enabled=true`, `ANTHROPIC_API_KEY` set and the app running against a dev database:
1. `POST /api/v1/storybook/children` with a profile, then `POST /api/v1/storybook/books`.
2. Poll `GET /api/v1/storybook/books/{id}` until `status` is `STORY_READY` (typically well under a minute).
3. Check that `pages` has 10 entries with Arabic text, and `tbl_storybook_ai_calls` has `STORY_PLAN` and `STORY_CRITIC` rows with costs.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/doova/ktab/features/storybook src/test/java/com/doova/ktab/features/storybook
git commit -m "feat(storybook): add story approval gate"
```

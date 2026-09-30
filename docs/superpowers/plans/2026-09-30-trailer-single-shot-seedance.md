# Single-Shot Seedance 2.5 Trailer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The whole 30-second trailer picture, including the full montage language, is produced by **one** Seedance 2.5 generation. FFmpeg only adds the voice-over, music and SFX and composites a pixel-perfect branded end card. No captions are burned into the video.

**Architecture:** The agent works audio first: it writes and measures the narration, gets forced-alignment timings and generates the music. It then writes one timecoded SHOT TIMELINE that carries the whole montage in the master prompt's language: the energy-wave rhythm, 0.2–0.5 s inserts, 1.5–2 s hero shots, a live quiet hold, designed in-camera transitions, the six motion styles, selective slow motion and speed ramps. FLUX.2 renders the reference frames, and a single `generate_video` call (`duration=30`) renders the film with every cut, ramp and dissolve already inside it. FFmpeg never cuts or retimes the picture. It lays the audio onto it, adjusted to the measured cuts. It then fades in end-card layers (scrim, cover, title, author, logo) that Ktab renders server-side with Playwright and mounts into the session. The captions ship as a downloadable sidecar `captions_ar.srt` only (not burned in, not played in the web player).

**Tech Stack:** Claude Managed Agents (publish via `ops/trailer-agent/update-agent.mjs`), Higgsfield MCP (FLUX.2 `generate_image`, Seedance 2.5 `generate_video`), ElevenLabs (eleven_v3, forced-alignment, music_v2), FFmpeg/ffprobe, Spring Boot, Playwright for Java (already used by `PlaywrightPdfRenderer`), JUnit 5 + AssertJ + Mockito.

**Spec:** this document + the directing source [`docs/trailer/master-directing-prompt.md`](../../trailer/master-directing-prompt.md) (the product owner's master prompt and its Ktab adaptations). It modifies `ops/trailer-agent/system-prompt.md` (agent v14, montage-of-clips). Capability basis: Higgsfield says Seedance 2.5 generates up to 30 s per generation, takes up to 50 image/clip references, and does multi-shot sequences ([higgsfield.ai/blog/seedance-2-5-on-higgsfield-2026](https://higgsfield.ai/blog/seedance-2-5-on-higgsfield-2026)). Task 0 checks this against the live tool.

**Delivery:** do all tasks on one branch and merge only after Task 6's real run passes. Tasks 1–5 change production defaults (job caps, `duration=30`) that the live v14 agent cannot work with, so they must not reach production before v15 is published.

## Global Constraints

- Final film: 30.0 s ±0.5 s, 1920×1080, 16:9, H.264 + AAC, at the source frame rate (see the frame-rate line below).
- **Exactly one accepted Seedance 2.5 generation is the picture.** FFmpeg may not trim (except the tail to 30.0 s), concat, reorder, `xfade`, `setpts`, `minterpolate`, `tpad`, loop or freeze it. Every montage device is prompted into Seedance.
- `generate_audio=false`. All sound comes from ElevenLabs and is mixed by FFmpeg.
- Narration: `eleven_v3`, `language_code: ar`, ≤ 28 s, and it **ends by 27.0 s** so the end card plays over music only.
- **Final 30 s beat map** (the master's structure; its closing beats are scaled for a 2.5 s end card): 00:00–02 restrained reveal · 02–03.5 first burst · 03.5–05.5 first hero shot · 05.5–10 deeper discovery · 10–14.5 expand, build momentum · 14.5–17 move closer, ≈ 1.5 s quiet hold · 17–23 stronger escalation · 23–24.5 defining image · 24.5–26 final rapid burst · 26–27.5 abrupt cut to near-stillness (1.5 s, as in the master) · **27.5–30 end-card plate**.
- Directing language: the master prompt's rhythm (slow anticipation → fast fragments → sustained reveal → deeper discovery → rising momentum → emotional pause → stronger acceleration → final burst → stillness), shot lengths (inserts 0.2–0.5 s, hero shots 1.5–2 s, intermediate lengths between them, shorter flashes only in the final burst), camera vocabulary and transition rules. Every Seedance prompt opens with the fixed `DIRECTING LANGUAGE` block (Task 4).
- **No burned captions.** `captions_ar.srt` stays a deliverable (forced-alignment, Arabic, ≤ 2 lines, ≤ 35 chars per line) as a download.
- Deliverables: `trailer.mp4`, `captions_ar.srt`, `script.md`, `qc_report.json`. `trailer_clean.mp4` is dropped: with no burned captions it would be identical to `trailer.mp4`.
- No text in any generated frame. The only on-screen text is the composited end card.
- End card: the last **2.5 s (27.5–30.0)**, over a Seedance-generated dark, low-detail negative-space plate (**nothing bright or detailed in the centre, left or bottom**, because the card occupies them: cover on the left, text on the right, logo at the bottom centre), built **only** from the Ktab-rendered layers in `/workspace/endcard/`. No `drawtext`, and the agent never types the title itself.
- `qc_report.json` keeps `status`, `frame_check.text_found` and `captions.language` (`TrailerHarvester.java:124-128`).
- Rollback to v14 is env-only: `KTAB_TRAILER_AGENT_VERSION=14`, `KTAB_TRAILER_HIGGSFIELD_GENERATE_ARGS=…duration=5…`, `KTAB_TRAILER_SOFT_HIGGSFIELD_GENERATIONS=35`, `KTAB_TRAILER_MAX_HIGGSFIELD_GENERATIONS=45`, `KTAB_TRAILER_MAX_HIGGSFIELD_CALLS=140`, `KTAB_TRAILER_MAX_VIDEO_JOBS=45`. For this to work, the task message stays **mode-neutral**: "one generation" lives only in the v15 system prompt, never in `TrailerTask`. The extra mounted end-card files are simply unused by v14.
- Frame rate (**decided: keep the source rate**): deliver at the Seedance source frame rate, e.g. **24 fps if Seedance outputs 24**. Never convert 24 ↔ 30 fps: that duplicates or drops frames, adds judder, and counts as re-editing the picture. The harvester does not check fps; `qc_report.json` `fps` records the actual value.
- Logo (**decided: the owner's full logo file**): the product owner supplies the full Ktab logo (mark + wordmark). It is committed as `src/main/resources/trailer/endcard/ktab-logo.png` (transparent PNG, ideally ≥ 600 px wide, light enough to read on a dark scrim). `static/images/logo.png` (the "K" icon only) is **not** used.
- Captions (**decided: download only**): `captions_ar.srt` is stored and offered as a download, as today (`TrailerService.java:86-87`). No WebVTT and no player track.

## Review Focus

1. **Sub-second inserts collapse inside the generation.** Seedance may merge or ignore 0.2–0.5 s shots. Expected: Task 0 measures the shortest insert that renders reliably, the prompt never plans below that floor, and QC counts detected inserts. Pinned in Task 4 (`MIN_INSERT_SECONDS` stated in the prompt).
2. **Long Arabic titles** (60+ characters) or a missing author/cover break the end-card layout. Expected: the title shrinks in steps and wraps to at most 3 lines, and it is **never truncated with an ellipsis** (a cut-off book title is worse than a smaller one). The author layer is skipped when blank, and the text block re-centres when there is no cover. Pinned in Task 2 (`longTitleShrinksAndWraps`, `noCoverCentresTheText`, and the IT's no-overflow check).
3. **Seedance cut drift.** Expected: detect the real cuts with `scdet`, move whole narration phrases ≤ ±0.4 s at natural gaps, and re-roll if the drift is larger. Never retime picture or voice. Pinned in Task 4.
4. **The generated clip is shorter than 30 s.** Expected: re-roll or fail; the picture is never padded. Pinned in Task 4.
5. **The video allowance is only advisory.** `SessionEvents.interpret` counts any tool whose name contains the marker `"generate"`, so `generate_image` and `generate_video` land in one counter (`SessionEvents.java:35-43`). A runaway agent could burn 10 × 30 s video jobs inside a total cap of 16. Expected: video jobs counted separately (tool name `generate_video`), and the reconciler stops the session above `maxVideoJobs`. Pinned in Task 1 (`SessionEventsTest`, `TrailerReconcilerTest`).
6. **A retried launch loses the cover on the end card.** `TrailerLauncher.java:42,51` downloads the cover only when it still needs uploading. On a retry the cover file id is reused, so the end-card renderer would get no cover. Expected: the cover is downloaded whenever the book has one, and uploaded only when needed. Pinned in Task 3 (`retryStillRendersTheCoverOnTheEndCard`).

---

## File Structure

| File | Change | Responsibility |
| --- | --- | --- |
| `ops/trailer-agent/system-prompt.md` | Intro and §1, §3–§10 rewritten or amended in the same hierarchy; §2 unchanged | Agent instructions |
| `src/test/java/.../trailer/agent/TrailerAgentLiveTest.java:30-32` | New `describe` signature (otherwise compilation breaks) | Live test |
| `ops/trailer-agent/agent.json` | `metadata.version` → `v15`, description | Published agent |
| `src/main/resources/trailer-agent/rubric.md` | Criteria 2, 3, 8, 9, 11, 13 rewritten; 3a, 14 added | Grader |
| `src/main/java/.../trailer/agent/SessionSnapshot.java`, `SessionEvents.java` | `videoJobs` counted separately | Enforced video cap |
| `src/main/java/.../trailer/pipeline/TrailerReconciler.java:42-67` | Stop above `maxVideoJobs`; mode-neutral advisory text | Enforced video cap |
| `src/main/java/.../trailer/endcard/EndCardRenderer.java` | **Create** | Renders transparent 1920×1080 PNG layers with Playwright |
| `src/main/java/.../trailer/endcard/EndCardHtml.java` | **Create** | Pure HTML/CSS builder (testable without a browser) |
| `src/main/resources/trailer/endcard/fonts/Cairo-Bold.ttf`, `Cairo-Regular.ttf`, `OFL.txt` | **Create** | Static Cairo instances from the Google Fonts Cairo repo |
| `src/main/java/.../trailer/agent/TrailerAgentGateway.java` | `SessionFile` record; `uploadFile`; new `startSession` | Mount any number of files |
| `src/main/java/.../trailer/agent/AnthropicTrailerAgentGateway.java:77-126` | Implement the above | Anthropic Files API + session resources |
| `src/main/java/.../trailer/pipeline/TrailerLauncher.java:40-70` | Render and upload the end-card layers + logo | Session launch |
| `src/main/java/.../trailer/agent/TrailerTask.java` | Video vs. total allowance | Task text |
| `src/main/java/.../trailer/config/TrailerProperties.java` | `maxVideoJobs`, new defaults | Config |
| `src/main/java/.../trailer/pipeline/TrailerHarvester.java:129` | Message wording ("captions", not "burned-in captions") | Harvest |
| `src/main/resources/application.properties:351-358` | New defaults | Config |
| Tests | `TrailerTaskTest`, `SessionEventsTest`, `TrailerReconcilerTest`, `TrailerLauncherTest`, `EndCardHtmlTest`, `EndCardRendererIT`, `ControlPlaneFilesTest` | |

---

### Task 0: Spike — can one Seedance 2.5 generation carry the montage?

**Files:** Create `docs/trailer/seedance-single-shot-spike.md`

- [ ] **Step 1: Authorize the Higgsfield connector** in claude.ai connector settings (it is not authorized in the Claude Code session).
- [ ] **Step 1b (alternative that needs no connector):** the Anthropic vault already holds a working Higgsfield token for the trailer agent. Run the spike as a throwaway Managed Agents session on a separate "spike" agent with the same MCP config, environment and vault (`ops/trailer-agent/agent.json` with a short spike system prompt).
- [ ] **Step 2: Call `models_explore` for `seedance_2_5`** and record: max `duration` per mode, the mode that accepts reference images, the reference argument name/format and how the prompt addresses references (e.g. `@image1`), **whether references can be tied to individual shots or only to the first/last frame**, the max reference count, the max prompt length, the resolutions available at `duration=30`, the **output frame rate**, and whether `generate_audio=false` is accepted.
- [ ] **Step 3: Generate one 30 s test film** using the **real prompt shape from Task 4** (`DIRECTING LANGUAGE` block + `GLOBAL LOOK` + `SHOT TIMELINE`) with 3 FLUX.2 references. The timeline must deliberately exercise every device: a 2 s slow restrained reveal → a first burst of three inserts of 0.2 s / 0.35 s / 0.5 s → a 2 s hero pullback → a match cut on shape → a partial arc → a foreground object covering the lens → a brief passage through darkness → a speed-ramped forward track (fast → slow → fast) → a 1.5 s near-locked quiet hold (live motion, not frozen) → a final burst with a < 0.2 s flash of an established image → an abrupt cut to a 1.5 s near-stillness → a 2.5 s dark negative-space plate with nothing bright or detailed in the centre, left or bottom. This also measures whether the full prompt fits Seedance's length limit. Also mount one test file at a nested path (`/workspace/endcard/test.png`) to confirm session resources can be mounted into a subdirectory (Task 3 depends on this).
- [ ] **Step 4: Measure.** Detect cuts with `ffmpeg -i film.mp4 -vf "scdet=threshold=10" -f null - 2>&1 | grep lavfi.scd.time` and compare them with the plan. Record: which inserts appeared, the **shortest insert that rendered as a distinct shot** (`MIN_INSERT_SECONDS`), whether the < 0.2 s flash rendered, per-cut drift, whether the occlusion, darkness passage and match cut rendered as planned, whether the speed ramp is visible, whether the quiet hold stayed alive (not frozen), whether the final plate kept the card area dark and empty, the prompt character count against the limit, the actual duration/resolution/fps, credits, the **wall time per 30 s job** (three sequential jobs plus the book reading must fit `ktab.trailer.max-session-age=2h`), a good `scdet` threshold for this footage, and whether `higgsfieldJobPattern` still matches both the `generate_image` and the `generate_video` replies.
- [ ] **Step 5: Stop/go gate.** Go if 30 s is accepted at ≥ 720p and most devices render. Report back to the product owner before continuing if any of these hold:
  - `MIN_INSERT_SECONDS` > 0.5. The "0.2–0.5 s inserts" become "the shortest measured insert", and by the master's rule the shot count is reduced.
  - References can only pin the first/last frame. The per-beat reference plan (§7.A) then shrinks to a master look + first frame.
  - Wall time does not fit the session age.
  - 30 s is not available.
- [ ] **Step 6: Commit** — `git add docs/trailer/seedance-single-shot-spike.md && git commit -m "docs(trailer): Seedance 2.5 single-shot montage spike"`

---

### Task 1: Separate, enforced video-job allowance (mode-neutral task text)

**Files:** `TrailerProperties.java:28-53`, `TrailerTask.java`, `SessionSnapshot.java`, `SessionEvents.java:26-67`, `TrailerReconciler.java:42-67`, `TrailerLauncher.java:60-62`, `application.properties:351-358`, `.env` (local, not committed); tests `TrailerTaskTest.java`, `SessionEventsTest.java`, `TrailerReconcilerTest.java`, `TrailerAgentLiveTest.java:30-32` (signature only)

**Produces:**
- `TrailerTask.describe(String title, String author, String language, String voiceId, int maxVideoJobs, int maxJobs, int maxInFlight, String generateArgs, boolean hasCover)`
- `TrailerProperties#getMaxVideoJobs()`
- `SessionSnapshot#videoJobs()`, a new last record component. A 6-arg constructor is kept that delegates with `videoJobs = 0`, so the existing 13 call sites still compile.

The task message stays **mode-neutral** so an env-only rollback to v14 works. "Exactly one generation" is said only in the v15 system prompt.

- [ ] **Step 1: Failing tests.** In `TrailerTaskTest`, replace the first test and move the others to the 9-arg signature `(…, 3, 16, 2, args, hasCover)`:

```java
@Test
void taskCarriesBookFactsVoiceAndSeparateVideoAndTotalAllowances() {
    String task = TrailerTask.describe("ثورة دونالد ترامب", "ألكسندر دوغين", "ar", "voice-123", 3, 16, 2,
            "model=seedance_2_5, aspect_ratio=16:9, duration=30", false);

    assertThat(task).contains("ثورة دونالد ترامب").contains("ألكسندر دوغين").contains("voice-123")
            .contains("at most 3 video jobs").contains("at most 16 Higgsfield jobs in total")
            .contains("image jobs count toward the total, not toward the video jobs")
            .contains("at most 2 generations").contains("duration=30")
            .contains("/workspace/endcard/").doesNotContain("Cover image:")
            .doesNotContain("one accepted"); // mode-neutral: v14 must still be able to run this task
}
```

In `SessionEventsTest`, add (the file builds events from raw JSON with its `events(String...)` helper, and `REAL_JOB_PATTERN` is the shipped pattern):

```java
@Test
void videoJobsAreCountedSeparatelyFromImageJobs() throws Exception {
    String pending = "[{\"type\":\"text\",\"text\":\"{\\\"results\\\":[{\\\"id\\\":\\\"j\\\",\\\"status\\\":\\\"pending\\\"}]}\"}]";
    SessionSnapshot s = SessionEvents.interpret("running", events(
            "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u1\",\"name\":\"generate_image\"}",
            "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u1\",\"content\":" + pending + "}",
            "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u2\",\"name\":\"generate_image\"}",
            "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u2\",\"content\":" + pending + "}",
            "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u3\",\"name\":\"generate_video\"}",
            "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u3\",\"content\":" + pending + "}"),
            List.of(), "generate", REAL_JOB_PATTERN);

    assertThat(s.higgsfieldGenerations()).isEqualTo(3);
    assertThat(s.videoJobs()).isEqualTo(1);
}
```

In `TrailerReconcilerTest`, add an overload of the file's `snapshot(...)` helper that takes `videoJobs` (and builds the 7-arg `SessionSnapshot`). Then add `videoJobsOverTheVideoAllowanceStopTheSession`, modelled on the existing total-cap test at line 74: `props.setMaxVideoJobs(3)`, a RUNNING snapshot with 8 generations, 8 calls and 4 video jobs. Assert the same stopped outcome that test asserts, with an error containing `"4 video jobs, over the cap of 3"`.

- [ ] **Step 2:** `./mvnw -q test -Dtest='TrailerTaskTest,SessionEventsTest,TrailerReconcilerTest'` → FAIL (compilation).

- [ ] **Step 3: Implement.** `TrailerTask.describe` (replacing both overloads):

```java
public static String describe(String title, String author, String language, String voiceId, int maxVideoJobs,
                              int maxJobs, int maxInFlight, String generateArgs, boolean hasCover) {
    String coverLine = hasCover ? "Cover image: /workspace/cover.jpg\n" : "";
    return """
            Produce the 30-second trailer for this book.
            Book: /workspace/book.pdf
            %sTitle: %s
            Author: %s
            Book language: %s
            ElevenLabs voice_id for the Arabic narration: %s
            End-card layers (rendered by Ktab, composite them as-is): /workspace/endcard/
            Higgsfield video allowance: at most %d video jobs in total, including replacements for a discarded video.
            Higgsfield total allowance: at most %d Higgsfield jobs in total; image jobs count toward the total, not toward the video jobs.
            Higgsfield concurrency: keep at most %d generations in flight at a time.
            Call generate_video with exactly these arguments: %s
            Write every deliverable to /mnt/session/outputs/ as described in your instructions.
            """.formatted(coverLine, title, author == null ? "" : author, language == null ? "ar" : language, voiceId,
            maxVideoJobs, maxJobs, maxInFlight,
            generateArgs == null || generateArgs.isBlank() ? "(see your instructions)" : generateArgs);
}
```

In `SessionEvents.interpret`, track the ids of calls whose tool name contains `generate_video` in a second set, and increment `videoJobs` alongside `generations` when such a call's result matches `jobPattern`. Pass it as the new last component of `SessionSnapshot`.

In `TrailerReconciler`, right after the total-cap check:

```java
if (s.videoJobs() > properties.getMaxVideoJobs()) { // single-shot: each 30 s job is the expensive unit
    stop(t, "Stopped: the agent created " + s.videoJobs() + " video jobs, over the cap of "
            + properties.getMaxVideoJobs() + ".");
    return;
}
```

Make the soft-cap advisory text (`TrailerReconciler.java:64-66`) mode-neutral: `"You have reached your primary generation allowance (N jobs). Do not submit further generation jobs. Finish the trailer with the footage you have already accepted."`

In `TrailerProperties`, add `/** Video jobs the agent may create (single-shot: 1 film + whole-film re-rolls). */ private int maxVideoJobs = 3;` and set `softHiggsfieldGenerations = 14`, `maxHiggsfieldGenerations = 16`, `maxHiggsfieldCalls = 40`, `higgsfieldGenerateArgs` = the Task 0 string. In `application.properties`, set the same defaults and add `ktab.trailer.max-video-jobs=${KTAB_TRAILER_MAX_VIDEO_JOBS:3}`. **In `.env` (local) and the deployment env**, update `KTAB_TRAILER_SOFT_HIGGSFIELD_GENERATIONS`, `KTAB_TRAILER_MAX_HIGGSFIELD_GENERATIONS`, `KTAB_TRAILER_MAX_HIGGSFIELD_CALLS` and `KTAB_TRAILER_HIGGSFIELD_GENERATE_ARGS`: they currently override the defaults with the v14 values 35/45/140/`duration=5` (`.env:100-109`). In `TrailerLauncher`, pass `properties.getMaxVideoJobs(), properties.getMaxHiggsfieldGenerations(), …`. **Also update `TrailerAgentLiveTest.java:30-32`**: it calls the 7-arg `describe` that this task deletes, so it would stop compiling. Change it to `TrailerTask.describe("Live test", "Test author", "ar", p.getVoiceId(), p.getMaxVideoJobs(), p.getMaxHiggsfieldGenerations(), p.getHiggsfieldMaxInFlight(), p.getHiggsfieldGenerateArgs(), false)`.

- [ ] **Step 4:** `./mvnw -q test -Dtest='com.doova.ktab.features.trailer.**'` → the Task 1 tests PASS. `ControlPlaneFilesTest` still has its 2 assertions that already fail today; Task 4 fixes them.
- [ ] **Step 5: Commit** — `feat(trailer): enforced video-job allowance, mode-neutral task text`

---

### Task 2: Server-rendered end-card layers (the "perfect" end card)

Why server-side: FFmpeg `drawtext` shapes Arabic unreliably (it depends on the build's fribidi/harfbuzz), and a different agent run would lay the card out differently every time. Chromium shapes Arabic correctly, and the design is identical on every trailer. The agent only fades the layers in.

**Design (1920×1080), matching the layout the product owner approved (2026-09-30, from a previous trailer):**
- `scrim.png`: a soft dark overlay over the whole frame (radial, centre ≈ 30% → edges ≈ 62%) plus a bottom gradient, so the card reads on any footage.
- `cover.png`: on the **left**: the cover at 580 px high (aspect preserved), left edge x = 240, vertically centred, 6 px radius, soft drop shadow.
- `title.png`: on the **right**, centred in an 800 px column (left edge x = 930): the main title in Cairo Bold, white, with a subtle text shadow. The size steps down with the main title length (≤ 24 chars → 60 px, ≤ 40 → 52 px, ≤ 56 → 44 px, else 38 px), at most 3 lines and **never truncated**: an inline script shrinks it 2 px at a time (floor 32 px) until it fits, and the renderer throws if it still does not.
- `subtitle.png`: only when the title has a subtitle. Ktab stores one title string, so **a title of the form "main: subtitle" is split at its first colon** (main above, subtitle below in Cairo Medium 34 px). No colon means no subtitle layer.
- `author.png`: a 350 px white rule with the author's name below it (Cairo Regular 30 px, 82% white), centred in the same column. Skipped when the author is blank.
- `logo.png`: the owner's logo (`trailer/endcard/ktab-logo.png`, trimmed of transparent padding), 56 px high, **bottom centre** (96 px above the bottom edge). Used as-is.
- With no cover, the text column moves to the screen centre (left edge x = 560).

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/endcard/EndCardHtml.java`, `EndCardRenderer.java`
- Create: `src/main/resources/trailer/endcard/fonts/Cairo-Bold.ttf`, `Cairo-Regular.ttf`, `OFL.txt` (static instances from github.com/google/fonts `ofl/cairo`, or instanced from the variable font at wght 700/400)
- Create: `src/main/resources/trailer/endcard/ktab-logo.png`, **the owner's full logo file, copied in as Step 0**
- Test: `src/test/java/com/doova/ktab/features/trailer/endcard/EndCardHtmlTest.java`, `EndCardRendererIT.java`

**Produces:** `EndCardRenderer#render(EndCardSpec spec, Path outDir) → List<Path>` (file names from `EndCardHtml.Layer`), `record EndCardSpec(String title, String author, Path coverOrNull, Path logo)`, and `enum EndCardHtml.Layer { SCRIM, COVER, TITLE, SUBTITLE, AUTHOR, LOGO }` with `fileName()` → `scrim.png`, etc.

- [ ] **Step 0: Add the logo.** Copy the owner's full logo file to `src/main/resources/trailer/endcard/ktab-logo.png`. Check that it has a transparent background and reads on dark; if it is dark-on-transparent, ask the owner for the light/negative version rather than recolouring it.
- [ ] **Step 1: Failing unit tests** (`EndCardHtmlTest`, no browser needed)

```java
class EndCardHtmlTest {

    private final EndCardSpec full = new EndCardSpec("ثورة دونالد ترامب", "ألكسندر دوغين",
            Path.of("cover.jpg"), Path.of("logo.png"));

    @Test
    void eachLayerShowsOnlyItsOwnElement() {
        String html = EndCardHtml.build(full, EndCardHtml.Layer.TITLE);
        assertThat(html).contains("dir=\"rtl\"").contains("class=\"only-title\"")
                .contains("ثورة دونالد ترامب").contains("Cairo-Bold.ttf");
    }

    @Test
    void longTitleShrinksAndWraps() {
        String longTitle = "عنوان طويل جدا لكتاب يتجاوز الحد المعتاد من الأحرف في سطر واحد";
        String html = EndCardHtml.build(new EndCardSpec(longTitle, "م", null, Path.of("logo.png")),
                EndCardHtml.Layer.TITLE);
        assertThat(html).contains("font-size:40px").doesNotContain("line-clamp").doesNotContain("ellipsis");
    }

    @Test
    void mediumTitleUsesTheMiddleStep() {
        String html = EndCardHtml.build(new EndCardSpec("عنوان متوسط الطول لكتاب جميل جدا", "م", null,
                Path.of("logo.png")), EndCardHtml.Layer.TITLE); // 32 chars → 60 px step
        assertThat(html).contains("font-size:60px");
    }

    @Test
    void noCoverCentresTheText() {
        String html = EndCardHtml.build(new EndCardSpec("ع", "م", null, Path.of("logo.png")),
                EndCardHtml.Layer.TITLE);
        assertThat(html).contains("class=\"stage no-cover\"");
        assertThat(EndCardHtml.layersFor(new EndCardSpec("ع", "م", null, Path.of("logo.png"))))
                .doesNotContain(EndCardHtml.Layer.COVER);
    }

    @Test
    void blankAuthorHasNoAuthorLayer() {
        assertThat(EndCardHtml.layersFor(new EndCardSpec("ع", " ", Path.of("c.jpg"), Path.of("l.png"))))
                .containsExactly(EndCardHtml.Layer.SCRIM, EndCardHtml.Layer.COVER,
                        EndCardHtml.Layer.TITLE, EndCardHtml.Layer.LOGO);
    }

    @Test
    void titleIsHtmlEscaped() {
        String html = EndCardHtml.build(new EndCardSpec("<b>x</b>", "م", null, Path.of("l.png")),
                EndCardHtml.Layer.TITLE);
        assertThat(html).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    }
}
```

- [ ] **Step 2:** `./mvnw -q test -Dtest=EndCardHtmlTest` → compilation FAIL.
- [ ] **Step 3: Implement `EndCardHtml`**: a static `build(EndCardSpec, Layer)` that returns one full HTML document. It has fixed 1920×1080 `.stage` markup containing all elements, and `<body class="only-<layer>">` with CSS `body:not(.only-scrim) .scrim, body:not(.only-cover) .cover, … {visibility:hidden}` so each screenshot contains exactly one element at its final position. `@font-face` loads `fonts/Cairo-Bold.ttf` and `fonts/Cairo-Regular.ttf` relative to the page. `html, body {background:transparent}`. Title font size follows the length rule above. All text goes through `HtmlUtils.htmlEscape` (`org.springframework.web.util.HtmlUtils`; it escapes markup characters and leaves Arabic as-is). `layersFor(spec)` returns the layers in z-order, skipping COVER when `coverOrNull == null` and AUTHOR when the author is blank.
- [ ] **Step 4: Implement `EndCardRenderer`** (`@Component`). It runs once per trailer launch, which is rare, so it uses a **short-lived** browser: `try (Playwright pw = Playwright.create(); Browser b = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setChromiumSandbox(false).setArgs(List.of("--disable-dev-shm-usage"))))`, with the same launch options as `PlaywrightPdfRenderer.java:104-107`. That way there is no second long-lived Chromium beside the storybook one and no lifecycle code to duplicate. The Docker image already has Chromium (`Dockerfile:31-34`). For each layer: copy the fonts, cover and logo into a temp dir, write `layer.html`, then run `page.setViewportSize(1920, 1080)`, `page.navigate(file://…)`, `page.waitForLoadState()`, then `page.waitForFunction("window.__fitted === true")` (in a context with JavaScript enabled, but only `file:` requests routed). The page's inline script runs `document.fonts.ready.then(fit)`: it shrinks the title 2 px at a time down to 32 px until it fits in 3 lines, then sets `window.__overflow` (true if it still does not fit) and `window.__fitted = true`. The renderer throws if `__overflow` is true. and `page.screenshot(new Page.ScreenshotOptions().setOmitBackground(true).setPath(outDir.resolve(layer.fileName())))`.
- [ ] **Step 5: Integration test** `EndCardRendererIT` (a `*IT`, so failsafe runs it on `mvn verify`, like `PlaywrightPdfRendererIT`): render the `full` spec, then assert each PNG is 1920×1080 with an alpha channel (`ImageIO.read(...).getColorModel().hasAlpha()`). Assert the pixel at (10, 10) of `title.png` is fully transparent and that `title.png` has opaque pixels inside the text box. Render a **120-character title** and assert its opaque pixels span at most 3 line-heights (it fits and is not cut off). `EndCardRenderer` itself throws if the title element still overflows at the 32 px floor, so a cut-off title can never ship silently. Write a combined preview (the layers drawn over mid-grey with `Graphics2D`) to `target/endcard-preview.png` for a **manual visual sign-off** of the design.
- [ ] **Step 6:** `./mvnw -q test -Dtest=EndCardHtmlTest && ./mvnw -q verify -Dit.test=EndCardRendererIT` → PASS; open `target/endcard-preview.png` and approve the look.
- [ ] **Step 7: Commit** — `feat(trailer): Playwright-rendered end-card layers with Cairo and Ktab logo`

---

### Task 3: Mount the end-card layers and the logo into every session

Today only `book.pdf` and `cover.jpg` are mounted (`AnthropicTrailerAgentGateway.java:103-121`). `/workspace/ktab_logo.png` never exists, so every trailer so far fell back to typing "كِتاب". From now on the logo reaches the agent only inside `logo.png`, already composed with the rest of the card.

**Files:** `TrailerAgentGateway.java`, `AnthropicTrailerAgentGateway.java:77-126`, `TrailerLauncher.java:40-70`; test `TrailerLauncherTest.java`

**Produces:** `record SessionFile(String fileId, String mountPath)` (nested in `TrailerAgentGateway`), `String uploadFile(Path file)` (replaces `uploadCover`), and `String startSession(long trailerId, String bookFileId, List<SessionFile> files, String taskDescription, String rubric)` (replaces the cover overload; the no-cover default method delegates with `List.of()`).

- [ ] **Step 1: Failing test** in `TrailerLauncherTest`:

```java
@Test
void mountsEndCardLayersAndCoverAtFixedPaths() {
    // arrange as in the existing cover test; endCardRenderer mock returns scrim/cover/title/author/logo paths
    when(gateway.uploadFile(any())).thenReturn("file_x");
    launcher.launch(7L);
    verify(gateway).startSession(eq(7L), eq("file_book_1"), argThat(files -> files.stream()
            .map(TrailerAgentGateway.SessionFile::mountPath).toList()
            .containsAll(List.of("/workspace/cover.jpg", "/workspace/endcard/scrim.png",
                    "/workspace/endcard/cover.png", "/workspace/endcard/title.png",
                    "/workspace/endcard/author.png", "/workspace/endcard/logo.png"))),
            contains("/workspace/endcard/"), anyString());
}
```

```java
@Test
void retryStillRendersTheCoverOnTheEndCard() {
    // arrange exactly as in uploadsTheBookAndCoverWhenPresent (facts with a coverKey), then:
    // the trailer already has book + cover file ids from an earlier, crashed launch
    t.setBookFileId("file_book_1");
    t.setCoverFileId("file_cover_1");
    launcher.launch(7L);
    verify(books).downloadCover(eq("books/3/cover.jpg"), any());   // still downloaded, for the end card
    verify(gateway, never()).uploadFile(argThat(p -> p.endsWith("cover.jpg"))); // not re-uploaded
    verify(endCardRenderer).render(argThat(spec -> spec.coverOrNull() != null), any());
}
```

The launcher gains a constructor dependency on `EndCardRenderer`. In the test class, add `final EndCardRenderer endCardRenderer = mock(EndCardRenderer.class);`, pass it to `new TrailerLauncher(...)`, and stub `render(...)` to return the five layer paths. Update the two existing tests to the new `startSession` / `uploadFile` names; the no-cover test's `startSession` matcher becomes `eq(7L), eq("file_1"), anyList(), contains("voice-1"), anyString()`.

- [ ] **Step 2:** `./mvnw -q test -Dtest=TrailerLauncherTest` → FAIL.
- [ ] **Step 3: Implement.**
  - **Gateway:** `uploadFile` is the old `uploadCover` body. `startSession` adds one `BetaManagedAgentsFileResourceParams` per `SessionFile`.
  - **Launcher:** always create the temp dir. **Download the cover whenever `facts.coverKey() != null`** (today it is downloaded only when it still needs uploading, `TrailerLauncher.java:42,51`), and upload it only when `t.getCoverFileId() == null`. Then call `endCardRenderer.render(new EndCardSpec(facts.title(), facts.author(), coverOrNull, logo), dir.resolve("endcard"))`, where `logo` is copied from the classpath resource `trailer/endcard/ktab-logo.png` (the owner's full logo, Task 2 Step 0). Upload each layer and add `new SessionFile(id, "/workspace/endcard/" + name)`.
  - The layers are re-rendered and re-uploaded on a retry (cheap); only the book and cover ids stay persisted as today.
  - Mounting into `/workspace/endcard/` relies on the Task 0 check. If nested mounts are refused, use flat paths `/workspace/endcard_<name>.png` and change the prompt paths to match.
- [ ] **Step 4:** `./mvnw -q test -Dtest='com.doova.ktab.features.trailer.**'` → PASS (`TrailerAgentLiveTest` compiles with the new signature).
- [ ] **Step 5: Commit** — `feat(trailer): mount Ktab logo and end-card layers into agent sessions`

---

### Task 4: Rewrite the system prompt, same §1–§10 hierarchy, with the master directing language

**Files:** test `ControlPlaneFilesTest.java`; modify `ops/trailer-agent/system-prompt.md`; source `docs/trailer/master-directing-prompt.md` (already committed with this plan)

- [ ] **Step 1: Failing tests.** Delete `systemPromptCrossfadesBetweenShotsInsteadOfHardCutting`. In `systemPromptPreventsHiggsfieldWaste`, use `"Never put text-bearing things in a generated shot"` (both assertions there already fail against v14). Keep `captions_ar.srt` in `systemPromptStatesTheNonNegotiables`. Add:

```java
@Test
void systemPromptMakesTheWholeMontageInOneGeneration() throws Exception {
    String prompt = Files.readString(dir.resolve("system-prompt.md"));
    assertThat(prompt).contains("exactly one accepted Seedance 2.5 generation")
            .contains("DIRECTING LANGUAGE").contains("GLOBAL LOOK").contains("SHOT TIMELINE")
            .contains("MIN_INSERT_SECONDS").contains("audio first")
            .contains("scdet").contains("±0.4").contains("picture is never padded")
            .contains("never re-edit the picture")
            .doesNotContain("stop_mode=clone").doesNotContain("minterpolate may");
}

@Test
void systemPromptCarriesTheMasterDirectingLanguage() throws Exception {
    // Source: docs/trailer/master-directing-prompt.md (2026-09-30).
    String prompt = Files.readString(dir.resolve("system-prompt.md"));
    assertThat(prompt).contains("central dramatic question").contains("emotional anchor")
            .contains("Slow anticipation → fast fragments → sustained reveal")
            .contains("never a frozen frame").contains("Slow camera movement does not automatically mean slow-motion")
            .contains("Avoid repeating the same push-in").contains("brief passage through darkness")
            .contains("reveal enough to create desire");
}

@Test
void systemPromptNeverBurnsCaptionsAndUsesTheRenderedEndCard() throws Exception {
    String prompt = Files.readString(dir.resolve("system-prompt.md"));
    assertThat(prompt).contains("Do not burn captions").contains("/workspace/endcard/scrim.png")
            .contains("/workspace/endcard/logo.png").contains("Never use drawtext").contains("st=27.5")
            .doesNotContain("subtitles=captions_ar.srt").doesNotContain("trailer_clean.mp4");
}
```

- [ ] **Step 2:** `./mvnw -q test -Dtest=ControlPlaneFilesTest` → FAIL.

- [ ] **Step 3: Rewrite, section by section.** Keep the headings and numbering; parts marked *verbatim* carry over unchanged. Master-prompt passages are copied **verbatim** from `docs/trailer/master-directing-prompt.md` where marked "(master)".

| § | New content |
| --- | --- |
| Intro | "**FLUX.2 locks the visual world → exactly one accepted Seedance 2.5 generation renders the complete 30-second montage → FFmpeg lays the voice-over, music and SFX under it and composites the Ktab end card. You never re-edit the picture.**" Replace the Bahrain-reference paragraph with the master's CREATIVE DIRECTION paragraph (master), then: "The final trailer must reveal enough to create desire, preserve enough to create curiosity, and leave one strong image in the viewer's mind." (master) |
| §1 | Verbatim, plus: the video allowance vs. total allowance, and the end-card layers listed by their literal paths: `/workspace/endcard/scrim.png`, `/workspace/endcard/cover.png` (only when a cover exists), `/workspace/endcard/title.png`, `/workspace/endcard/author.png` (only when an author exists), `/workspace/endcard/logo.png`. |
| §2 | Verbatim. |
| §3 | Deliverables: `trailer.mp4` (no burned captions), `captions_ar.srt` (sidecar for the player), `script.md`, `qc_report.json`. |
| §4 | Verbatim, except: the frame-rate bullet ("Frame rate: 30 fps") becomes "the Seedance source frame rate, e.g. 24 fps; never convert 24 ↔ 30 fps"; the subtitle-typography bullet becomes "**Do not burn captions** into any video"; the people bullet becomes "Never depict identifiable real people (including the author). **Fiction:** the book's fictional characters may appear with faces, with consistent identity, anatomy and wardrobe. **Nonfiction about real persons:** anonymous figures, hands, silhouettes, places and objects only." Keep "Never put text-bearing things in a generated shot", but its last sentence (today: "(a) the Arabic captions burned in during editing and (b) the branded end card", `system-prompt.md:114`) becomes "The only permitted on-screen text is the Ktab end card composited from `/workspace/endcard/` (§8.E)." Delete the cover-image sentence's reference to "end-card compositing" by the agent: the cover reaches the end card only through `cover.png`. `/workspace/cover.jpg` stays for palette grounding. Add from the master's OUTPUT RULES: "Avoid frozen-image animation, plastic skin, unstable faces, duplicated objects, unmotivated camera movement, excessive shake, random stock-style imagery and effects unrelated to the book." (master) |
| §5 intro | "Plan **audio first**: §6.A–C are produced and measured before the SHOT TIMELINE is written, because the picture cannot be re-cut." |
| §5.A | New title **"Narrative focus and treatment."** Put the master's NARRATIVE FOCUS first (master): **one central dramatic question, one emotional anchor, up to three recurring motifs**; introduce motifs before fragmenting them; each return deepens meaning; withhold the answer. Then the fiction/nonfiction lines from CREATIVE DIRECTION (master). Keep the existing bullets for palette (from the cover), locations, period and scale changes. |
| §5.B | Replace the rhythm table with the master's wave order (master) and this **Ktab 30 s beat map** (the master's structure, scaled by its own rule for a 2.5 s end card): 00:00–02 restrained intriguing reveal, one visual question, slow deliberate move · 02–03.5 first burst of short connected details · 03.5–05.5 first hero shot revealing the larger context · 05.5–10 deeper via a motivated transition, brief discoveries + one slower meaningful detail · 10–14.5 expand the world, build momentum, human presence where relevant · 14.5–17 move closer emotionally, hold one quiet image ≈ 1.5 s (**narration and music pause here**) · 17–23 stronger escalation, increasingly charged fragments · 23–24.5 one defining image/motif · 24.5–26 final rapid burst revisiting established images · 26–27.5 **abrupt cut to near-stillness** (≈ 1.5 s), question unresolved · **27.5–30 dark, low-detail negative-space plate for the Ktab end card, with nothing bright or detailed in the centre, left or bottom; do not generate a title.** Shot lengths (master): inserts 0.2–0.5 s, **never below MIN_INSERT_SECONDS = <Task 0 value>**; hero shots 1.5–2 s; intermediate lengths to connect them; shorter flashes only in the final burst, established imagery only. Add verbatim: "Preserve this emotional order while adapting its intensity to the genre. Escalation may come from emotional recognition, revelation or visual association rather than physical danger or faster action. Do not force every book into thriller pacing.", "Treat the suggested sections as dramatic beats, not as single shots." and "Reduce the number of shots when needed so important images remain readable." (master) |
| §5.C | New title: **"The SHOT TIMELINE is the edit."** One row per shot, with the master's SHOT PLANNING fields plus Ktab's: exact time range (snapped to measured phrase boundaries/pauses) · narrative purpose · subject and action · **framing and lens perspective** · ONE dominant camera move with start → end composition · motion style (§7.C.1) · speed behaviour · reference ID · verified pages · **cut or transition cue (outgoing cue + incoming match)** · narration/music/SFX cue. Durations sum to exactly 30.0 s. "Give each shot one clear visual idea, with action and camera movement achievable within its allotted time." (master) Re-rolls are whole-film only. |
| §5.D | Replace with the master's EDITING AND TRANSITIONS (master): hard cuts as the foundation; screen-direction continuation; shape/composition/texture/highlight match; a foreground object covering the lens; **a brief passage through darkness**; motivated whip pans or **focus changes**. "Give each designed transition a clear outgoing cue and incoming match … Do not introduce essential new information in flashes too brief to understand." Ktab rule: **at most one in-camera dissolve**, for a reflective beat; FFmpeg adds no transition except the end-card fades. |
| §5.E | Lock order: narration → measure → alignment → music → SHOT TIMELINE → references → the one video call. Approve before any paid video call. |
| §6.A | Verbatim, plus: "narration ends by 27.0 s; stops for the 14.5–17 s quiet hold; leave ≥ 0.3 s gaps where cuts land." |
| §6.B | Verbatim, but the output is a **sidecar** `captions_ar.srt`. |
| §6.C–D | Verbatim, plus the master's line: "Build visual beats that can support rising sound, impacts and a deliberate silence." Place a sound accent on the 26 s abrupt cut to stillness. |
| §7 intro | Stage ownership as in the intro. The **pre-generation reference gate** replaces the pre-edit gate. Add (master): "Any reference image guides only its assigned role; do not import unrelated subjects from it." |
| §7.A | Verbatim (FLUX.2 templates move here from §7.C.1), plus the master's VISUAL QUALITY paragraph (master) as the look standard for every keyframe. **References are per beat or location, not per shot.** A 30 s master-rhythm film has ~20–30 shots, but the image budget is the total allowance minus the video allowance minus 2 spare (16 − 3 − 2 = **11 images**): 1 master look + up to 10 beat/location references. Shots in the same world share a reference. Stay within the measured reference limit. |
| §7.B | "**Exactly one accepted Seedance 2.5 generation**, exact task arguments (`duration=30`, `generate_audio=false`), references attached." Keep the resolution-honesty paragraph. |
| §7.C | **Three-part prompt**, in this order: (1) the fixed `DIRECTING LANGUAGE` block below, copied verbatim; (2) `GLOBAL LOOK:` book-specific palette, light, lens character, grain, world, the dramatic question's imagery and motifs; (3) `SHOT TIMELINE:` one line per shot, e.g. `[00:02.0–00:02.3] SHOT 3 · INSERT · awaken curiosity · @ref2 · extreme macro, 100 mm · locked · dust lifts off unmarked leather · CUT: hard cut on beat`. Replace the motion-principles bullets with the master's **entire** CAMERA LANGUAGE section, verbatim (master): "Make every camera move serve discovery, scale, tension or emotional proximity", the eight-item vocabulary "as a selective vocabulary, not a checklist", "one dominant camera move per shot. Combine movements only when the shot has enough time to read clearly", start/direction/end composition, "Avoid repeating the same push-in across consecutive shots", lens perspective and selective focus, "whip pans, crash zooms and short speed ramps sparingly at specific energy changes", "Fast camera travel must preserve coherent geography, perspective and parallax", "Keep rapid inserts simple enough to register at their intended duration". State the measured prompt limit: "if over it, shorten GLOBAL LOOK and shot descriptions; never shorten the DIRECTING LANGUAGE block, the timecodes or the restrictions." |
| §7.C.1 | **Keep all six styles**, each mapped to the master vocabulary and condensed to shot-line phrasing: slow motion → "only for selected dramatic emphasis"; speed ramp → "only at a specific energy change, same camera path"; orbit → partial arc; environmental transformation → locked camera, listed elements only; handheld → restrained, intimate or urgent human moments; macro → extreme macro inserts of meaningful details. Keep "don't force all six". |
| §7.D | Continuity within one generation, plus the master's SUBJECT AND ENVIRONMENT MOTION (master): breathing, blinking, gaze, body weight; "Slow camera movement does not automatically mean slow-motion action"; near-stillness is "never a frozen frame"; preserve identity, anatomy, wardrobe, object count, scale, lighting and spatial relationships; fantasy only when the book supports it. |
| §7.E | One video job at a time; a re-roll only after the previous job is terminal and rejected with a named defect; at most the video allowance. Images at most 2 in flight. Preset-suggestion, 429 back-off and `jobs_wait` verbatim. Save to `/workspace/film_v<N>.mp4`. |
| §7.F | Accept the clip: duration ≥ 29.5 s (otherwise re-roll: **the picture is never padded**); every frame free of text; `scdet` cuts vs. the SHOT TIMELINE; **count of inserts that rendered**; each designed transition rendered as planned; quiet hold and near-stillness alive, not frozen (the 27.5–30 s end-card plate is exempt: "The final editorial background may be fully static" (master)); no unstable faces, duplicated objects or plastic skin; the 27.5–30 s plate dark and low-detail, with nothing bright or detailed in the centre, left or bottom. A re-roll changes only the prompt lines tied to the named defect. |
| §7.G | **Picture lock**: every planned cut within ±0.4 s, or conformable in §8.B. Otherwise re-roll or fail. |
| §8.A | Keep the source frame rate. Scale only if the source is not 1920×1080 (record `source_resolution` honestly as today). Trim only the tail to 30.0 s. |
| §8.B | **"Conform the voice-over to the locked picture."** You **never re-edit the picture**: no trim (except the tail), concat, reorder, `xfade`, `setpts`, `minterpolate` or `tpad`. Detect cuts with `scdet`; move whole narration phrases split at forced-alignment gaps ≥ 0.3 s by at most **±0.4 s**; never time-stretch the voice; put impacts and music accents on the *measured* cuts; protect the quiet hold; rewrite the SRT from the shifted phrase times. |
| §8.C | Verbatim mix rules. Music resolves under the end card; audio fades out over 29.75–30.0 s. Export `trailer.mp4` directly. |
| §8.D | New title: **"Arabic caption file (not burned)."** Validate `captions_ar.srt`. **Do not burn captions**; no `subtitles` filter. |
| §8.E | **End card from the Ktab layers only**, over the 27.5–30 s plate. Each PNG is an input `-loop 1 -framerate <source fps> -t 30 -i /workspace/endcard/<layer>.png` and is faded with `format=rgba,fade=t=in:st=<s>:d=<d>:alpha=1` before `overlay`. Timings, tightened for 2.5 s: scrim `fade=t=in:st=27.5:d=0.4`; cover `st=27.55:d=0.45` plus a 24 px slide-up (`overlay=y='if(lt(t,28.0),24*(1-(t-27.55)/0.45),0)'`); title `st=27.75:d=0.35`; subtitle `st=27.9:d=0.35`; author `st=28.05:d=0.35`; logo `st=28.2:d=0.35`; fully built 28.55–29.75 (the title is fully visible for ≈ 1.65 s, from 28.1, and the whole card for ≈ 1.2 s); fade the whole picture to black over 29.75–30.0. Skip missing layers. "**Never use drawtext**, and never retype or re-lay-out the title, author or logo." |
| §9 | Technical checks on `trailer.mp4` only. Frame check: 1 fps **plus ±2 frames around every detected cut and insert**; text allowed only at ≥ 27.5 s (end card). **`frame_check.text_found` means text found in the generated footage before 27.5 s**; the end card never sets it (the harvester marks the trailer NEEDS_REVIEW when it is true, `TrailerHarvester.java:126`). Caption-file checks. End-card frames at 27.6 / 28.0 / 28.6 / 29.5 s (not later than 29.5: the picture fades to black from 29.75). `fps` in the QC report is the actual source rate (e.g. 24), not 30. QC schema: keep `status`, `frame_check.text_found`, `captions.language`; add `higgsfield.{video_jobs, single_generation, requested_duration_seconds, generated_duration_seconds}`, `visual_review.{planned_shots, detected_cuts, planned_inserts, rendered_inserts, max_cut_drift_seconds, cuts_within_tolerance, designed_transitions_rendered, quiet_hold_alive, audio_conformed_to_picture, picture_unedited}`, `captions` becomes `{language:"ar", burned_in:false, cues, max_chars_per_line, aligned_from}` (remove `font_family`, `font_style`, `font_verified`: nothing is rendered with a caption font any more), and `end_card` becomes `{layers_used, staggered_fades, plate_card_area_clear, footage_continues_beneath}` (remove `book_title_visible` etc. in favour of `layers_used`). Remove the `trailer_clean`, micro-insert-edit and FFmpeg speed-ramp fields. Delete the caption-font checks (`system-prompt.md:669`) and the "captioned and clean MP4s" check (`:671`). |
| §10 | Completion = one accepted generation is the entire picture with the master rhythm inside it, picture unedited, audio conformed ≤ ±0.4 s, no burned captions, end card from the Ktab layers, all checks recorded truthfully, and the trailer leaves one strong image and an unanswered question. |

**The fixed `DIRECTING LANGUAGE` block** (goes into §7.C verbatim; it opens every Seedance prompt; condensed from the master prompt, ≈ 1,650 characters; Task 0 checks it fits with the rest of the prompt):

```text
DIRECTING LANGUAGE: Hyper-realistic cinematic book trailer, picture only. Waves of energy:
slow anticipation → fast fragments → sustained reveal → deeper discovery → rising momentum →
emotional pause → stronger acceleration → final burst → stillness. Clean hard cuts are the
foundation; designed transitions only where the SHOT TIMELINE names them, each with a clear
outgoing cue and incoming match, no morphing. Every camera move serves discovery, scale, tension
or emotional proximity; one dominant move per shot with a clear start and end composition; never
the same push-in on consecutive shots; fast camera travel keeps coherent geography, perspective
and parallax. Rapid inserts stay simple enough to read at their length; every burst adds
information or connection, never essential new information in a flash. Slow camera movement is not
slow motion; slow motion only where marked. Near-stillness is a nearly locked camera with subtle
live motion, never a frozen frame. Natural breathing, blinking, gaze and body weight. Preserve
identity, anatomy, wardrobe, object count, scale, lighting and geography across every cut.
Photographic realism: convincing skin, fabric and reflections, soft highlight roll-off,
realistic motion blur, restrained film grain; haze, dust or flares only when motivated.
No typography, captions, subtitles, logos, watermarks, readable text or interface graphics.
No frozen-image animation, plastic skin, unstable faces, duplicated objects, unmotivated camera
movement, excessive shake, random stock-style imagery or effects unrelated to the book.
```

- [ ] **Step 4:** `./mvnw -q test -Dtest=ControlPlaneFilesTest` → the prompt tests PASS.
- [ ] **Step 5: Leftover sweep:** `grep -n "trailer_clean\|subtitles=\|drawtext=\|editorial shot\|source clip\|xfade\|minterpolate\|ktab_logo\|Bahrain\|26.5" ops/trailer-agent/system-prompt.md`. Every hit must be gone or sit inside a "never" rule.
- [ ] **Step 6: Commit** — `git add ops/trailer-agent/system-prompt.md docs/trailer/master-directing-prompt.md src/test/java/com/doova/ktab/features/trailer/ControlPlaneFilesTest.java && git commit -m "feat(trailer): single-generation prompt with master directing language, sidecar captions, rendered end card"`

---

### Task 5: Rubric and harvester wording

**Files:** `src/main/resources/trailer-agent/rubric.md`, `TrailerHarvester.java:129`

- [ ] **Step 1: Rubric edits.**
  - Criterion 2 becomes: "`trailer.mp4` carries **no burned-in captions**. The only on-screen text is the end card from 27.5 s onward."
  - Criterion 3 becomes: "The whole picture comes from a **single Seedance 2.5 generation** (`higgsfield.single_generation`, `visual_review.picture_unedited`). It follows the master rhythm: a restrained opening, a first burst of short inserts, a 1.5–2 s hero reveal, a quiet ≈ 1.5 s hold that is alive (not frozen), a stronger escalation, a final burst, an abrupt cut to near-stillness and a dark negative-space plate. Designed transitions (match cut, occlusion, darkness passage, whip/focus change) are legible; check the frames around every `scdet` cut. No padding, looping, morphing or FFmpeg transitions."
  - Criterion 3a (new): "`script.md` names one central dramatic question, one emotional anchor and at most three motifs; the trailer withholds the answer and reveals no ending."
  - Criterion 8 becomes: "No frame of `trailer.mp4` before 27.5 s shows any text, lettering, logo or watermark (Arabic or any other script). The frame check in `qc_report.json` covers at least one frame per second plus the frames around every detected cut, and reports `text_found: false`. From 27.5 s, only the Ktab end-card layers show text." The old wording names `trailer_clean.mp4` twice, so it must be replaced entirely.
  - Criterion 9 becomes: "No identifiable real person (including the author) is depicted. Fictional characters appear only for fiction books, with consistent faces, anatomy and wardrobe. Nonfiction about real persons uses anonymous figures, hands, silhouettes, places and objects." It replaces "The imagery is symbolic", which contradicts the master prompt's photographic, character-led direction.
  - Criterion 11: the schema fields from Task 4.
  - Criterion 13 becomes: "The last 2.5 s (from 27.5 s) show the Ktab end-card layers (scrim, cover if provided, title, author, the full Ktab logo) fading in staggered over the generated dark plate, fully built by 28.6 s, then fading to black from 29.75 s. Nothing is typed with drawtext."
  - Criterion 14 (new): "`script.md` has a SHOT TIMELINE summing to 30.0 s; `max_cut_drift_seconds` ≤ 0.4."
- [ ] **Step 1b: Rubric test.** Add it to `ControlPlaneFilesTest` **before** making the Step 1 edits, and watch it fail:

```java
@Test
void rubricGradesTheSingleGenerationTrailer() throws Exception {
    String rubric = Files.readString(Path.of("src/main/resources/trailer-agent/rubric.md"));
    assertThat(rubric).contains("single Seedance 2.5 generation").contains("no burned-in captions")
            .contains("central dramatic question").contains("Fictional characters appear only for fiction books")
            .doesNotContain("trailer_clean.mp4").doesNotContain("The imagery is symbolic");
}
```

- [ ] **Step 2: Harvester wording.** `TrailerHarvester.java:129`: "The burned-in captions are not reported as Arabic (R4)." → "The captions are not reported as Arabic (R4)." `trailer_clean.mp4` stays in `TYPES` (it is optional and harmless for old v14 runs).
- [ ] **Step 3:** `./mvnw -q test -Dtest='com.doova.ktab.features.trailer.**'` → all PASS (`ControlPlaneFilesTest` is fully green again).
- [ ] **Step 4: Commit** — `feat(trailer): grade single-generation montage and rendered end card`

---

### Task 6: Publish v15 and run one real trailer

This runs **after Tasks 0–5 are finished and all trailer tests pass**. Publishing a new agent version in Claude is safe while production still runs v14: every session pins the version it was started with (`TrailerProperties.agentVersion` → `AnthropicTrailerAgentGateway.java:97-100`), so production keeps using 14 until its env var changes in Step 5.

- [ ] **Step 1: Prepare the agent definition.** In `ops/trailer-agent/agent.json`, set `"metadata": { …, "version": "v15" }` and change the description to: "Turns a Ktab book PDF into a 30-second 16:9 trailer: one Seedance 2.5 generation anchored by FLUX.2 references carrying the full montage, ElevenLabs eleven_v3 Arabic narration and instrumental score mixed in FFmpeg, Ktab-rendered end card, downloadable Arabic captions (not burned in)."
- [ ] **Step 1a: Publish the agent in Claude as version 15.** With `ANTHROPIC_API_KEY` and `KTAB_TRAILER_AGENT_ID` in `.env`, run:

```bash
node ops/trailer-agent/update-agent.mjs
```

It reads `system-prompt.md` and `agent.json` and POSTs them to `/v1/agents/<id>` (`update-agent.mjs:31-47`).
- **Expected output:** `New Version : 15`.
- The API assigns the number itself: it is the previous version + 1, and the live agent is on 14 (`.env:85`). If any other update was published in between, the printed number will be higher. In that case use the printed number everywhere below instead of 15, and write it down in the run notes.
- If the call fails, fix the error and re-run. Do not continue with an unpublished prompt.
- [ ] **Step 1b: Point the branch at version 15.** Set `KTAB_TRAILER_AGENT_VERSION=15` in `.env` and change the default in `application.properties` to `ktab.trailer.agent-version=${KTAB_TRAILER_AGENT_VERSION:15}`. The production env var stays at 14 until Step 5.
- [ ] **Step 1c: Confirm the published version.** Open the agent in the Claude Console (Managed Agents → the Ktab trailer agent). Check that version 15 is listed and that its system prompt starts with the new intro ("FLUX.2 locks the visual world → exactly one accepted Seedance 2.5 generation …").
- [ ] **Step 2:** Run the same book as the 2026-09-28 run. Watch it at `platform.claude.com/workspaces/<ws>/sessions/<id>`.
- [ ] **Step 3:** Record in `docs/trailer/single-shot-first-run.md`: video/total jobs, cost, wall time, planned vs. rendered inserts, max cut drift, phrases shifted, re-roll reasons, end-card frames at 27.6/28.0/28.6/29.5 s, how well the master rhythm reads (beat by beat), and a side-by-side comparison with v14.
- [ ] **Step 4: Commit** — `git add ops/trailer-agent/agent.json src/main/resources/application.properties docs/trailer/single-shot-first-run.md && git commit -m "chore(trailer): publish single-shot agent v15 and record first run"` (`.env` is local and not committed).
- [ ] **Step 5: Release.** Only if the run passes the rubric and you approve the film, do these together:
  - Merge the branch.
  - Set the production env: `KTAB_TRAILER_AGENT_VERSION=15`, `KTAB_TRAILER_SOFT_HIGGSFIELD_GENERATIONS=14`, `KTAB_TRAILER_MAX_HIGGSFIELD_GENERATIONS=16`, `KTAB_TRAILER_MAX_HIGGSFIELD_CALLS=40`, `KTAB_TRAILER_MAX_VIDEO_JOBS=3`, and `KTAB_TRAILER_HIGGSFIELD_GENERATE_ARGS=<Task 0 args>`.
  - Otherwise don't merge: production stays on v14, and the published version 15 simply goes unused.

---

## Decisions taken (change before execution if you disagree)

- **Seedance native audio off.** ElevenLabs + FFmpeg own the whole soundtrack.
- **Captions remain a sidecar `.srt`.** The forced-alignment timings are needed anyway, to move the voice onto the real cuts.
- **`trailer_clean.mp4` is dropped.** Without burned captions it would duplicate `trailer.mp4`.
- **End card rendered by Ktab (Playwright + Cairo), not typed by the agent.** It gives correct Arabic shaping and the same design on every trailer, and it uses the real Ktab logo.
- **3 video jobs** (1 + 2 whole-film re-rolls). There is no automatic fallback to the v14 montage.
- **Master prompt adopted as the directing standard**, with these Ktab adaptations (recorded in `docs/trailer/master-directing-prompt.md`):
  - The end card gets **2.5 s (27.5–30)** instead of 2 s, because it has four elements. The master's closing beats are scaled by its own rule: defining image 23–24.5, final burst 24.5–26, near-stillness 26–27.5 (keeping the master's 1.5 s).
  - Fictional characters may show faces, only for fiction books; real people never.
  - No editorial text for text-dependent clues; the only editorial text is the end card.
  - At most one dissolve.
  - The "individual clips" and "other durations" lines are dropped.
- **The ending is a Seedance negative-space plate**, as the master prompt specifies, not a Seedance "blank book reveal". Seedance never draws the cover, title or logo.

## Decisions confirmed by the product owner (2026-09-30)

1. **Frame rate:** keep the Seedance source rate (24 fps if it outputs 24). No conversion.
2. **Logo:** the owner's full logo file, committed as `src/main/resources/trailer/endcard/ktab-logo.png` (Task 2 Step 0).
3. **Captions:** `captions_ar.srt` as a download only. No WebVTT and no player track.
4. **End card:** 2.5 s (27.5–30.0). Fictional faces only for fiction books, no editorial text for clues, and the "individual clips" and "other durations" lines dropped: all confirmed.

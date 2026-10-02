# Ktab Trailer Agent (Claude Managed Agents + Higgsfield MCP + ElevenLabs v3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From a Ktab book (does not need to be published; can be `DRAFT`, `UNDER_REVIEW`, or `PUBLISHED`), an autonomous Claude agent produces a finished 30-second 16:9 trailer. The trailer has:
- Arabic eleven_v3 narration;
- burned-in **Arabic** captions synced to that narration (no English captions anywhere);
- an ElevenLabs instrumental score;
- Higgsfield visuals;
- no text in the picture itself: the only on-screen text is those Arabic captions.

Ktab stores the result in R2 and serves it for download.

**Architecture:** The creative and production work runs inside a **Claude Managed Agents** session. Spring Boot only launches it, watches it, and collects the output files. This is the same shape as the pasted OpenAI design, moved to Claude.

The control plane is created once from version-controlled files in `ops/trailer-agent/`:
- a **cloud environment** with `ffmpeg`, `poppler-utils` and `jq`;
- a versioned **agent**: `claude-opus-5`, the full agent toolset, and the Higgsfield MCP server with `always_allow`;
- a **vault** holding two credentials:
  - the ElevenLabs key, as an `environment_variable` credential that is substituted when a request leaves the sandbox, so the agent never sees it;
  - the Higgsfield MCP login, as an `mcp_oauth` credential that Anthropic refreshes automatically.

The data plane runs in Spring, once per trailer:
1. Upload the book PDF to the Files API.
2. Create a session with the PDF mounted, the vault attached, a hard dollar budget, and a `user.define_outcome` kickoff carrying a rubric. The grader re-runs the agent until the rubric passes.
3. Find out when the session finishes: a signed webhook, plus a polling reconciler because webhooks can be lost.
4. Enforce a cap on Higgsfield generations.
5. Download `/mnt/session/outputs/*`.
6. Re-verify the MP4 with ffprobe.
7. Upload to R2.

**Tech Stack:**
- Spring Boot 3.5 and Postgres/Flyway.
- `com.anthropic:anthropic-java:2.34.0`, already in the pom; the Managed Agents beta surface is under `client.beta()`.
- Claude Managed Agents (`managed-agents-2026-04-01`).
- Higgsfield hosted MCP (`https://mcp.higgsfield.ai/mcp`, OAuth).
- ElevenLabs REST: `eleven_v3` TTS, Forced Alignment, and Music `music_v2`.
- FFmpeg, both inside the agent sandbox and in the Ktab image for re-verification.
- Cloudflare R2 through the existing `S3Client`.

**Spec:** the pasted "Ktab Trailer Agent" design, adapted to Claude, together with the Requirements section below. This plan **supersedes** `docs/superpowers/plans/2026-09-28-book-trailer.md`, the backend-orchestrated pipeline, which was not implemented. It keeps that plan's access rules and limits.

## Requirements

| # | Requirement |
|---|---|
| R1 | An `AUTHOR` can request a trailer for their own books, a `LIBRARIAN` or `ADMIN_LIBRARIAN` for their organization's books, and an `ADMIN` for any book. The book does not need to be published (any status: `DRAFT`, `UNDER_REVIEW`, or `PUBLISHED`). |
| R2 | Output is `trailer.mp4`: 30 s (±0.5), 16:9, 1920×1080, H.264 + AAC. It is accompanied by `trailer_clean.mp4` (no captions) and `captions_ar.srt` (the burned-in captions). These three files are what Ktab stores in R2. *(2026-09-29, product owner.)* |
| R3 | Narration: ElevenLabs `eleven_v3`, Arabic, measured duration ≤ 28 s. |
| R4 | Captions: **Arabic** (the narration as spoken, without audio tags), timed from ElevenLabs Forced Alignment of the Arabic narration and burned into `trailer.mp4` only, rendered right-to-left with a proper Arabic font. There are **no English captions**: none are generated, burned in or stored. *(Changed 2026-09-29 from English captions, product owner.)* |
| R5 | The **generated visuals contain no text of any kind**: no letters, signs, labels, plaques, maps, screens or book-cover lettering. The Arabic captions are the only text on screen, and `trailer_clean.mp4` has none at all. The book cover appears blank. |
| R6 | Music: ElevenLabs Music, instrumental, 30 s, mixed under the voice. |
| R7 | Faithful to the book: no invented events. The author's interpretations are framed as the author's, and there is no political advocacy. No identifiable real person is depicted. |
| R8 | The files are stored in Ktab's R2 bucket and downloaded through presigned URLs. Status can be polled, and a running trailer can be cancelled. |
| R9 | Spend is bounded: a hard Claude dollar budget per session, and a cap on Higgsfield generation calls per trailer. |

## Global Constraints

- Package: `com.doova.ktab.features.trailer`. Tables use the `tbl_*`/`col_*` naming plus the BaseEntity columns. Migration: `V21__book_trailers.sql` (V20 is the latest).
- Feature flag: `ktab.trailer.enabled`, default `false`. When it is off, there is no worker, no controller and no scheduling.
- **Never commit secrets.** From the environment only:
  - `ANTHROPIC_API_KEY`;
  - `ANTHROPIC_WEBHOOK_SIGNING_KEY` (the `whsec_…` secret);
  - `ELEVENLABS_API_KEY`, which is used only by `ops/trailer-agent/setup.sh` to create the vault credential;
  - Higgsfield tokens, which are never stored by Ktab: they go straight into the vault.
- **Control plane lives in files, not in the request path.** Agent, environment and vault are created once with `ops/trailer-agent/setup.sh`. Their IDs come from environment variables:
  - `KTAB_TRAILER_AGENT_ID`
  - `KTAB_TRAILER_AGENT_VERSION`
  - `KTAB_TRAILER_ENVIRONMENT_ID`
  - `KTAB_TRAILER_VAULT_ID`

  Spring never calls `agents.create()`.
- The MCP toolset **must** use `permission_policy: always_allow`. MCP tools default to `always_ask`, and then every session would stall at `requires_action`.
- anthropic-java 2.34.0 has no typed setters for three things, so they are sent as raw JSON:
  - session `budget` goes through `putAdditionalBodyProperty` (verified with javap);
  - session `initial_events` goes through `putAdditionalBodyProperty`;
  - the `environment_variable` credential is created with curl in `setup.sh`.
- Output files are listed with `FileListParams.scopeId(sessionId)` **plus** `.addBeta("managed-agents-2026-04-01")`.
- The trailer feature does not import `features.storybook` in main code. Tests may reuse `storybook.support.StorybookJpaIT` and `UserFixtures`.
- Endpoints are called by the outside world, so they sit under the already-public `/api/v1/public/**`:
  - webhook: `/api/v1/public/trailer-agent/webhook`;
  - OAuth callback: `/api/v1/public/trailer-agent/higgsfield/callback`.

## Decisions

- **D1 Agent-orchestrated, not backend-orchestrated.** The agent chooses shots, retries, mixing and QC, and the rubric grader enforces the quality bar. Ktab owns access, spend caps, storage and a final independent ffprobe check. Trade-off: each run is less deterministic and costs more than a hand-written pipeline, in exchange for far less code.
- **D2 `claude-opus-5`, effort `high`.** This is the skill default for complex agentic work. It is set on the agent object, since a per-session effort override is ignored.
- **D3 Outcome kickoff** in `initial_events`, so session creation and kickoff are one atomic call. The rubric is `src/main/resources/trailer-agent/rubric.md`, and `max_iterations` is 3.
- **D4 Budget** `ktab.trailer.budget-cents=2000` ($20 list cost) per session. It covers Claude tokens and runtime only. **Higgsfield and ElevenLabs are billed by those vendors**, so D5 bounds Higgsfield separately.
- **D5 Higgsfield spend guard: count jobs, not calls.** The first real run spent 11 of 12 allowed calls on 7 jobs: 2 calls hit Higgsfield's concurrency limit and 2 returned preset suggestions, and neither kind creates a job. So the reconciler pairs each `agent.mcp_tool_use` whose tool name contains `ktab.trailer.higgsfield-generation-marker` (default `generate`) with its `agent.mcp_tool_result` (matched by `mcp_tool_use_id`). A call counts as a **job** only when the result is not an error and its text matches `ktab.trailer.higgsfield-job-pattern`, a regex recorded in the Task 1 spike from a real job-created reply. There are two limits:
  - `max-higgsfield-generations=8` **jobs** (6 clips plus 2 spares): this is the money limit;
  - `max-higgsfield-calls=24` calls of any outcome: a runaway guard against a loop of rejected calls.

  Crossing either limit interrupts the session and fails the trailer.
- **D6 Completion signal:** webhook plus reconciler. Webhooks are thin, unordered and droppable, so the reconciler polls RUNNING sessions every 60 s. A webhook only pulls that check forward to now.
- **D7 Harvest verdict.** READY requires three things:
  - outcome `satisfied`;
  - `qc_report.json.status == "ok"`;
  - Ktab's own ffprobe: 29.5–30.5 s and 1920×1080.

  Any other finished state that has a `trailer.mp4` becomes **NEEDS_REVIEW**, and an admin can approve or reject it. Without a `trailer.mp4` the trailer is FAILED.
- **D8 Higgsfield OAuth** (verified live from `https://mcp.higgsfield.ai/.well-known/oauth-authorization-server`):
  - dynamic client registration at `/oauth2/register`;
  - auth code with PKCE S256;
  - `refresh_token` grant;
  - scopes `openid email offline_access`;
  - `token_endpoint_auth_method: none`.

  An admin clicks "Connect Higgsfield" once. Ktab stores the tokens in the vault as `mcp_oauth` with a refresh block, and from then on Anthropic refreshes them. A `vault_credential.refresh_failed` webhook tells admins to reconnect.
- **D9 Networking** is `unrestricted` in phase 1, because Higgsfield's CDN hostnames are unknown until the spike. Task 7 records the observed hosts and switches the environment to `limited` networking with an allowlist that includes `api.elevenlabs.io`.

## Review Focus

1. **The MCP permission policy is left at its default (`always_ask`).** The session would idle at `requires_action` forever. The reconciler must fail such a trailer with a readable reason instead of waiting indefinitely. Pinned in Task 5: `requiresActionFailsTheTrailerWithAPermissionHint`.
2. **The budget is reached mid-run.** The session idles with `budget_reached` and never finishes on its own. The trailer is failed, or marked NEEDS_REVIEW if a `trailer.mp4` already exists, and is not polled forever. Pinned in Task 5: `budgetReachedWithoutAnOutputFails`.
3. **The Higgsfield job cap or call cap is exceeded.** The session is interrupted and the trailer fails. Rejected calls (concurrency limit, preset notices) do not count as jobs. Pinned in Task 3: `countsOnlyCallsThatCreatedAJob`; and in Task 5: `tooManyHiggsfieldGenerationsInterruptsTheSession` and `aLoopOfRejectedCallsHitsTheCallCap`.
4. **The agent claims success but the MP4 is wrong** (for example 24 s long or 1280×720). Ktab's ffprobe check marks it NEEDS_REVIEW, never READY. Pinned in Task 6: `wrongDurationIsNeedsReviewEvenIfTheGraderWasSatisfied`.
5. **A webhook arrives twice, out of order, or never.** Delivery is deduplicated by event id, and the reconciler still finishes the trailer with no webhook at all. Pinned in Task 5: `reconcilerFinishesWithoutAnyWebhook`; and in Task 6: `duplicateWebhookIsIgnored`.

---

### Task 1: Control plane — environment, agent, vault (files + setup script) and live spike

**Files:**
- Create: `ops/trailer-agent/environment.json`
- Create: `ops/trailer-agent/agent.json`
- Create: `ops/trailer-agent/system-prompt.md`
- Create: `ops/trailer-agent/setup.sh`
- Create: `ops/trailer-agent/update-agent.sh`
- Create: `ops/trailer-agent/README.md`
- Test: `src/test/java/com/doova/ktab/features/trailer/ControlPlaneFilesTest.java`

**Interfaces:**
- Produces:
  - the environment variables `KTAB_TRAILER_AGENT_ID`, `KTAB_TRAILER_AGENT_VERSION`, `KTAB_TRAILER_ENVIRONMENT_ID` and `KTAB_TRAILER_VAULT_ID`, printed by `setup.sh`;
  - the agent contract Spring relies on. The agent:
    - reads `/workspace/book.pdf`;
    - writes `/mnt/session/outputs/{trailer.mp4, trailer_clean.mp4, captions_ar.srt, script.md, qc_report.json}`;
    - calls ElevenLabs with `$ELEVENLABS_API_KEY`.

- [ ] **Step 1: Write the failing guard test**

This test pins the settings whose silent loss breaks production: the MCP `always_allow` policy, ffmpeg in the environment, the model, and no secrets in the files.

```java
// src/test/java/com/doova/ktab/features/trailer/ControlPlaneFilesTest.java
package com.doova.ktab.features.trailer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneFilesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Path dir = Path.of("ops/trailer-agent");

    @Test
    void environmentInstallsFfmpegAndPoppler() throws Exception {
        JsonNode env = json.readTree(dir.resolve("environment.json").toFile());
        assertThat(env.at("/config/type").asText()).isEqualTo("cloud");
        assertThat(env.at("/config/packages/apt").toString()).contains("ffmpeg").contains("poppler-utils")
                .contains("fonts-noto-core"); // Noto Naskh Arabic for the burned-in Arabic captions
    }

    @Test
    void mcpToolsRunWithoutApprovalOtherwiseEverySessionStalls() throws Exception {
        JsonNode agent = json.readTree(dir.resolve("agent.json").toFile());
        assertThat(agent.at("/model/id").asText()).isEqualTo("claude-opus-5");
        assertThat(agent.at("/mcp_servers/0/url").asText()).isEqualTo("https://mcp.higgsfield.ai/mcp");
        JsonNode mcpToolset = null;
        for (JsonNode tool : agent.get("tools")) {
            if ("mcp_toolset".equals(tool.path("type").asText())) {
                mcpToolset = tool;
            }
        }
        assertThat(mcpToolset).isNotNull();
        assertThat(mcpToolset.at("/default_config/permission_policy/type").asText()).isEqualTo("always_allow");
    }

    @Test
    void noSecretsAreCommitted() throws Exception {
        for (String f : new String[]{"environment.json", "agent.json", "system-prompt.md", "setup.sh", "update-agent.sh"}) {
            String text = Files.readString(dir.resolve(f));
            assertThat(text).doesNotContainPattern("sk-ant-[A-Za-z0-9]").doesNotContainPattern("sk_[0-9a-f]{20,}")
                    .doesNotContain("whsec_");
        }
    }

    @Test
    void systemPromptStatesTheNonNegotiables() throws Exception {
        String prompt = Files.readString(dir.resolve("system-prompt.md"));
        assertThat(prompt).contains("eleven_v3").contains("forced-alignment").contains("/mnt/session/outputs/")
                .contains("qc_report.json").contains("captions_ar.srt").contains("Concurrency")
                .contains("Never put text-bearing things in a shot");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=ControlPlaneFilesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, because `ops/trailer-agent/environment.json` is missing.

- [ ] **Step 3: Write the control-plane files**

```json
// ops/trailer-agent/environment.json  (remove this comment line; JSON has no comments)
{
  "name": "ktab-trailer-env",
  "config": {
    "type": "cloud",
    "packages": { "type": "packages", "apt": ["ffmpeg", "poppler-utils", "jq", "fonts-dejavu-core", "fonts-noto-core"] },
    "networking": { "type": "unrestricted" }
  }
}
```

```json
// ops/trailer-agent/agent.json  ("system" is filled from system-prompt.md by setup.sh; remove this line)
{
  "name": "Ktab Cinematic Trailer Agent",
  "description": "Turns a Ktab book PDF into a 30-second 16:9 trailer: Higgsfield visuals, ElevenLabs eleven_v3 Arabic narration, burned-in Arabic captions, instrumental score.",
  "model": { "id": "claude-opus-5", "effort": "high" },
  "system": "",
  "mcp_servers": [
    { "type": "url", "name": "higgsfield", "url": "https://mcp.higgsfield.ai/mcp" }
  ],
  "tools": [
    { "type": "agent_toolset_20260401", "default_config": { "enabled": true, "permission_policy": { "type": "always_allow" } } },
    { "type": "mcp_toolset", "mcp_server_name": "higgsfield", "default_config": { "permission_policy": { "type": "always_allow" } } }
  ],
  "metadata": { "owner": "ktab", "feature": "book-trailer" }
}
```

```markdown
<!-- ops/trailer-agent/system-prompt.md -->
You are Ktab's cinematic trailer producer. From one book you produce a finished, verified 30-second trailer.

## Inputs
- `/workspace/book.pdf` (read-only). Read it with the `read` tool; for long books extract text with `pdftotext -layout`.
- The task message gives the book title, author, language, the ElevenLabs voice id to use, the Higgsfield job allowance, and the exact `generate_video` arguments to use.

## Deliverables — write all of them to `/mnt/session/outputs/`
- `trailer.mp4` — final trailer with burned-in **Arabic** captions.
- `trailer_clean.mp4` — the same trailer without captions.
- `captions_ar.srt` — the Arabic captions that are burned in.
- `script.md` — the Arabic narration, a time-coded storyboard, and for every claim the book page it comes from.
- `qc_report.json` — the verification report described below.

## Hard requirements
- Duration 30.0 s (±0.5), 16:9, 1920×1080, 30 fps, H.264 video, AAC audio.
- Narration in Arabic (Modern Standard Arabic unless the book is written in a dialect) with ElevenLabs `eleven_v3`; its measured length must be at most 28 s. If it is longer, shorten the script and regenerate — do not speed the audio up past natural delivery.
- The generated visuals must contain no text or lettering of any kind, in any script. The only text on screen is the Arabic captions you burn in yourself. Show the book as a blank premium hardcover while the narration says the title.
- Never depict an identifiable real person (politicians, celebrities, the author). Use symbolic imagery: places, architecture, objects, silhouettes seen from behind, weather, light.
- Be faithful to the book. Do not invent events or quotes. Present the author's interpretations as the author's, distinguish them from established facts, and do not advocate a political position.
- Never report success before the files exist and you have verified them.

## How to use the tools
**ElevenLabs** — call the REST API with `curl` from `bash`. The key is available as `$ELEVENLABS_API_KEY`; send it only in the `xi-api-key` header, never in a URL, file or message.
- Narration: `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128` with JSON `{"text": ..., "model_id": "eleven_v3", "language_code": "ar"}`. Guide delivery with punctuation and v3 audio tags such as `[thoughtful]`; v3 does not support SSML `<break>` tags. Save as `voiceover.mp3` and measure it with `ffprobe`.
- Caption timing: `POST https://api.elevenlabs.io/v1/forced-alignment` (multipart: `file=@voiceover.mp3`, `text=<the Arabic transcript exactly as spoken, with audio tags removed>`). It returns word timings. Group the Arabic words into complete phrases; each phrase becomes one cue in `captions_ar.srt` with that phrase's start and end, written exactly as spoken (no audio tags). Start every Arabic cue line with U+200F (right-to-left mark). Do not produce English captions or subtitles of any kind.
- Music: `POST https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128` with `{"model_id": "music_v2", "prompt": <a brief written for THIS book>, "music_length_ms": 30000, "force_instrumental": true}`.

**Higgsfield** — use the `higgsfield` MCP tools. Request 16:9 clips with no speech, no music, no captions, no text overlays, no logos or watermarks, and no lettering on book covers. Generation is asynchronous: poll the job until it completes and download the finished file into `/workspace/clips/`; an accepted request is not a finished video.
- **Call `generate_video` with exactly the arguments given in the task message** (model, aspect ratio, duration and anything else listed there). Underspecified calls come back as preset suggestions instead of jobs and waste the allowance. If a reply is a suggestion rather than a job, read what it asks for and add that argument. Do not resend the same call.
- **Concurrency: keep at most the number of generations in flight that the task message gives (default 2).** Submit the next shot only after one finishes. If a call is refused because too many requests are queued or running, or with any rate-limit or 429 error, wait 60 seconds before trying again, then 120 seconds, then 240 seconds. Never retry immediately. Refused calls are logged and count toward a hard call limit.
- **Never put text-bearing things in a shot.** Plan every shot to avoid anything that video models fill with letters: classrooms and lecture halls, whiteboards and blackboards, libraries and bookshelves with visible spines, maps and globes, newspapers, documents and letters, screens and monitors, shop fronts, street signs, plaques and inscriptions, monuments with engraving, flags with emblems, banners, vehicles with markings. Prefer landscapes, skies, weather, architecture seen from a distance, hands, silhouettes, objects without markings, light and shadow. A shot that needs one of these ideas must show it without writing: a blank book, a map shape made of light, an unmarked door.
- Stay within the job allowance in the task message. It is enforced, and exceeding it stops the session. Plan six shots and keep the spare jobs for replacing a shot that fails the frame check.

**FFmpeg** — normalize every clip with `scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,fps=30,setsar=1`, trim and concatenate to exactly 30 s, place the voice so it ends before the final fade, duck the music under the voice (`sidechaincompress`), normalize loudness to about −14 LUFS (`loudnorm`), and write `trailer_clean.mp4`. Then burn the captions: `subtitles=captions_ar.srt:force_style='FontName=Noto Naskh Arabic,FontSize=26,PrimaryColour=&H00F0F8FF,OutlineColour=&H80000000,BorderStyle=1,Outline=1,Shadow=1,Alignment=2,MarginV=48'` to produce `trailer.mp4`. Arabic captions: at most two lines, at most 35 visible characters per line, inside the safe area. Check several caption frames with `read` to confirm the letters are joined and run right-to-left.

## Quality control before you finish
1. `ffprobe` both MP4s: duration, width, height, frame rate, codecs.
2. Extract one frame per second from `trailer_clean.mp4` into `/workspace/qc_frames/` and look at every frame with `read`. If any frame shows text or lettering of any script, regenerate or cut that section and check again.
3. Confirm every cue in `captions_ar.srt` lies within 0–30 s and matches the spoken Arabic phrase word for word.
4. Write `qc_report.json`:
   `{"status":"ok"|"failed","failure_reason":null|string,"duration_seconds":n,"width":n,"height":n,"fps":n,"video_codec":s,"audio_codec":s,"narration":{"model_id":"eleven_v3","language_code":"ar","voice_id":s,"seconds":n},"music":{"model_id":"music_v2","seconds":n},"higgsfield":{"generations":n,"calls":n,"discarded":n,"models":[s]},"captions":{"language":"ar","file":"captions_ar.srt","burned_into":"trailer.mp4","cues":n,"max_chars_per_line":n,"aligned_from":"forced-alignment"},"frame_check":{"frames_checked":n,"text_found":bool}}`
If a requirement cannot be met, still write `qc_report.json` with `"status":"failed"` and the reason, and say so plainly.
```

```bash
#!/usr/bin/env bash
# ops/trailer-agent/setup.sh — one-time control-plane setup. Needs: curl, jq, ANTHROPIC_API_KEY, ELEVENLABS_API_KEY.
# Prints the IDs to put in the Ktab deployment environment. Re-running creates NEW resources; use update-agent.sh to change the agent.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANTHROPIC_API_KEY:?set ANTHROPIC_API_KEY}"
: "${ELEVENLABS_API_KEY:?set ELEVENLABS_API_KEY (only used to create the vault credential)}"

API=https://api.anthropic.com
H=(-H "content-type: application/json" -H "x-api-key: $ANTHROPIC_API_KEY"
   -H "anthropic-version: 2023-06-01" -H "anthropic-beta: managed-agents-2026-04-01")

ENV_ID=$(curl -sS --fail-with-body "${H[@]}" -X POST "$API/v1/environments" -d @environment.json | jq -r .id)

AGENT=$(jq --rawfile sys system-prompt.md '.system = $sys' agent.json \
  | curl -sS --fail-with-body "${H[@]}" -X POST "$API/v1/agents" -d @-)
AGENT_ID=$(jq -r .id <<<"$AGENT"); AGENT_VERSION=$(jq -r .version <<<"$AGENT")

VAULT_ID=$(curl -sS --fail-with-body "${H[@]}" -X POST "$API/v1/vaults" \
  -d '{"display_name":"ktab-trailer-agent","metadata":{"owner":"ktab"}}' | jq -r .id)

# ElevenLabs key: substituted at egress, header only, api.elevenlabs.io only. Never visible inside the sandbox.
jq -n --arg v "$ELEVENLABS_API_KEY" '{display_name:"ElevenLabs (trailer agent)",auth:{type:"environment_variable",
  secret_name:"ELEVENLABS_API_KEY",secret_value:$v,networking:{type:"limited",allowed_hosts:["api.elevenlabs.io"]},
  injection_location:{header:true}}}' \
  | curl -sS --fail-with-body "${H[@]}" -X POST "$API/v1/vaults/$VAULT_ID/credentials" -d @- >/dev/null

cat <<EOF
Add to the Ktab deployment environment:
KTAB_TRAILER_ENVIRONMENT_ID=$ENV_ID
KTAB_TRAILER_AGENT_ID=$AGENT_ID
KTAB_TRAILER_AGENT_VERSION=$AGENT_VERSION
KTAB_TRAILER_VAULT_ID=$VAULT_ID
Next: sign in to Ktab as ADMIN and connect Higgsfield (POST /api/v1/admin/trailer-agent/higgsfield/connect).
EOF
```

```bash
#!/usr/bin/env bash
# ops/trailer-agent/update-agent.sh — publish a new agent version from agent.json + system-prompt.md.
# Usage: KTAB_TRAILER_AGENT_ID=agent_... ./update-agent.sh   → prints the new version; roll it out via KTAB_TRAILER_AGENT_VERSION.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANTHROPIC_API_KEY:?}"; : "${KTAB_TRAILER_AGENT_ID:?}"
jq --rawfile sys system-prompt.md '.system = $sys | del(.name)' agent.json \
  | curl -sS --fail-with-body -X POST "https://api.anthropic.com/v1/agents/$KTAB_TRAILER_AGENT_ID" \
      -H "content-type: application/json" -H "x-api-key: $ANTHROPIC_API_KEY" \
      -H "anthropic-version: 2023-06-01" -H "anthropic-beta: managed-agents-2026-04-01" -d @- \
  | jq -r '"New agent version: \(.version)"'
```

```markdown
<!-- ops/trailer-agent/README.md -->
# Trailer agent control plane

Created once, changed by publishing agent versions. Spring only creates sessions.

1. `ANTHROPIC_API_KEY=… ELEVENLABS_API_KEY=… ./setup.sh` → put the printed IDs in the deployment env.
2. Start Ktab with `KTAB_TRAILER_ENABLED=true`, sign in as ADMIN, `POST /api/v1/admin/trailer-agent/higgsfield/connect`,
   open the returned URL, approve in Higgsfield. The token lands in the vault; Anthropic refreshes it.
3. Console → Manage → Webhooks: add `https://<ktab-host>/api/v1/public/trailer-agent/webhook`, subscribe to
   `session.status_idled`, `session.status_terminated`, `session.outcome_evaluation_ended`, `vault_credential.refresh_failed`;
   put the `whsec_…` secret in `ANTHROPIC_WEBHOOK_SIGNING_KEY`.
4. Changing the prompt or tools: edit the files, run `./update-agent.sh`, set `KTAB_TRAILER_AGENT_VERSION` to the new version.
Watch any run live at `https://platform.claude.com/workspaces/<workspace>/sessions/<session_id>`.
```

- [ ] **Step 4: Run the guard test to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 5: Live spike — the go/no-go gate (real money, done by a human)**

1. Confirm eleven_v3 supports Arabic:
   `curl -s https://api.elevenlabs.io/v1/models -H "xi-api-key: $ELEVENLABS_API_KEY" | jq '.[] | select(.model_id=="eleven_v3") | {can_do_text_to_speech, languages: [.languages[].language_id]}'`
   Expected: `true`, and `ar` appears in the languages. If not, stop and tell the product owner.
2. Pick an Arabic-capable voice (`GET /v1/voices`) and note its `voice_id`. It becomes `KTAB_TRAILER_VOICE_ID`.
3. Run `setup.sh`.
4. Do the Higgsfield connect once, by hand, **after Task 3 exists**, or use Claude Code's `/mcp` login to see the flow.
5. Create one session by hand, following the README of step 4, with a small public-domain PDF.
6. Watch it in the Console and record these things in plan D5 and D9 and in the configuration:
   - the exact Higgsfield generation tool names;
   - the exact `generate_video` arguments that create a job on the first call, with no preset suggestion. They become `KTAB_TRAILER_HIGGSFIELD_GENERATE_ARGS`, for example `model=seedance_2_5, aspect_ratio=16:9, duration=5`;
   - a regex that matches the text of a job-created reply and not a rejected or suggestion reply, for example `(?i)\"(job|request)_id\"`. It becomes `KTAB_TRAILER_HIGGSFIELD_JOB_PATTERN`;
   - the account's concurrent-generation limit shown in the Higgsfield Console. Set `KTAB_TRAILER_HIGGSFIELD_MAX_IN_FLIGHT` below it (default 2);
   - the CDN hosts the agent downloaded clips from;
   - the list cost and duration of the run.

- [ ] **Step 6: Commit**

```bash
git add ops/trailer-agent src/test/java/com/doova/ktab/features/trailer/ControlPlaneFilesTest.java
git commit -m "feat(trailer): add trailer agent control plane (environment, agent, vault setup)"
```

---

### Task 2: Schema, entity, repositories, properties

**Files:**
- Create: `src/main/resources/db/migration/V21__book_trailers.sql`
- Create: `src/main/java/com/doova/ktab/features/trailer/config/TrailerProperties.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/enums/TrailerStatus.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/model/BookTrailer.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/model/TrailerOAuthState.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/repository/BookTrailerRepository.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/repository/TrailerOAuthStateRepository.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/support/TrailerFixtures.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/repository/BookTrailerRepositoryIT.java`

**Interfaces:**
- Produces:
  - `TrailerStatus`:
    - values `QUEUED, RUNNING, HARVESTING, READY, NEEDS_REVIEW, FAILED, CANCELLED`;
    - `static final Set<TrailerStatus> ACTIVE` = `QUEUED, RUNNING, HARVESTING`;
    - `boolean isActive()`.
  - `BookTrailer` fields:
    - `bookId`, `requestedById`, `status`;
    - `sessionId`, `bookFileId`, `agentVersion`, `outcomeResult`, `outcomeExplanation`, `higgsfieldGenerations` (jobs), `higgsfieldCalls`;
    - `videoKey`, `cleanVideoKey`, `captionsKey` (Arabic captions), `qcReportJson`;
    - `error`, `nextCheckAt`, `startedAt`, `finishedAt`.
  - `TrailerOAuthState` fields: `state`, `codeVerifier`, `clientId`, `redirectUri`, `adminUserId`, `used`.
  - `BookTrailerRepository`:
    - `findByBookIdOrderByIdDesc`
    - `existsByBookIdAndStatusIn`
    - `countByBookIdAndCreatedAtAfterAndStatusNotIn`
    - `Optional<BookTrailer> findBySessionId(String)`
    - `List<BookTrailer> findTop10ByStatusOrderByIdAsc(TrailerStatus)`
    - `List<BookTrailer> findDueRunning(Instant now, Pageable)`
  - `TrailerOAuthStateRepository.findByStateAndUsedFalse(String)`
  - `TrailerProperties`, whose fields are listed in the code below.

- [ ] **Step 1: Write the failing IT**

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
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTrailerRepositoryIT extends StorybookJpaIT {

    @Autowired BookTrailerRepository trailers;

    private Book book() {
        return TrailerFixtures.publishedBook(em, UserFixtures.reader(em, "a-" + System.nanoTime() + "@x.com"));
    }

    private BookTrailer trailer(Book book, TrailerStatus status) {
        BookTrailer t = new BookTrailer();
        t.setBookId(book.getId());
        t.setStatus(status);
        return t;
    }

    @Test
    void onlyOneActiveTrailerPerBook() {
        Book book = book();
        trailers.saveAndFlush(trailer(book, TrailerStatus.RUNNING));
        trailers.saveAndFlush(trailer(book, TrailerStatus.READY));

        assertThatThrownBy(() -> trailers.saveAndFlush(trailer(book, TrailerStatus.QUEUED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsBySessionIdAndDueRunningTrailers() {
        BookTrailer due = trailer(book(), TrailerStatus.RUNNING);
        due.setSessionId("sesn_due");
        due.setNextCheckAt(Instant.now().minusSeconds(5));
        trailers.saveAndFlush(due);
        BookTrailer later = trailer(book(), TrailerStatus.RUNNING);
        later.setSessionId("sesn_later");
        later.setNextCheckAt(Instant.now().plusSeconds(600));
        trailers.saveAndFlush(later);

        assertThat(trailers.findBySessionId("sesn_due")).isPresent();
        assertThat(trailers.findDueRunning(Instant.now(), PageRequest.of(0, 50)))
                .extracting(BookTrailer::getSessionId).contains("sesn_due").doesNotContain("sesn_later");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `STORYBOOK_IT_DB_URL=jdbc:postgresql://localhost:5432/ktab_storybook_it STORYBOOK_IT_DB_PASSWORD=123456 mvn -o verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BookTrailerRepositoryIT`
Expected: compilation failure, because `BookTrailerRepository` is missing.

- [ ] **Step 3: Implement**

```sql
-- src/main/resources/db/migration/V21__book_trailers.sql
-- ============================================================================
-- Flyway Migration V21: book trailers produced by the Claude Managed Agents trailer agent
-- ============================================================================

CREATE TABLE IF NOT EXISTS tbl_book_trailers (
    col_id                    BIGSERIAL    PRIMARY KEY,
    col_book_id               BIGINT       NOT NULL REFERENCES tbl_books (col_id) ON DELETE CASCADE,
    col_requested_by          BIGINT       REFERENCES tbl_users (col_id) ON DELETE SET NULL,
    col_status                VARCHAR(20)  NOT NULL,
    col_session_id            VARCHAR(80),
    col_book_file_id          VARCHAR(80),
    col_agent_version         INTEGER,
    col_outcome_result        VARCHAR(40),
    col_outcome_explanation   TEXT,
    col_higgsfield_generations INTEGER     NOT NULL DEFAULT 0,  -- jobs created (D5)
    col_higgsfield_calls      INTEGER      NOT NULL DEFAULT 0,  -- generate calls of any outcome (D5)
    col_video_key             VARCHAR(300),
    col_clean_video_key       VARCHAR(300),
    col_captions_key          VARCHAR(300),                    -- captions_ar.srt (burned in); the only captions file stored
    col_qc_report             TEXT,
    col_error                 TEXT,
    col_next_check_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    col_started_at            TIMESTAMPTZ,
    col_finished_at           TIMESTAMPTZ,
    col_created_by            BIGINT,
    col_last_modified_by      BIGINT,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                   INTEGER      NOT NULL DEFAULT 0
);

-- One active trailer per book (race-safe across double clicks and instances). Must match TrailerStatus.ACTIVE.
CREATE UNIQUE INDEX IF NOT EXISTS uq_book_trailer_active ON tbl_book_trailers (col_book_id)
    WHERE col_status IN ('QUEUED', 'RUNNING', 'HARVESTING');
CREATE UNIQUE INDEX IF NOT EXISTS uq_book_trailer_session ON tbl_book_trailers (col_session_id)
    WHERE col_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_book_trailer_due ON tbl_book_trailers (col_status, col_next_check_at);
CREATE INDEX IF NOT EXISTS idx_book_trailer_book ON tbl_book_trailers (col_book_id, created_at);

-- Short-lived PKCE state for the admin "Connect Higgsfield" flow (D8).
CREATE TABLE IF NOT EXISTS tbl_trailer_oauth_states (
    col_id                BIGSERIAL    PRIMARY KEY,
    col_state             VARCHAR(100) NOT NULL,
    col_code_verifier     VARCHAR(200) NOT NULL,
    col_client_id         VARCHAR(200) NOT NULL,
    col_redirect_uri      VARCHAR(500) NOT NULL,
    col_admin_user_id     BIGINT       NOT NULL REFERENCES tbl_users (col_id) ON DELETE CASCADE,
    col_used              BOOLEAN      NOT NULL DEFAULT FALSE,
    col_created_by        BIGINT,
    col_last_modified_by  BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_trailer_oauth_state UNIQUE (col_state)
);
```

```java
// src/main/java/com/doova/ktab/features/trailer/enums/TrailerStatus.java
package com.doova.ktab.features.trailer.enums;

import java.util.EnumSet;
import java.util.Set;

public enum TrailerStatus {
    QUEUED, RUNNING, HARVESTING, READY, NEEDS_REVIEW, FAILED, CANCELLED;

    /** Must match uq_book_trailer_active in V21__book_trailers.sql. */
    public static final Set<TrailerStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING, HARVESTING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/config/TrailerProperties.java
package com.doova.ktab.features.trailer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ktab.trailer")
public class TrailerProperties {

    private boolean enabled = false;

    /** Control-plane IDs printed by ops/trailer-agent/setup.sh. */
    private String agentId;
    private int agentVersion;
    private String environmentId;
    private String vaultId;
    private String workspace = "default";

    private String anthropicApiKey;
    private String webhookSigningKey;

    private String voiceId;
    /** D4: list-cost cap per session in US cents, as the API expects ("2000" = $20.00). */
    private long budgetCents = 2000;
    private int maxOutcomeIterations = 3;
    /** D5: jobs actually created (the money limit): 6 clips + 2 spares. */
    private int maxHiggsfieldGenerations = 8;
    /** D5: generate calls of any outcome, a runaway guard against loops of rejected calls. */
    private int maxHiggsfieldCalls = 24;
    private String higgsfieldGenerationMarker = "generate";
    /** D5: matches the text of a job-created generate reply; recorded in the Task 1 spike. */
    private String higgsfieldJobPattern = "(?i)\"?(job|request|generation)_?id\"?";
    /** Exact generate_video arguments the agent must use, so no call comes back as a preset suggestion. */
    private String higgsfieldGenerateArgs = "";
    /** Generations the agent may keep queued or running at once; keep below the account's concurrency limit. */
    private int higgsfieldMaxInFlight = 2;

    private String higgsfieldMcpUrl = "https://mcp.higgsfield.ai/mcp";
    private String higgsfieldCallbackUrl;

    private String ffprobePath = "ffprobe";
    private Duration reconcileEvery = Duration.ofSeconds(60);
    private Duration launchTimeout = Duration.ofMinutes(5);
    /** A session still running after this is interrupted and failed. */
    private Duration maxSessionAge = Duration.ofHours(2);
    private int perBookPer30Days = 3;
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

import java.time.Instant;

@Entity
@Table(name = "tbl_book_trailers")
@Getter
@Setter
public class BookTrailer extends BaseEntity {

    @Column(name = "col_book_id", nullable = false)
    private Long bookId;

    @Column(name = "col_requested_by")
    private Long requestedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false, length = 20)
    private TrailerStatus status = TrailerStatus.QUEUED;

    @Column(name = "col_session_id", length = 80)
    private String sessionId;

    @Column(name = "col_book_file_id", length = 80)
    private String bookFileId;

    @Column(name = "col_agent_version")
    private Integer agentVersion;

    @Column(name = "col_outcome_result", length = 40)
    private String outcomeResult;

    @Column(name = "col_outcome_explanation", columnDefinition = "TEXT")
    private String outcomeExplanation;

    /** Jobs Higgsfield actually created (D5). */
    @Column(name = "col_higgsfield_generations", nullable = false)
    private int higgsfieldGenerations;

    /** Generate calls of any outcome, including rejected ones (D5). */
    @Column(name = "col_higgsfield_calls", nullable = false)
    private int higgsfieldCalls;

    @Column(name = "col_video_key", length = 300)
    private String videoKey;

    @Column(name = "col_clean_video_key", length = 300)
    private String cleanVideoKey;

    /** captions_ar.srt, the captions burned into trailer.mp4 — the only captions file Ktab stores. */
    @Column(name = "col_captions_key", length = 300)
    private String captionsKey;

    @Column(name = "col_qc_report", columnDefinition = "TEXT")
    private String qcReportJson;

    @Column(name = "col_error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "col_next_check_at", nullable = false)
    private Instant nextCheckAt = Instant.now();

    @Column(name = "col_started_at")
    private Instant startedAt;

    @Column(name = "col_finished_at")
    private Instant finishedAt;
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/model/TrailerOAuthState.java
package com.doova.ktab.features.trailer.model;

import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_trailer_oauth_states")
@Getter
@Setter
public class TrailerOAuthState extends BaseEntity {

    @Column(name = "col_state", nullable = false, length = 100)
    private String state;

    @Column(name = "col_code_verifier", nullable = false, length = 200)
    private String codeVerifier;

    @Column(name = "col_client_id", nullable = false, length = 200)
    private String clientId;

    @Column(name = "col_redirect_uri", nullable = false, length = 500)
    private String redirectUri;

    @Column(name = "col_admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "col_used", nullable = false)
    private boolean used;
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/repository/BookTrailerRepository.java
package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookTrailerRepository extends JpaRepository<BookTrailer, Long> {

    List<BookTrailer> findByBookIdOrderByIdDesc(Long bookId);

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<TrailerStatus> statuses);

    long countByBookIdAndCreatedAtAfterAndStatusNotIn(Long bookId, LocalDateTime after, Collection<TrailerStatus> excluded);

    Optional<BookTrailer> findBySessionId(String sessionId);

    List<BookTrailer> findTop10ByStatusOrderByIdAsc(TrailerStatus status);

    @Query("select t from BookTrailer t where t.status = com.doova.ktab.features.trailer.enums.TrailerStatus.RUNNING "
            + "and t.nextCheckAt <= :now order by t.nextCheckAt")
    List<BookTrailer> findDueRunning(@Param("now") Instant now, Pageable page);
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/repository/TrailerOAuthStateRepository.java
package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.trailer.model.TrailerOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrailerOAuthStateRepository extends JpaRepository<TrailerOAuthState, Long> {

    Optional<TrailerOAuthState> findByStateAndUsedFalse(String state);
}
```

- [ ] **Step 4: Run it to verify it passes**

Run the Step 2 command again. Expected: `Tests run: 2, Failures: 0`. Flyway applies V21 and `ddl-auto=validate` passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V21__book_trailers.sql src/main/java/com/doova/ktab/features/trailer src/test/java/com/doova/ktab/features/trailer
git commit -m "feat(trailer): add trailer job schema, entities and properties"
```

---

### Task 3: Agent gateway (Anthropic SDK data plane) and session interpretation

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/agent/TrailerAgentGateway.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/agent/AnthropicTrailerAgentGateway.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/agent/SessionSnapshot.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/agent/SessionEvents.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/agent/TrailerTask.java`
- Create: `src/main/resources/trailer-agent/rubric.md`
- Test: `src/test/java/com/doova/ktab/features/trailer/agent/SessionEventsTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/agent/TrailerTaskTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/agent/TrailerAgentLiveTest.java`

**Interfaces:**
- Consumes: `TrailerProperties`.
- Produces:
  - `TrailerAgentGateway`, the only class that touches the Anthropic SDK:
    - `String uploadBook(Path pdf)`
    - `String startSession(long trailerId, String fileId, String taskDescription, String rubric)`, which returns the session id
    - `SessionSnapshot snapshot(String sessionId)`
    - `void interrupt(String sessionId)`
    - `List<OutputFile> outputs(String sessionId)`
    - `void download(String fileId, Path target)`
    - `void archive(String sessionId)`
    - `void upsertHiggsfieldCredential(HiggsfieldTokens tokens)`
    - `Optional<WebhookNotice> verifyWebhook(String body, Map<String,String> headers)`
    - `String traceUrl(String sessionId)`
  - Records nested in the gateway: `OutputFile(String id, String filename, long sizeBytes)`, `HiggsfieldTokens(String accessToken, String refreshToken, OffsetDateTime expiresAt, String clientId, String tokenEndpoint)` and `WebhookNotice(String eventId, String type, String resourceId)`.
  - `SessionSnapshot(Phase phase, String stopReason, String outcomeResult, String outcomeExplanation, int higgsfieldGenerations, int higgsfieldCalls)`, where `higgsfieldGenerations` counts jobs created, where `Phase` is one of `RUNNING, FINISHED, NEEDS_ACTION, BUDGET_REACHED, TERMINATED`.
  - `SessionEvents.interpret(String sessionStatus, List<JsonNode> events, List<OutcomeResult> outcomes, String generationMarker, java.util.regex.Pattern jobPattern)`, which returns a `SessionSnapshot` and is pure logic that can be unit-tested. `OutcomeResult(String result, String explanation)` is nested in `SessionEvents`.
  - `TrailerTask.describe(String title, String author, String language, String voiceId, int maxJobs, int maxInFlight, String generateArgs)`, which returns a `String`.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/doova/ktab/features/trailer/agent/SessionEventsTest.java
package com.doova.ktab.features.trailer.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEventsTest {

    private final ObjectMapper json = new ObjectMapper();
    static final java.util.regex.Pattern JOB = java.util.regex.Pattern.compile("(?i)\"?(job|request)_?id\"?");

    private JsonNode ev(String raw) throws Exception {
        return json.readTree(raw);
    }

    private List<JsonNode> events(String... raws) throws Exception {
        List<JsonNode> list = new ArrayList<>();
        for (String r : raws) {
            list.add(ev(r));
        }
        return list;
    }

    @Test
    void idleEndTurnWithSatisfiedOutcomeIsFinished() throws Exception {
        SessionSnapshot s = SessionEvents.interpret("idle",
                events("{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"end_turn\"}}"),
                List.of(new SessionEvents.OutcomeResult("satisfied", "All criteria met")), "generate", JOB);

        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.FINISHED);
        assertThat(s.outcomeResult()).isEqualTo("satisfied");
    }

    @Test
    void requiresActionIsReportedNotTreatedAsDone() throws Exception {
        SessionSnapshot s = SessionEvents.interpret("idle",
                events("{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"requires_action\"}}"),
                List.of(), "generate", JOB);
        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.NEEDS_ACTION);
    }

    @Test
    void budgetReachedIsItsOwnPhase() throws Exception {
        SessionSnapshot s = SessionEvents.interpret("idle",
                events("{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"end_turn\"}}",
                        "{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"budget_reached\"}}"),
                List.of(), "generate", JOB);
        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.BUDGET_REACHED);
    }

    @Test
    void countsOnlyCallsThatCreatedAJob() throws Exception {
        // Mirrors the first real run: jobs, a concurrency rejection, and a preset suggestion.
        SessionSnapshot s = SessionEvents.interpret("running", events(
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u1\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u1\",\"is_error\":false,\"content\":[{\"type\":\"text\",\"text\":\"{\\\"job_id\\\":\\\"j-1\\\"}\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u2\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u2\",\"is_error\":true,\"content\":[{\"type\":\"text\",\"text\":\"Maximum number of concurrent requests (4) has been reached\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u3\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u3\",\"is_error\":false,\"content\":[{\"type\":\"text\",\"text\":\"Suggested preset: cinematic. Resend with a preset.\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u4\",\"name\":\"get_generation_status\"}",
                "{\"type\":\"agent.tool_use\",\"id\":\"u5\",\"name\":\"bash\"}"), List.of(), "generate", JOB);

        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.RUNNING);
        assertThat(s.higgsfieldGenerations()).isEqualTo(1); // jobs
        assertThat(s.higgsfieldCalls()).isEqualTo(3);       // every generate call
    }

    @Test
    void terminatedIsTerminated() throws Exception {
        assertThat(SessionEvents.interpret("terminated", List.of(), List.of(), "generate", JOB).phase())
                .isEqualTo(SessionSnapshot.Phase.TERMINATED);
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/agent/TrailerTaskTest.java
package com.doova.ktab.features.trailer.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrailerTaskTest {

    @Test
    void taskCarriesBookFactsVoiceAndTheGenerationAllowance() {
        String task = TrailerTask.describe("ثورة دونالد ترامب", "ألكسندر دوغين", "ar", "voice-123", 8, 2,
                "model=seedance_2_5, aspect_ratio=16:9, duration=5");

        assertThat(task).contains("ثورة دونالد ترامب").contains("ألكسندر دوغين").contains("voice-123")
                .contains("at most 8").contains("at most 2 at a time").contains("model=seedance_2_5")
                .contains("/workspace/book.pdf").contains("/mnt/session/outputs/");
    }

    @Test
    void rubricIsOnTheClasspathAndGradeable() throws Exception {
        String rubric = new String(TrailerTaskTest.class.getResourceAsStream("/trailer-agent/rubric.md").readAllBytes());
        assertThat(rubric).contains("1920").contains("eleven_v3").contains("forced-alignment").contains("qc_report.json")
                .contains("captions_ar.srt").contains("right-to-left");
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/agent/TrailerAgentLiveTest.java
package com.doova.ktab.features.trailer.agent;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real session, real spend (Claude + Higgsfield + ElevenLabs). Needs the control plane from Task 1 and a connected
 * Higgsfield vault credential. Run: TRAILER_AGENT_LIVE=true TRAILER_LIVE_PDF=/path/book.pdf KTAB_TRAILER_* ANTHROPIC_API_KEY
 */
@EnabledIfEnvironmentVariable(named = "TRAILER_AGENT_LIVE", matches = "true")
class TrailerAgentLiveTest {

    @Test
    void startsASessionAndPrintsTheTraceUrl() throws Exception {
        TrailerProperties p = new TrailerProperties();
        p.setAnthropicApiKey(System.getenv("ANTHROPIC_API_KEY"));
        p.setAgentId(System.getenv("KTAB_TRAILER_AGENT_ID"));
        p.setAgentVersion(Integer.parseInt(System.getenv("KTAB_TRAILER_AGENT_VERSION")));
        p.setEnvironmentId(System.getenv("KTAB_TRAILER_ENVIRONMENT_ID"));
        p.setVaultId(System.getenv("KTAB_TRAILER_VAULT_ID"));
        p.setVoiceId(System.getenv("KTAB_TRAILER_VOICE_ID"));
        AnthropicTrailerAgentGateway gateway = new AnthropicTrailerAgentGateway(p);

        String fileId = gateway.uploadBook(Path.of(System.getenv("TRAILER_LIVE_PDF")));
        String sessionId = gateway.startSession(0L, fileId,
                TrailerTask.describe("Live test", "Test author", "ar", p.getVoiceId(), p.getMaxHiggsfieldGenerations(),
                        p.getHiggsfieldMaxInFlight(), p.getHiggsfieldGenerateArgs()),
                TrailerTask.rubric());

        System.out.println("Watch: " + gateway.traceUrl(sessionId));
        assertThat(sessionId).startsWith("sesn_");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o test -Dtest='SessionEventsTest,TrailerTaskTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, because `SessionEvents` is missing.

- [ ] **Step 3: Write the rubric**

```markdown
<!-- src/main/resources/trailer-agent/rubric.md -->
# Ktab trailer rubric (starter — tune after the first real runs)

Grade each criterion independently. The artifacts are in /mnt/session/outputs/.

1. `trailer.mp4` exists; ffprobe shows H.264 video, AAC audio, 1920x1080, a 16:9 display aspect, and a duration between 29.5 and 30.5 seconds.
2. `trailer_clean.mp4` exists with the same video properties and duration, and it has no burned-in captions.
3. `captions_ar.srt` is valid SRT: numbered cues, `HH:MM:SS,mmm --> HH:MM:SS,mmm` timings in increasing order, every cue within 0–30 s, at most 2 lines per cue, and at most 35 visible characters per line, in Arabic only. Its cues are burned into `trailer.mp4` in a proper Arabic font, with joined letters and right-to-left order. No English captions or subtitles exist in any deliverable.
4. The caption timings come from ElevenLabs forced-alignment of the Arabic narration (the alignment step is recorded in `qc_report.json` as `"aligned_from":"forced-alignment"`). Each Arabic cue is exactly the phrase spoken during its time span.
5. The narration was generated with `model_id` `eleven_v3` and `language_code` `ar`, is in Arabic, and is at most 28 seconds long.
6. The music is an instrumental track from the ElevenLabs Music API; the narration stays clearly intelligible above it for the whole trailer.
7. No frame of `trailer_clean.mp4` shows text, lettering, a logo or a watermark in any script. In `trailer.mp4`, the only text is the Arabic captions from `captions_ar.srt`. The frame check in `qc_report.json` covers at least one frame per second of `trailer_clean.mp4` and reports `text_found: false`, and `captions.language` is `"ar"`.
8. No identifiable real person is depicted. The imagery is symbolic.
9. `script.md` ties every narrated claim to a page of the book. It invents no events or quotes, and it presents the author's interpretations as the author's views, not as facts.
10. `qc_report.json` exists with every field from the system prompt's schema, its values match the files, and `"status"` is `"ok"`.
```

- [ ] **Step 4: Implement the gateway, snapshot, interpretation and task text**

```java
// src/main/java/com/doova/ktab/features/trailer/agent/SessionSnapshot.java
package com.doova.ktab.features.trailer.agent;

/** higgsfieldGenerations = jobs Higgsfield created; higgsfieldCalls = generate calls of any outcome (D5). */
public record SessionSnapshot(Phase phase, String stopReason, String outcomeResult, String outcomeExplanation,
                              int higgsfieldGenerations, int higgsfieldCalls) {

    public enum Phase {
        /** Agent still working (running, rescheduling, or transiently idle between steps). */
        RUNNING,
        /** Idle with a terminal stop reason (end_turn / retries_exhausted); read outcomeResult. */
        FINISHED,
        /** Idle waiting for a tool confirmation — should never happen with always_allow (Review Focus 1). */
        NEEDS_ACTION,
        /** Paused at the session budget; only a budget change resumes it (Review Focus 2). */
        BUDGET_REACHED,
        TERMINATED
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/agent/SessionEvents.java
package com.doova.ktab.features.trailer.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

/** Pure interpretation of a session's status + events, so the gate logic is unit-tested without the SDK. */
public final class SessionEvents {

    public record OutcomeResult(String result, String explanation) {
    }

    private SessionEvents() {
    }

    public static SessionSnapshot interpret(String sessionStatus, List<JsonNode> events, List<OutcomeResult> outcomes,
                                            String generationMarker, java.util.regex.Pattern jobPattern) {
        int generations = 0; // jobs created
        int calls = 0;
        java.util.Set<String> generateUseIds = new java.util.HashSet<>();
        String lastIdleStop = null;
        String marker = generationMarker.toLowerCase(Locale.ROOT);
        for (JsonNode e : events) {
            String type = e.path("type").asText();
            if ("agent.mcp_tool_use".equals(type)
                    && e.path("name").asText().toLowerCase(Locale.ROOT).contains(marker)) {
                calls++;
                generateUseIds.add(e.path("id").asText());
            } else if ("agent.mcp_tool_result".equals(type)
                    && generateUseIds.contains(e.path("mcp_tool_use_id").asText())
                    && !e.path("is_error").asBoolean(false)
                    && jobPattern.matcher(e.path("content").toString()).find()) {
                generations++; // D5: only replies that actually created a job count toward the money limit
            } else if ("session.status_idle".equals(type)) {
                lastIdleStop = e.path("stop_reason").path("type").asText(null);
            } else if ("session.status_running".equals(type)) {
                lastIdleStop = null; // a later run supersedes an earlier idle
            }
        }
        OutcomeResult outcome = outcomes.isEmpty() ? null : outcomes.get(outcomes.size() - 1);
        String result = outcome == null ? null : outcome.result();
        String explanation = outcome == null ? null : outcome.explanation();

        SessionSnapshot.Phase phase;
        if ("terminated".equals(sessionStatus)) {
            phase = SessionSnapshot.Phase.TERMINATED;
        } else if (!"idle".equals(sessionStatus) || lastIdleStop == null) {
            phase = SessionSnapshot.Phase.RUNNING;
        } else if ("requires_action".equals(lastIdleStop)) {
            phase = SessionSnapshot.Phase.NEEDS_ACTION;
        } else if ("budget_reached".equals(lastIdleStop)) {
            phase = SessionSnapshot.Phase.BUDGET_REACHED;
        } else {
            phase = SessionSnapshot.Phase.FINISHED; // end_turn, retries_exhausted
        }
        return new SessionSnapshot(phase, lastIdleStop, result, explanation, generations, calls);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/agent/TrailerTask.java
package com.doova.ktab.features.trailer.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The per-trailer task text sent as the outcome description; the durable instructions live in the agent's system prompt. */
public final class TrailerTask {

    private TrailerTask() {
    }

    public static String describe(String title, String author, String language, String voiceId, int maxJobs,
                                  int maxInFlight, String generateArgs) {
        return """
                Produce the 30-second trailer for this book.
                Book: /workspace/book.pdf
                Title: %s
                Author: %s
                Book language: %s
                ElevenLabs voice_id for the Arabic narration: %s
                Higgsfield allowance: at most %d video jobs in total, including replacements.
                Higgsfield concurrency: keep at most %d at a time in flight.
                Call generate_video with exactly these arguments: %s
                Captions: Arabic only (captions_ar.srt), burned into trailer.mp4. No English captions.
                Write every deliverable to /mnt/session/outputs/ as described in your instructions.
                """.formatted(title, author == null ? "" : author, language == null ? "ar" : language, voiceId, maxJobs,
                maxInFlight, generateArgs == null || generateArgs.isBlank() ? "(see your instructions)" : generateArgs);
    }

    public static String rubric() {
        try (InputStream in = TrailerTask.class.getResourceAsStream("/trailer-agent/rubric.md")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource trailer-agent/rubric.md");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/agent/TrailerAgentGateway.java
package com.doova.ktab.features.trailer.agent;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything Ktab does against Claude Managed Agents. The pipeline and tests depend on this, not on the SDK. */
public interface TrailerAgentGateway {

    record OutputFile(String id, String filename, long sizeBytes) {
    }

    record HiggsfieldTokens(String accessToken, String refreshToken, OffsetDateTime expiresAt, String clientId,
                            String tokenEndpoint) {
    }

    record WebhookNotice(String eventId, String type, String resourceId) {
    }

    String uploadBook(Path pdf);

    String startSession(long trailerId, String fileId, String taskDescription, String rubric);

    SessionSnapshot snapshot(String sessionId);

    void interrupt(String sessionId);

    List<OutputFile> outputs(String sessionId);

    void download(String fileId, Path target);

    void archive(String sessionId);

    void upsertHiggsfieldCredential(HiggsfieldTokens tokens);

    /** Verifies the HMAC signature; empty when the signature is invalid. */
    Optional<WebhookNotice> verifyWebhook(String body, Map<String, String> headers);

    String traceUrl(String sessionId);
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/agent/AnthropicTrailerAgentGateway.java
package com.doova.ktab.features.trailer.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.UnwrapWebhookParams;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.models.beta.files.FileListParams;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileUploadParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsAgentParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsFileResourceParams;
import com.anthropic.models.beta.sessions.BetaManagedAgentsSession;
import com.anthropic.models.beta.sessions.SessionCreateParams;
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsSessionEvent;
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserInterruptEventParams;
import com.anthropic.models.beta.sessions.events.EventSendParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsMcpOAuthCreateParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsMcpOAuthRefreshParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsTokenEndpointAuthNoneParam;
import com.anthropic.models.beta.vaults.credentials.CredentialCreateParams;
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsCredential;
import com.anthropic.models.beta.vaults.credentials.CredentialArchiveParams;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only class that talks to the Anthropic SDK for trailers. Not an AnthropicClient bean on purpose: storybook injects
 * AnthropicClient by type, and a second bean would make that injection ambiguous.
 * Type names verified with javap against anthropic-java-core 2.34.0; see Global Constraints for the raw-JSON exceptions.
 */
@Component
@Slf4j
public class AnthropicTrailerAgentGateway implements TrailerAgentGateway {

    static final String MANAGED_AGENTS_BETA = "managed-agents-2026-04-01";

    private final AnthropicClient client;
    private final TrailerProperties properties;
    private final ObjectMapper sdkJson = ObjectMappers.jsonMapper();

    public AnthropicTrailerAgentGateway(TrailerProperties properties) {
        this.properties = properties;
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
        if (properties.getAnthropicApiKey() != null && !properties.getAnthropicApiKey().isBlank()) {
            builder.apiKey(properties.getAnthropicApiKey());
        } else {
            builder.apiKey("missing-anthropic-api-key"); // lets the app start with the feature off
        }
        this.client = builder.build();
    }

    @Override
    public String uploadBook(Path pdf) {
        FileMetadata file = client.beta().files().upload(FileUploadParams.builder().file(pdf).build());
        return file.id();
    }

    @Override
    public String startSession(long trailerId, String fileId, String taskDescription, String rubric) {
        // D3: outcome kickoff in initial_events so create + kickoff are one atomic call.
        // D4: budget. Neither has a typed setter in anthropic-java 2.34.0, so both go as raw body properties.
        JsonValue initialEvents = JsonValue.from(List.of(Map.of(
                "type", "user.define_outcome",
                "description", taskDescription,
                "rubric", Map.of("type", "text", "content", rubric),
                "max_iterations", properties.getMaxOutcomeIterations())));
        JsonValue budget = JsonValue.from(Map.of("type", "limit",
                "max_list_cost", Map.of("amount", Long.toString(properties.getBudgetCents()), "currency", "USD")));

        BetaManagedAgentsSession session = client.beta().sessions().create(SessionCreateParams.builder()
                .agent(BetaManagedAgentsAgentParams.builder()
                        .type(BetaManagedAgentsAgentParams.Type.AGENT)
                        .id(properties.getAgentId())
                        .version(properties.getAgentVersion())
                        .build())
                .environmentId(properties.getEnvironmentId())
                .addVaultId(properties.getVaultId())
                .addResource(BetaManagedAgentsFileResourceParams.builder()
                        .type(BetaManagedAgentsFileResourceParams.Type.FILE)
                        .fileId(fileId)
                        .mountPath("/workspace/book.pdf")
                        .build())
                .title("ktab-trailer-" + trailerId)
                .metadata(SessionCreateParams.Metadata.builder()
                        .putAdditionalProperty("ktab_trailer_id", JsonValue.from(Long.toString(trailerId)))
                        .build())
                .putAdditionalBodyProperty("initial_events", initialEvents)
                .putAdditionalBodyProperty("budget", budget)
                .build());
        log.info("trailer {} session {} started: {}", trailerId, session.id(), traceUrl(session.id()));
        return session.id();
    }

    @Override
    public SessionSnapshot snapshot(String sessionId) {
        BetaManagedAgentsSession session = client.beta().sessions().retrieve(sessionId);
        List<JsonNode> events = new ArrayList<>();
        for (BetaManagedAgentsSessionEvent event : client.beta().sessions().events().list(sessionId).autoPager()) {
            events.add(sdkJson.valueToTree(event));
        }
        List<SessionEvents.OutcomeResult> outcomes = session.outcomeEvaluations().stream()
                .map(o -> new SessionEvents.OutcomeResult(o.result(), o.explanation().orElse(null)))
                .toList();
        return SessionEvents.interpret(session.status().asString(), events, outcomes,
                properties.getHiggsfieldGenerationMarker(),
                java.util.regex.Pattern.compile(properties.getHiggsfieldJobPattern()));
    }

    @Override
    public void interrupt(String sessionId) {
        client.beta().sessions().events().send(sessionId, EventSendParams.builder()
                .addEvent(BetaManagedAgentsUserInterruptEventParams.builder()
                        .type(BetaManagedAgentsUserInterruptEventParams.Type.USER_INTERRUPT)
                        .build())
                .build());
    }

    @Override
    public List<OutputFile> outputs(String sessionId) {
        List<OutputFile> files = new ArrayList<>();
        for (FileMetadata f : client.beta().files().list(FileListParams.builder()
                .scopeId(sessionId)
                .addBeta(MANAGED_AGENTS_BETA) // scope_id needs the Managed Agents beta header (environments doc)
                .build()).autoPager()) {
            files.add(new OutputFile(f.id(), f.filename(), f.sizeBytes()));
        }
        return files;
    }

    @Override
    public void download(String fileId, Path target) {
        try (HttpResponse response = client.beta().files().download(fileId);
             InputStream body = response.body()) {
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void archive(String sessionId) {
        try {
            client.beta().sessions().archive(sessionId);
        } catch (RuntimeException e) {
            // SSE idle can precede the queryable status (client-patterns); the next reconcile retries.
            log.info("archive of {} deferred: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public void upsertHiggsfieldCredential(HiggsfieldTokens t) {
        // Keys (mcp_server_url, client_id, token_endpoint) are immutable, so archive any existing Higgsfield credential
        // and create a fresh one; a vault allows one active credential per mcp_server_url.
        for (BetaManagedAgentsCredential c : client.beta().vaults().credentials().list(properties.getVaultId()).autoPager()) {
            JsonNode auth = sdkJson.valueToTree(c).path("auth");
            if (properties.getHiggsfieldMcpUrl().equals(auth.path("mcp_server_url").asText())
                    && c.archivedAt().isEmpty()) {
                client.beta().vaults().credentials().archive(c.id(), CredentialArchiveParams.builder()
                        .vaultId(properties.getVaultId()).build());
            }
        }
        client.beta().vaults().credentials().create(properties.getVaultId(), CredentialCreateParams.builder()
                .displayName("Higgsfield MCP (Ktab)")
                .auth(BetaManagedAgentsMcpOAuthCreateParams.builder()
                        .type(BetaManagedAgentsMcpOAuthCreateParams.Type.MCP_OAUTH)
                        .mcpServerUrl(properties.getHiggsfieldMcpUrl())
                        .accessToken(t.accessToken())
                        .expiresAt(t.expiresAt())
                        .refresh(BetaManagedAgentsMcpOAuthRefreshParams.builder()
                                .tokenEndpoint(t.tokenEndpoint())
                                .clientId(t.clientId())
                                .refreshToken(t.refreshToken())
                                .resource(properties.getHiggsfieldMcpUrl())
                                .tokenEndpointAuth(BetaManagedAgentsTokenEndpointAuthNoneParam.builder()
                                        .type(BetaManagedAgentsTokenEndpointAuthNoneParam.Type.NONE)
                                        .build())
                                .build())
                        .build())
                .build());
    }

    @Override
    public Optional<WebhookNotice> verifyWebhook(String body, Map<String, String> headers) {
        Headers.Builder h = Headers.builder();
        headers.forEach(h::put);
        try {
            var event = client.beta().webhooks().unwrap(UnwrapWebhookParams.builder()
                    .body(body)
                    .headers(h.build())
                    .secret(properties.getWebhookSigningKey())
                    .build());
            JsonNode data = sdkJson.readTree(body).path("data"); // body is now verified
            return Optional.of(new WebhookNotice(event.id(), data.path("type").asText(), data.path("id").asText()));
        } catch (RuntimeException | IOException e) {
            log.warn("trailer webhook rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String traceUrl(String sessionId) {
        return "https://platform.claude.com/workspaces/" + properties.getWorkspace() + "/sessions/" + sessionId;
    }
}
```

> **SDK names the compiler may correct.** Every class above was checked with `javap` against 2.34.0 except the credential **list/archive** calls: `vaults().credentials().list(vaultId)`, `BetaManagedAgentsCredential.archivedAt()` and `CredentialArchiveParams.vaultId(...)`. If the compiler rejects one of them, run `javap -classpath <anthropic-java-core jar> com.anthropic.services.blocking.beta.vaults.CredentialService` and fix the name. Keep the behavior: archive the old Higgsfield credential, then create the new one.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o test -Dtest='SessionEventsTest,TrailerTaskTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 7, Failures: 0`. Also run `mvn -o compile` to prove the gateway compiles against the real SDK.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/agent src/main/resources/trailer-agent src/test/java/com/doova/ktab/features/trailer/agent
git commit -m "feat(trailer): add Managed Agents gateway, session interpretation, task text and rubric"
```

---

### Task 4: "Connect Higgsfield" admin OAuth flow → vault credential

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/oauth/Pkce.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/oauth/HiggsfieldOAuthService.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/oauth/PkceTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/oauth/HiggsfieldOAuthServiceTest.java`

**Interfaces:**
- Consumes:
  - `TrailerAgentGateway.upsertHiggsfieldCredential`
  - `TrailerOAuthStateRepository`
  - `TrailerProperties.higgsfieldMcpUrl` and `TrailerProperties.higgsfieldCallbackUrl`
- Produces:
  - `Pkce.verifier()`, which returns a `String`
  - `Pkce.challenge(String verifier)`, which returns a `String` (S256, base64url, no padding)
  - `HiggsfieldOAuthService(RestClient.Builder, TrailerOAuthStateRepository, TrailerAgentGateway, TrailerProperties)`:
    - `String begin(Long adminUserId)`, which returns the authorize URL to open
    - `void complete(String code, String state)`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/doova/ktab/features/trailer/oauth/PkceTest.java
package com.doova.ktab.features.trailer.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceTest {

    @Test
    void rfc7636AppendixBVector() {
        // RFC 7636 Appendix B: verifier -> S256 challenge
        assertThat(Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void verifierIsUrlSafeAndLongEnough() {
        String v = Pkce.verifier();
        assertThat(v).matches("[A-Za-z0-9_-]{43,128}");
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/oauth/HiggsfieldOAuthServiceTest.java
package com.doova.ktab.features.trailer.oauth;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.model.TrailerOAuthState;
import com.doova.ktab.features.trailer.repository.TrailerOAuthStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HiggsfieldOAuthServiceTest {

    static final String METADATA = """
            {"issuer":"https://mcp.higgsfield.ai","authorization_endpoint":"https://mcp.higgsfield.ai/oauth2/authorize",
             "token_endpoint":"https://mcp.higgsfield.ai/oauth2/token","registration_endpoint":"https://mcp.higgsfield.ai/oauth2/register"}
            """;

    final TrailerOAuthStateRepository states = mock(TrailerOAuthStateRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    MockRestServiceServer server;
    HiggsfieldOAuthService service;

    @BeforeEach
    void setUp() {
        TrailerProperties p = new TrailerProperties();
        p.setHiggsfieldCallbackUrl("https://ktab.example/api/v1/public/trailer-agent/higgsfield/callback");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new HiggsfieldOAuthService(builder, states, gateway, p);
        when(states.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void beginRegistersAPublicClientAndReturnsAPkceAuthorizeUrl() {
        server.expect(requestTo("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server"))
                .andRespond(withSuccess(METADATA, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mcp.higgsfield.ai/oauth2/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
                .andExpect(jsonPath("$.redirect_uris[0]").value("https://ktab.example/api/v1/public/trailer-agent/higgsfield/callback"))
                .andRespond(withSuccess("{\"client_id\":\"cid-1\"}", MediaType.APPLICATION_JSON));

        String url = service.begin(9L);

        assertThat(url).startsWith("https://mcp.higgsfield.ai/oauth2/authorize?")
                .contains("client_id=cid-1").contains("code_challenge_method=S256").contains("response_type=code")
                .contains("offline_access").contains("resource=");
        ArgumentCaptor<TrailerOAuthState> saved = ArgumentCaptor.forClass(TrailerOAuthState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().getClientId()).isEqualTo("cid-1");
        assertThat(saved.getValue().getAdminUserId()).isEqualTo(9L);
    }

    @Test
    void completeExchangesTheCodeAndStoresTokensInTheVault() {
        TrailerOAuthState st = new TrailerOAuthState();
        st.setState("s1");
        st.setCodeVerifier("v1");
        st.setClientId("cid-1");
        st.setRedirectUri("https://ktab.example/cb");
        st.setAdminUserId(9L);
        st.setCreatedAt(java.time.LocalDateTime.now());
        when(states.findByStateAndUsedFalse("s1")).thenReturn(Optional.of(st));
        server.expect(requestTo("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server"))
                .andRespond(withSuccess(METADATA, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mcp.higgsfield.ai/oauth2/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=v1")))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        service.complete("code-1", "s1");

        ArgumentCaptor<TrailerAgentGateway.HiggsfieldTokens> tokens = ArgumentCaptor.forClass(TrailerAgentGateway.HiggsfieldTokens.class);
        verify(gateway).upsertHiggsfieldCredential(tokens.capture());
        assertThat(tokens.getValue().refreshToken()).isEqualTo("rt");
        assertThat(tokens.getValue().tokenEndpoint()).isEqualTo("https://mcp.higgsfield.ai/oauth2/token");
        assertThat(st.isUsed()).isTrue();
    }

    @Test
    void unknownOrReusedStateIsRejected() {
        when(states.findByStateAndUsedFalse("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete("c", "nope")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aTokenWithoutARefreshTokenIsRejectedBecauseTheVaultCouldNotKeepItAlive() {
        TrailerOAuthState st = new TrailerOAuthState();
        st.setState("s2");
        st.setCodeVerifier("v");
        st.setClientId("c");
        st.setRedirectUri("r");
        st.setAdminUserId(9L);
        st.setCreatedAt(java.time.LocalDateTime.now());
        when(states.findByStateAndUsedFalse("s2")).thenReturn(Optional.of(st));
        server.expect(requestTo("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server"))
                .andRespond(withSuccess(METADATA, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mcp.higgsfield.ai/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.complete("c", "s2")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(gateway);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o test -Dtest='PkceTest,HiggsfieldOAuthServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, because `Pkce` is missing.

- [ ] **Step 3: Implement**

```java
// src/main/java/com/doova/ktab/features/trailer/oauth/Pkce.java
package com.doova.ktab.features.trailer.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** RFC 7636 PKCE, S256. */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pkce() {
    }

    public static String verifier() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // 64 chars
    }

    public static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String state() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/oauth/HiggsfieldOAuthService.java
package com.doova.ktab.features.trailer.oauth;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.model.TrailerOAuthState;
import com.doova.ktab.features.trailer.repository.TrailerOAuthStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * D8: admin connects Ktab's Higgsfield account once; tokens go straight to the Anthropic vault as mcp_oauth with a refresh
 * block, after which Anthropic refreshes them. Endpoints are discovered from Higgsfield's RFC 8414 metadata at runtime.
 */
@Service
public class HiggsfieldOAuthService {

    static final String SCOPE = "openid email offline_access";

    private final RestClient http;
    private final TrailerOAuthStateRepository states;
    private final TrailerAgentGateway gateway;
    private final TrailerProperties properties;

    @Autowired
    public HiggsfieldOAuthService(TrailerOAuthStateRepository states, TrailerAgentGateway gateway,
                                  TrailerProperties properties) {
        this(RestClient.builder(), states, gateway, properties);
    }

    HiggsfieldOAuthService(RestClient.Builder builder, TrailerOAuthStateRepository states, TrailerAgentGateway gateway,
                           TrailerProperties properties) {
        this.http = builder.build();
        this.states = states;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Transactional
    public String begin(Long adminUserId) {
        JsonNode meta = metadata();
        String redirect = properties.getHiggsfieldCallbackUrl();
        JsonNode reg = http.post().uri(meta.path("registration_endpoint").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "client_name", "Ktab trailer agent",
                        "redirect_uris", List.of(redirect),
                        "grant_types", List.of("authorization_code", "refresh_token"),
                        "response_types", List.of("code"),
                        "token_endpoint_auth_method", "none",
                        "scope", SCOPE))
                .retrieve().body(JsonNode.class);
        String clientId = reg == null ? null : reg.path("client_id").asText(null);
        if (clientId == null) {
            throw new IllegalStateException("Higgsfield did not return a client_id on registration");
        }

        TrailerOAuthState st = new TrailerOAuthState();
        st.setState(Pkce.state());
        st.setCodeVerifier(Pkce.verifier());
        st.setClientId(clientId);
        st.setRedirectUri(redirect);
        st.setAdminUserId(adminUserId);
        states.save(st);

        return UriComponentsBuilder.fromUriString(meta.path("authorization_endpoint").asText())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirect)
                .queryParam("scope", SCOPE)
                .queryParam("state", st.getState())
                .queryParam("code_challenge", Pkce.challenge(st.getCodeVerifier()))
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", properties.getHiggsfieldMcpUrl())
                .encode().build().toUriString();
    }

    @Transactional
    public void complete(String code, String state) {
        TrailerOAuthState st = states.findByStateAndUsedFalse(state)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.TRAILER_OAUTH_INVALID));
        if (st.getCreatedAt() == null || st.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
            throw new BadRequestException(ApiMessageKey.TRAILER_OAUTH_INVALID);
        }
        st.setUsed(true); // single use, even if the exchange below fails

        String tokenEndpoint = metadata().path("token_endpoint").asText();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", st.getRedirectUri());
        form.add("client_id", st.getClientId());
        form.add("code_verifier", st.getCodeVerifier());
        form.add("resource", properties.getHiggsfieldMcpUrl());
        JsonNode token = http.post().uri(tokenEndpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);

        String refresh = token == null ? null : token.path("refresh_token").asText(null);
        if (refresh == null) {
            // Without a refresh token the vault credential dies at expiry; make the admin retry with offline_access.
            throw new BadRequestException(ApiMessageKey.TRAILER_OAUTH_INVALID);
        }
        gateway.upsertHiggsfieldCredential(new TrailerAgentGateway.HiggsfieldTokens(
                token.path("access_token").asText(), refresh,
                OffsetDateTime.now().plusSeconds(token.path("expires_in").asLong(3600)),
                st.getClientId(), tokenEndpoint));
    }

    private JsonNode metadata() {
        String base = UriComponentsBuilder.fromUriString(properties.getHiggsfieldMcpUrl()).replacePath(null).build().toUriString();
        JsonNode meta = http.get().uri(base + "/.well-known/oauth-authorization-server").retrieve().body(JsonNode.class);
        if (meta == null || !meta.hasNonNull("token_endpoint")) {
            throw new IllegalStateException("Higgsfield OAuth metadata unavailable");
        }
        return meta;
    }
}
```

`TRAILER_OAUTH_INVALID` is added with the other message keys in Task 6.

- [ ] **Step 4: Run them to verify they pass**

Run the Step 2 command again, after Task 6's Step 3 adds the message key. If you run Task 4 first, add the key now. Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/oauth src/test/java/com/doova/ktab/features/trailer/oauth
git commit -m "feat(trailer): add admin Higgsfield OAuth (DCR + PKCE) that stores tokens in the agent vault"
```

---

### Task 5: Launcher, reconciler (completion + spend guard) and worker

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerStore.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerLauncher.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerReconciler.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerWorker.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/config/TrailerConfig.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerLauncherTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerReconcilerTest.java`

**Interfaces:**
- Consumes:
  - `TrailerAgentGateway`, `SessionSnapshot`, `TrailerTask`;
  - `BookRepository`, `AttachmentService.getAttachment(bookId, "Book", "PDF_SOURCE")`;
  - `S3Client`, via `TrailerStore`.
- Produces:
  - `TrailerStore`:
    - `void downloadTo(String key, Path target)`
    - `void upload(String key, Path file, String contentType)`
    - `static String key(long bookId, long trailerId, String filename)`
  - `TrailerLauncher.launch(Long trailerId)`
  - `TrailerReconciler.reconcile(Long trailerId)`, which moves a trailer from RUNNING to HARVESTING, FAILED or CANCELLED, or keeps it RUNNING
  - `TrailerWorker.tick()`
  - `TrailerHarvester.harvest(Long)`, which is implemented in Task 6. The worker calls it.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerReconcilerTest.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.SessionSnapshot;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrailerReconcilerTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerReconciler reconciler = new TrailerReconciler(trailers, gateway, props);
    final BookTrailer t = new BookTrailer();

    @BeforeEach
    void setUp() {
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.RUNNING);
        t.setSessionId("sesn_1");
        t.setStartedAt(Instant.now());
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void snapshot(SessionSnapshot.Phase phase, String stop, String outcome, int generations) {
        when(gateway.snapshot("sesn_1")).thenReturn(new SessionSnapshot(phase, stop, outcome, "why", generations, generations));
    }

    @Test
    void reconcilerFinishesWithoutAnyWebhook() {
        snapshot(SessionSnapshot.Phase.FINISHED, "end_turn", "satisfied", 5);

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.HARVESTING);
        assertThat(t.getOutcomeResult()).isEqualTo("satisfied");
        assertThat(t.getHiggsfieldGenerations()).isEqualTo(5);
    }

    @Test
    void stillRunningIsCheckedAgainLater() {
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 2);

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getNextCheckAt()).isAfter(Instant.now());
    }

    @Test
    void tooManyHiggsfieldGenerationsInterruptsTheSession() {
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 9); // job cap 8

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("Higgsfield").contains("8");
    }

    @Test
    void aLoopOfRejectedCallsHitsTheCallCap() {
        // 25 generate calls but only 5 jobs: the agent is stuck retrying rejected calls (D5).
        when(gateway.snapshot("sesn_1")).thenReturn(
                new SessionSnapshot(SessionSnapshot.Phase.RUNNING, null, null, null, 5, 25));

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("calls").contains("24");
    }

    @Test
    void rejectedCallsBelowTheCapDoNotStopAGoodRun() {
        // The first real run: 7 jobs from 11 calls must keep running.
        when(gateway.snapshot("sesn_1")).thenReturn(
                new SessionSnapshot(SessionSnapshot.Phase.RUNNING, null, null, null, 7, 11));

        reconciler.reconcile(7L);

        verify(gateway, never()).interrupt(anyString());
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getHiggsfieldCalls()).isEqualTo(11);
    }

    @Test
    void requiresActionFailsTheTrailerWithAPermissionHint() {
        snapshot(SessionSnapshot.Phase.NEEDS_ACTION, "requires_action", null, 1);

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("always_allow");
    }

    @Test
    void budgetReachedWithoutAnOutputFails() {
        snapshot(SessionSnapshot.Phase.BUDGET_REACHED, "budget_reached", null, 4);
        when(gateway.outputs("sesn_1")).thenReturn(List.of());

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        assertThat(t.getError()).contains("budget");
    }

    @Test
    void budgetReachedWithATrailerIsHarvestedForReview() {
        snapshot(SessionSnapshot.Phase.BUDGET_REACHED, "budget_reached", null, 4);
        when(gateway.outputs("sesn_1")).thenReturn(List.of(new TrailerAgentGateway.OutputFile("f1", "trailer.mp4", 10)));

        reconciler.reconcile(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.HARVESTING);
    }

    @Test
    void aSessionOlderThanTheMaximumAgeIsStopped() {
        t.setStartedAt(Instant.now().minus(props.getMaxSessionAge()).minusSeconds(60));
        snapshot(SessionSnapshot.Phase.RUNNING, null, null, 3);

        reconciler.reconcile(7L);

        verify(gateway).interrupt("sesn_1");
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
    }

    @Test
    void cancelledTrailerIsLeftAlone() {
        t.setStatus(TrailerStatus.CANCELLED);

        reconciler.reconcile(7L);

        verifyNoInteractions(gateway);
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerLauncherTest.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerLauncherTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerBookSource books = mock(TrailerBookSource.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerLauncher launcher = new TrailerLauncher(trailers, gateway, books, props);

    @Test
    void uploadsTheBookStartsTheSessionAndRecordsIt() throws Exception {
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.QUEUED);
        props.setVoiceId("voice-1");
        props.setAgentVersion(4);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(books.facts(3L)).thenReturn(new TrailerBookSource.BookFacts("عنوان", "مؤلف", "ar", "books/3/source.pdf"));
        when(books.downloadPdf(eq("books/3/source.pdf"), any())).thenAnswer(i -> i.getArgument(1));
        when(gateway.uploadBook(any())).thenReturn("file_1");
        when(gateway.startSession(eq(7L), eq("file_1"), contains("voice-1"), anyString())).thenReturn("sesn_1");

        launcher.launch(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.RUNNING);
        assertThat(t.getSessionId()).isEqualTo("sesn_1");
        assertThat(t.getBookFileId()).isEqualTo("file_1");
        assertThat(t.getAgentVersion()).isEqualTo(4);
    }

    @Test
    void aBookWithoutAPdfFailsImmediately() {
        BookTrailer t = new BookTrailer();
        t.setId(8L);
        t.setBookId(4L);
        t.setStatus(TrailerStatus.QUEUED);
        when(trailers.findById(8L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(books.facts(4L)).thenReturn(new TrailerBookSource.BookFacts("t", "a", "ar", null));

        launcher.launch(8L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        verifyNoInteractions(gateway);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o test -Dtest='TrailerLauncherTest,TrailerReconcilerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 3: Implement the store, book source, launcher, reconciler, worker and config**

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerStore.java
package com.doova.ktab.features.trailer.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TrailerStore {

    private final S3Client s3;
    private final String bucket;

    public TrailerStore(S3Client s3, @Value("${cloudflare.r2.bucketName:${aws.s3.bucketName:ktab-bucket}}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public static String key(long bookId, long trailerId, String filename) {
        return "trailers/" + bookId + "/" + trailerId + "/" + filename;
    }

    public Path downloadTo(String key, Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), target);
        return target;
    }

    public void upload(String key, Path file, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromFile(file));
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerBookSource.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/** Reads lazy book relations inside a transaction (the worker thread has none of its own). */
@Component
@RequiredArgsConstructor
public class TrailerBookSource {

    public record BookFacts(String title, String author, String language, String pdfKey) {
    }

    private final BookRepository books;
    private final AttachmentService attachments;
    private final TrailerStore store;

    @Transactional(readOnly = true)
    public BookFacts facts(Long bookId) {
        Book book = books.findById(bookId).orElseThrow();
        String pdfKey = attachments.getAttachment(bookId, Book.class.getSimpleName(), "PDF_SOURCE")
                .map(Attachment::getStoragePath).orElse(null);
        return new BookFacts(book.getTitle(), authorName(book), book.getLanguage(), pdfKey);
    }

    public Path downloadPdf(String key, Path target) {
        return store.downloadTo(key, target);
    }

    private static String authorName(Book book) {
        if (book.getCustomAuthorName() != null && !book.getCustomAuthorName().isBlank()) {
            return book.getCustomAuthorName();
        }
        User author = book.getAuthor();
        return author == null ? "" : (author.getFirstName() + " " + (author.getLastName() == null ? "" : author.getLastName())).strip();
    }
}
```

Add `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerBookSource.java` to this task's **Create** list.

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerLauncher.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.agent.TrailerTask;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** QUEUED -> RUNNING: upload the PDF, create the session (outcome kickoff + budget in one call). */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerLauncher {

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerBookSource books;
    private final TrailerProperties properties;

    public void launch(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        if (t.getStatus() != TrailerStatus.QUEUED) {
            return;
        }
        TrailerBookSource.BookFacts facts = books.facts(t.getBookId());
        if (facts.pdfKey() == null) {
            fail(t, "The book has no source PDF to read.");
            return;
        }
        Path dir = null;
        try {
            if (t.getBookFileId() == null) {
                dir = Files.createTempDirectory("trailer-" + trailerId + "-");
                Path pdf = books.downloadPdf(facts.pdfKey(), dir.resolve("book.pdf"));
                t.setBookFileId(gateway.uploadBook(pdf));
                t = trailers.save(t); // a retry after a crash reuses the uploaded file
            }
            String sessionId = gateway.startSession(trailerId, t.getBookFileId(),
                    TrailerTask.describe(facts.title(), facts.author(), facts.language(), properties.getVoiceId(),
                            properties.getMaxHiggsfieldGenerations(), properties.getHiggsfieldMaxInFlight(),
                            properties.getHiggsfieldGenerateArgs()),
                    TrailerTask.rubric());
            t.setSessionId(sessionId);
            t.setAgentVersion(properties.getAgentVersion());
            t.setStatus(TrailerStatus.RUNNING);
            t.setStartedAt(Instant.now());
            t.setNextCheckAt(Instant.now().plus(properties.getReconcileEvery()));
            trailers.save(t);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            if (dir != null) {
                org.springframework.util.FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }

    private void fail(BookTrailer t, String reason) {
        t.setStatus(TrailerStatus.FAILED);
        t.setError(reason);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerReconciler.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.SessionSnapshot;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** RUNNING -> HARVESTING / FAILED. Works without webhooks (D6); webhooks only pull nextCheckAt forward. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerReconciler {

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerProperties properties;

    public void reconcile(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        if (t.getStatus() != TrailerStatus.RUNNING || t.getSessionId() == null) {
            return;
        }
        SessionSnapshot s = gateway.snapshot(t.getSessionId());
        t.setHiggsfieldGenerations(s.higgsfieldGenerations());
        t.setHiggsfieldCalls(s.higgsfieldCalls());

        if (s.higgsfieldGenerations() > properties.getMaxHiggsfieldGenerations()) { // D5: the money limit
            stop(t, "Stopped: the agent created " + s.higgsfieldGenerations() + " Higgsfield jobs, over the cap of "
                    + properties.getMaxHiggsfieldGenerations() + ".");
            return;
        }
        if (s.higgsfieldCalls() > properties.getMaxHiggsfieldCalls()) { // D5: runaway guard
            stop(t, "Stopped: the agent made " + s.higgsfieldCalls() + " Higgsfield generate calls, over the cap of "
                    + properties.getMaxHiggsfieldCalls() + " calls; it is probably retrying rejected calls in a loop.");
            return;
        }
        switch (s.phase()) {
            case RUNNING -> {
                if (t.getStartedAt() != null && t.getStartedAt().plus(properties.getMaxSessionAge()).isBefore(Instant.now())) {
                    stop(t, "Stopped: the session ran longer than " + properties.getMaxSessionAge() + ".");
                    return;
                }
                t.setNextCheckAt(Instant.now().plus(properties.getReconcileEvery()));
                trailers.save(t);
            }
            case FINISHED, TERMINATED -> toHarvest(t, s);
            case NEEDS_ACTION -> stop(t, "The agent is waiting for a tool approval. Set permission_policy always_allow on "
                    + "the agent's toolsets (ops/trailer-agent/agent.json) and publish a new agent version.");
            case BUDGET_REACHED -> {
                boolean hasTrailer = gateway.outputs(t.getSessionId()).stream()
                        .anyMatch(f -> "trailer.mp4".equals(f.filename()));
                if (hasTrailer) {
                    s = new SessionSnapshot(s.phase(), s.stopReason(), "budget_reached",
                            "Session budget reached before the grader was satisfied.", s.higgsfieldGenerations(),
                            s.higgsfieldCalls());
                    toHarvest(t, s);
                } else {
                    fail(t, "The session reached its $" + (properties.getBudgetCents() / 100.0)
                            + " budget before producing a trailer.");
                }
            }
        }
    }

    private void toHarvest(BookTrailer t, SessionSnapshot s) {
        t.setOutcomeResult(s.outcomeResult());
        t.setOutcomeExplanation(s.outcomeExplanation());
        t.setStatus(TrailerStatus.HARVESTING);
        trailers.save(t);
    }

    private void stop(BookTrailer t, String reason) {
        try {
            gateway.interrupt(t.getSessionId());
        } catch (RuntimeException e) {
            log.warn("interrupt of {} failed: {}", t.getSessionId(), e.getMessage());
        }
        fail(t, reason);
    }

    private void fail(BookTrailer t, String reason) {
        t.setStatus(TrailerStatus.FAILED);
        t.setError(reason);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerWorker.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Single scheduler. Each trailer step is small (a few API calls), so one thread per tick is enough; @Version on
 * BookTrailer rejects a stale save if a user cancels mid-step or a second instance races (Review Focus 5).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class TrailerWorker {

    private final BookTrailerRepository trailers;
    private final TrailerLauncher launcher;
    private final TrailerReconciler reconciler;
    private final TrailerHarvester harvester;

    @Scheduled(fixedDelayString = "${ktab.trailer.worker-tick:15s}")
    public void tick() {
        trailers.findTop10ByStatusOrderByIdAsc(TrailerStatus.QUEUED).forEach(t -> run(t, launcher::launch, "launch"));
        trailers.findDueRunning(Instant.now(), PageRequest.of(0, 20)).forEach(t -> run(t, reconciler::reconcile, "reconcile"));
        trailers.findTop10ByStatusOrderByIdAsc(TrailerStatus.HARVESTING).forEach(t -> run(t, harvester::harvest, "harvest"));
    }

    private void run(BookTrailer t, Consumer<Long> step, String name) {
        try {
            step.accept(t.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("trailer {} {} lost a race; next tick re-reads it", t.getId(), name);
        } catch (RuntimeException e) {
            // Transient API/network errors: leave the row as is; the next tick retries. Launch failures that
            // persist are visible in logs and in the Console; the maxSessionAge guard bounds a stuck RUNNING row.
            log.error("trailer {} {} failed", t.getId(), name, e);
        }
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/config/TrailerConfig.java
package com.doova.ktab.features.trailer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(TrailerProperties.class)
public class TrailerConfig {

    /** Scheduling only with the feature on, so a disabled feature starts no worker. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
    static class Scheduling {
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

These tests need Task 6's `TrailerHarvester` to compile, because the worker references it. Implement Task 6 Steps 1–3 first, or temporarily run only the listed test classes after Task 6. Run the Step 2 command. Expected: `Tests run: 10, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer/pipeline src/main/java/com/doova/ktab/features/trailer/config/TrailerConfig.java src/test/java/com/doova/ktab/features/trailer/pipeline
git commit -m "feat(trailer): add launcher, reconciler with spend and permission guards, and worker"
```

---

### Task 6: Harvester (independent verification → R2), service, controllers, webhook

**Files:**
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerHarvester.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/pipeline/MediaProbe.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerAccess.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerService.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerView.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerConflictException.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerController.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerAdminController.java`
- Create: `src/main/java/com/doova/ktab/features/trailer/web/TrailerPublicController.java`
- Modify: `src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java`. Insert after `STORYBOOK_NOT_READY("storybook.not.ready"), STORYBOOK_INSUFFICIENT_CREDITS("storybook.insufficient.credits"),`.
- Modify: `src/main/resources/messages.properties`. Append the keys at the end.
- Test: `src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerHarvesterTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/web/TrailerServiceTest.java`
- Test: `src/test/java/com/doova/ktab/features/trailer/web/TrailerPublicControllerTest.java`

**Interfaces:**
- Consumes:
  - from earlier tasks: `TrailerAgentGateway`, `TrailerStore`, `BookTrailerRepository`, `HiggsfieldOAuthService`, `TrailerProperties`;
  - from Ktab: `BookRepository`, `UserRepository`, `FileStorageService.getPreSignedDownloadUrl`.
- Produces:
  - `TrailerHarvester.harvest(Long)`
  - `MediaProbe.probe(Path)`, which returns `Media(double seconds, int width, int height)`
  - `TrailerService`:
    - `create(User, Long bookId)`
    - `list(User, Long bookId)`
    - `get(User, Long id)`
    - `downloadUrls(User, Long id)`
    - `cancel(User, Long id)`
    - `review(User admin, Long id, boolean approve)`
    - `nudge(String sessionId)`
  - REST endpoints:
    - `/api/v1/trailers/**`
    - `/api/v1/admin/trailer-agent/**`
    - `/api/v1/public/trailer-agent/webhook`
    - `/api/v1/public/trailer-agent/higgsfield/callback`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/doova/ktab/features/trailer/pipeline/TrailerHarvesterTest.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerHarvesterTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerStore store = mock(TrailerStore.class);
    final MediaProbe probe = mock(MediaProbe.class);
    final TrailerHarvester harvester = new TrailerHarvester(trailers, gateway, store, probe, new TrailerProperties());
    final BookTrailer t = new BookTrailer();

    static final String SRT = "1\n00:00:01,000 --> 00:00:03,500\nA new chapter begins.\n\n";
    static final String QC_OK = "{\"status\":\"ok\",\"duration_seconds\":30.0,"
            + "\"captions\":{\"language\":\"ar\"},\"frame_check\":{\"text_found\":false}}";

    @BeforeEach
    void setUp() {
        t.setId(7L);
        t.setBookId(3L);
        t.setSessionId("sesn_1");
        t.setStatus(TrailerStatus.HARVESTING);
        t.setOutcomeResult("satisfied");
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(gateway.outputs("sesn_1")).thenReturn(List.of(
                new TrailerAgentGateway.OutputFile("f1", "trailer.mp4", 9),
                new TrailerAgentGateway.OutputFile("f2", "trailer_clean.mp4", 9),
                new TrailerAgentGateway.OutputFile("f3", "captions_ar.srt", 9),
                new TrailerAgentGateway.OutputFile("f4", "qc_report.json", 9)));
        doAnswer(i -> {
            Path target = i.getArgument(1);
            String id = i.getArgument(0);
            Files.writeString(target, switch (id) { case "f3" -> SRT; case "f4" -> QC_OK; default -> "mp4"; });
            return null;
        }).when(gateway).download(anyString(), any());
    }

    @Test
    void satisfiedOkAndCorrectMediaIsReadyAndStoredInR2() {
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(30.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.READY);
        assertThat(t.getVideoKey()).isEqualTo("trailers/3/7/trailer.mp4");
        verify(store).upload(eq("trailers/3/7/trailer.mp4"), any(), eq("video/mp4"));
        verify(store).upload(eq("trailers/3/7/captions_ar.srt"), any(), eq("application/x-subrip"));
        assertThat(t.getCaptionsKey()).isEqualTo("trailers/3/7/captions_ar.srt");
        verify(gateway).archive("sesn_1");
    }

    @Test
    void wrongDurationIsNeedsReviewEvenIfTheGraderWasSatisfied() {
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(24.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
        assertThat(t.getError()).contains("24.0");
    }

    @Test
    void graderNotSatisfiedIsNeedsReview() {
        t.setOutcomeResult("max_iterations_reached");
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(30.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
    }

    @Test
    void nonArabicCaptionsAreNeedsReview() {
        // The agent must burn Arabic captions only (R4); a report saying otherwise is not READY.
        doAnswer(i -> {
            Path target = i.getArgument(1);
            String id = i.getArgument(0);
            Files.writeString(target, switch (id) {
                case "f3" -> SRT;
                case "f4" -> "{\"status\":\"ok\",\"captions\":{\"language\":\"en\"},\"frame_check\":{\"text_found\":false}}";
                default -> "mp4";
            });
            return null;
        }).when(gateway).download(anyString(), any());
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(30.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
        assertThat(t.getError()).contains("Arabic");
    }

    @Test
    void noTrailerFileFails() {
        when(gateway.outputs("sesn_1")).thenReturn(List.of(new TrailerAgentGateway.OutputFile("f4", "qc_report.json", 9)));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        verify(store, never()).upload(anyString(), any(), anyString());
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/web/TrailerPublicControllerTest.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.oauth.HiggsfieldOAuthService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerPublicControllerTest {

    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerService service = mock(TrailerService.class);
    final HiggsfieldOAuthService oauth = mock(HiggsfieldOAuthService.class);
    final TrailerPublicController controller = new TrailerPublicController(gateway, service, oauth);

    @Test
    void invalidSignatureIsRejected() {
        when(gateway.verifyWebhook(anyString(), anyMap())).thenReturn(Optional.empty());
        assertThat(controller.webhook("{}", Map.of()).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void idledSessionPullsTheCheckForward() {
        when(gateway.verifyWebhook(anyString(), anyMap())).thenReturn(Optional.of(
                new TrailerAgentGateway.WebhookNotice("whe_1", "session.status_idled", "sesn_1")));

        assertThat(controller.webhook("{}", Map.of()).getStatusCode().value()).isEqualTo(204);
        verify(service).nudge("sesn_1");
    }

    @Test
    void duplicateWebhookIsIgnored() {
        when(gateway.verifyWebhook(anyString(), anyMap())).thenReturn(Optional.of(
                new TrailerAgentGateway.WebhookNotice("whe_2", "session.status_idled", "sesn_1")));

        controller.webhook("{}", Map.of());
        controller.webhook("{}", Map.of());

        verify(service, times(1)).nudge("sesn_1");
    }
}
```

```java
// src/test/java/com/doova/ktab/features/trailer/web/TrailerServiceTest.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerServiceTest {

    final BookRepository books = mock(BookRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final TrailerProperties props = new TrailerProperties();
    final TrailerService service = new TrailerService(new TrailerAccess(books, users), trailers, gateway, storage, props);

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
    void authorOnlyTheirOwnBook() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(ResourceNotFoundException.class);

        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.DRAFT)));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(author, 3L).status()).isEqualTo(TrailerStatus.QUEUED);
    }

    @Test
    void librarianIsScopedToTheirOrganization() {
        LibraryOrganization org = new LibraryOrganization();
        org.setId(50L);
        User managed = user(UserRole.LIBRARIAN, 2);
        managed.setLibraryOrganization(org);
        when(users.findById(2L)).thenReturn(Optional.of(managed));
        when(books.findByIdAndLibraryOrganizationId(3L, 50L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(user(UserRole.LIBRARIAN, 2), 3L).status()).isEqualTo(TrailerStatus.QUEUED);
    }

    @Test
    void secondActiveTrailerIsAConflictAndTheMonthlyLimitApplies() {
        User author = user(UserRole.AUTHOR, 1);
        when(books.findByIdAndAuthor(3L, author)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));
        when(trailers.existsByBookIdAndStatusIn(eq(3L), anyCollection())).thenReturn(true);
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(TrailerConflictException.class);

        when(trailers.existsByBookIdAndStatusIn(eq(3L), anyCollection())).thenReturn(false);
        when(trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(eq(3L), any(), anyCollection())).thenReturn(3L);
        assertThatThrownBy(() -> service.create(author, 3L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelInterruptsTheSession() {
        User admin = user(UserRole.ADMIN, 9);
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.RUNNING);
        t.setSessionId("sesn_1");
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(books.findById(3L)).thenReturn(Optional.of(book(BookStatus.PUBLISHED)));

        service.cancel(admin, 7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.CANCELLED);
        verify(gateway).interrupt("sesn_1");
    }

    @Test
    void onlyAdminsApproveTrailersNeedingReview() {
        BookTrailer t = new BookTrailer();
        t.setId(7L);
        t.setBookId(3L);
        t.setStatus(TrailerStatus.NEEDS_REVIEW);
        when(trailers.findById(7L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.review(user(UserRole.AUTHOR, 1), 7L, true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        service.review(user(UserRole.ADMIN, 9), 7L, true);
        assertThat(t.getStatus()).isEqualTo(TrailerStatus.READY);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -o test -Dtest='TrailerHarvesterTest,TrailerServiceTest,TrailerPublicControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 3: Add the message keys**

In `ApiMessageKey.java`, insert this block right after the line `STORYBOOK_NOT_READY("storybook.not.ready"), STORYBOOK_INSUFFICIENT_CREDITS("storybook.insufficient.credits"),`:

```java
    // ===== BOOK TRAILER (agent) =====
    TRAILER_CREATED("trailer.created"),
    TRAILER_FETCHED("trailer.fetched"),
    TRAILER_NOT_FOUND("trailer.not.found"),
    TRAILER_ALREADY_RUNNING("trailer.already.running"),
    TRAILER_LIMIT_REACHED("trailer.limit.reached"),
    TRAILER_NOT_READY("trailer.not.ready"),
    TRAILER_CANCELLED("trailer.cancelled"),
    TRAILER_REVIEWED("trailer.reviewed"),
    TRAILER_OAUTH_INVALID("trailer.oauth.invalid"),
    TRAILER_HIGGSFIELD_CONNECTED("trailer.higgsfield.connected"),
```

Append to `messages.properties`:

```properties
trailer.created=بدأنا بإنشاء الإعلان التشويقي للكتاب.
trailer.fetched=تم جلب الإعلان التشويقي.
trailer.not.found=الإعلان التشويقي غير موجود.
trailer.already.running=يوجد إعلان تشويقي قيد الإنشاء لهذا الكتاب.
trailer.limit.reached=وصلت إلى الحد الأقصى من الإعلانات التشويقية لهذا الكتاب خلال 30 يومًا.
trailer.not.ready=الإعلان التشويقي غير جاهز بعد.
trailer.cancelled=تم إلغاء الإعلان التشويقي.
trailer.reviewed=تمت مراجعة الإعلان التشويقي.
trailer.oauth.invalid=رابط الربط مع Higgsfield غير صالح أو منتهي الصلاحية. أعد المحاولة.
trailer.higgsfield.connected=تم ربط حساب Higgsfield بنجاح.
```

- [ ] **Step 4: Implement the harvester, probe, access, service and controllers**

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/MediaProbe.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Ktab's own ffprobe — the agent's QC is not trusted alone (D7, Review Focus 4). */
@Component
public class MediaProbe {

    public record Media(double seconds, int width, int height) {
    }

    private final TrailerProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public MediaProbe(TrailerProperties properties) {
        this.properties = properties;
    }

    public Media probe(Path file) {
        try {
            Process p = new ProcessBuilder(properties.getFfprobePath(), "-v", "error", "-select_streams", "v:0",
                    "-show_entries", "stream=width,height:format=duration", "-of", "json", file.toString())
                    .redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IllegalStateException("ffprobe failed: " + new String(out, StandardCharsets.UTF_8));
            }
            JsonNode root = json.readTree(out);
            JsonNode stream = root.path("streams").path(0);
            return new Media(root.path("format").path("duration").asDouble(), stream.path("width").asInt(),
                    stream.path("height").asInt());
        } catch (IOException e) {
            throw new IllegalStateException("ffprobe unavailable (is ffmpeg installed?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/pipeline/TrailerHarvester.java
package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** HARVESTING -> READY / NEEDS_REVIEW / FAILED. Downloads outputs, verifies them independently, stores them in R2. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerHarvester {

    static final Map<String, String> TYPES = Map.of(
            "trailer.mp4", "video/mp4",
            "trailer_clean.mp4", "video/mp4",
            "captions_ar.srt", "application/x-subrip",
            "script.md", "text/markdown",
            "qc_report.json", "application/json");
    private static final Pattern CUE_TIME = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) --> (\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final TrailerStore store;
    private final MediaProbe probe;
    private final TrailerProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public void harvest(Long trailerId) {
        BookTrailer t = trailers.findById(trailerId).orElseThrow();
        if (t.getStatus() != TrailerStatus.HARVESTING) {
            return;
        }
        Map<String, TrailerAgentGateway.OutputFile> outputs = gateway.outputs(t.getSessionId()).stream()
                .filter(f -> TYPES.containsKey(f.filename()))
                .collect(Collectors.toMap(TrailerAgentGateway.OutputFile::filename, Function.identity(), (a, b) -> b));
        if (!outputs.containsKey("trailer.mp4")) {
            finish(t, TrailerStatus.FAILED, "The agent finished without producing trailer.mp4 ("
                    + t.getOutcomeResult() + (t.getOutcomeExplanation() == null ? "" : ": " + t.getOutcomeExplanation()) + ").");
            return;
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("trailer-harvest-" + trailerId + "-");
            for (TrailerAgentGateway.OutputFile f : outputs.values()) {
                Path local = dir.resolve(f.filename());
                gateway.download(f.id(), local);
                String key = TrailerStore.key(t.getBookId(), t.getId(), f.filename());
                store.upload(key, local, TYPES.get(f.filename()));
                switch (f.filename()) {
                    case "trailer.mp4" -> t.setVideoKey(key);
                    case "trailer_clean.mp4" -> t.setCleanVideoKey(key);
                    case "captions_ar.srt" -> t.setCaptionsKey(key);
                    case "qc_report.json" -> t.setQcReportJson(Files.readString(local));
                    default -> { }
                }
            }
            List<String> problems = verify(dir, t);
            if (problems.isEmpty()) {
                finish(t, TrailerStatus.READY, null);
            } else {
                finish(t, TrailerStatus.NEEDS_REVIEW, String.join(" ", problems));
            }
            gateway.archive(t.getSessionId());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (dir != null) {
                FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }

    private List<String> verify(Path dir, BookTrailer t) throws IOException {
        List<String> problems = new ArrayList<>();
        if (!"satisfied".equals(t.getOutcomeResult())) {
            problems.add("The grader result was " + t.getOutcomeResult() + ".");
        }
        MediaProbe.Media m = probe.probe(dir.resolve("trailer.mp4"));
        if (Math.abs(m.seconds() - 30.0) > 0.5) {
            problems.add(String.format(java.util.Locale.ROOT, "Duration is %.1fs, not 30s.", m.seconds()));
        }
        if (m.width() != 1920 || m.height() != 1080) {
            problems.add("Resolution is " + m.width() + "x" + m.height() + ", not 1920x1080.");
        }
        Path srt = dir.resolve("captions_ar.srt");
        if (!Files.exists(srt)) {
            problems.add("captions_ar.srt is missing.");
        } else {
            problems.addAll(checkSrt(Files.readString(srt)));
        }
        Path qc = dir.resolve("qc_report.json");
        JsonNode report = Files.exists(qc) ? json.readTree(qc.toFile()) : null;
        if (report == null || !"ok".equals(report.path("status").asText())) {
            problems.add("qc_report.json is missing or not ok.");
        } else if (report.path("frame_check").path("text_found").asBoolean(true)) {
            problems.add("The agent's frame check found text on screen.");
        } else if (!"ar".equals(report.path("captions").path("language").asText())) {
            problems.add("The burned-in captions are not Arabic (R4).");
        }
        return problems;
    }

    static List<String> checkSrt(String srt) {
        List<String> problems = new ArrayList<>();
        Matcher m = CUE_TIME.matcher(srt);
        int cues = 0;
        while (m.find()) {
            cues++;
            double end = Integer.parseInt(m.group(5)) * 3600 + Integer.parseInt(m.group(6)) * 60
                    + Integer.parseInt(m.group(7)) + Integer.parseInt(m.group(8)) / 1000.0;
            if (end > 30.5) {
                problems.add("A caption ends at " + end + "s, after the trailer.");
                break;
            }
        }
        if (cues == 0) {
            problems.add("captions_ar.srt has no cues.");
        }
        return problems;
    }

    private void finish(BookTrailer t, TrailerStatus status, String error) {
        t.setStatus(status);
        t.setError(error);
        t.setFinishedAt(Instant.now());
        trailers.save(t);
    }
}
```

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

import java.time.Instant;

public record TrailerView(Long id, Long bookId, TrailerStatus status, String outcomeResult, String error,
                          Integer higgsfieldGenerations, Instant startedAt, Instant finishedAt) {

    static TrailerView of(BookTrailer t) {
        return new TrailerView(t.getId(), t.getBookId(), t.getStatus(), t.getOutcomeResult(), t.getError(),
                t.getHiggsfieldGenerations(), t.getStartedAt(), t.getFinishedAt());
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

/** R1: author -> own books, librarian/admin-librarian -> their organization's books, admin -> any. */
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
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrailerService {

    private final TrailerAccess access;
    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final FileStorageService storage;
    private final TrailerProperties properties;

    @Transactional
    public TrailerView create(User user, Long bookId) {
        access.requireBook(user, bookId);
        if (trailers.existsByBookIdAndStatusIn(bookId, TrailerStatus.ACTIVE)) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
        if (!access.isAdmin(user) && trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(bookId,
                LocalDateTime.now().minusDays(30), EnumSet.of(TrailerStatus.FAILED, TrailerStatus.CANCELLED))
                >= properties.getPerBookPer30Days()) {
            throw new BadRequestException(ApiMessageKey.TRAILER_LIMIT_REACHED);
        }
        BookTrailer t = new BookTrailer();
        t.setBookId(bookId);
        t.setRequestedById(user.getId());
        t.setStatus(TrailerStatus.QUEUED);
        try {
            return TrailerView.of(trailers.save(t));
        } catch (DataIntegrityViolationException raced) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
    }

    @Transactional(readOnly = true)
    public List<TrailerView> list(User user, Long bookId) {
        access.requireBook(user, bookId);
        return trailers.findByBookIdOrderByIdDesc(bookId).stream().map(TrailerView::of).toList();
    }

    @Transactional(readOnly = true)
    public TrailerView get(User user, Long id) {
        return TrailerView.of(owned(user, id));
    }

    /** Presigned links: captioned MP4, clean MP4 and the Arabic SRT (whichever exist). NEEDS_REVIEW is downloadable by admins. */
    @Transactional(readOnly = true)
    public Map<String, String> downloadUrls(User user, Long id) {
        BookTrailer t = owned(user, id);
        boolean allowed = t.getStatus() == TrailerStatus.READY
                || (t.getStatus() == TrailerStatus.NEEDS_REVIEW && access.isAdmin(user));
        if (!allowed || t.getVideoKey() == null) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("video", presign(t.getVideoKey(), "trailer-" + t.getBookId() + ".mp4"));
        if (t.getCleanVideoKey() != null) {
            urls.put("videoClean", presign(t.getCleanVideoKey(), "trailer-" + t.getBookId() + "-clean.mp4"));
        }
        if (t.getCaptionsKey() != null) {
            urls.put("captions", presign(t.getCaptionsKey(), "trailer-" + t.getBookId() + "-ar.srt"));
        }
        return urls;
    }

    @Transactional
    public void cancel(User user, Long id) {
        BookTrailer t = owned(user, id);
        if (!t.getStatus().isActive()) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        if (t.getSessionId() != null) {
            gateway.interrupt(t.getSessionId());
        }
        t.setStatus(TrailerStatus.CANCELLED);
        t.setFinishedAt(Instant.now());
    }

    @Transactional
    public TrailerView review(User admin, Long id, boolean approve) {
        if (!access.isAdmin(admin)) {
            throw new AccessDeniedException("Only admins review trailers");
        }
        BookTrailer t = trailers.findById(id).orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
        if (t.getStatus() != TrailerStatus.NEEDS_REVIEW) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        t.setStatus(approve ? TrailerStatus.READY : TrailerStatus.FAILED);
        return TrailerView.of(t);
    }

    /** Webhook: check this session on the next tick instead of waiting for the regular interval (D6). */
    @Transactional
    public void nudge(String sessionId) {
        trailers.findBySessionId(sessionId).ifPresent(t -> t.setNextCheckAt(Instant.now()));
    }

    private String presign(String key, String filename) {
        return storage.getPreSignedDownloadUrl(key, Duration.ofMinutes(10), filename);
    }

    private BookTrailer owned(User user, Long id) {
        BookTrailer t = trailers.findById(id).orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
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
    private final MessageSource messages;

    @PostMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<TrailerView>> create(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.create(user, bookId), ApiMessageKey.TRAILER_CREATED.getMessage(messages), HttpStatus.ACCEPTED);
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<List<TrailerView>>> list(@CurrentUser User user, @PathVariable Long bookId) {
        return ResponseUtils.success(service.list(user, bookId), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TrailerView>> get(@CurrentUser User user, @PathVariable Long id) {
        return ResponseUtils.success(service.get(user, id), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> download(@CurrentUser User user, @PathVariable Long id) {
        return ResponseUtils.success(service.downloadUrls(user, id), ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@CurrentUser User user, @PathVariable Long id) {
        service.cancel(user, id);
        return ResponseUtils.success(null, ApiMessageKey.TRAILER_CANCELLED.getMessage(messages), HttpStatus.OK);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerAdminController.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.trailer.oauth.HiggsfieldOAuthService;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/trailer-agent", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
public class TrailerAdminController {

    private final HiggsfieldOAuthService oauth;
    private final TrailerService service;
    private final MessageSource messages;

    /** Returns the Higgsfield authorize URL; the admin opens it and approves Ktab's access. */
    @PostMapping("/higgsfield/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> connect(@CurrentUser User admin) {
        return ResponseUtils.success(Map.of("authorizeUrl", oauth.begin(admin.getId())),
                ApiMessageKey.TRAILER_FETCHED.getMessage(messages), HttpStatus.OK);
    }

    @PostMapping("/trailers/{id}/review")
    public ResponseEntity<ApiResponse<TrailerView>> review(@CurrentUser User admin, @PathVariable Long id,
                                                           @RequestParam boolean approve) {
        return ResponseUtils.success(service.review(admin, id, approve),
                ApiMessageKey.TRAILER_REVIEWED.getMessage(messages), HttpStatus.OK);
    }
}
```

```java
// src/main/java/com/doova/ktab/features/trailer/web/TrailerPublicController.java
package com.doova.ktab.features.trailer.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.oauth.HiggsfieldOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Called by Anthropic (webhook) and by the admin's browser (OAuth callback); lives under the public /api/v1/public/**. */
@ApiVersion(1)
@RestController
@RequestMapping(path = "/public/trailer-agent")
@ConditionalOnProperty(prefix = "ktab.trailer", name = "enabled", havingValue = "true")
@Slf4j
public class TrailerPublicController {

    private final TrailerAgentGateway gateway;
    private final TrailerService service;
    private final HiggsfieldOAuthService oauth;
    /** Dedupe on the per-event id (retries reuse it). Bounded; the reconciler covers anything forgotten. */
    private final Set<String> seen = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 10_000;
        }
    });

    public TrailerPublicController(TrailerAgentGateway gateway, TrailerService service, HiggsfieldOAuthService oauth) {
        this.gateway = gateway;
        this.service = service;
        this.oauth = oauth;
    }

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> webhook(@RequestBody String rawBody, @RequestHeader Map<String, String> headers) {
        var notice = gateway.verifyWebhook(rawBody, headers); // raw body: re-serialized JSON breaks the HMAC
        if (notice.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        synchronized (seen) {
            if (!seen.add(notice.get().eventId())) {
                return ResponseEntity.noContent().build();
            }
        }
        switch (notice.get().type()) {
            case "session.status_idled", "session.status_terminated", "session.outcome_evaluation_ended" ->
                    service.nudge(notice.get().resourceId());
            case "vault_credential.refresh_failed" ->
                    log.error("Higgsfield vault credential {} failed to refresh — an admin must reconnect Higgsfield",
                            notice.get().resourceId());
            default -> { }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/higgsfield/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        oauth.complete(code, state);
        return ResponseEntity.ok("<html><body dir=\"rtl\"><h3>تم ربط حساب Higgsfield بنجاح. يمكنك إغلاق هذه النافذة.</h3></body></html>");
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o test -Dtest='Trailer*Test,SessionEventsTest,PkceTest,HiggsfieldOAuthServiceTest,ControlPlaneFilesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: every trailer unit test passes, including Task 4's and Task 5's. Then run `mvn -o test -Dtest=StorybookMessagesTest` to confirm that no existing message-key test breaks.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/doova/ktab/features/trailer src/main/java/com/doova/ktab/enums/message/ApiMessageKey.java src/main/resources/messages.properties src/test/java/com/doova/ktab/features/trailer
git commit -m "feat(trailer): harvest and verify agent outputs into R2; add trailer, admin and public endpoints"
```

---

### Task 7: Configuration, Docker, end-to-end run, network lockdown

**Files:**
- Modify: `src/main/resources/application.properties` (append the block below)
- Modify: `Dockerfile` (add `ffmpeg` to the runtime `apt-get install` line)
- Modify: `docs/superpowers/plans/2026-09-28-book-trailer.md` (superseded banner)
- Create: `docs/trailer/launch-checklist.md`

- [ ] **Step 1: Append the configuration**

```properties
# ===== Book trailers (Claude Managed Agents trailer agent) =====
ktab.trailer.enabled=${KTAB_TRAILER_ENABLED:false}
ktab.trailer.anthropic-api-key=${ANTHROPIC_API_KEY:}
ktab.trailer.webhook-signing-key=${ANTHROPIC_WEBHOOK_SIGNING_KEY:}
ktab.trailer.agent-id=${KTAB_TRAILER_AGENT_ID:}
ktab.trailer.agent-version=${KTAB_TRAILER_AGENT_VERSION:0}
ktab.trailer.environment-id=${KTAB_TRAILER_ENVIRONMENT_ID:}
ktab.trailer.vault-id=${KTAB_TRAILER_VAULT_ID:}
ktab.trailer.workspace=${KTAB_TRAILER_WORKSPACE:default}
ktab.trailer.voice-id=${KTAB_TRAILER_VOICE_ID:}
ktab.trailer.budget-cents=2000
ktab.trailer.max-higgsfield-generations=8
ktab.trailer.max-higgsfield-calls=24
ktab.trailer.higgsfield-job-pattern=${KTAB_TRAILER_HIGGSFIELD_JOB_PATTERN:(?i)"?(job|request|generation)_?id"?}
ktab.trailer.higgsfield-generate-args=${KTAB_TRAILER_HIGGSFIELD_GENERATE_ARGS:}
ktab.trailer.higgsfield-max-in-flight=${KTAB_TRAILER_HIGGSFIELD_MAX_IN_FLIGHT:2}
ktab.trailer.higgsfield-generation-marker=generate
ktab.trailer.higgsfield-callback-url=${KTAB_PUBLIC_BASE_URL:http://localhost:8080}/api/v1/public/trailer-agent/higgsfield/callback
ktab.trailer.per-book-per30-days=3
ktab.trailer.ffprobe-path=ffprobe
```

- [ ] **Step 2: Add ffmpeg to the runtime image (ffprobe is needed by `MediaProbe`)**

In `Dockerfile`, replace:

```dockerfile
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl && \
```

with:

```dockerfile
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl ffmpeg && \
```

- [ ] **Step 3: Mark the old plan as superseded**

Insert as the first line of `docs/superpowers/plans/2026-09-28-book-trailer.md`:

```markdown
> **SUPERSEDED** by `2026-09-28-trailer-agent.md` (agent-orchestrated on Claude Managed Agents). Not implemented; kept for its analysis.
```

- [ ] **Step 4: Verify that the flag-off app still boots and the whole suite passes**

Run: `mvn -o clean test` (the flag is off by default).
Expected: BUILD SUCCESS. No `TrailerWorker`, `TrailerController` or `TrailerPublicController` bean is created.

Then run: `STORYBOOK_IT_DB_URL=… STORYBOOK_IT_DB_PASSWORD=… mvn -o verify -Dit.test='com.doova.ktab.features.trailer.**.*IT' -Dfailsafe.failIfNoSpecifiedTests=false -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
Expected: every trailer IT passes.

- [ ] **Step 5: Run one real trailer end to end (real spend)**

1. Set `KTAB_TRAILER_ENABLED=true` and every ID and key from Task 1. Put `ffmpeg` on PATH (Windows: `winget install Gyan.FFmpeg`).
2. As ADMIN, call `POST /api/v1/admin/trailer-agent/higgsfield/connect`, open `authorizeUrl` and approve. Expected: the Arabic "connected" page, and the Console shows a Higgsfield credential in the vault.
3. As the author of a book (draft, under review, or published), call `POST /api/v1/trailers/books/{bookId}`. Expected: 202 with `QUEUED`. Within 15 s it becomes `RUNNING`. Open the session in the Console; the trace URL is in the logs.
4. Wait for `READY` or `NEEDS_REVIEW` (typically 10–30 min). Call `GET /api/v1/trailers/{id}/download` and check that:
   - the video is 30 s and 1920×1080;
   - the Arabic narration is clear, the burned-in Arabic captions are joined, right-to-left and in sync, R2 holds `captions_ar.srt`, and no English captions appear anywhere;
   - **no Arabic text appears in the frames**;
   - no real person is depicted;
   - the music sits under the voice.
5. Record three things in `docs/trailer/launch-checklist.md`:
   - the session list cost, from the Console;
   - the Higgsfield credits used;
   - the ElevenLabs characters used.

- [ ] **Step 6: Lock down networking (D9)**

1. From the spike and the end-to-end session traces, list every host the sandbox reached. Expect `api.elevenlabs.io` plus Higgsfield's CDN host(s).
2. Update the environment:

   ```bash
   curl -sS -X POST "https://api.anthropic.com/v1/environments/$KTAB_TRAILER_ENVIRONMENT_ID" -H "content-type: application/json" \
     -H "x-api-key: $ANTHROPIC_API_KEY" -H "anthropic-version: 2023-06-01" -H "anthropic-beta: managed-agents-2026-04-01" \
     -d '{"config":{"type":"cloud","packages":{"type":"packages","apt":["ffmpeg","poppler-utils","jq","fonts-dejavu-core","fonts-noto-core"]},
          "networking":{"type":"limited","allow_package_managers":true,"allow_mcp_servers":true,
                        "allowed_hosts":["api.elevenlabs.io","<higgsfield-cdn-host>"]}}}'
   ```

   This only affects **new** sessions.
3. Mirror the change in `ops/trailer-agent/environment.json`.
4. Run the Step 5 end-to-end run once more.

- [ ] **Step 7: Write the launch checklist**

```markdown
<!-- docs/trailer/launch-checklist.md -->
# Trailer agent launch checklist

- [ ] eleven_v3 lists Arabic (`GET /v1/models`); Arabic voice chosen → KTAB_TRAILER_VOICE_ID.
- [ ] `ops/trailer-agent/setup.sh` run; IDs in the deploy env; nothing secret committed (ControlPlaneFilesTest green).
- [ ] Higgsfield connected from the admin endpoint; vault shows the credential; `vault_credential.refresh_failed` is subscribed.
- [ ] Webhook endpoint registered in Console with the four event types; ANTHROPIC_WEBHOOK_SIGNING_KEY set.
- [ ] Higgsfield generation tool names confirmed; `ktab.trailer.higgsfield-generation-marker` matches them.
- [ ] One real trailer per book type (Arabic political with real people named, Arabic children's, English novel):
      30 s, 1920×1080, no text in the visuals, Arabic captions correctly shaped and in sync, no real-person likeness.
- [ ] Higgsfield `generate_video` arguments, job-reply pattern and concurrency limit recorded (Task 1 spike); a real run shows
      no preset-suggestion replies and no concurrency rejections; `higgsfield_calls - higgsfield_generations` stays near 0.
- [ ] Measured cost per trailer (Claude list cost + Higgsfield credits + ElevenLabs characters) recorded; budget-cents and
      max-higgsfield-generations (jobs) and max-higgsfield-calls tuned from it; Higgsfield and ElevenLabs account spend alerts set.
- [ ] Networking switched to `limited` with the observed hosts (D9).
- [ ] ElevenLabs Music commercial-use terms confirmed for the plan tier; authors' ToS covers AI-generated promotional media.
- [ ] KTAB_TRAILER_ENABLED=true on one instance first; watch logs for "trailer … failed" and the NEEDS_REVIEW queue.
```

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/application.properties Dockerfile docs/trailer/launch-checklist.md docs/superpowers/plans/2026-09-28-book-trailer.md ops/trailer-agent/environment.json
git commit -m "feat(trailer): configure trailer agent, add ffprobe to image, launch checklist"
```

---

## Out of scope (follow-ups)

- Showing the latest READY trailer on the book response and in the reader app.
- Letting the author steer a trailer by message (the session is reusable after its outcome) instead of regenerating it.
- A per-tenant vault (a library organization's own Higgsfield account). Today one Ktab-owned vault serves every trailer.
- Billing trailers through a credits port.

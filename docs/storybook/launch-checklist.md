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

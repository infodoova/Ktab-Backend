# Phase 0 results

Date run: 2026-09-24 · Ktab commit: HEAD

## Character consistency (decision D11 thresholds in brackets)

| Model | First-try QA pass [≥ 85%] | Pass after ≤ 3 retries [≥ 97%] | Human consistency avg [≥ 4] | Style stability avg | Mean cost per 15-page book [≤ $3.50] |
|---|---|---|---|---|---|
| gemini-3.1-flash-image | | | | | |
| gemini-3-pro-image | | | | | |

Most common QA failures (from `results.csv` → `problems`):

## Arabic quality

| Variety | Stories | Grammar avg [≥ 4] | Gender errors [0 for MSA] | Tashkeel avg [≥ 4] | Dialect fidelity avg [≥ 4] | Age fit avg [≥ 4] |
|---|---|---|---|---|---|---|
| MSA | 8 | | | | — | |
| Lebanese | 4 | | | — | | |
| Egyptian | 4 | | | — | | |
| Gulf | 4 | | | — | | |

Critic accuracy: pages the critic failed that the editor graded ≥ 4 (false alarms): __; pages the critic passed that the editor graded ≤ 2 (misses): __.

## Decisions

- Image model: go / no-go with Nano Banana 2 as primary. If no-go: build FLUX.2 Pro and GPT Image 2.5 adapters (decision D10) and re-run.
- Word limits per age band (D2): keep / change to __.
- `PARTIAL` tashkeel rule (D3): keep shadda + tanween / change to __.
- Prompt changes made during the spike (link commits):

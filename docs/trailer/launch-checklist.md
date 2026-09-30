# Trailer agent launch checklist

- [x] eleven_v3 lists Arabic (`GET /v1/models`); Arabic voices chosen → KTAB_TRAILER_VOICES (the agent picks one per book; there is no single voice id).
- [x] `ops/trailer-agent/setup.sh` run; IDs in the deploy env; nothing secret committed (ControlPlaneFilesTest green).
- [x] Higgsfield connected from the admin endpoint; vault shows the credential; `vault_credential.refresh_failed` is subscribed.
- [x] Webhook endpoint registered in Console with the four event types; ANTHROPIC_WEBHOOK_SIGNING_KEY set.
- [ ] Higgsfield generation tool names confirmed; `ktab.trailer.higgsfield-generation-marker` matches them.
- [ ] One real trailer per book type (Arabic political with real people named, Arabic children's, English novel):
      30 s, 1920×1080, no on-screen Arabic/other text, captions in sync, no real-person likeness.
- [ ] Measured cost per trailer (Claude list cost + Higgsfield credits + ElevenLabs characters) recorded; budget-cents and
      max-higgsfield-generations tuned from it; Higgsfield and ElevenLabs account spend alerts set.
- [ ] Networking switched to `limited` with the observed hosts (D9).
- [ ] ElevenLabs Music commercial-use terms confirmed for the plan tier; authors' ToS covers AI-generated promotional media.
- [ ] KTAB_TRAILER_ENABLED=true on one instance first; watch logs for "trailer … failed" and the NEEDS_REVIEW queue.

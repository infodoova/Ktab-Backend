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

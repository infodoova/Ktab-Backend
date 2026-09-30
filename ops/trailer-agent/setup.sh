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

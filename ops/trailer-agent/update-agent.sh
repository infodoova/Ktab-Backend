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

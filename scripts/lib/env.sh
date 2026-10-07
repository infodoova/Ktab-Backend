#!/usr/bin/env bash
###############################################################################
# Ktab Backend - helpers for reading an environment file (sourced, not run)
#
#   source "$(dirname "${BASH_SOURCE[0]}")/lib/env.sh"
#
# Why this exists: the old scripts did  export $(grep -v '^#' .env.production | xargs ...)
# which breaks on blank lines, comments that are indented, quoted values and values
# with spaces or special characters. These helpers read one key at a time instead and
# never execute anything from the file.
###############################################################################

# The marker merge_env.sh and the generated .env.production use for "keep what the server has".
ENV_PLACEHOLDER="__KEEP_EXISTING_VALUE_FROM_THE_SERVER__"

# env_get FILE KEY [DEFAULT]
# Prints the value of KEY (the last assignment wins). Carriage returns are removed and one pair of
# surrounding single or double quotes is stripped. Prints DEFAULT (or nothing) when the key is absent or empty.
env_get() {
  local file="$1" key="$2" default="${3:-}" line value
  line="$(grep -E "^[[:space:]]*${key}=" "$file" 2>/dev/null | tail -n 1 || true)"
  if [ -z "$line" ]; then printf '%s' "$default"; return 0; fi
  value="${line#*=}"
  value="${value%$'\r'}"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  if [ -z "$value" ]; then printf '%s' "$default"; else printf '%s' "$value"; fi
}

# env_is_set FILE KEY  -> success when KEY has a real value (not empty, not the placeholder)
env_is_set() {
  local value
  value="$(env_get "$1" "$2")"
  [ -n "$value" ] && [ "$value" != "$ENV_PLACEHOLDER" ]
}

# env_has_carriage_returns FILE  -> success when the file has Windows line endings
env_has_carriage_returns() {
  # tr, not grep: some grep builds (Git Bash on Windows) hide the carriage returns they read
  [ -n "$(tr -cd '\r' < "$1" | head -c 1)" ]
}

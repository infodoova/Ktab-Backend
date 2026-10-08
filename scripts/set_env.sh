#!/usr/bin/env bash
###############################################################################
# Ktab Backend - set ONE variable in .env.production, optionally restart the app
#
# Usage:   bash scripts/set_env.sh KEY [source] [--redeploy]
#
# Where the value comes from (pick one; with none, it is asked for with hidden input):
#   --value 'text'        the value on the command line  (ends up in your shell history - avoid for secrets)
#   --stdin               read it from standard input    (echo "$X" | bash scripts/set_env.sh KEY --stdin)
#   --from-file PATH      the file's content, trailing newlines removed
#   --base64-file PATH    the file, base64-encoded on one line (e.g. a Google service-account .json)
#
#   --redeploy            afterwards recreate ktab-app so it reads the new value (no rebuild, no git pull)
#
# Examples:
#   bash scripts/set_env.sh GCP_CREDENTIALS_BASE64 --base64-file ~/service-account.json --redeploy
#   bash scripts/set_env.sh GCP_CREDENTIALS_BASE64 --redeploy        # paste the base64 text when asked
#
# Safety: the old file is copied to .env.production.bak (mode 600) first; only the one line is changed
# (or appended when the key is missing); the value is never printed.
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${ENV_FILE:-.env.production}"
KEY="${1:-}"
if [ -z "$KEY" ] || [ "$KEY" = "-h" ] || [ "$KEY" = "--help" ]; then
  sed -n '2,23p' "$0"
  exit 0
fi
shift
if ! [[ "$KEY" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "[-] '$KEY' is not a valid variable name." >&2
  exit 1
fi

VALUE=""
HAVE_VALUE=0
REDEPLOY=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --value)       [ "$#" -ge 2 ] || { echo "[-] --value needs an argument" >&2; exit 1; }
                   VALUE="$2"; HAVE_VALUE=1; shift 2 ;;
    --stdin)       VALUE="$(cat)"; HAVE_VALUE=1; shift ;;
    --from-file)   [ "$#" -ge 2 ] && [ -f "$2" ] || { echo "[-] --from-file needs an existing file" >&2; exit 1; }
                   VALUE="$(cat "$2")"; HAVE_VALUE=1; shift 2 ;;
    --base64-file) [ "$#" -ge 2 ] && [ -f "$2" ] || { echo "[-] --base64-file needs an existing file" >&2; exit 1; }
                   VALUE="$(base64 < "$2" | tr -d '\n\r')"; HAVE_VALUE=1; shift 2 ;;
    --redeploy)    REDEPLOY=1; shift ;;
    *) echo "[-] Unknown option: $1 (use --help)" >&2; exit 1 ;;
  esac
done

if [ "$HAVE_VALUE" -eq 0 ]; then
  read -r -s -p "Value for $KEY (hidden): " VALUE
  echo
fi
VALUE="${VALUE//$'\r'/}"
VALUE="${VALUE//$'\n'/}"
if [ -z "$VALUE" ]; then
  echo "[-] The value is empty; nothing was changed." >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "[-] $ENV_FILE not found in $ROOT_DIR." >&2
  exit 1
fi

cp -p "$ENV_FILE" "${ENV_FILE}.bak"
chmod 600 "${ENV_FILE}.bak" 2>/dev/null || true

# ENVIRON (not awk -v) so backslashes and = signs in the value stay exactly as given.
TMP="$(mktemp "${ENV_FILE}.XXXXXX")"
trap 'rm -f "$TMP"' EXIT
NEW_VALUE="$VALUE" awk -v key="$KEY" '
  BEGIN { v = ENVIRON["NEW_VALUE"]; done = 0 }
  {
    line = $0; sub(/\r$/, "", line)
    if (index(line, key "=") == 1) { if (!done) { print key "=" v; done = 1 } ; next }
    print line
  }
  END { if (!done) print key "=" v }
' "$ENV_FILE" > "$TMP"
chmod --reference="$ENV_FILE" "$TMP" 2>/dev/null || chmod 600 "$TMP"
mv "$TMP" "$ENV_FILE"
trap - EXIT

echo "[+] $KEY updated in $ENV_FILE (${#VALUE} characters). Previous file: ${ENV_FILE}.bak"

if [ "$REDEPLOY" -eq 1 ]; then
  if ! docker ps --format '{{.Names}}' | grep -q '^ktab-db$'; then
    echo "[-] Container ktab-db is not running; start the stack with: bash scripts/deploy.sh" >&2
    exit 1
  fi
  echo "[*] Recreating ktab-app so it reads the new value..."
  docker compose --env-file "$ENV_FILE" up -d --force-recreate --no-deps ktab-app
  echo "[*] Waiting for ktab-app to become healthy (up to about 5 minutes)..."
  for _ in $(seq 1 50); do
    if [ "$(docker inspect --format='{{.State.Health.Status}}' ktab-app 2>/dev/null)" = "healthy" ]; then
      echo "[+] ktab-app is healthy with the new $KEY."
      exit 0
    fi
    sleep 6
  done
  echo "[!] ktab-app did not become healthy in time. Check:  docker logs --tail 100 ktab-app" >&2
  exit 1
else
  echo "[i] Not applied yet. Restart the app with:  docker compose --env-file $ENV_FILE up -d --force-recreate --no-deps ktab-app"
fi

#!/usr/bin/env bash
###############################################################################
# Ktab Backend - Production Deployment / Update Script
# Run from the repository root on the VPS:  bash scripts/deploy.sh [options]
#
# Options:
#   --no-backup   do not take a database backup first (not recommended: new releases can add migrations)
#   --no-pull     do not run git pull
#
# What it does:
#   1. checks .env.production (exists, Linux line endings, database password and JWT secret set)
#   2. git pull
#   3. backs up the database (when it is running)
#   4. builds the new image WHILE the old version keeps serving, then replaces only what changed
#   5. waits until the app is healthy (Flyway runs any new migrations on start) and shows the Flyway version
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
# shellcheck source=lib/env.sh
source "${ROOT_DIR}/scripts/lib/env.sh"

DO_BACKUP=1
DO_PULL=1
for arg in "$@"; do
  case "$arg" in
    --no-backup) DO_BACKUP=0 ;;
    --no-pull)   DO_PULL=0 ;;
    -h|--help)   sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "[-] Unknown option: $arg (use --help)" >&2; exit 1 ;;
  esac
done

ENV_FILE=".env.production"
COMPOSE=(docker compose --env-file "$ENV_FILE")

echo "============================================================"
echo "  🚀 Deploying Ktab Backend to Production"
echo "============================================================"

# 1. Check .env.production
if [ ! -f "$ENV_FILE" ]; then
  echo "[-] ERROR: $ENV_FILE is missing in $ROOT_DIR!" >&2
  echo "    Create it before deploying. On a server that already runs Ktab, merge the new variables with:" >&2
  echo "      bash scripts/merge_env.sh" >&2
  exit 1
fi

if env_has_carriage_returns "$ENV_FILE"; then
  echo "[-] ERROR: $ENV_FILE has Windows line endings (CRLF), which end up inside the values." >&2
  echo "    Fix it with:  sed -i 's/\\r\$//' $ENV_FILE" >&2
  exit 1
fi

for required in DB_PASSWORD JWT_SECRET; do
  if ! env_is_set "$ENV_FILE" "$required"; then
    echo "[-] ERROR: $required is missing or still the placeholder in $ENV_FILE." >&2
    exit 1
  fi
done

if grep -q "$ENV_PLACEHOLDER" "$ENV_FILE"; then
  echo "[-] ERROR: $ENV_FILE still contains $ENV_PLACEHOLDER. Replace it with the real value." >&2
  exit 1
fi

# Warnings that do not stop the deploy
if ! grep -qE '^[[:space:]]*CORS_ALLOWED_ORIGINS=' "$ENV_FILE"; then
  echo "[!] CORS_ALLOWED_ORIGINS is not set: only localhost origins are allowed, so the website will be blocked."
fi
if [ "$(env_get "$ENV_FILE" QSTASH_VERIFY_SIGNATURE true)" != "true" ]; then
  echo "[!] QSTASH_VERIFY_SIGNATURE is not true: calls to the OCR callback are not checked for a QStash signature."
fi
for feature in KTAB_STORYBOOK_ENABLED KTAB_TRAILER_ENABLED KTAB_OCR_ENABLED KTAB_STUDIO_ENABLED; do
  echo "    $feature = $(env_get "$ENV_FILE" "$feature" "false (default)")"
done

# 2. Docker present?
if ! command -v docker >/dev/null 2>&1; then
  echo "[-] ERROR: Docker is not installed. Please run: sudo bash scripts/vps_setup.sh first." >&2
  exit 1
fi

# 3. Pull latest git changes if in a git repository and the branch is tracked
if [ "$DO_PULL" = "1" ] && [ -d ".git" ]; then
  echo "[*] Checking for git updates..."
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD || echo "")
  if [ -n "$CURRENT_BRANCH" ] && [ "$CURRENT_BRANCH" != "HEAD" ]; then
    echo "    Pulling latest commits from git ($CURRENT_BRANCH)..."
    if ! git pull --rebase; then
      # Building the old code without saying so is worse than stopping.
      echo "[-] ERROR: git pull failed, so the new version was NOT fetched. Nothing was deployed." >&2
      if ! git diff --quiet -- nginx/conf.d/ktab.conf 2>/dev/null; then
        echo "    nginx/conf.d/ktab.conf was rewritten on this server by scripts/setup_ssl.sh and blocks the pull. Put the repository" >&2
        echo "    version back, pull, and write the HTTPS config again (the certificate is kept):" >&2
        echo "      git checkout -- nginx/conf.d/ktab.conf && git pull --rebase && sudo bash scripts/setup_ssl.sh <DOMAIN> <EMAIL>" >&2
      else
        echo "    Look at 'git status': local changes or a conflict are in the way." >&2
      fi
      echo "    To deploy the files exactly as they are on this server, run with --no-pull." >&2
      exit 1
    fi
  fi
fi

# 4. Back up the database and show where it is now
DB_USER="$(env_get "$ENV_FILE" DB_USER ktab_user)"
DB_NAME="$(env_get "$ENV_FILE" DB_NAME ktab)"
# The Flyway version the database is at (empty when it cannot be read)
flyway_version() {
  docker exec ktab-db psql -U "$DB_USER" -d "$DB_NAME" -t -A \
    -c "select version from flyway_schema_history where success order by installed_rank desc limit 1" 2>/dev/null | head -1 || true
}

if docker ps --format '{{.Names}}' | grep -q '^ktab-db$'; then
  echo "[*] Database is at Flyway version: $(flyway_version)"
  if [ "$DO_BACKUP" = "1" ]; then
    echo "[*] Backing up the database before deploying..."
    if ! bash "$ROOT_DIR/scripts/backup_db.sh"; then
      echo "[-] ERROR: the backup failed, so the deploy was stopped. Fix it, or use --no-backup." >&2
      exit 1
    fi
  else
    echo "[!] Skipping the database backup (--no-backup)."
  fi
else
  echo "[*] ktab-db is not running yet (first deploy): nothing to back up."
fi

# 5. Build the new images first; the running versions keep serving until the build is done.
# ktab-frontend is only built if it's defined in docker-compose.yml (older checkouts won't have it).
echo "[*] Building the application image(s) (the old versions keep running meanwhile)..."
BUILD_SERVICES=(ktab-app)
if "${COMPOSE[@]}" config --services | grep -qx ktab-frontend; then
  BUILD_SERVICES+=(ktab-frontend)
fi
"${COMPOSE[@]}" build "${BUILD_SERVICES[@]}"

echo "[*] Starting containers (only what changed is recreated)..."
"${COMPOSE[@]}" up -d --remove-orphans

# 6. Wait for the application to become healthy. Flyway applies new migrations during this start.
echo "[*] Waiting for ktab-app to become healthy (new migrations can make the first start slower)..."
MAX_RETRIES="${DEPLOY_WAIT_RETRIES:-50}"      # polls of 6 s each by default: about 5 minutes
WAIT_SLEEP="${DEPLOY_WAIT_SLEEP:-6}"
COUNTER=0
HEALTHY=0
until [ "$COUNTER" -ge "$MAX_RETRIES" ]; do
  STATUS=$(docker inspect --format='{{json .State.Health.Status}}' ktab-app 2>/dev/null || echo "\"starting\"")
  if [ "$STATUS" = "\"healthy\"" ]; then
    HEALTHY=1
    echo "    ✅ ktab-app is HEALTHY!"
    break
  fi
  echo "    Waiting for ktab-app to report healthy... ($((COUNTER + 1))/$MAX_RETRIES)"
  sleep "$WAIT_SLEEP"
  COUNTER=$((COUNTER + 1))
done

if [ "$HEALTHY" != "1" ]; then
  echo "[-] ERROR: ktab-app did not become healthy within $((MAX_RETRIES * WAIT_SLEEP)) seconds. Last log lines:" >&2
  docker logs --tail 60 ktab-app >&2 || true
  echo "    Common causes: a Flyway checksum error, a missing JWT_SECRET, or a wrong database password." >&2
  echo "    To go back: see 'Rolling back' in docs/contabo_deployment_guide.md" >&2
  exit 1
fi

# 7. Cleanup and status
echo "[*] Cleaning up dangling Docker images..."
docker image prune -f >/dev/null 2>&1 || true

echo "============================================================"
if docker ps --format '{{.Names}}' | grep -q '^ktab-db$'; then
  echo "  Database is now at Flyway version: $(flyway_version)   (expected: 35 or higher)"
fi
"${COMPOSE[@]}" ps
echo "============================================================"
echo "  ✅ Ktab Backend is up and running!"
echo "  Test it:  curl http://$(curl -s https://api.ipify.org || echo '<YOUR_VPS_IP>')/actuator/health"
echo "  Then follow Step 6 of docs/contabo_deployment_guide.md."
echo "============================================================"

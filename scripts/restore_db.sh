#!/usr/bin/env bash
###############################################################################
# Ktab Backend - PostgreSQL Database Restore Script
# Supports both plain SQL dumps and custom-format dumps (pg_dump -Fc / pgAdmin)
# Usage:   bash scripts/restore_db.sh <dump_file>
# Example: bash scripts/restore_db.sh backups/ktab_backup_20261007_030000.sql.gz
#
# What it does, in order:
#   1. takes a safety backup of the CURRENT database (skip with RESTORE_SKIP_SNAPSHOT=1)
#   2. stops ktab-app so nothing writes while the data is replaced
#   3. restores the dump
#   4. starts ktab-app again
#
# The dump carries the Flyway history of the moment it was taken. Restoring an older dump and then starting the
# newer code re-applies the migrations that the dump does not have yet.
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
# shellcheck source=lib/env.sh
source "${ROOT_DIR}/scripts/lib/env.sh"

if [ "$#" -lt 1 ]; then
  echo "Usage: bash scripts/restore_db.sh <backup_file.sql|backup_file.sql.gz>"
  echo "Example: bash scripts/restore_db.sh backups/ktab_backup.sql"
  exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "[-] ERROR: File '$BACKUP_FILE' not found!" >&2
  exit 1
fi

# Load the database name and user (read, not executed)
DB_USER="ktab_user"
DB_NAME="ktab"
if [ -f "${ROOT_DIR}/.env.production" ]; then
  DB_USER="$(env_get "${ROOT_DIR}/.env.production" DB_USER ktab_user)"
  DB_NAME="$(env_get "${ROOT_DIR}/.env.production" DB_NAME ktab)"
fi

echo "============================================================"
echo "  🔄 Restoring Database to Container: ktab-db"
echo "  Target Database: $DB_NAME"
echo "  Target User:     $DB_USER"
echo "  Backup File:     $BACKUP_FILE"
echo "============================================================"

# Check if ktab-db is running
if ! docker ps --format '{{.Names}}' | grep -q "^ktab-db$"; then
  echo "[-] ERROR: Container 'ktab-db' is not running." >&2
  echo "    Please run: docker compose --env-file .env.production up -d ktab-db" >&2
  exit 1
fi

# Confirm with user if interactive
if [ -t 0 ]; then
  read -r -p "⚠️  WARNING: This will overwrite tables in '$DB_NAME'. Continue? (y/N): " CONFIRM
  if [[ "$CONFIRM" != [yY] && "$CONFIRM" != [yY][eE][sS] ]]; then
    echo "[*] Restore cancelled."
    exit 0
  fi
fi

# 1. Safety backup of what is there now
if [ "${RESTORE_SKIP_SNAPSHOT:-0}" != "1" ]; then
  echo "[*] Taking a safety backup of the current database first..."
  if ! bash "${ROOT_DIR}/scripts/backup_db.sh"; then
    echo "[-] ERROR: the safety backup failed, so nothing was restored." >&2
    echo "    Fix that, or set RESTORE_SKIP_SNAPSHOT=1 if you accept losing the current data." >&2
    exit 1
  fi
fi

# 2. Stop the app so nothing writes (or holds locks) during the restore
APP_WAS_RUNNING=0
if docker ps --format '{{.Names}}' | grep -q "^ktab-app$"; then
  APP_WAS_RUNNING=1
  echo "[*] Stopping ktab-app during the restore..."
  docker compose --env-file .env.production stop ktab-app
fi

# Detect dump format: PostgreSQL custom format starts with bytes 'PGDMP'
echo "[*] Detecting backup format..."
if [[ "$BACKUP_FILE" == *.gz ]]; then
  MAGIC=$(gunzip -c "$BACKUP_FILE" 2>/dev/null | head -c 5 || true)
else
  MAGIC=$(head -c 5 "$BACKUP_FILE" || true)
fi

# 3. Clean wipe of public schema to guarantee pristine restoration
echo "[*] Recreating 'public' schema to guarantee a clean slate..."
docker exec -i ktab-db psql -U "$DB_USER" -d "$DB_NAME" \
  -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO \"$DB_USER\"; GRANT ALL ON SCHEMA public TO public;"

# 4. Execute restore
echo "[*] Executing database restore..."
RESTORE_STATUS=0
if [[ "$MAGIC" == "PGDMP" ]]; then
  echo "    Detected: PostgreSQL custom format — using pg_restore"
  set +e
  set +o pipefail
  if [[ "$BACKUP_FILE" == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | docker exec -i ktab-db pg_restore \
      -U "$DB_USER" -d "$DB_NAME" \
      --no-owner --no-acl -v 2>&1 | tee /tmp/pg_restore.log | tail -25
    RESTORE_STATUS=${PIPESTATUS[1]}
  else
    docker exec -i ktab-db pg_restore \
      -U "$DB_USER" -d "$DB_NAME" \
      --no-owner --no-acl -v < "$BACKUP_FILE" 2>&1 | tee /tmp/pg_restore.log | tail -25
    RESTORE_STATUS=${PIPESTATUS[0]}
  fi
  set -e
  set -o pipefail

  # pg_restore returns 0 on success, 1 on success with non-fatal warnings, and 2 on fatal errors
  if [ "$RESTORE_STATUS" -eq 1 ]; then
    echo "[!] pg_restore finished with minor warnings (non-fatal). Data restored successfully."
  elif [ "$RESTORE_STATUS" -gt 1 ]; then
    echo "[-] ERROR: pg_restore failed with fatal error code $RESTORE_STATUS" >&2
    echo "    Check /tmp/pg_restore.log for details." >&2
    if [ "$APP_WAS_RUNNING" = "1" ]; then
      docker compose --env-file .env.production up -d ktab-app
    fi
    exit "$RESTORE_STATUS"
  fi
else
  echo "    Detected: Plain SQL format — using psql"
  if [[ "$BACKUP_FILE" == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | docker exec -i ktab-db psql -U "$DB_USER" -d "$DB_NAME" -q -v ON_ERROR_STOP=0
  else
    docker exec -i ktab-db psql -U "$DB_USER" -d "$DB_NAME" -q -v ON_ERROR_STOP=0 < "$BACKUP_FILE"
  fi
fi

echo "[*] Database restored."

# 5. Start the app again so Spring refreshes its connection pool and caches
if [ "$APP_WAS_RUNNING" = "1" ]; then
  echo "[*] Starting ktab-app again..."
  docker compose --env-file .env.production up -d ktab-app
fi

echo "============================================================"
echo "  ✅ Restore Complete!"
echo "  Check the Flyway version the restored data is at:"
echo "    docker exec ktab-db psql -U $DB_USER -d $DB_NAME -c \"select version, success from flyway_schema_history order by installed_rank desc limit 3;\""
echo "============================================================"

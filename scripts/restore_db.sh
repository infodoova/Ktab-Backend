#!/usr/bin/env bash
###############################################################################
# Ktab Backend - PostgreSQL Database Restore Script
# Supports both plain SQL dumps and custom-format dumps (pg_dump -Fc / pgAdmin)
# Usage:   bash scripts/restore_db.sh <dump_file>
# Example: bash scripts/restore_db.sh backups/ktab_backup.sql
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ "$#" -lt 1 ]; then
  echo "Usage: bash scripts/restore_db.sh <backup_file.sql|backup_file.sql.gz>"
  echo "Example: bash scripts/restore_db.sh backups/ktab_backup.sql"
  exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "[-] ERROR: File '$BACKUP_FILE' not found!"
  exit 1
fi

# Load credentials from .env.production if it exists
if [ -f "${ROOT_DIR}/.env.production" ]; then
  export $(grep -v '^#' "${ROOT_DIR}/.env.production" | xargs -d '\n')
fi

DB_USER="${DB_USER:-ktab_user}"
DB_NAME="${DB_NAME:-ktab}"

echo "============================================================"
echo "  🔄 Restoring Database to Container: ktab-db"
echo "  Target Database: $DB_NAME"
echo "  Target User:     $DB_USER"
echo "  Backup File:     $BACKUP_FILE"
echo "============================================================"

# Check if ktab-db is running
if ! docker ps --format '{{.Names}}' | grep -q "^ktab-db$"; then
  echo "[-] ERROR: Container 'ktab-db' is not running."
  echo "    Please run: docker compose --env-file .env.production up -d ktab-db"
  exit 1
fi

# Confirm with user if interactive
if [ -t 0 ]; then
  read -p "⚠️  WARNING: This will overwrite tables in '$DB_NAME'. Continue? (y/N): " CONFIRM
  if [[ "$CONFIRM" != [yY] && "$CONFIRM" != [yY][eE][sS] ]]; then
    echo "[*] Restore cancelled."
    exit 0
  fi
fi

# Detect dump format: PostgreSQL custom format starts with bytes 'PGDMP'
echo "[*] Detecting backup format..."
FILE_TO_CHECK="$BACKUP_FILE"
if [[ "$BACKUP_FILE" == *.gz ]]; then
  # Peek at the first 5 bytes of the gzipped file
  MAGIC=$(gunzip -c "$BACKUP_FILE" 2>/dev/null | head -c 5)
else
  MAGIC=$(head -c 5 "$BACKUP_FILE")
fi

# Execute restore
echo "[*] Executing database restore..."
if [[ "$MAGIC" == "PGDMP" ]]; then
  echo "    Detected: PostgreSQL custom format — using pg_restore"
  if [[ "$BACKUP_FILE" == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | docker exec -i ktab-db pg_restore \
      -U "$DB_USER" -d "$DB_NAME" \
      --no-owner --no-acl --clean --if-exists -v 2>&1 | tail -20
  else
    docker exec -i ktab-db pg_restore \
      -U "$DB_USER" -d "$DB_NAME" \
      --no-owner --no-acl --clean --if-exists -v < "$BACKUP_FILE" 2>&1 | tail -20
  fi
else
  echo "    Detected: Plain SQL format — using psql"
  if [[ "$BACKUP_FILE" == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | docker exec -i ktab-db psql -U "$DB_USER" -d "$DB_NAME" -q
  else
    docker exec -i ktab-db psql -U "$DB_USER" -d "$DB_NAME" -q < "$BACKUP_FILE"
  fi
fi

echo "[*] Database restored successfully."

# Restart ktab-app so Hibernate/Spring Boot refreshes connection pool and caches
if docker ps --format '{{.Names}}' | grep -q "^ktab-app$"; then
  echo "[*] Restarting ktab-app container to refresh connections..."
  docker compose --env-file .env.production restart ktab-app
fi

echo "============================================================"
echo "  ✅ Restore Complete!"
echo "============================================================"

#!/usr/bin/env bash
###############################################################################
# Ktab Backend - Automated PostgreSQL Database Backup Script
# Usage: bash scripts/backup_db.sh
# Suitable for automated cron jobs: 0 3 * * * /path/to/scripts/backup_db.sh
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${ROOT_DIR}/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RETENTION_DAYS=7

mkdir -p "$BACKUP_DIR"

# Source environment variables if .env.production exists
if [ -f "${ROOT_DIR}/.env.production" ]; then
  # Export only non-commented lines
  export $(grep -v '^#' "${ROOT_DIR}/.env.production" | xargs -d '\n')
fi

DB_USER="${DB_USER:-ktab_user}"
DB_NAME="${DB_NAME:-ktab}"
BACKUP_FILE="${BACKUP_DIR}/ktab_backup_${TIMESTAMP}.sql.gz"

echo "[*] Creating database backup for '${DB_NAME}'..."

if ! docker ps | grep -q "ktab-db"; then
  echo "[-] ERROR: Container 'ktab-db' is not running!"
  exit 1
fi

docker exec -t ktab-db pg_dump -U "$DB_USER" -d "$DB_NAME" --clean --if-exists | gzip > "$BACKUP_FILE"

FILESIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "    ✅ Backup created successfully: $BACKUP_FILE ($FILESIZE)"

# Rotate old backups older than RETENTION_DAYS
echo "[*] Cleaning up backups older than ${RETENTION_DAYS} days..."
find "$BACKUP_DIR" -type f -name "ktab_backup_*.sql.gz" -mtime +${RETENTION_DAYS} -exec rm -f {} +

echo "[*] Database backup routine completed."

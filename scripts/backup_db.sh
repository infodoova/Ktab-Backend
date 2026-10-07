#!/usr/bin/env bash
###############################################################################
# Ktab Backend - PostgreSQL Database Backup Script
# Usage: bash scripts/backup_db.sh
# Suitable for cron:  0 3 * * * /bin/bash /home/ktabadmin/Ktab-Backend/scripts/backup_db.sh >> /var/log/ktab_backup.log 2>&1
#
# Environment (all optional):
#   RETENTION_DAYS   how many days of backups to keep (default 7)
#   BACKUP_DIR       where to write them (default <repo>/backups)
#
# Also run automatically by scripts/deploy.sh before a deploy and by scripts/restore_db.sh before a restore.
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/env.sh
source "${ROOT_DIR}/scripts/lib/env.sh"

BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
ENV_FILE="${ROOT_DIR}/.env.production"

mkdir -p "$BACKUP_DIR"

# Only the database name and user are needed; read them without executing anything from the file.
DB_USER="ktab_user"
DB_NAME="ktab"
if [ -f "$ENV_FILE" ]; then
  DB_USER="$(env_get "$ENV_FILE" DB_USER ktab_user)"
  DB_NAME="$(env_get "$ENV_FILE" DB_NAME ktab)"
fi
BACKUP_FILE="${BACKUP_DIR}/ktab_backup_${TIMESTAMP}.sql.gz"

echo "[*] Creating database backup for '${DB_NAME}'..."

if ! docker ps --format '{{.Names}}' | grep -q '^ktab-db$'; then
  echo "[-] ERROR: Container 'ktab-db' is not running!" >&2
  exit 1
fi

# A half-written file must never be mistaken for a good backup.
cleanup_partial() { rm -f "$BACKUP_FILE"; }
trap cleanup_partial ERR

# No -t here: a pseudo-terminal rewrites line endings (\n becomes \r\n) and corrupts the dump.
docker exec ktab-db pg_dump -U "$DB_USER" -d "$DB_NAME" --clean --if-exists | gzip > "$BACKUP_FILE"

# Verify: the gzip must be intact and not empty.
if ! gzip -t "$BACKUP_FILE" 2>/dev/null; then
  echo "[-] ERROR: the backup file is not a valid gzip archive: $BACKUP_FILE" >&2
  cleanup_partial
  exit 1
fi
if [ "$(gzip -dc "$BACKUP_FILE" | head -c 1000 | wc -c)" -lt 200 ]; then
  echo "[-] ERROR: the backup looks empty: $BACKUP_FILE" >&2
  cleanup_partial
  exit 1
fi
trap - ERR

FILESIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "    ✅ Backup created successfully: $BACKUP_FILE ($FILESIZE)"

# Rotate old backups
echo "[*] Cleaning up backups older than ${RETENTION_DAYS} days..."
find "$BACKUP_DIR" -type f -name "ktab_backup_*.sql.gz" -mtime +"${RETENTION_DAYS}" -exec rm -f {} +

echo "[*] Database backup routine completed."
echo "    A backup on this server does not survive losing the server: copy it off the VPS now and then."

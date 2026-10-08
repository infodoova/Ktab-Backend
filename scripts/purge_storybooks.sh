#!/usr/bin/env bash
###############################################################################
# Ktab Backend - delete all storybook data except the books you name
#
# Usage:   bash scripts/purge_storybooks.sh <keep_id>[,<keep_id>...]            # dry run: shows what would go
#          bash scripts/purge_storybooks.sh <keep_id>[,<keep_id>...] --execute  # really deletes
# Example: bash scripts/purge_storybooks.sh 44
#          bash scripts/purge_storybooks.sh 44 --execute
#
# What it deletes (everything for the books that are NOT kept):
#   tbl_storybooks and, through ON DELETE CASCADE, their characters, pages, page images, jobs and credit holds;
#   tbl_storybook_ai_calls of those books (and calls that belong to no book);
#   saved child profiles that no kept book uses.
# What it keeps: the named books with all their data, the users, and the credit ACCOUNTS (balances).
#
# Safety:
#   - the dry run does everything inside a transaction and rolls it back
#   - --execute first takes a database backup (scripts/backup_db.sh) and stops if that fails
#   - it refuses to run when a kept id does not exist, so a typo cannot wipe everything
#   - the object-storage keys (images, PDFs, photos) of the deleted books are written to a file; this script does NOT
#     delete the files in R2/S3 - use that list afterwards
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
# shellcheck source=lib/env.sh
source "${ROOT_DIR}/scripts/lib/env.sh"

KEEP="${1:-}"
MODE="${2:-}"
if [ -z "$KEEP" ] || ! [[ "$KEEP" =~ ^[0-9]+(,[0-9]+)*$ ]]; then
  echo "Usage: bash scripts/purge_storybooks.sh <keep_id>[,<keep_id>...] [--execute]" >&2
  exit 1
fi
if [ -n "$MODE" ] && [ "$MODE" != "--execute" ]; then
  echo "[-] Unknown option '$MODE' (only --execute is accepted)." >&2
  exit 1
fi

DB_USER="ktab_user"
DB_NAME="ktab"
if [ -f .env.production ]; then
  DB_USER="$(env_get .env.production DB_USER ktab_user)"
  DB_NAME="$(env_get .env.production DB_NAME ktab)"
fi

if ! docker ps --format '{{.Names}}' | grep -q '^ktab-db$'; then
  echo "[-] ERROR: Container 'ktab-db' is not running!" >&2
  exit 1
fi

psql_q() { docker exec -i ktab-db psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -X -q -t -A "$@"; }

# 1. Every kept id must exist.
EXPECTED=$(awk -F, '{print NF}' <<<"$KEEP")
FOUND=$(psql_q -c "SELECT count(*) FROM tbl_storybooks WHERE col_id IN ($KEEP);")
if [ "$FOUND" != "$EXPECTED" ]; then
  echo "[-] ERROR: only $FOUND of the $EXPECTED kept ids ($KEEP) exist in tbl_storybooks. Nothing was changed." >&2
  exit 1
fi

# 2. What would go.
echo "[*] Keeping storybook id(s): $KEEP"
psql_q -c "
SELECT 'storybooks to delete      : ' || count(*) FROM tbl_storybooks WHERE col_id NOT IN ($KEEP);
SELECT 'pages to delete           : ' || count(*) FROM tbl_storybook_pages WHERE col_storybook_id NOT IN ($KEEP);
SELECT 'page images to delete     : ' || count(*) FROM tbl_storybook_page_images i JOIN tbl_storybook_pages p ON p.col_id = i.col_page_id WHERE p.col_storybook_id NOT IN ($KEEP);
SELECT 'characters to delete      : ' || count(*) FROM tbl_storybook_characters WHERE col_storybook_id NOT IN ($KEEP);
SELECT 'jobs to delete            : ' || count(*) FROM tbl_storybook_jobs WHERE col_storybook_id NOT IN ($KEEP);
SELECT 'credit holds to delete    : ' || count(*) FROM tbl_storybook_credit_holds WHERE col_storybook_id NOT IN ($KEEP);
SELECT 'ai calls to delete        : ' || count(*) FROM tbl_storybook_ai_calls WHERE col_storybook_id IS NULL OR col_storybook_id NOT IN ($KEEP);
SELECT 'child profiles to delete  : ' || count(*) FROM tbl_storybook_child_profiles WHERE col_id NOT IN (SELECT col_child_profile_id FROM tbl_storybooks WHERE col_id IN ($KEEP));
"

if [ "$MODE" != "--execute" ]; then
  echo
  echo "[i] Dry run only - nothing was changed. Add --execute to delete."
  exit 0
fi

# 3. Safety backup, then the object-storage key list, then the delete in one transaction.
echo "[*] Taking a backup first..."
bash "${ROOT_DIR}/scripts/backup_db.sh"

mkdir -p "${ROOT_DIR}/backups"
KEYS_FILE="${ROOT_DIR}/backups/storybook_purge_keys_$(date +%Y%m%d_%H%M%S).txt"
psql_q -c "
SELECT col_pdf_key FROM tbl_storybooks WHERE col_id NOT IN ($KEEP) AND col_pdf_key IS NOT NULL
UNION SELECT col_sheet_key FROM tbl_storybook_characters WHERE col_storybook_id NOT IN ($KEEP) AND col_sheet_key IS NOT NULL
UNION SELECT col_photo_key FROM tbl_storybook_characters WHERE col_storybook_id NOT IN ($KEEP) AND col_photo_key IS NOT NULL
UNION SELECT col_master_sheet_key FROM tbl_storybook_characters WHERE col_storybook_id NOT IN ($KEEP) AND col_master_sheet_key IS NOT NULL
UNION SELECT i.col_image_key FROM tbl_storybook_page_images i JOIN tbl_storybook_pages p ON p.col_id = i.col_page_id WHERE p.col_storybook_id NOT IN ($KEEP)
UNION SELECT i.col_web_image_key FROM tbl_storybook_page_images i JOIN tbl_storybook_pages p ON p.col_id = i.col_page_id WHERE p.col_storybook_id NOT IN ($KEEP) AND i.col_web_image_key IS NOT NULL
ORDER BY 1;" > "$KEYS_FILE"
echo "    Storage keys of the deleted books: $KEYS_FILE ($(wc -l < "$KEYS_FILE") keys)"

echo "[*] Deleting..."
psql_q <<SQL
BEGIN;
DELETE FROM tbl_storybook_ai_calls WHERE col_storybook_id IS NULL OR col_storybook_id NOT IN ($KEEP);
DELETE FROM tbl_storybooks WHERE col_id NOT IN ($KEEP);   -- cascades to characters, pages, page images, jobs, credit holds
DELETE FROM tbl_storybook_child_profiles
 WHERE col_id NOT IN (SELECT col_child_profile_id FROM tbl_storybooks);
COMMIT;
SQL

echo "[*] Left now:"
psql_q -c "
SELECT 'storybooks : ' || count(*) FROM tbl_storybooks;
SELECT 'pages      : ' || count(*) FROM tbl_storybook_pages;
SELECT 'jobs       : ' || count(*) FROM tbl_storybook_jobs;
SELECT 'profiles   : ' || count(*) FROM tbl_storybook_child_profiles;"
echo "[+] Done. Delete the files listed in $KEYS_FILE from the bucket if you want the space back."

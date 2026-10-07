#!/usr/bin/env bash
###############################################################################
# Ktab Backend - merge a new environment file into the one that is already live
#
# For a server that already runs Ktab: add the variables the new version needs
# WITHOUT touching the values that are already set (database password, JWT
# secret, API keys ...). Values are never printed, only variable names.
#
# Usage (from the repository root on the VPS):
#   bash scripts/merge_env.sh [CURRENT] [NEW] [--take KEY1,KEY2]
#
#   CURRENT  the live file          (default: .env.production)
#   NEW      the file to merge in   (default: .env.production.new)
#   --take   variables whose value from NEW should replace the live one
#   --dry-run  show what would happen and change nothing (no backup, no write)
#
# What it does:
#   - backs CURRENT up to CURRENT.bak-<timestamp>
#   - keys only in NEW  -> appended to CURRENT
#   - keys in both, same value -> left alone
#   - keys in both, different value -> CURRENT is kept, the name is listed for review
#                                      (unless the key is in --take)
#   - keys only in CURRENT -> left alone, listed (they may be unused by the new version)
#   - a NEW value of __KEEP_EXISTING_VALUE_FROM_THE_SERVER__ is never copied
###############################################################################

set -euo pipefail

CURRENT=".env.production"
NEW=".env.production.new"
TAKE=""
DRY_RUN=0
PLACEHOLDER="__KEEP_EXISTING_VALUE_FROM_THE_SERVER__"

positional=()
while [ $# -gt 0 ]; do
  case "$1" in
    --take) TAKE="${2:-}"; shift 2 ;;
    --take=*) TAKE="${1#--take=}"; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
    *) positional+=("$1"); shift ;;
  esac
done
[ "${#positional[@]}" -ge 1 ] && CURRENT="${positional[0]}"
[ "${#positional[@]}" -ge 2 ] && NEW="${positional[1]}"

[ -f "$CURRENT" ] || { echo "[-] ERROR: $CURRENT not found" >&2; exit 1; }
[ -f "$NEW" ]     || { echo "[-] ERROR: $NEW not found" >&2; exit 1; }

# value of KEY in FILE (everything after the first '='), empty if the key is absent
value_of() { grep -m1 -E "^$2=" "$1" | sed -E "s/^$2=//" || true; }
has_key()  { grep -q -E "^$1=" "$2"; }
in_list()  { case ",$2," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

# Work on a copy; it replaces the live file only at the end, so a dry run (or a failure) never changes it
WORK="$(mktemp)"
trap 'rm -f "$WORK"' EXIT
tr -d '\r' < "$CURRENT" > "$WORK"          # a file edited on Windows may have CRLF line endings
BACKUP="${CURRENT}.bak-$(date +%Y%m%d-%H%M%S)"

added=(); same=(); differs=(); taken=(); skipped=(); missing_required=()

while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  case "$line" in ''|\#*) continue ;; esac
  key="${line%%=*}"
  case "$key" in *[!A-Za-z0-9_]*|'') continue ;; esac
  new_value="${line#*=}"

  if [ "$new_value" = "$PLACEHOLDER" ]; then
    if has_key "$key" "$WORK"; then skipped+=("$key"); else missing_required+=("$key"); fi
    continue
  fi

  if has_key "$key" "$WORK"; then
    old_value="$(value_of "$WORK" "$key")"
    if [ "$old_value" = "$new_value" ]; then
      same+=("$key")
    elif in_list "$key" "$TAKE"; then
      # replace the live line with the new one, in place
      tmp="$(mktemp)"
      # the new line travels through the environment: awk -v would interpret backslashes in it (a regex value)
      NEW_LINE="$line" awk -v k="$key" 'BEGIN{FS="="} $1==k && !done {print ENVIRON["NEW_LINE"]; done=1; next} {print}' "$WORK" > "$tmp"
      cat "$tmp" > "$WORK"; rm -f "$tmp"
      taken+=("$key")
    else
      differs+=("$key")
    fi
  else
    if [ "${#added[@]}" -eq 0 ]; then
      printf '\n# --- added by scripts/merge_env.sh on %s ---\n' "$(date +%Y-%m-%d)" >> "$WORK"
    fi
    printf '%s\n' "$line" >> "$WORK"
    added+=("$key")
  fi
done < "$NEW"

only_current=()
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in ''|\#*) continue ;; esac
  key="${line%%=*}"
  case "$key" in *[!A-Za-z0-9_]*|'') continue ;; esac
  has_key "$key" "$NEW" || only_current+=("$key")
done < "$WORK"

if [ "$DRY_RUN" = "1" ]; then
  echo "[*] DRY RUN: nothing was changed."
else
  cp -p "$CURRENT" "$BACKUP"
  chmod 600 "$BACKUP"
  cat "$WORK" > "$CURRENT"
  chmod 600 "$CURRENT"
fi

list() { local title="$1"; shift; if [ "$#" -gt 0 ]; then echo; echo "$title ($#):"; printf '    %s\n' "$@"; fi; }

if [ "$DRY_RUN" = "1" ]; then
  echo "[*] Would merge $NEW into $CURRENT"
else
  echo "[*] Merged $NEW into $CURRENT   (backup: $BACKUP)"
fi
echo "    unchanged: ${#same[@]}   added: ${#added[@]}   taken from NEW: ${#taken[@]}   kept (different): ${#differs[@]}"
list "ADDED (new variables)" "${added[@]+"${added[@]}"}"
list "TAKEN from the new file (--take)" "${taken[@]+"${taken[@]}"}"
list "DIFFERENT - live value kept, review each one (use --take KEY to switch)" "${differs[@]+"${differs[@]}"}"
list "ONLY IN THE LIVE FILE (not in the new file; may be unused now)" "${only_current[@]+"${only_current[@]}"}"
list "MUST BE SET BY YOU - missing in the live file and a placeholder in the new one" "${missing_required[@]+"${missing_required[@]}"}"

if [ "${#missing_required[@]}" -gt 0 ]; then
  echo; echo "[!] Set the variables above in $CURRENT before deploying." >&2
  exit 2
fi
if [ "$DRY_RUN" = "1" ]; then
  echo; echo "[+] Dry run finished. Run it again without --dry-run to apply."
else
  echo; echo "[+] Done. Review the lists, then: bash scripts/deploy.sh"
fi

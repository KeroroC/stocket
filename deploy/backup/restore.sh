#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

force=false
if [[ "${1:-}" == "--force" ]]; then force=true; shift; fi
backup=${1:-}
target=${2:-${ATTACHMENT_DIR:-/var/lib/stocket/attachments}}
[[ -n "$backup" ]] || die "usage: restore.sh [--force] BACKUP_DIR [ATTACHMENT_DIR]"
[[ "$target" == /* && "$target" != "/" ]] || die "attachment target must be a non-root absolute path"
"$BACKUP_SCRIPT_DIR/verify.sh" "$backup"
load_database_secret

table_count=$(psql -Atqc "select count(*) from pg_catalog.pg_tables where schemaname not in ('pg_catalog','information_schema')")
attachments_empty=true; is_empty_directory "$target" || attachments_empty=false
if [[ "$table_count" != "0" || "$attachments_empty" != "true" ]]; then
  if [[ "$force" != "true" ]]; then die "restore target database and attachment directory must be empty"; fi
  [[ -n "${PRE_RESTORE_BACKUP_ROOT:-}" ]] || die "--force requires PRE_RESTORE_BACKUP_ROOT"
  STOCKET_BACKUP_QUIESCED=true BACKUP_ROOT="$PRE_RESTORE_BACKUP_ROOT" ATTACHMENT_DIR="$target" \
    "$BACKUP_SCRIPT_DIR/backup.sh" >/dev/null
fi

parent=$(dirname "$target")
mkdir -p "$parent"
staging=$(mktemp -d "$parent/.stocket-restore.XXXXXX")
trap 'rm -rf "$staging"' EXIT
tar -xf "$backup/attachments.tar" -C "$staging"
(cd "$staging" && sha256sum --check --strict "$backup/attachment-files.sha256" >/dev/null)

pg_restore --clean --if-exists --no-owner --no-privileges --dbname "${PGDATABASE:-stocket}" "$backup/database.dump"
if [[ -d "$target" ]]; then rmdir "$target" 2>/dev/null || { [[ "$force" == "true" ]] && rm -rf "$target" || die "attachment target is not empty"; }; fi
mv "$staging" "$target"
trap - EXIT
sync
printf 'restored %s\n' "$backup"

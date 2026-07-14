#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

[[ "${STOCKET_BACKUP_QUIESCED:-false}" == "true" ]] || die "app writes must be quiesced before backup"
require_command pg_dump; require_command jq; require_command sha256sum; require_command flock
load_database_secret

BACKUP_ROOT=${BACKUP_ROOT:-/var/lib/stocket/backups}
ATTACHMENT_DIR=${ATTACHMENT_DIR:-/var/lib/stocket/attachments}
BACKUP_ID=${BACKUP_ID:-$(date -u +%Y%m%dT%H%M%SZ)}
STOCKET_VERSION=${STOCKET_VERSION:-dev}
validate_backup_id "$BACKUP_ID"
[[ -d "$ATTACHMENT_DIR" ]] || die "attachment directory unavailable"
mkdir -p "$BACKUP_ROOT"

exec 9>"$BACKUP_ROOT/.backup.lock"
flock -n 9 || die "another backup is running"

partial="$BACKUP_ROOT/$BACKUP_ID.partial"
final="$BACKUP_ROOT/$BACKUP_ID"
[[ ! -e "$partial" && ! -e "$final" ]] || die "backup id already exists"
mkdir -p "$partial"

pg_dump --format=custom --no-owner --no-privileges --file "$partial/database.dump"
(cd "$ATTACHMENT_DIR" && tar -cf "$partial/attachments.tar" .)
(cd "$ATTACHMENT_DIR" && find . -type f -print0 | sort -z | xargs -0 -r sha256sum) >"$partial/attachment-files.sha256"
attachment_count=$(wc -l <"$partial/attachment-files.sha256" | tr -d ' ')

jq -n --arg version "$STOCKET_VERSION" --argjson attachmentFiles "$attachment_count" \
  '{version:$version,databaseFormat:"postgres-custom",attachmentSchema:1,attachmentFiles:$attachmentFiles}' \
  >"$partial/config-summary.json"
jq -n --arg id "$BACKUP_ID" --arg createdAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg version "$STOCKET_VERSION" \
  --argjson attachmentFiles "$attachment_count" \
  '{schemaVersion:1,id:$id,createdAt:$createdAt,version:$version,database:"database.dump",attachments:"attachments.tar",attachmentFiles:$attachmentFiles}' \
  >"$partial/manifest.json"

(cd "$partial" && sha256sum database.dump attachments.tar attachment-files.sha256 config-summary.json manifest.json >SHA256SUMS)
"$BACKUP_SCRIPT_DIR/verify.sh" "$partial"
sync
mv "$partial" "$final"
ln -sfn "$BACKUP_ID" "$BACKUP_ROOT/.latest.new"
mv -Tf "$BACKUP_ROOT/.latest.new" "$BACKUP_ROOT/latest"
sync

"$BACKUP_SCRIPT_DIR/retention.sh" "$BACKUP_ROOT"
printf '%s\n' "$final"

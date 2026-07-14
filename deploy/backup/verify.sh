#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

directory=${1:-}
[[ -n "$directory" && -d "$directory" ]] || die "backup directory required"
for file in database.dump attachments.tar attachment-files.sha256 config-summary.json manifest.json SHA256SUMS; do
  require_file "$directory/$file"
done
verify_checksums "$directory"
pg_restore --list "$directory/database.dump" >/dev/null
tar -tf "$directory/attachments.tar" >/dev/null
jq -e '.schemaVersion == 1 and (.id | test("^[0-9]{8}T[0-9]{6}Z$")) and .database == "database.dump" and .attachments == "attachments.tar"' \
  "$directory/manifest.json" >/dev/null
printf 'verified %s\n' "$directory"

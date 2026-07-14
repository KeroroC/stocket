#!/usr/bin/env bash
set -euo pipefail

BACKUP_SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

die() { printf 'stocket-backup: %s\n' "$1" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || die "required command missing: $1"; }
require_file() { [[ -f "$1" ]] || die "required file missing: $1"; }

load_database_secret() {
  if [[ -n "${STOCKET_DB_PASSWORD_FILE:-}" ]]; then
    require_file "$STOCKET_DB_PASSWORD_FILE"
    export PGPASSWORD
    PGPASSWORD=$(<"$STOCKET_DB_PASSWORD_FILE")
  fi
}

validate_backup_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "invalid backup id"
}

verify_checksums() {
  local directory=$1
  require_file "$directory/SHA256SUMS"
  (cd "$directory" && sha256sum --check --strict SHA256SUMS >/dev/null)
}

is_empty_directory() {
  [[ -d "$1" ]] || return 0
  [[ -z "$(find "$1" -mindepth 1 -maxdepth 1 -print -quit)" ]]
}

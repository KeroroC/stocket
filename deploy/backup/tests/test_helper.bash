setup_backup_test() {
  TEST_ROOT=$(mktemp -d)
  export TEST_ROOT
  export BACKUP_ROOT="$TEST_ROOT/backups"
  export ATTACHMENT_DIR="$TEST_ROOT/attachments with spaces"
  export STOCKET_BACKUP_QUIESCED=true
  export STOCKET_VERSION=0.1.0-test
  export PG_RESTORE_LOG="$TEST_ROOT/pg-restore.log"
  mkdir -p "$BACKUP_ROOT" "$ATTACHMENT_DIR" "$TEST_ROOT/bin"
  printf 'attachment payload' >"$ATTACHMENT_DIR/file with spaces.bin"
  create_stub_commands
  export PATH="$TEST_ROOT/bin:$PATH"
  BACKUP_DIR=$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)
  export BACKUP_DIR
}

teardown_backup_test() { rm -rf "$TEST_ROOT"; }

create_stub_commands() {
  cat >"$TEST_ROOT/bin/pg_dump" <<'SH'
#!/bin/sh
[ "${FAIL_PG_DUMP:-false}" = true ] && exit 42
file=
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--file" ]; then shift; file=$1; fi
  shift
done
[ -n "$file" ] || exit 2
printf 'custom postgres dump\n' >"$file"
SH
  cat >"$TEST_ROOT/bin/pg_restore" <<'SH'
#!/bin/sh
if [ "${1:-}" = "--list" ]; then [ -s "${2:-}" ]; exit; fi
printf '%s\n' "$*" >>"${PG_RESTORE_LOG:?}"
SH
  cat >"$TEST_ROOT/bin/psql" <<'SH'
#!/bin/sh
printf '%s\n' "${PSQL_TABLE_COUNT:-0}"
SH
  chmod +x "$TEST_ROOT/bin/pg_dump" "$TEST_ROOT/bin/pg_restore" "$TEST_ROOT/bin/psql"
}

create_backup() {
  export BACKUP_ID=${1:-20260714T000000Z}
  "$BACKUP_DIR/backup.sh" >/dev/null
  printf '%s/%s' "$BACKUP_ROOT" "$BACKUP_ID"
}

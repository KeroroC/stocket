#!/usr/bin/env bats
load test_helper
setup() { setup_backup_test; }
teardown() { teardown_backup_test; }

@test "rejects a backup with a checksum mismatch" {
  backup=$(create_backup 20260714T040506Z)
  printf 'tamper' >>"$backup/database.dump"
  run "$BACKUP_DIR/verify.sh" "$backup"
  [ "$status" -ne 0 ]
}

@test "restores database and attachments only into empty targets" {
  backup=$(create_backup 20260714T050607Z)
  target="$TEST_ROOT/restored attachments"
  run "$BACKUP_DIR/restore.sh" "$backup" "$target"
  [ "$status" -eq 0 ]
  [ "$(cat "$target/file with spaces.bin")" = "attachment payload" ]
  grep -q -- '--clean --if-exists' "$PG_RESTORE_LOG"

  printf occupied >"$target/occupied"
  run "$BACKUP_DIR/restore.sh" "$backup" "$target"
  [ "$status" -ne 0 ]
  [[ "$output" == *"must be empty"* ]]
}

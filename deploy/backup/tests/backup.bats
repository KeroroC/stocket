#!/usr/bin/env bats
load test_helper
setup() { setup_backup_test; }
teardown() { teardown_backup_test; }

@test "publishes an immutable verified backup with paths containing spaces" {
  run create_backup 20260714T010203Z
  [ "$status" -eq 0 ]
  [ -d "$BACKUP_ROOT/20260714T010203Z" ]
  [ ! -e "$BACKUP_ROOT/20260714T010203Z.partial" ]
  [ "$(readlink "$BACKUP_ROOT/latest")" = "20260714T010203Z" ]
  run "$BACKUP_DIR/verify.sh" "$BACKUP_ROOT/20260714T010203Z"
  [ "$status" -eq 0 ]
}

@test "does not publish when pg_dump fails" {
  export FAIL_PG_DUMP=true BACKUP_ID=20260714T020304Z
  run "$BACKUP_DIR/backup.sh"
  [ "$status" -ne 0 ]
  [ ! -e "$BACKUP_ROOT/20260714T020304Z" ]
}

@test "refuses a concurrent backup lock" {
  exec 8>"$BACKUP_ROOT/.backup.lock"
  flock -n 8
  export BACKUP_ID=20260714T030405Z
  run "$BACKUP_DIR/backup.sh"
  [ "$status" -ne 0 ]
  [[ "$output" == *"another backup is running"* ]]
}

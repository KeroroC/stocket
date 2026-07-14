#!/usr/bin/env bats
load test_helper
setup() { setup_backup_test; }
teardown() { teardown_backup_test; }

@test "keeps seven daily and four weekly selections without deleting invalid backups" {
  for day in $(seq -w 1 15); do create_backup "202606${day}T010000Z" >/dev/null; done
  mkdir "$BACKUP_ROOT/20260501T010000Z"
  printf invalid >"$BACKUP_ROOT/20260501T010000Z/manifest.json"
  mkdir "$BACKUP_ROOT/20260401T010000Z.partial"
  touch -d '2 days ago' "$BACKUP_ROOT/20260401T010000Z.partial"

  run "$BACKUP_DIR/retention.sh" "$BACKUP_ROOT"
  [ "$status" -eq 0 ]
  complete=$(find "$BACKUP_ROOT" -maxdepth 1 -type d -name '202606*T010000Z' | wc -l)
  [ "$complete" -ge 7 ]
  [ "$complete" -le 11 ]
  [ -d "$BACKUP_ROOT/$(readlink "$BACKUP_ROOT/latest")" ]
  [ -d "$BACKUP_ROOT/20260501T010000Z" ]
  [ ! -d "$BACKUP_ROOT/20260401T010000Z.partial" ]
}

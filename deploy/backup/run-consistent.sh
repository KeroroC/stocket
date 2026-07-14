#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
COMPOSE=(docker compose --env-file "$ROOT/.env" -f "$ROOT/deploy/compose.yml" -f "$ROOT/deploy/compose.production.yml")

restart() { "${COMPOSE[@]}" start app >/dev/null 2>&1 || true; }
trap restart EXIT
"${COMPOSE[@]}" stop app
"${COMPOSE[@]}" --profile operations run --rm -e STOCKET_BACKUP_QUIESCED=true backup
restart
trap - EXIT

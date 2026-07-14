#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RUN_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/stocket-restore-smoke.XXXXXX")
PROJECT="stocket-restore-$RANDOM-$$"
HTTP_PORT=${STOCKET_SMOKE_HTTP_PORT:-18080}
HTTPS_PORT=${STOCKET_SMOKE_HTTPS_PORT:-18443}
BASE_URL="https://localhost:$HTTPS_PORT"
mkdir -p "$RUN_ROOT/secrets" "$RUN_ROOT/attachment-volume/attachments" \
  "$RUN_ROOT/attachment-volume/backups" "$RUN_ROOT/backups"
chmod 0777 "$RUN_ROOT/attachment-volume" "$RUN_ROOT/attachment-volume/attachments" \
  "$RUN_ROOT/attachment-volume/backups" "$RUN_ROOT/backups"
printf 'restore-smoke-password-2026' >"$RUN_ROOT/secrets/postgres-password.txt"
printf 'QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=' >"$RUN_ROOT/secrets/master-key.txt"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost \
  -keyout "$RUN_ROOT/secrets/tls.key" -out "$RUN_ROOT/secrets/tls.crt" >/dev/null 2>&1
chmod 0644 "$RUN_ROOT/secrets/tls.key" "$RUN_ROOT/secrets/tls.crt"

cat >"$RUN_ROOT/smoke.env" <<EOF
POSTGRES_DB=stocket
POSTGRES_USER=stocket
POSTGRES_PASSWORD=restore-smoke-password-2026
POSTGRES_PASSWORD_FILE=$RUN_ROOT/secrets/postgres-password.txt
STOCKET_FRONTEND_URL=$BASE_URL
STOCKET_MASTER_KEY_FILE=$RUN_ROOT/secrets/master-key.txt
STOCKET_TLS_CERT_FILE=$RUN_ROOT/secrets/tls.crt
STOCKET_TLS_KEY_FILE=$RUN_ROOT/secrets/tls.key
STOCKET_VERSION=0.1.0-smoke
STOCKET_TRUSTED_PROXY_CIDRS=172.16.0.0/12
EOF
cat >"$RUN_ROOT/override.yml" <<EOF
services:
  gateway:
    ports: !override
      - target: 8080
        published: "$HTTP_PORT"
      - target: 8443
        published: "$HTTPS_PORT"
  app:
    volumes: !override
      - type: bind
        source: $RUN_ROOT/attachment-volume
        target: /var/lib/stocket
  backup:
    volumes: !override
      - type: bind
        source: $RUN_ROOT/attachment-volume
        target: /var/lib/stocket
        read_only: true
      - type: bind
        source: $RUN_ROOT/backups
        target: /var/lib/stocket/backups
  restore:
    volumes: !override
      - type: bind
        source: $RUN_ROOT/attachment-volume
        target: /var/lib/stocket
      - type: bind
        source: $RUN_ROOT/backups
        target: /var/lib/stocket/backups
        read_only: true
volumes:
  postgres-data:
    name: ${PROJECT}-postgres-data
EOF

COMPOSE=(docker compose --env-file "$RUN_ROOT/smoke.env" -p "$PROJECT" -f "$ROOT/deploy/compose.yml" -f "$ROOT/deploy/compose.production.yml" -f "$RUN_ROOT/override.yml")
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans >"$RUN_ROOT/compose-down.log" 2>&1 || true
  if [[ "${KEEP_SMOKE_ENV:-false}" == "true" ]]; then printf 'restore smoke artifacts: %s\n' "$RUN_ROOT"; else rm -rf "$RUN_ROOT"; fi
}
trap cleanup EXIT

if [[ "${STOCKET_SMOKE_SKIP_BUILD:-false}" != "true" ]]; then "${COMPOSE[@]}" --profile operations build app gateway backup; fi
"${COMPOSE[@]}" up -d postgres app gateway
for attempt in $(seq 1 90); do curl -ksSf "$BASE_URL/readyz" >/dev/null && break; sleep 2; done
curl -ksSf "$BASE_URL/readyz" >/dev/null

export STOCKET_SMOKE_BASE_URL="$BASE_URL" STOCKET_SMOKE_STATE_FILE="$RUN_ROOT/state.json" STOCKET_SMOKE_COOKIE_JAR="$RUN_ROOT/cookies.txt"
"$ROOT/deploy/smoke/api-smoke.sh"
"$ROOT/deploy/smoke/inventory-smoke.sh" seed

"${COMPOSE[@]}" stop app
"${COMPOSE[@]}" --profile operations run --rm -e STOCKET_BACKUP_QUIESCED=true backup >"$RUN_ROOT/backup.log"
backup_id=$(readlink "$RUN_ROOT/backups/latest")
[[ -n "$backup_id" ]]
"${COMPOSE[@]}" down --volumes --remove-orphans
find "$RUN_ROOT/attachment-volume/attachments" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +

"${COMPOSE[@]}" up -d postgres
for attempt in $(seq 1 60); do "${COMPOSE[@]}" exec -T postgres pg_isready -U stocket -d stocket >/dev/null 2>&1 && break; sleep 1; done
"${COMPOSE[@]}" --profile operations run --rm restore \
  "/var/lib/stocket/backups/$backup_id" /var/lib/stocket/attachments
"${COMPOSE[@]}" up -d app gateway
for attempt in $(seq 1 90); do curl -ksSf "$BASE_URL/readyz" >/dev/null && break; sleep 2; done
curl -ksSf "$BASE_URL/readyz" >/dev/null

rm -f "$RUN_ROOT/cookies.txt"
"$ROOT/deploy/smoke/api-smoke.sh"
"$ROOT/deploy/smoke/inventory-smoke.sh" verify
if ! migration_version=$("${COMPOSE[@]}" exec -T postgres psql -U stocket -d stocket -Atqc \
  "select version from flyway_schema_history where success order by installed_rank desc limit 1" \
  2>"$RUN_ROOT/flyway-query.err"); then
  printf 'Flyway version query failed:\n' >&2
  sed -n '1,80p' "$RUN_ROOT/flyway-query.err" >&2
  "${COMPOSE[@]}" ps >&2 || true
  exit 1
fi
[[ "$migration_version" == "7" ]] || { printf 'unexpected Flyway version: %s\n' "$migration_version" >&2; exit 1; }
if ! open_issues=$("${COMPOSE[@]}" exec -T postgres psql -U stocket -d stocket -Atqc \
  "select count(*) from inventory_reconciliation_issue where status='OPEN'" \
  2>"$RUN_ROOT/reconciliation-query.err"); then
  printf 'reconciliation query failed:\n' >&2
  sed -n '1,80p' "$RUN_ROOT/reconciliation-query.err" >&2
  exit 1
fi
[[ "$open_issues" == "0" ]] || { printf 'open inventory reconciliation issues: %s\n' "$open_issues" >&2; exit 1; }
printf 'restore smoke passed; backup=%s artifacts=%s\n' "$backup_id" "$RUN_ROOT"

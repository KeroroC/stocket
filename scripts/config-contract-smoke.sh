#!/usr/bin/env bash
set -euo pipefail

require_literal() {
  local file="$1"
  local literal="$2"
  if ! rg -qF -- "$literal" "$file"; then
    printf 'Missing required configuration in %s: %s\n' "$file" "$literal" >&2
    exit 1
  fi
}

require_literal backend/src/main/resources/application.yml 'frontend-url: ${STOCKET_FRONTEND_URL:}'
require_literal deploy/compose.yml 'STOCKET_FRONTEND_URL: ${STOCKET_FRONTEND_URL:-}'
require_literal .env.example 'STOCKET_FRONTEND_URL='
require_literal deploy/gateway/default.conf 'location ~ ^/api/v1/invites/[^/]+/(status|accept)$ {'
require_literal deploy/gateway/default.conf 'location ~ ^/invite/[^/]+$ {'

redacted_location_count="$(rg -cF 'access_log off;' deploy/gateway/default.conf)"
if (( redacted_location_count < 2 )); then
  printf 'Expected access logging to be disabled for both token-bearing invite routes\n' >&2
  exit 1
fi

#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${STOCKET_SMOKE_BASE_URL:-https://localhost}
CURL=(curl --fail --silent --show-error --insecure)

"${CURL[@]}" "$BASE_URL/" >/dev/null
"${CURL[@]}" "$BASE_URL/api/v1/system" | jq -e '.name == "stocket" and (.version | length > 0)' >/dev/null
"${CURL[@]}" "$BASE_URL/livez" | jq -e '.status == "UP"' >/dev/null
"${CURL[@]}" "$BASE_URL/readyz" | jq -e '.status == "UP"' >/dev/null

headers=$(mktemp); trap 'rm -f "$headers"' EXIT
"${CURL[@]}" -D "$headers" -o /dev/null "$BASE_URL/"
grep -qi '^strict-transport-security:' "$headers"
grep -qi '^content-security-policy:' "$headers"
grep -qi '^x-content-type-options: nosniff' "$headers"
status=$(curl --silent --insecure -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/env")
[[ "$status" == "404" ]]
printf 'api smoke passed: %s\n' "$BASE_URL"

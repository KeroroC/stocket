#!/usr/bin/env bash
set -euo pipefail

MODE=${1:-}
BASE_URL=${STOCKET_SMOKE_BASE_URL:-https://localhost}
STATE_FILE=${STOCKET_SMOKE_STATE_FILE:?STOCKET_SMOKE_STATE_FILE is required}
PASSWORD=${STOCKET_SMOKE_PASSWORD:-correct-horse-battery-staple-2026}
COOKIE_JAR=${STOCKET_SMOKE_COOKIE_JAR:-$(mktemp)}
CURL=(curl --fail --silent --show-error --insecure -b "$COOKIE_JAR" -c "$COOKIE_JAR")

cleanup() {
  if [[ -n "${TEMP_IMAGE:-}" ]]; then
    rm -f "$TEMP_IMAGE"
  fi
}
trap cleanup EXIT

csrf() {
  CSRF_TOKEN=$("${CURL[@]}" "$BASE_URL/api/v1/auth/csrf" | jq -r .token)
  [[ -n "$CSRF_TOKEN" && "$CSRF_TOKEN" != null ]]
}

post_json() {
  local path=$1 body=$2
  "${CURL[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H 'Content-Type: application/json' \
    -X POST "$BASE_URL$path" --data "$body"
}

await_json() {
  local url=$1 filter=$2 response='' attempt
  shift 2
  for attempt in $(seq 1 30); do
    if response=$("${CURL[@]}" "$url") && jq -e "$@" "$filter" <<<"$response" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  printf 'timed out waiting for %s; response=%s\n' "$url" "$response" >&2
  return 1
}

seed() {
  csrf
  post_json /api/v1/setup/initialize \
    "{\"householdName\":\"恢复验收家庭\",\"timezone\":\"Asia/Shanghai\",\"username\":\"restore-admin\",\"displayName\":\"恢复管理员\",\"password\":\"$PASSWORD\"}" >/dev/null
  category=$(post_json /api/v1/categories '{"name":"恢复验收","defaultInventoryType":"BATCH","attributeSchema":[]}' | jq -r .id)
  fridge=$(post_json /api/v1/locations '{"name":"恢复冰箱","parentId":null}' | jq -r .id)
  pantry=$(post_json /api/v1/locations '{"name":"恢复储物间","parentId":null}' | jq -r .id)
  item=$(post_json /api/v1/items "{\"name\":\"恢复牛奶\",\"categoryId\":\"$category\",\"defaultUnit\":\"盒\",\"customAttributes\":{},\"barcodes\":[\"RESTORE-6901\"],\"tags\":[\"恢复\"]}" | jq -r .id)
  entry=$("${CURL[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H 'Content-Type: application/json' \
    -H 'Idempotency-Key: restore-receive-1' -X POST "$BASE_URL/api/v1/inventory/receipts" \
    --data "{\"itemId\":\"$item\",\"type\":\"BATCH\",\"quantity\":\"5\",\"locationId\":\"$fridge\",\"receivedAt\":\"2026-07-14T00:00:00Z\",\"expirationDate\":\"2026-08-14\",\"batchNumber\":\"RESTORE-1\",\"customAttributes\":{}}" | jq -r .id)
  "${CURL[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H 'Content-Type: application/json' \
    -H 'Idempotency-Key: restore-consume-1' -X POST "$BASE_URL/api/v1/inventory/entries/$entry/consume" \
    --data '{"quantity":"1"}' >/dev/null
  "${CURL[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H 'Content-Type: application/json' \
    -H 'Idempotency-Key: restore-transfer-1' -X POST "$BASE_URL/api/v1/inventory/entries/$entry/transfer" \
    --data "{\"targetLocationId\":\"$pantry\",\"quantity\":\"1\"}" >/dev/null

  TEMP_IMAGE=$(mktemp -t stocket-smoke.XXXXXX.png)
  printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' | base64 --decode >"$TEMP_IMAGE" 2>/dev/null \
    || printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' | base64 -D >"$TEMP_IMAGE"
  attachment=$("${CURL[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -H 'X-Request-Id: smoke-attachment-001' \
    -X POST "$BASE_URL/api/v1/attachments?ownerType=ITEM_DEFINITION&ownerId=$item&purpose=ITEM_IMAGE" \
    -F "file=@$TEMP_IMAGE;type=image/png" | jq -r .id)
  image_sha=$(shasum -a 256 "$TEMP_IMAGE" | awk '{print $1}')
  jq -n --arg item "$item" --arg entry "$entry" --arg attachment "$attachment" --arg imageSha "$image_sha" \
    '{itemId:$item,entryId:$entry,attachmentId:$attachment,imageSha:$imageSha}' >"$STATE_FILE"
}

verify() {
  printf 'inventory smoke: authenticate restored account\n'
  csrf
  post_json /api/v1/auth/login "{\"username\":\"restore-admin\",\"password\":\"$PASSWORD\"}" >/dev/null
  csrf
  item=$(jq -r .itemId "$STATE_FILE"); attachment=$(jq -r .attachmentId "$STATE_FILE"); expected=$(jq -r .imageSha "$STATE_FILE")
  printf 'inventory smoke: await catalog projection\n'
  await_json "$BASE_URL/api/v1/catalog/search?q=RESTORE-6901" '.items[0].id == $item' --arg item "$item"
  printf 'inventory smoke: verify inventory and reminders\n'
  "${CURL[@]}" "$BASE_URL/api/v1/inventory/entries?itemId=$item" | jq -e '.total >= 2' >/dev/null
  "${CURL[@]}" "$BASE_URL/api/v1/reminders" | jq -e 'type == "array" or has("content")' >/dev/null
  printf 'inventory smoke: verify attachment bytes\n'
  downloaded=$(mktemp); "${CURL[@]}" "$BASE_URL/api/v1/attachments/$attachment/content" >"$downloaded"
  actual=$(shasum -a 256 "$downloaded" | awk '{print $1}'); rm -f "$downloaded"
  [[ "$actual" == "$expected" ]]
  printf 'inventory smoke: await audit projection\n'
  await_json "$BASE_URL/api/v1/admin/audit-logs?requestId=smoke-attachment-001" \
    '.items[0].eventType == "AttachmentUploaded"'
  printf 'inventory smoke: run reconciliation\n'
  "${CURL[@]}" -H "X-XSRF-TOKEN: $CSRF_TOKEN" -X POST "$BASE_URL/api/v1/admin/inventory/reconcile" >/dev/null
  printf 'inventory smoke verification passed\n'
}

case "$MODE" in seed) seed ;; verify) verify ;; *) printf 'usage: %s seed|verify\n' "$0" >&2; exit 2 ;; esac

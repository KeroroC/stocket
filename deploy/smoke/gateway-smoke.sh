#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
MODE=${1:-}

fail() {
  printf 'gateway-smoke: %s\n' "$1" >&2
  exit 1
}

static_check() {
  local production_file=${1:-"$ROOT/deploy/compose.production.yml"}
  local rendered
  rendered=$(mktemp)
  trap 'rm -f "$rendered"' RETURN
  docker compose --env-file "$ROOT/.env.example" \
    -f "$ROOT/deploy/compose.yml" -f "$production_file" config --format json >"$rendered"
  python3 - "$rendered" "$ROOT" <<'PY'
import json
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text())
root = pathlib.Path(sys.argv[2])
services = document.get("services", {})
required = {"postgres", "app", "gateway"}
missing = required - services.keys()
assert not missing, f"missing services: {sorted(missing)}"

for name in required:
    service = services[name]
    assert service.get("read_only") is True, f"{name}: read_only must be true"
    assert "ALL" in service.get("cap_drop", []), f"{name}: cap_drop ALL missing"
    assert "no-new-privileges:true" in service.get("security_opt", []), f"{name}: no-new-privileges missing"
    user = str(service.get("user", ""))
    assert user and user not in {"0", "0:0", "root"}, f"{name}: non-root user required"
    assert service.get("healthcheck"), f"{name}: healthcheck missing"
    limits = service.get("deploy", {}).get("resources", {}).get("limits", {})
    assert limits.get("memory") and limits.get("cpus"), f"{name}: resource limits missing"
    logging = service.get("logging", {})
    assert logging.get("driver") == "json-file", f"{name}: json-file logging required"
    options = logging.get("options", {})
    assert options.get("max-size") and options.get("max-file"), f"{name}: log rotation missing"
    image = service.get("image")
    if image:
        assert not image.endswith(":latest"), f"{name}: latest image forbidden"

assert not services["postgres"].get("ports"), "postgres must not publish ports"
assert not services["app"].get("ports"), "app must not publish ports"
gateway_ports = services["gateway"].get("ports", [])
published = {str(port.get("published")) for port in gateway_ports}
assert published == {"80", "443"}, f"gateway must publish only 80 and 443, got {published}"

gateway_volumes = services["gateway"].get("volumes", [])
tls_targets = {volume.get("target") for volume in gateway_volumes if volume.get("read_only")}
assert "/run/tls/tls.crt" in tls_targets and "/run/tls/tls.key" in tls_targets, "read-only TLS mounts missing"

for dockerfile in (root / "deploy/app/Dockerfile", root / "deploy/frontend/Dockerfile"):
    text = dockerfile.read_text()
    lowered = text.lower()
    assert "tls.key" not in lowered and "private.key" not in lowered, f"{dockerfile}: TLS key referenced by build"

gateway = (root / "deploy/gateway/default.conf").read_text()
proxy_headers = (root / "deploy/gateway/proxy-headers.conf").read_text()
assert "map $http_x_request_id $stocket_request_id" in gateway, "validated request-id map missing"
assert "X-Request-Id $stocket_request_id" in proxy_headers, "gateway must preserve valid request ids"
PY
}

runtime_check() {
  local base_url=${STOCKET_SMOKE_BASE_URL:-https://localhost}
  curl --fail --silent --show-error --insecure --head "http://localhost/" | grep -qi '^location: https://' \
    || fail "HTTP does not redirect to HTTPS"
  local headers
  headers=$(mktemp)
  trap 'rm -f "$headers"' RETURN
  curl --fail --silent --show-error --insecure -D "$headers" -o /dev/null "$base_url/"
  grep -qi '^strict-transport-security:' "$headers" || fail "HSTS missing"
  grep -qi '^content-security-policy:' "$headers" || fail "CSP missing"
  grep -qi '^x-content-type-options: nosniff' "$headers" || fail "nosniff missing"
  curl --fail --silent --show-error --insecure "$base_url/api/v1/system" >/dev/null \
    || fail "system API unavailable through gateway"
}

case "$MODE" in
  --static) static_check "${2:-}" ;;
  --runtime) runtime_check ;;
  *) fail "usage: $0 --static [compose.production.yml] | --runtime" ;;
esac

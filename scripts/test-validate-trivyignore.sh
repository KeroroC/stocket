#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/stocket-trivyignore-test.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

run_validator() {
  python3 "$ROOT/scripts/validate-trivyignore.py" --today 2026-07-14 "$1"
}

cat >"$TMP/valid" <<'EOF'
# impact: vulnerable parser is not reachable from uploaded content
# mitigation: attachment validation rejects the affected format
# owner: security-team
# expires: 2026-08-13
CVE-2026-12345
EOF
run_validator "$TMP/valid"

cat >"$TMP/expired" <<'EOF'
# impact: test
# mitigation: test
# owner: security-team
# expires: 2026-07-13
CVE-2026-12346
EOF
if run_validator "$TMP/expired"; then
  printf 'expired waiver was accepted\n' >&2
  exit 1
fi

cat >"$TMP/too-long" <<'EOF'
# impact: test
# mitigation: test
# owner: security-team
# expires: 2026-08-14
CVE-2026-12347
EOF
if run_validator "$TMP/too-long"; then
  printf 'waiver longer than 30 days was accepted\n' >&2
  exit 1
fi

cat >"$TMP/missing-owner" <<'EOF'
# impact: test
# mitigation: test
# expires: 2026-07-20
CVE-2026-12348
EOF
if run_validator "$TMP/missing-owner"; then
  printf 'waiver without owner was accepted\n' >&2
  exit 1
fi

printf 'trivyignore validator tests passed\n'

#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/stocket-release-test.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

fixture() {
  local dir=$1
  mkdir -p "$dir"
  printf 'amd64-binary' >"$dir/stocket-linux-amd64"
  printf 'arm64-binary' >"$dir/stocket-linux-arm64"
  printf 'sha256:%064d\n' 1 >"$dir/stocket-app-amd64.digest"
  printf 'sha256:%064d\n' 2 >"$dir/stocket-app-arm64.digest"
  printf 'sha256:%064d\n' 3 >"$dir/stocket-app-multiarch.digest"
  printf '{"spdxVersion":"SPDX-2.3"}\n' >"$dir/stocket-app.spdx.json"
  printf '{"predicateType":"https://slsa.dev/provenance/v1"}\n' >"$dir/provenance.json"
  printf '{"nativeTests":"passed","smoke":"passed"}\n' >"$dir/test-summary.json"
  printf '{"SchemaVersion":2,"Results":[]}\n' >"$dir/trivy-release.json"
  "$ROOT/deploy/release/generate-manifest.sh" "$dir" v0.1.0 \
    0123456789abcdef0123456789abcdef01234567 2026-07-14T12:00:00Z ghcr.io/example/stocket true
}

mkdir -p "$TMP/bin"
cat >"$TMP/bin/cosign" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$TMP/bin/cosign"

fixture "$TMP/valid"
PATH="$TMP/bin:$PATH" TRIVYIGNORE_FILE="$ROOT/.trivyignore" \
  "$ROOT/deploy/release/verify-release.sh" "$TMP/valid" v0.1.0

cp -R "$TMP/valid" "$TMP/tampered"
printf 'tampered' >>"$TMP/tampered/stocket-linux-amd64"
if PATH="$TMP/bin:$PATH" "$ROOT/deploy/release/verify-release.sh" "$TMP/tampered" v0.1.0; then
  printf 'tampered binary was accepted\n' >&2
  exit 1
fi

cp -R "$TMP/valid" "$TMP/missing-arm64"
rm "$TMP/missing-arm64/stocket-linux-arm64"
if PATH="$TMP/bin:$PATH" "$ROOT/deploy/release/verify-release.sh" "$TMP/missing-arm64" v0.1.0; then
  printf 'release without ARM64 was accepted\n' >&2
  exit 1
fi

cp -R "$TMP/valid" "$TMP/wrong-digest"
jq '.architectures[1].imageDigest="sha256:bad"' "$TMP/wrong-digest/release-manifest.json" \
  >"$TMP/wrong-digest/manifest.tmp"
mv "$TMP/wrong-digest/manifest.tmp" "$TMP/wrong-digest/release-manifest.json"
(cd "$TMP/wrong-digest" && sha256sum release-manifest.json stocket-linux-amd64 stocket-linux-arm64 \
  stocket-app-amd64.digest stocket-app-arm64.digest stocket-app.spdx.json provenance.json \
  stocket-app-multiarch.digest test-summary.json trivy-release.json >SHA256SUMS)
if PATH="$TMP/bin:$PATH" "$ROOT/deploy/release/verify-release.sh" "$TMP/wrong-digest" v0.1.0; then
  printf 'invalid image digest was accepted\n' >&2
  exit 1
fi

cat >"$TMP/expired.trivyignore" <<'EOF'
# impact: test
# mitigation: test
# owner: security-team
# expires: 2026-01-01
CVE-2026-99999
EOF
if PATH="$TMP/bin:$PATH" TRIVYIGNORE_FILE="$TMP/expired.trivyignore" \
  "$ROOT/deploy/release/verify-release.sh" "$TMP/valid" v0.1.0; then
  printf 'release with expired vulnerability waiver was accepted\n' >&2
  exit 1
fi

printf 'release tool tests passed\n'

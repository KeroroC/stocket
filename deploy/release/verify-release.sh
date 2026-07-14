#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RELEASE_DIR=${1:?usage: verify-release.sh DIR EXPECTED_TAG}
EXPECTED_TAG=${2:?expected tag is required}
MANIFEST="$RELEASE_DIR/release-manifest.json"
TRIVYIGNORE_FILE=${TRIVYIGNORE_FILE:-$ROOT/.trivyignore}

python3 "$ROOT/scripts/validate-trivyignore.py" "$TRIVYIGNORE_FILE"
[[ "$EXPECTED_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { printf 'invalid expected tag\n' >&2; exit 1; }
[[ -f "$MANIFEST" && -f "$RELEASE_DIR/SHA256SUMS" ]] || { printf 'manifest or checksums missing\n' >&2; exit 1; }

required=(
  release-manifest.json stocket-app-multiarch.digest
  stocket-app.spdx.json provenance.json test-summary.json trivy-release.json
)
for file in "${required[@]}"; do
  [[ -f "$RELEASE_DIR/$file" ]] || { printf 'missing release file: %s\n' "$file" >&2; exit 1; }
  awk '{print $2}' "$RELEASE_DIR/SHA256SUMS" | grep -Fxq "$file" || {
    printf 'checksum entry missing: %s\n' "$file" >&2; exit 1;
  }
done
(cd "$RELEASE_DIR" && sha256sum --check --strict SHA256SUMS >/dev/null)

version=${EXPECTED_TAG#v}
jq -e --arg tag "$EXPECTED_TAG" --arg version "$version" '
  .schemaVersion == 1 and .tag == $tag and .version == $version and .sourceClean == true and
  (.commit | test("^[0-9a-f]{40}$")) and
  (.builtAt | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T.*Z$")) and
  (.imageRepository | type == "string" and length > 0) and
  (.multiArchImageDigest | test("^sha256:[0-9a-f]{64}$")) and
  (.platforms | sort) == ["linux/amd64","linux/arm64"] and
  .sbom == "stocket-app.spdx.json" and .vulnerabilityReport == "trivy-release.json" and
  .provenance == "provenance.json" and
  .testSummary == "test-summary.json"
' "$MANIFEST" >/dev/null
jq -e '.spdxVersion | startswith("SPDX-")' "$RELEASE_DIR/stocket-app.spdx.json" >/dev/null
jq -e 'type == "object"' "$RELEASE_DIR/provenance.json" "$RELEASE_DIR/test-summary.json" >/dev/null
jq -e 'type == "object"' "$RELEASE_DIR/trivy-release.json" >/dev/null

repository=$(jq -r .imageRepository "$MANIFEST")
command -v cosign >/dev/null || { printf 'cosign is required for release verification\n' >&2; exit 1; }
issuer=${COSIGN_CERTIFICATE_OIDC_ISSUER:-https://token.actions.githubusercontent.com}
identity=${COSIGN_CERTIFICATE_IDENTITY_REGEXP:-'^https://github.com/.+/.github/workflows/release\.yml@refs/tags/v[0-9]+\.[0-9]+\.[0-9]+$'}
while read -r digest; do
  cosign verify --certificate-identity-regexp "$identity" --certificate-oidc-issuer "$issuer" \
    "$repository@$digest" >/dev/null
  cosign verify-attestation --type spdxjson --certificate-identity-regexp "$identity" \
    --certificate-oidc-issuer "$issuer" "$repository@$digest" >/dev/null
done < <(jq -r '.multiArchImageDigest' "$MANIFEST")

printf 'release verification passed: %s\n' "$EXPECTED_TAG"

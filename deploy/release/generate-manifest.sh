#!/usr/bin/env bash
set -euo pipefail

RELEASE_DIR=${1:?usage: generate-manifest.sh DIR TAG COMMIT BUILT_AT IMAGE_REPOSITORY SOURCE_CLEAN}
TAG=${2:?tag is required}
COMMIT=${3:?commit is required}
BUILT_AT=${4:?built-at is required}
IMAGE_REPOSITORY=${5:?image repository is required}
SOURCE_CLEAN=${6:?source-clean flag is required}

[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { printf 'invalid release tag: %s\n' "$TAG" >&2; exit 1; }
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { printf 'invalid commit SHA: %s\n' "$COMMIT" >&2; exit 1; }
[[ "$BUILT_AT" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T.*Z$ ]] || { printf 'invalid UTC build time: %s\n' "$BUILT_AT" >&2; exit 1; }
[[ "$SOURCE_CLEAN" == "true" ]] || { printf 'release source must be clean\n' >&2; exit 1; }
[[ "$IMAGE_REPOSITORY" != *@* && "$IMAGE_REPOSITORY" != *:* ]] || {
  printf 'image repository must not include tag or digest: %s\n' "$IMAGE_REPOSITORY" >&2; exit 1;
}

VERSION=${TAG#v}
required=(
  stocket-linux-amd64 stocket-linux-arm64
  stocket-app-amd64.digest stocket-app-arm64.digest stocket-app-multiarch.digest
  stocket-app.spdx.json provenance.json test-summary.json trivy-release.json
)
for file in "${required[@]}"; do
  [[ -f "$RELEASE_DIR/$file" ]] || { printf 'missing release input: %s\n' "$file" >&2; exit 1; }
done

hash_file() { sha256sum "$1" | awk '{print $1}'; }
amd64_sha=$(hash_file "$RELEASE_DIR/stocket-linux-amd64")
arm64_sha=$(hash_file "$RELEASE_DIR/stocket-linux-arm64")
amd64_digest=$(tr -d '[:space:]' <"$RELEASE_DIR/stocket-app-amd64.digest")
arm64_digest=$(tr -d '[:space:]' <"$RELEASE_DIR/stocket-app-arm64.digest")
multiarch_digest=$(tr -d '[:space:]' <"$RELEASE_DIR/stocket-app-multiarch.digest")
for digest in "$amd64_digest" "$arm64_digest" "$multiarch_digest"; do
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { printf 'invalid image digest: %s\n' "$digest" >&2; exit 1; }
done

jq -n \
  --arg version "$VERSION" --arg tag "$TAG" --arg commit "$COMMIT" --arg builtAt "$BUILT_AT" \
  --arg repository "$IMAGE_REPOSITORY" --arg amd64Sha "$amd64_sha" --arg arm64Sha "$arm64_sha" \
  --arg amd64Digest "$amd64_digest" --arg arm64Digest "$arm64_digest" --arg multiDigest "$multiarch_digest" \
  '{schemaVersion:1,version:$version,tag:$tag,commit:$commit,builtAt:$builtAt,sourceClean:true,
    imageRepository:$repository,multiArchImageDigest:$multiDigest,
    architectures:[
      {name:"linux/amd64",binary:"stocket-linux-amd64",binarySha256:$amd64Sha,imageDigest:$amd64Digest},
      {name:"linux/arm64",binary:"stocket-linux-arm64",binarySha256:$arm64Sha,imageDigest:$arm64Digest}
    ],sbom:"stocket-app.spdx.json",vulnerabilityReport:"trivy-release.json",
    provenance:"provenance.json",testSummary:"test-summary.json"}' \
  >"$RELEASE_DIR/release-manifest.json"

(cd "$RELEASE_DIR" && sha256sum release-manifest.json "${required[@]}" >SHA256SUMS)
printf 'generated release manifest: %s\n' "$RELEASE_DIR/release-manifest.json"

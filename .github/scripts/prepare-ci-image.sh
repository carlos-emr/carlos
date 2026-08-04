#!/usr/bin/env bash
#
# Resolve a CARLOS CI image from immutable build inputs and verify its provenance before execution.

set -euo pipefail

if [ "$#" -ne 4 ]; then
    echo "Usage: $0 <image-repository> <local-tag> <context-path> <trusted-source-ref>" >&2
    exit 2
fi

image_repository=$1
local_tag=$2
context_path=$3
trusted_source_ref=$4

if [ -z "${GITHUB_OUTPUT:-}" ] || [ -z "${GITHUB_REPOSITORY:-}" ]; then
    echo "GITHUB_OUTPUT and GITHUB_REPOSITORY must be set" >&2
    exit 2
fi

content_hash=$(find "$context_path" \
    -path '*/db_data/*' -prune -o \
    -type f -exec sha256sum {} \; 2>/dev/null | sort | sha256sum | cut -d' ' -f1)
image_ref="${image_repository}:content-${content_hash}"

echo "content_hash=$content_hash" >> "$GITHUB_OUTPUT"
echo "image_ref=$image_ref" >> "$GITHUB_OUTPUT"
echo "Resolving content-addressed CI image: $image_ref"

pull_error=$(mktemp)
trap 'rm -f "$pull_error"' EXIT

if docker pull "$image_ref" 2>"$pull_error"; then
    existing_hash=$(docker inspect "$image_ref" \
        --format '{{ index .Config.Labels "org.openosp.content-hash" }}')
    if [ "$existing_hash" != "$content_hash" ]; then
        echo "CI image label mismatch: expected $content_hash, found ${existing_hash:-<none>}" >&2
        exit 1
    fi

    # Verify the exact manifest Docker pulled, not the tag as it currently resolves in the
    # registry. This closes the tag-mutation window between pull and provenance verification.
    pulled_digest=$(docker inspect "$image_ref" --format '{{ index .RepoDigests 0 }}')
    if [[ ! "$pulled_digest" =~ @sha256:[0-9a-f]{64}$ ]] || \
       [ "${pulled_digest%@*}" != "$image_repository" ]; then
        echo "Unable to resolve the pulled image digest: ${pulled_digest:-<none>}" >&2
        exit 1
    fi

    # Repository identity alone is not sufficient: constrain the attestation to the protected
    # container publishing workflow and the PR's base branch so a different workflow/ref cannot
    # bless an image under the expected content tag.
    gh attestation verify "oci://${pulled_digest}" \
        --repo "$GITHUB_REPOSITORY" \
        --signer-workflow "$GITHUB_REPOSITORY/.github/workflows/container-images.yml" \
        --source-ref "$trusted_source_ref"

    docker tag "$image_ref" "$local_tag"
    echo "pulled=true" >> "$GITHUB_OUTPUT"
    echo "reason=verified" >> "$GITHUB_OUTPUT"
    echo "Verified provenance and content label for $image_ref"
    exit 0
fi

if grep -qiE 'manifest unknown|not found|name unknown' "$pull_error"; then
    echo "No attested image exists for these build inputs; CI will build the ci target locally"
    echo "pulled=false" >> "$GITHUB_OUTPUT"
    echo "reason=not-found" >> "$GITHUB_OUTPUT"
    exit 0
fi

echo "Failed to pull $image_ref" >&2
sed -n '1,120p' "$pull_error" >&2
exit 1

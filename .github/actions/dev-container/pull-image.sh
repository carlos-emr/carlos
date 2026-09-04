#!/usr/bin/env bash
# Try to pull the pre-built CARLOS dev image and classify the result so the
# calling workflow can distinguish "not in the registry, build it locally" from
# "the registry is unreachable / access denied, fail the job".
#
# Inputs:  IMAGE (env)  -- fully qualified image reference to pull
# Outputs: pulled=true|false and reason=success|not-found|error, written to
#          $GITHUB_OUTPUT. Never exits non-zero: every outcome is reported
#          through the outputs and acted on by the next step.
#
# License:
# This file is part of the CARLOS EMR project and is subject to the licensing
# terms outlined in the repository's LICENSE file.
set -uo pipefail

: "${IMAGE:?IMAGE must be set to the image reference to pull}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT must point at the step output file}"

error_log=$(mktemp)
trap 'rm -f "$error_log"' EXIT

echo "Attempting to pull pre-built image from $IMAGE..."
# Capture stderr so we can distinguish "not found" from other errors
if docker pull "$IMAGE" 2>"$error_log"; then
  docker tag "$IMAGE" carlos-tomcat-dev
  echo "pulled=true" >> "$GITHUB_OUTPUT"
  echo "reason=success" >> "$GITHUB_OUTPUT"
  echo "Successfully pulled pre-built image!"
else
  echo "pulled=false" >> "$GITHUB_OUTPUT"
  if grep -qiE 'manifest unknown|not found' "$error_log"; then
    echo "reason=not-found" >> "$GITHUB_OUTPUT"
    echo "Pre-built image not available (not found in registry), will build locally"
  else
    echo "reason=error" >> "$GITHUB_OUTPUT"
    echo "Image pull failed due to an unexpected error:"
    cat "$error_log"
  fi
fi

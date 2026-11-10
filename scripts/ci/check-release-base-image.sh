#!/usr/bin/env bash
# Keep the release builder's JDK base aligned with the development Dockerfile.
# Run from the repository root. Licensed under the repository's LICENSE.
set -euo pipefail

# Both Dockerfiles must name the same digest-pinned base. `|| true` lets a
# missing pin reach the diagnostic below instead of failing the grep pipeline.
dev=$(grep -m1 -oE '^FROM [^ ]+@sha256:[0-9a-f]{64}' .devcontainer/development/Dockerfile | cut -d' ' -f2 || true)
rel=$(grep -m1 -oE '^FROM [^ ]+@sha256:[0-9a-f]{64}' .github/release-build/Dockerfile | cut -d' ' -f2 || true)
if [ -z "$dev" ] || [ -z "$rel" ] || [ "$dev" != "$rel" ]; then
  echo "::error::.github/release-build/Dockerfile base ($rel) does not match .devcontainer/development/Dockerfile base ($dev); update them together."
  exit 1
fi
echo "Release build base image: $rel"

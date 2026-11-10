#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Shared, verified Flyway distribution for schema and OSCAR reference CI.
set -euo pipefail
destination="${1:?usage: fetch-flyway.sh DESTINATION [ARCHIVE]}"
# Keep aligned with flyway.version in pom.xml.
version=13.12.0
checksum=e332704f4bbb16baba4ac565a822ccb24354003a284deaa11668c1be77013c34
if [ -e "$destination" ] || [ -L "$destination" ]; then
  echo 'Flyway destination must be fresh; refusing stale CLI files' >&2
  exit 1
fi
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
archive="${2:-$scratch/flyway.tgz}"
if [ ! -f "$archive" ]; then
  # Refresh the release-asset redirect on each CI attempt while keeping the bytes pinned.
  curl -fsSL --retry 3 --retry-delay 2 --retry-max-time 180 \
    --connect-timeout 15 --max-time 120 \
    "https://github.com/flyway/flyway/releases/download/flyway-$version/flyway-commandline-$version-linux-x64.tar.gz?download=1&run=${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}" \
    -o "$scratch/flyway.tgz"
  # Verify before publishing to the shared archive cache.
  echo "$checksum  $scratch/flyway.tgz" | sha256sum -c - >&2
  if [ "$archive" != "$scratch/flyway.tgz" ]; then
    cp "$scratch/flyway.tgz" "$archive"
  fi
fi
# A cache hit is subject to the same checksum gate as a fresh download.
echo "$checksum  $archive" | sha256sum -c - >&2
mkdir -p "$destination"
tar xzf "$archive" --strip-components=1 -C "$destination"
test -x "$destination/flyway"

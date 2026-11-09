#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Download the shared CLI pin, verify it, and print only its version on stdout.
# Keep this pin aligned with flyway.version in pom.xml.
set -euo pipefail
archive=${1:?A Flyway archive destination is required}
version=13.12.0
sha256=e332704f4bbb16baba4ac565a822ccb24354003a284deaa11668c1be77013c34
curl -fsSL --retry 3 --retry-delay 2 --retry-max-time 180 \
  --connect-timeout 15 --max-time 120 \
  "https://github.com/flyway/flyway/releases/download/flyway-$version/flyway-commandline-$version-linux-x64.tar.gz" \
  -o "$archive"
printf '%s  %s\n' "$sha256" "$archive" | sha256sum -c - >&2
printf '%s\n' "$version"

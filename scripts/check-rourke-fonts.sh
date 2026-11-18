#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
# Kept for the existing CI step. The matrix now covers every report renderer and isolates the
# JDK's font cache per scenario; see scripts/check-report-fonts.sh (issue #5046).
set -euo pipefail
exec bash "$(dirname "$0")/check-report-fonts.sh" "$@"

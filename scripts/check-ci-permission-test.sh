#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Execute the real startup-refusal test without root's permission bypass.
set -euo pipefail
cd "$(dirname "$0")/.."
reports="$PWD/target/ci-permission-reports"
test ! -e "$reports" || { echo 'Permission reports must be fresh' >&2; exit 1; }
command=()
if [ "$(id -u)" = 0 ]; then
  # Remove Linux's DAC bypass for this process tree only. The test itself
  # checks Files.isWritable; the report gate refuses a skipped invocation.
  command=(setpriv --bounding-set=-dac_override,-dac_read_search)
fi
"${command[@]}" mvn -B -ntp surefire:test \
  '-Dtest=CarlosPropertiesModernChartPersistenceUnitTest#shouldRefuseCutover_whenUnsafeSourceIsReadOnly' \
  -Dtest.forkCount=1 -Dtest.reportsDirectory="$reports" \
  -Djacoco.destFile="$reports/jacoco.exec" "$@"

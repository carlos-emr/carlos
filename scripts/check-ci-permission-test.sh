#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Execute the real startup-refusal test without root's permission bypass.
set -euo pipefail
cd "$(dirname "$0")/.."
reports="${CARLOS_PERMISSION_REPORTS:-$PWD/target/ci-permission-reports}"
execution="${CARLOS_PERMISSION_EXECUTION:-$reports/jacoco.exec}"
coverage="${CARLOS_PERMISSION_COVERAGE:-$reports/coverage}"
for output in "$reports" "$execution" "$coverage"; do
  [[ "$output" = /* ]] && [ ! -e "$output" ] && [ ! -L "$output" ] \
    || { echo 'Permission outputs must be fresh absolute paths' >&2; exit 1; }
done
command=()
if [ "$(id -u)" = 0 ]; then
  # Remove Linux's DAC bypass for this process tree only. The test itself
  # checks Files.isWritable; the report gate refuses a skipped invocation.
  command=(setpriv --bounding-set=-dac_override,-dac_read_search)
fi
selector=$(python3 - <<'SELECTOR'
import json
from pathlib import Path
cases = json.loads(Path('scripts/ci/permission-cases.json').read_text())
print(','.join(row['classname'] + '#' + row['name'] for row in cases))
SELECTOR
)
# The source-bound verifier clears compiled classes before each Java lane.
# Compile before dropping DAC bypass; run only the refusal cases afterward.
mvn -B -ntp test-compile "$@"
# Keep an empty prefix safe under Bash <4.4 with nounset.
${command[@]+"${command[@]}"} mvn -B -ntp jacoco:prepare-agent surefire:test jacoco:report \
  "-Dtest=$selector" \
  -Dtest.forkCount=1 -Dtest.reportsDirectory="$reports" \
  -Djacoco.destFile="$execution" -Djacoco.dataFile="$execution" \
  -Dtest.coverageDirectory="$coverage" "$@"

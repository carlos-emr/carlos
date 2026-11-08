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
selector=$(python3 - <<'SELECTOR'
import json
from pathlib import Path
cases = json.loads(Path('scripts/ci/permission-cases.json').read_text())
print(','.join(row['classname'] + '#' + row['name'] for row in cases))
SELECTOR
)
# This dedicated Surefire goal requires compiled test classes from the prior
# full Maven build. Keep an empty prefix safe under Bash <4.4 with nounset.
${command[@]+"${command[@]}"} mvn -B -ntp surefire:test \
  "-Dtest=$selector" \
  -Dtest.forkCount=1 -Dtest.reportsDirectory="$reports" \
  -Djacoco.destFile="$reports/jacoco.exec" "$@"

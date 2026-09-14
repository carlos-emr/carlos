#!/usr/bin/env bash
#
# Test script for the SQL safety hook.
# Sends simulated Edit/Write JSON payloads and verifies exit codes.
#
# Usage: bash .claude/hooks/test-sql-safety-hook.sh
#
# Exit codes from hook:
#   0 = allowed (safe or not applicable)
#   2 = blocked (unsafe SQL detected)
#

set -euo pipefail

HOOK="$(cd "$(dirname "$0")" && pwd)/validate-sql-safety.py"
PASS=0
FAIL=0
TOTAL=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

run_test() {
    local description="$1"
    local expected_exit="$2"
    local json_payload="$3"
    TOTAL=$((TOTAL + 1))

    # Run the hook, capture exit code and stderr output for diagnostics
    set +e
    local hook_output
    hook_output=$(echo "$json_payload" | python3 "$HOOK" 2>&1)
    actual_exit=$?
    set -e

    if [ "$actual_exit" -eq "$expected_exit" ]; then
        echo -e "  ${GREEN}PASS${NC}: $description (exit=$actual_exit)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC}: $description (expected=$expected_exit, got=$actual_exit)"
        if [ -n "$hook_output" ]; then
            echo -e "    Hook output:"
            echo "$hook_output" | sed 's/^/      /'
        fi
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "============================================"
echo " SQL Safety Hook Test Suite"
echo "============================================"
echo ""

FIXTURES="$(cd "$(dirname "$0")" && pwd)/test-sql-safety-fixtures.json"
current_section=""

# The Python fixture emitter below uses tab-delimited fields for Bash.
# Keep emitted section, description, expected_exit, and JSON payload fields single-line and tab-free.
while IFS=$'	' read -r section description expected_exit json_payload; do
    if [ "$section" != "$current_section" ]; then
        if [ -n "$current_section" ]; then
            echo ""
        fi
        echo -e "${YELLOW}--- $section ---${NC}"
        current_section="$section"
    fi
    run_test "$description" "$expected_exit" "$json_payload"
done < <(python3 - "$FIXTURES" <<'PYFIXTURES'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    cases = json.load(handle)

for case in cases:
    payload = json.dumps(case["payload"], separators=(",", ":"))
    print("	".join((case["section"], case["description"], str(case["expected_exit"]), payload)))
PYFIXTURES
)

echo ""

# ----------------------------------------------------------------
# Summary
# ----------------------------------------------------------------
echo "============================================"
echo " RESULTS: $PASS passed, $FAIL failed (out of $TOTAL)"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi

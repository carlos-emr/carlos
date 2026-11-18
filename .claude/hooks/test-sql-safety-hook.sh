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
fixture_dir=$(mktemp -d)
trap 'rm -rf -- "$fixture_dir"' EXIT

# Materialize the tab-delimited payloads first. A Python failure inside process
# substitution would be hidden from set -e and could silently skip the fixtures.
python3 - "$FIXTURES" > "$fixture_dir/cases.tsv" <<'PYFIXTURES'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    cases = json.load(handle)
if not isinstance(cases, list) or not cases:
    raise ValueError("SQL safety fixtures must be a nonempty list")
for case in cases:
    for field in ("section", "description"):
        if not isinstance(case[field], str) or not case[field] or any(
            c in case[field] for c in "\t\r\n"
        ):
            raise ValueError(f"Invalid fixture field: {field}")
    if type(case["expected_exit"]) is not int or case["expected_exit"] not in (0, 2):
        raise ValueError("Invalid expected_exit")
    payload = json.dumps(case["payload"], separators=(",", ":"))
    print("\t".join((case["section"], case["description"], str(case["expected_exit"]), payload)))
PYFIXTURES
expected_total=$(wc -l < "$fixture_dir/cases.tsv")
while IFS=$'\t' read -r section description expected_exit json_payload; do
    if [ "$section" != "$current_section" ]; then
        if [ -n "$current_section" ]; then
            echo ""
        fi
        echo -e "${YELLOW}--- $section ---${NC}"
        current_section="$section"
    fi
    run_test "$description" "$expected_exit" "$json_payload"
done < "$fixture_dir/cases.tsv"

# Reconstruct real files: prose must allow unrelated edits, but pre-existing
# unsafe SQL must still block them, proving the whole file was actually read.
python3 - "$fixture_dir" <<'PYEDIT'
import sys
from pathlib import Path

content = '''class EmailManager {
    void check() {
        throw new IllegalStateException("Invalid email transport status transition from "
                + previousStatus + " to " + emailStatus);
    }
    int retries = 1;
}
'''
Path(sys.argv[1], "EmailManager.java").write_text(content, encoding="utf-8")
Path(sys.argv[1], "UnsafeQuery.java").write_text(content.replace(
    "int retries = 1;", 'int retries = 1; String sql = "WHERE id = " + userId;'
), encoding="utf-8")
PYEDIT
python3 - "$fixture_dir" > "$fixture_dir/edits.tsv" <<'PYEDIT'
import json
import sys
from pathlib import Path

for tool in ("Edit", "MultiEdit"):
    for file, expected_exit, replacement in (
        ("EmailManager.java", 0, "int retries = 2;"),
        ("EmailManager.java", 2, 'String sql = "SELECT * FROM users WHERE id = " + userId;'),  # nosemgrep: skills.code-injection.skill-sql-string-formatting.skill-sql-string-formatting -- Intentionally unsafe Java fixture sent only to the hook; never executed as SQL.
        ("UnsafeQuery.java", 2, "int retries = 2;"),
    ):
        edit = {"old_string": "int retries = 1;", "new_string": replacement}
        tool_input = {"file_path": str(Path(sys.argv[1], file))}
        tool_input.update(edit if tool == "Edit" else {"edits": [edit]})
        payload = {"tool_name": tool, "tool_input": tool_input}
        description = f"{tool} reconstructs {file} (expected {expected_exit})"
        print("\t".join((description, str(expected_exit), json.dumps(payload))))
PYEDIT
expected_total=$((expected_total + 6))
while IFS=$'\t' read -r description expected_exit json_payload; do
    run_test "$description" "$expected_exit" "$json_payload"
done < "$fixture_dir/edits.tsv"

if [ "$TOTAL" -ne "$expected_total" ]; then
    echo "Expected $expected_total checks, ran $TOTAL" >&2
    exit 1
fi

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

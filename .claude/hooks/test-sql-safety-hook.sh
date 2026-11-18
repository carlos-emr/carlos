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
    local warning="${4:-optional}"
    TOTAL=$((TOTAL + 1))

    # Run the hook, capture exit code and stderr output for diagnostics
    set +e
    local hook_output
    hook_output=$(echo "$json_payload" | python3 "$HOOK" 2>&1)
    actual_exit=$?
    set -e

    if [ "$actual_exit" -eq "$expected_exit" ] && { [ "$warning" != required ] || [[ "$hook_output" == *"WARNING:"* ]]; }; then
        echo -e "  ${GREEN}PASS${NC}: $description (exit=$actual_exit)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC}: $description (expected=$expected_exit, got=$actual_exit)"
        if [ "$warning" = required ] && [[ "$hook_output" != *"WARNING:"* ]]; then
            echo "    Required pre-existing-finding warning was absent."
        fi
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

# Reconstruct real files: new/modified unsafe SQL blocks; unchanged legacy SQL
# is explicitly warned without blocking unrelated edits.
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
        ("UnsafeQuery.java", 0, "int retries = 2;"),
    ):
        edit = {"old_string": "int retries = 1;", "new_string": replacement}
        tool_input = {"file_path": str(Path(sys.argv[1], file))}
        tool_input.update(edit if tool == "Edit" else {"edits": [edit]})
        payload = {"tool_name": tool, "tool_input": tool_input}
        description = f"{tool} reconstructs {file} (expected {expected_exit})"
        print("\t".join((description, str(expected_exit), json.dumps(payload), "required" if file == "UnsafeQuery.java" and expected_exit == 0 else "optional")))

# Statement identity must cover more than the short diagnostic excerpt. These
# source files are synthetic and only sent to the hook, never compiled/executed.
root = Path(sys.argv[1])
sql = 'String sql = "WHERE id = " + userId;'
legacy = 'class Legacy {\n// original comment\nvoid run(String userId) {\n' + sql + '\n}\nvoid other(String userId) { int clean = 1; }\nint retries = 1;\n}\n'
cases = []
def case(name, expected, edits, *, tool="Edit", content=legacy, warning="optional", replacement=None):
    filename = root / ("Legacy" + str(len(cases)) + ".java")
    filename.write_text(content, encoding="utf-8")
    payload = {"tool_name": tool, "tool_input": {"file_path": str(filename)}}
    if tool == "Write":
        payload["tool_input"]["content"] = content if replacement is None else replacement
    elif tool == "MultiEdit":
        payload["tool_input"]["edits"] = edits
    else:
        payload["tool_input"].update(edits[0])
    cases.append((name, expected, payload, warning))

case("legacy comment-only edit warns without blocking", 0,
     [{"old_string": "original comment", "new_string": "updated comment"}], warning="required")
case("unrelated field edit warns without blocking", 0,
     [{"old_string": "int retries = 1;", "new_string": "int retries = 2;"}], warning="required")
case("line-number shifts do not create SQL findings", 0,
     [{"old_string": "// original comment", "new_string": "// two\n// comment lines"}], warning="required")
case("inline Java comment is not a SQL change", 0,
     [{"old_string": "+ userId;", "new_string": "+ /* explanation */ userId;"}], warning="required")
case("Java layout is not a SQL change", 0,
     [{"old_string": sql, "new_string": 'String sql="WHERE id = "+userId;'}], warning="required")
case("changed raw operand still blocks", 2,
     [{"old_string": "+ userId;", "new_string": "+ request.getParameter(\"id\");"}])
case("changed SQL literal still blocks", 2,
     [{"old_string": "WHERE id = ", "new_string": "WHERE id  = "}])
case("copying identical unsafe SQL still blocks", 2,
     [{"old_string": sql, "new_string": sql + "\n" + sql}], warning="required")
case("moving SQL into another method still blocks", 2,
     [{"old_string": sql, "new_string": ""}, {"old_string": "int clean = 1;", "new_string": sql}], tool="MultiEdit")
case("removing unsafe SQL is allowed", 0,
     [{"old_string": sql, "new_string": ""}])
case("parameterizing unsafe SQL is allowed", 0,
     [{"old_string": sql, "new_string": 'String sql = "WHERE id = ?";'}])
case("Write still scans the complete file", 2, [], tool="Write")
case("mismatched edit cannot hide new unsafe SQL", 2,
     [{"old_string": "does not exist", "new_string": sql}], warning="required")
case("MultiEdit unrelated changes warn without blocking", 0,
     [{"old_string": "original comment", "new_string": "changed"},
      {"old_string": "int retries = 1;", "new_string": "int retries = 2;"}], tool="MultiEdit", warning="required")
introduced = 'String additional = "WHERE id = " + otherId;'
case("MultiEdit is judged by its complete final content", 0,
     [{"old_string": "int retries = 1;", "new_string": introduced},
      {"old_string": introduced, "new_string": "int retries = 2;"}], tool="MultiEdit", warning="required")
case("new unsafe SQL cannot borrow unchanged legacy findings", 2,
     [{"old_string": "int retries = 1;", "new_string": introduced}], warning="required")
case("replace_all cannot hide duplicated unsafe SQL", 2,
     [{"old_string": "int clean = 1;", "new_string": sql + sql, "replace_all": True}], warning="required")
long_sql = 'String sql = "WHERE id = " + userId + "' + 'x' * 100 + '";'
case("a change beyond the diagnostic excerpt still blocks", 2,
     [{"old_string": 'x' * 100, "new_string": 'x' * 99 + 'y'}], content=legacy.replace(sql, long_sql))
case("a new enclosing condition is still a changed finding", 2,
     [{"old_string": sql, "new_string": "if (allowed) { " + sql + " }"}])
case("unchanged repeated legacy statements retain multiplicity", 0,
     [{"old_string": "original comment", "new_string": "updated"}],
     content=legacy.replace(sql, sql + "\n" + sql), warning="required")

# Review regressions: these strings are fixture data, never executed as SQL.
lambda_legacy = '''class Legacy {
  void run(String userId) {
    run(() -> {
      int n = 1;
      if (ready) {
        String sql = "WHERE id = '" + userId + "'";
      }
    });
  }
}
'''
case("unrelated lambda statement does not change the SQL scope", 0,
     [{"old_string": "int n = 1;", "new_string": "int n = 2;"}],
     content=lambda_legacy, warning="required")
case("changed lambda condition still blocks", 2,
     [{"old_string": "if (ready)", "new_string": "if (allowed)"}],
     content=lambda_legacy)
case("changed raw operand in a lambda still blocks", 2,
     [{"old_string": "+ userId +", "new_string": "+ otherId +"}],
     content=lambda_legacy)
for_legacy = lambda_legacy.replace("if (ready)", "for (int i = 0; i < limit; i++)")
case("changed for initializer remains part of the SQL scope", 2,
     [{"old_string": "int i = 0", "new_string": "int i = 1"}],
     content=for_legacy)
case("changed for condition remains part of the SQL scope", 2,
     [{"old_string": "i < limit", "new_string": "i < otherLimit"}],
     content=for_legacy)
text_block = '''class Legacy {
  // original comment
  void run(String name) {
    String sql = """
      SELECT * FROM t WHERE marker = '"' AND name = '%s'
      """.formatted(name);
  }
}
'''
case("changed embedded quote in a SQL text block still blocks", 2,
     [{"old_string": """marker = '"'""", "new_string": "marker = ' '"}],
     content=text_block)
case("changed literal whitespace in a SQL text block still blocks", 2,
     [{"old_string": "SELECT * FROM", "new_string": "SELECT  * FROM"}],
     content=text_block)
case("unrelated comment around a SQL text block warns without blocking", 0,
     [{"old_string": "original comment", "new_string": "updated comment"}],
     content=text_block, warning="required")
case("Write detects a parenthesized raw SQL predicate operand", 2, [], tool="Write",
     content='String filter = "lower(last_name) = \'" + (lastName) + "\'";')
case("Write allows a parenthesized constant SQL operand", 0, [], tool="Write",
     content='String filter = "lower(last_name) = \'" + ("Smith") + "\'";')
case("Write allows parenthesized prose concatenation", 0, [], tool="Write",
     content='String message = "Message from " + (senderName);')
case("Write allows a parenthesized SQL placeholder counter", 0, [], tool="Write",
     content='String sql = "SELECT * FROM t WHERE id = ?" + (paramIndex++);')

for name, expected, payload, warning in cases:
    print("\t".join((name, str(expected), json.dumps(payload), warning)))
PYEDIT
expected_total=$((expected_total + $(wc -l < "$fixture_dir/edits.tsv")))
while IFS=$'\t' read -r description expected_exit json_payload warning; do
    run_test "$description" "$expected_exit" "$json_payload" "$warning"
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

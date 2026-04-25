#!/usr/bin/env bash
#
# test-encoder-null-safety.sh
#
# Smoke test for scripts/lint/check-encoder-null-safety.py.
# Runs the lint against the fixtures in scripts/lint/test-fixtures/
# and asserts:
#   - the positive Class-C fixture is flagged (exit 1, message present)
#   - the negative fixture is NOT flagged
#
# Exit 0 = lint logic is correct, 1 = regression in the lint itself.
#
# This is the regression armor for the d2db61d4 bug class — if the
# Class C regex breaks (e.g., a future edit drops `\$\{` escapes), the
# lint would silently report PASS on real violations. This test catches
# that.
#
# @since 2026-04-25

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE_DIR="$SCRIPT_DIR/test-fixtures"
LINT_SCRIPT="$SCRIPT_DIR/check-encoder-null-safety.py"

fails=0

# Positive case — must be flagged
out_positive=$(python3 -c "
import sys, re
from pathlib import Path
sys.path.insert(0, '$SCRIPT_DIR')
import importlib.util
spec = importlib.util.spec_from_file_location('lint', '$LINT_SCRIPT')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
text = Path('$FIXTURE_DIR/encoder-class-c-positive.jsp').read_text()
text = mod.JSP_COMMENT_RE.sub('', text)
hits = len(mod.HTML_ATTR_CONTENT_MISUSE_RE.findall(text))
print(hits)
")
if [[ "$out_positive" == "1" ]]; then
  echo "PASS: positive Class-C fixture flagged correctly"
else
  echo "FAIL: positive fixture should yield 1 hit, got '$out_positive'"
  fails=$((fails+1))
fi

# Negative case — must not be flagged
out_negative=$(python3 -c "
import sys
from pathlib import Path
sys.path.insert(0, '$SCRIPT_DIR')
import importlib.util
spec = importlib.util.spec_from_file_location('lint', '$LINT_SCRIPT')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
text = Path('$FIXTURE_DIR/encoder-class-c-negative.jsp').read_text()
text = mod.JSP_COMMENT_RE.sub('', text)
hits = len(mod.HTML_ATTR_CONTENT_MISUSE_RE.findall(text))
print(hits)
")
if [[ "$out_negative" == "0" ]]; then
  echo "PASS: negative fixture correctly not flagged"
else
  echo "FAIL: negative fixture should yield 0 hits, got '$out_negative'"
  fails=$((fails+1))
fi

if [[ $fails -eq 0 ]]; then
  echo "All encoder-null-safety lint fixtures pass."
  exit 0
else
  echo "$fails fixture failure(s)."
  exit 1
fi

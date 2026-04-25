#!/usr/bin/env bash
#
# test-encoder-null-safety.sh
#
# Smoke test for scripts/lint/check-encoder-null-safety.py.
#
# Invokes the lint script as an actual subprocess (not via importlib) so a
# regression in argv handling, main() flow, or exit-code logic gets caught.
#
# Tests two fixtures:
#   - encoder-class-c-positive.jsp  : MUST be flagged (lint exits non-zero)
#   - encoder-class-c-negative.jsp  : MUST NOT be flagged (lint exits zero)
#
# Each fixture is staged into a fresh temp `src/main/webapp/WEB-INF/...`
# layout so the lint's hard-coded JSP_ROOT walk picks it up. We can't
# point the lint at a different root without forking the script, but
# staging fixtures into a temp tree lets us exercise the real entrypoint.
#
# Exit 0 = lint behavior is correct, 1 = regression in the lint itself.
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

stage_and_run() {
  local fixture_name="$1"           # e.g. "encoder-class-c-positive.jsp"
  local expected_exit="$2"          # "0" or "1"
  local expected_stdout_match="$3"  # text that must appear in stdout (empty = no check)
  local label="$4"

  local stage_root
  stage_root="$(mktemp -d)"
  trap 'rm -rf "$stage_root"' RETURN

  # Replicate the lint's hard-coded REPO_ROOT/src/main/webapp layout so the
  # walk hits the fixture file. The lint also reads the allowlist relative
  # to its own directory, so we run with cwd somewhere harmless and the
  # default allowlist (we don't want to change CI behavior).
  local jsp_target="$stage_root/src/main/webapp/billingONFixture.jsp"
  mkdir -p "$(dirname "$jsp_target")"
  cp "$FIXTURE_DIR/$fixture_name" "$jsp_target"

  # The lint resolves REPO_ROOT relative to its own location:
  # `Path(__file__).resolve().parent.parent.parent`.
  # We need to invoke a copy of the lint inside the staged tree so its
  # parent-walking lands on $stage_root.
  mkdir -p "$stage_root/scripts/lint"
  cp "$LINT_SCRIPT" "$stage_root/scripts/lint/check-encoder-null-safety.py"
  # Empty allowlist so no fixture is silently exempted.
  : > "$stage_root/scripts/lint/encode-null-safety-allowlist.txt"

  local out
  local rc
  out=$(python3 "$stage_root/scripts/lint/check-encoder-null-safety.py" 2>&1)
  rc=$?

  local pass_label="PASS"
  local mode="ok"
  if [[ "$rc" != "$expected_exit" ]]; then
    pass_label="FAIL"
    mode="exit_mismatch"
  elif [[ -n "$expected_stdout_match" && "$out" != *"$expected_stdout_match"* ]]; then
    pass_label="FAIL"
    mode="output_mismatch"
  fi

  if [[ "$pass_label" == "PASS" ]]; then
    echo "PASS: $label (exit=$rc)"
  else
    echo "FAIL: $label ($mode)"
    echo "  expected exit: $expected_exit  got: $rc"
    if [[ -n "$expected_stdout_match" ]]; then
      echo "  expected output to contain: $expected_stdout_match"
    fi
    echo "  --- stdout/stderr ---"
    echo "$out" | sed 's/^/    /'
    fails=$((fails+1))
  fi

  rm -rf "$stage_root"
  trap - RETURN
}

stage_and_run \
  "encoder-class-c-positive.jsp" \
  "1" \
  "Class C — forHtmlContent in attribute context" \
  "positive Class-C fixture flagged"

stage_and_run \
  "encoder-class-c-negative.jsp" \
  "0" \
  "" \
  "negative fixture not flagged"

if [[ $fails -eq 0 ]]; then
  echo "All encoder-null-safety lint fixtures pass."
  exit 0
else
  echo "$fails fixture failure(s)."
  exit 1
fi

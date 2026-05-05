#!/usr/bin/env bash
#
# test-security-exception-message-convention.sh
#
# Smoke test for scripts/lint/check-security-exception-message-convention.sh.
#
# @since 2026-05-05

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_SCRIPT="$SCRIPT_DIR/check-security-exception-message-convention.sh"

stage_root=""
cleanup() {
  if [[ -n "$stage_root" && -d "$stage_root" ]]; then
    python3 - "$stage_root" <<'PY'
import shutil
import sys

shutil.rmtree(sys.argv[1])
PY
  fi
}
trap cleanup EXIT

stage_root="$(mktemp -d)"
mkdir -p "$stage_root/src/main/java/example"

fails=0

run_case() {
  local name="$1"
  local source="$2"
  local expected_exit="$3"
  local expected_output="$4"
  local path="$stage_root/src/main/java/example/${name}.java"

  printf '%s\n' "$source" > "$path"

  local out
  local rc
  out=$("$CHECK_SCRIPT" "$stage_root/src" 2>&1)
  rc=$?

  if [[ "$rc" == "$expected_exit" && ( -z "$expected_output" || "$out" == *"$expected_output"* ) ]]; then
    echo "PASS: $name (exit=$rc)"
  else
    echo "FAIL: $name"
    echo "  expected exit: $expected_exit  got: $rc"
    if [[ -n "$expected_output" ]]; then
      echo "  expected output to contain: $expected_output"
    fi
    echo "  --- stdout/stderr ---"
    echo "$out" | sed 's/^/    /'
    fails=$((fails + 1))
  fi

  python3 - "$path" <<'PY'
import pathlib
import sys

pathlib.Path(sys.argv[1]).unlink()
PY
}

run_case \
  "ColonFormViolation" \
  'class ColonFormViolation { void deny() { throw new SecurityException("missing required sec object: _lab"); } }' \
  "1" \
  "missing required sec object: _lab"

run_case \
  "ParenFormAllowed" \
  'class ParenFormAllowed { void deny() { throw new SecurityException("missing required sec object (_lab)"); } }' \
  "0" \
  ""

if [[ "$fails" -eq 0 ]]; then
  echo "All SecurityException message convention lint fixtures pass."
  exit 0
fi

echo "$fails fixture failure(s)."
exit 1

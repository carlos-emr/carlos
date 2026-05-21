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
stage_root_created=""
cleanup() {
  if [[ -n "$stage_root" && "$stage_root" == "$stage_root_created" && -d "$stage_root" ]]; then
    rm -rf -- "$stage_root"
  fi
}
trap cleanup EXIT

stage_root="$(mktemp -d)"
stage_root_created="$stage_root"
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

  local expected_path="$stage_root/src/main/java/example/${name}.java"
  if [[ "$path" == "$expected_path" ]]; then
    rm -f -- "$path"
  else
    echo "Refusing to remove unexpected fixture path: $path" >&2
    fails=$((fails + 1))
  fi
}

run_missing_target_case() {
  local out
  local rc
  out=$("$CHECK_SCRIPT" "$stage_root/does-not-exist" 2>&1)
  rc=$?

  if [[ "$rc" -ne 0 && "$out" == *"SecurityException message convention: ERROR"* ]]; then
    echo "PASS: MissingTargetError (exit=$rc)"
  else
    echo "FAIL: MissingTargetError"
    echo "  expected non-zero exit with convention error output; got: $rc"
    echo "  --- stdout/stderr ---"
    echo "$out" | sed 's/^/    /'
    fails=$((fails + 1))
  fi
}

run_case \
  "OldFormViolation" \
  'class OldFormViolation { void deny() { throw new SecurityException("missing required sec object (_lab)"); } }' \
  "1" \
  "missing required sec object (_lab)"

run_case \
  "StandardFormAllowed" \
  'class StandardFormAllowed { void deny() { throw new SecurityException("missing required security object: _lab"); } }' \
  "0" \
  ""

run_missing_target_case

if [[ "$fails" -eq 0 ]]; then
  echo "All SecurityException message convention lint fixtures pass."
  exit 0
fi

echo "$fails fixture failure(s)."
exit 1

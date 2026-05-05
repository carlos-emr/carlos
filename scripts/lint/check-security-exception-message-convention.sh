#!/usr/bin/env bash
#
# check-security-exception-message-convention.sh
#
# CI guard for SecurityException message consistency: failed
# SecurityInfoManager.hasPrivilege() checks must use:
#   missing required sec object (_object)
# not:
#   missing required sec object: _object
#
# @since 2026-05-05

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [[ "$#" -gt 0 ]]; then
  targets=("$@")
else
  targets=("$REPO_ROOT/src")
fi

tmp_stdout=""
tmp_stderr=""
cleanup() {
  for file in "$tmp_stdout" "$tmp_stderr"; do
    case "$file" in
      /tmp/security-exception-message-convention.stdout.*|/tmp/security-exception-message-convention.stderr.*)
        rm -f -- "$file"
        ;;
    esac
  done
}
trap cleanup EXIT

tmp_stdout="$(mktemp -t security-exception-message-convention.stdout.XXXXXX)"
tmp_stderr="$(mktemp -t security-exception-message-convention.stderr.XXXXXX)"

set +e
grep -RInE 'missing required sec object:[[:space:]]*($|[^)])' "${targets[@]}" >"$tmp_stdout" 2>"$tmp_stderr"
grep_status=$?
set -e

if [[ "$grep_status" -eq 1 ]]; then
  echo "SecurityException message convention: PASS"
  exit 0
fi

if [[ "$grep_status" -ne 0 ]]; then
  echo "SecurityException message convention: ERROR"
  echo
  echo "grep failed while scanning for colon-form messages:"
  cat "$tmp_stderr"
  if [[ -s "$tmp_stdout" ]]; then
    echo
    echo "Matches found before grep failed:"
    cat "$tmp_stdout"
  fi
  exit "$grep_status"
fi

violations="$(cat "$tmp_stdout")"

echo "SecurityException message convention: FAIL"
echo
echo "Use paren form for failed security-object privilege checks:"
echo "  missing required sec object (_objectname)"
echo
echo "Colon-form violations:"
echo "$violations"
exit 1

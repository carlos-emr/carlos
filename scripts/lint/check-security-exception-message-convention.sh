#!/usr/bin/env bash
#
# check-security-exception-message-convention.sh
#
# CI guard for SecurityException message consistency. Failed
# SecurityInfoManager.hasPrivilege() checks use:
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

if ! violations=$(grep -RInE 'missing required sec object:[[:space:]]*[^)]' "${targets[@]}" 2>/dev/null); then
  echo "SecurityException message convention: PASS"
  exit 0
fi

echo "SecurityException message convention: FAIL"
echo
echo "Use paren form for failed security-object privilege checks:"
echo "  missing required sec object (_objectname)"
echo
echo "Colon-form violations:"
echo "$violations"
exit 1

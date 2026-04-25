#!/usr/bin/env bash
#
# check-jsp-size.sh
#
# CI guard against the "god JSP" anti-pattern that caused #1922 (billingON.jsp
# rendered output exceeding 1 MB page buffer -> truncation mid-forward). The
# refactor in chore/billing-refactor moved ~22 KB of source and ~19 DAO lookups
# out of billingON.jsp into BillingONFormDataAssembler + BillingONFormViewModel
# + JSTL iteration. This script prevents silent regression by failing CI when a
# billing JSP grows back past a threshold.
#
# Thresholds (tunable via env vars):
#   - JSP_BYTE_THRESHOLD       max file size per billing JSP (default 81920 = 80 KB)
#   - JSP_GETBEAN_THRESHOLD    max SpringUtils.getBean calls per JSP (default 6)
#   - JSP_SCRIPTLET_THRESHOLD  max scriptlet blocks per JSP (default 20)
#
# Per-file allowlist exemptions live in `billing-jsp-size-allowlist.txt` next
# to this script, one path per line.
#
# Exit codes:
#   0 - all billing JSPs are under threshold
#   1 - one or more JSPs exceed threshold (CI should fail)
#   2 - usage error
#
# @since 2026-04-24

set -euo pipefail

# extglob is required by the allowlist whitespace-trimming patterns below
# (`*([[:space:]])`). Default bash settings have it off, which silently makes
# the trim a no-op and causes allowlist entries with incidental whitespace to
# miss.
shopt -s extglob

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

BILLING_JSP_ROOT="$REPO_ROOT/src/main/webapp/WEB-INF/jsp/billing"
ALLOWLIST_FILE="$SCRIPT_DIR/billing-jsp-size-allowlist.txt"

BYTE_THRESHOLD=${JSP_BYTE_THRESHOLD:-81920}     # 80 KB
GETBEAN_THRESHOLD=${JSP_GETBEAN_THRESHOLD:-6}
SCRIPTLET_THRESHOLD=${JSP_SCRIPTLET_THRESHOLD:-20}

if [[ ! -d "$BILLING_JSP_ROOT" ]]; then
  echo "Expected billing JSP tree at $BILLING_JSP_ROOT; skipping (nothing to check)." >&2
  exit 0
fi

# Load allowlist (one path per line, # comments, blank lines ignored)
declare -A ALLOWLIST=()
if [[ -f "$ALLOWLIST_FILE" ]]; then
  while IFS= read -r line; do
    # Strip comments and whitespace
    line="${line%%#*}"
    line="${line##*([[:space:]])}"
    line="${line%%*([[:space:]])}"
    [[ -z "$line" ]] && continue
    ALLOWLIST["$line"]=1
  done < "$ALLOWLIST_FILE"
fi

fails=0
allowlist_hits=0

# Find all JSPs under billing/
while IFS= read -r -d '' jsp; do
  rel="${jsp#$REPO_ROOT/}"
  if [[ -n "${ALLOWLIST[$rel]:-}" ]]; then
    allowlist_hits=$((allowlist_hits + 1))
    continue
  fi

  size=$(wc -c < "$jsp")
  # Count occurrences (not lines) — a JSP can have multiple SpringUtils.getBean
  # calls or multiple scriptlet opens on a single line, and grep -c only
  # reports the line count which silently undercounts.
  getbean_count=$(grep -o "SpringUtils\.getBean" "$jsp" 2>/dev/null | wc -l || true)
  # Count opening scriptlet blocks (not directives like <%@, <%--, <%=, <%!).
  scriptlet_count=$(grep -o "<%[^@!=-]" "$jsp" 2>/dev/null | wc -l || true)

  if (( size > BYTE_THRESHOLD )); then
    printf "FAIL: %s is %d bytes (threshold %d)\n" "$rel" "$size" "$BYTE_THRESHOLD"
    fails=$((fails + 1))
  fi
  if (( getbean_count > GETBEAN_THRESHOLD )); then
    printf "FAIL: %s has %d SpringUtils.getBean lookups (threshold %d)\n" \
      "$rel" "$getbean_count" "$GETBEAN_THRESHOLD"
    fails=$((fails + 1))
  fi
  if (( scriptlet_count > SCRIPTLET_THRESHOLD )); then
    printf "FAIL: %s has %d scriptlet blocks (threshold %d)\n" \
      "$rel" "$scriptlet_count" "$SCRIPTLET_THRESHOLD"
    fails=$((fails + 1))
  fi
done < <(find "$BILLING_JSP_ROOT" -type f \( -name "*.jsp" -o -name "*.jspf" \) -print0)

if (( fails > 0 )); then
  printf "\n%d billing JSP guard violations. Move data-building to an action + view model; use JSTL for iteration.\n" "$fails" >&2
  printf "(To acknowledge a known baseline, add the path to %s.)\n" "$(realpath --relative-to="$REPO_ROOT" "$ALLOWLIST_FILE")" >&2
  exit 1
fi

if (( allowlist_hits > 0 )); then
  printf "All non-allowlisted billing JSPs within size / DAO / scriptlet thresholds. %d allowlisted files skipped.\n" "$allowlist_hits"
else
  echo "All billing JSPs within size / DAO / scriptlet thresholds."
fi
exit 0

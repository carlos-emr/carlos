#!/usr/bin/env bash
#
# check-jsp-size.sh
#
# CI guard against the "god JSP" anti-pattern (billingON.jsp rendered output
# exceeding 1 MB page buffer -> truncation mid-forward). The Ontario-billing
# refactor moved ~22 KB of source and ~19 DAO lookups out of billingON.jsp
# into BillingOnFormViewModelAssembler + BillingOnFormViewModel + JSTL
# iteration. This script prevents silent regression by failing CI when a
# billing JSP grows back past a threshold.
#
# Thresholds (tunable via env vars):
#   - JSP_BYTE_THRESHOLD       max file size per billing JSP (default 81920 = 80 KB)
#   - JSP_GETBEAN_THRESHOLD    max SpringUtils.getBean calls per JSP (default 6)
#   - JSP_SCRIPTLET_THRESHOLD  max scriptlet blocks per JSP (default 20)
#
# Per-file baselines live in `billing-jsp-size-baseline.txt` next to this
# script. Baseline files are still checked and fail if they grow beyond their
# recorded byte / getBean / scriptlet counts.
#
# Exit codes:
#   0 - all billing JSPs are under threshold
#   1 - one or more JSPs exceed threshold (CI should fail)
#   2 - usage error
#
# @since 2026-04-24

set -euo pipefail

# extglob is required by the baseline whitespace-trimming patterns below
# (`*([[:space:]])`). Default bash settings have it off, which silently makes
# the trim a no-op and causes baseline entries with incidental whitespace to
# miss.
shopt -s extglob

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

BILLING_JSP_ROOT="$REPO_ROOT/src/main/webapp/WEB-INF/jsp/billing"
BASELINE_FILE="$SCRIPT_DIR/billing-jsp-size-baseline.txt"

BYTE_THRESHOLD=${JSP_BYTE_THRESHOLD:-81920}     # 80 KB
GETBEAN_THRESHOLD=${JSP_GETBEAN_THRESHOLD:-6}
SCRIPTLET_THRESHOLD=${JSP_SCRIPTLET_THRESHOLD:-20}

if [[ ! -d "$BILLING_JSP_ROOT" ]]; then
  echo "Expected billing JSP tree at $BILLING_JSP_ROOT; skipping (nothing to check)." >&2
  exit 0
fi

# Load baselines: path bytes getbean_count scriptlet_count
declare -A BASELINE_SIZE=()
declare -A BASELINE_GETBEAN=()
declare -A BASELINE_SCRIPTLET=()
if [[ -f "$BASELINE_FILE" ]]; then
  while IFS= read -r line; do
    # Strip comments and whitespace
    line="${line%%#*}"
    line="${line##*([[:space:]])}"
    line="${line%%*([[:space:]])}"
    [[ -z "$line" ]] && continue
    read -r path bytes getbeans scriptlets <<< "$line"
    if [[ -z "${path:-}" || -z "${bytes:-}" || -z "${getbeans:-}" || -z "${scriptlets:-}" ]]; then
      printf "Invalid baseline row in %s: %s\n" "$(realpath --relative-to="$REPO_ROOT" "$BASELINE_FILE")" "$line" >&2
      exit 2
    fi
    BASELINE_SIZE["$path"]="$bytes"
    BASELINE_GETBEAN["$path"]="$getbeans"
    BASELINE_SCRIPTLET["$path"]="$scriptlets"
  done < "$BASELINE_FILE"
fi

fails=0
baseline_hits=0

# Find all JSPs under billing/
while IFS= read -r -d '' jsp; do
  rel="${jsp#$REPO_ROOT/}"

  size=$(wc -c < "$jsp")
  # Count occurrences (not lines) — a JSP can have multiple SpringUtils.getBean
  # calls or multiple scriptlet opens on a single line, and grep -c only
  # reports the line count which silently undercounts.
  getbean_count=$(grep -o "SpringUtils\.getBean" "$jsp" 2>/dev/null | wc -l || true)
  # Count opening scriptlet blocks (not directives like <%@, <%--, <%=, <%!).
  scriptlet_count=$(grep -o "<%[^@!=-]" "$jsp" 2>/dev/null | wc -l || true)

  if [[ -n "${BASELINE_SIZE[$rel]:-}" ]]; then
    baseline_hits=$((baseline_hits + 1))
    if (( size > BASELINE_SIZE[$rel] )); then
      printf "FAIL: %s is %d bytes (baseline %d)\n" "$rel" "$size" "${BASELINE_SIZE[$rel]}"
      fails=$((fails + 1))
    fi
    if (( getbean_count > BASELINE_GETBEAN[$rel] )); then
      printf "FAIL: %s has %d SpringUtils.getBean lookups (baseline %d)\n" \
        "$rel" "$getbean_count" "${BASELINE_GETBEAN[$rel]}"
      fails=$((fails + 1))
    fi
    if (( scriptlet_count > BASELINE_SCRIPTLET[$rel] )); then
      printf "FAIL: %s has %d scriptlet blocks (baseline %d)\n" \
        "$rel" "$scriptlet_count" "${BASELINE_SCRIPTLET[$rel]}"
      fails=$((fails + 1))
    fi
    continue
  fi

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
  printf "(To acknowledge a known baseline, add path/metric counts to %s.)\n" "$(realpath --relative-to="$REPO_ROOT" "$BASELINE_FILE")" >&2
  exit 1
fi

if (( baseline_hits > 0 )); then
  printf "All billing JSPs within size / DAO / scriptlet thresholds or recorded baselines. %d baseline files checked.\n" "$baseline_hits"
else
  echo "All billing JSPs within size / DAO / scriptlet thresholds."
fi
exit 0

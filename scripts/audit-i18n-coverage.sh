#!/bin/bash
#
# audit-i18n-coverage.sh
#
# Scans all JSP/JSPF files under src/main/webapp/ and categorizes each by
# i18n coverage level, producing a CSV report with per-file metrics.
#
# Categories:
#   full     - Uses fmt:message; no detected hardcoded user-visible text
#   partial  - Uses fmt:message but still has hardcoded text
#   none     - No fmt:message usage at all
#
# Hardcoded text is detected heuristically in:
#   <td>, <th>, <label>, <button>, <option> text content
#   title="..." and placeholder="..." attributes with plain text
#   alert("...") and confirm("...") JavaScript calls
#
# Also flags files using the legacy repeated inline fmt:setBundle pattern.
#
# Usage (run from project root):
#   ./scripts/audit-i18n-coverage.sh
#   ./scripts/audit-i18n-coverage.sh --output report.csv
#   ./scripts/audit-i18n-coverage.sh --webapp-dir src/main/webapp
#
# Output:
#   CSV report (default: i18n-coverage-report.csv in project root)
#   Summary table printed to stdout

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WEBAPP_DIR="${PROJECT_ROOT}/src/main/webapp"
OUTPUT_FILE="${PROJECT_ROOT}/i18n-coverage-report.csv"

# ── Argument Parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output)
            if [[ -z "${2:-}" || "$2" == --* ]]; then
                echo "ERROR: --output requires a file path argument" >&2
                exit 1
            fi
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --webapp-dir)
            if [[ -z "${2:-}" || "$2" == --* ]]; then
                echo "ERROR: --webapp-dir requires a directory path argument" >&2
                exit 1
            fi
            WEBAPP_DIR="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [--output FILE] [--webapp-dir DIR]"
            echo ""
            echo "Options:"
            echo "  --output FILE       CSV output path (default: i18n-coverage-report.csv)"
            echo "  --webapp-dir DIR    Webapp root to scan (default: src/main/webapp)"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            echo "Use --help for usage." >&2
            exit 1
            ;;
    esac
done

if [ ! -d "$WEBAPP_DIR" ]; then
    echo "ERROR: webapp directory not found: $WEBAPP_DIR" >&2
    exit 1
fi

# ── Helpers ───────────────────────────────────────────────────────────────────

# Count lines in FILE matching INCLUDE_PATTERN that do NOT match EXCLUDE_PATTERN.
# Returns 0 when no lines match the include pattern.
count_filtered_lines() {
    local file="$1"
    local include="$2"
    local exclude="$3"
    local n
    # grep -E for include; if none found, exit 1 silently → n=0
    n=$(grep -E "$include" "$file" 2>/dev/null | grep -cvE "$exclude" 2>/dev/null) || true
    echo "${n:-0}"
}

# Heuristic count of hardcoded (non-i18n) user-visible strings in a JSP file.
# Detects plain text in HTML elements and JavaScript alert/confirm calls.
# Note: intentionally conservative to avoid false positives from EL/scriptlets.
count_hardcoded() {
    local file="$1"
    local total=0 n
    # Common exclusion pattern: lines that already use EL, fmt:message, or scriptlets
    local excl='\$\{[^}]*\}|fmt:message|<%[^@!\-]'

    # 1. <td>/<th> cells with capitalized plain-text content
    n=$(count_filtered_lines "$file" \
        '<(td|th)[^>]*>[[:space:]]*[A-Z][a-z]{1,}' \
        "$excl")
    total=$((total + n))

    # 2. <label> elements with capitalized plain-text content
    n=$(count_filtered_lines "$file" \
        '<label[^>]*>[[:space:]]*[A-Z][a-z]{1,}' \
        "$excl")
    total=$((total + n))

    # 3. <button> elements with capitalized plain-text content
    n=$(count_filtered_lines "$file" \
        '<button[^>]*>[[:space:]]*[A-Z][a-z]{1,}' \
        "$excl")
    total=$((total + n))

    # 4. <option> elements with capitalized plain-text content
    n=$(count_filtered_lines "$file" \
        '<option[^>]*>[[:space:]]*[A-Z][a-z]{1,}' \
        "$excl")
    total=$((total + n))

    # 5. <input type="submit/button/reset"> with plain value attribute
    #    Match in either attribute order; handle optional whitespace around '='
    #    and both single/double quotes; second alternation anchored to <input
    n=$(count_filtered_lines "$file" \
        "<input[^>]+type[[:space:]]*=[[:space:]]*[\"'](submit|button|reset)[\"'][^>]+value[[:space:]]*=[[:space:]]*[\"'][A-Za-z][a-z]+[\"']|<input[^>]+value[[:space:]]*=[[:space:]]*[\"'][A-Za-z][a-z]+[\"'][^>]+type[[:space:]]*=[[:space:]]*[\"'](submit|button|reset)[\"']" \
        '\$\{[^}]*\}|fmt:message')
    total=$((total + n))

    # 6. title="..." or title='...' attributes with plain text (≥4 chars, not starting with EL)
    #    Handle optional whitespace around '=' and both single/double quotes
    n=$(grep -oE 'title[[:space:]]*=[[:space:]]*"[^"$<{%][^"]{3,}"' "$file" 2>/dev/null | \
        grep -cE '[A-Za-z]{3}' 2>/dev/null) || true
    total=$((total + ${n:-0}))
    n=$(grep -oE "title[[:space:]]*=[[:space:]]*'[^'\$<{%][^']{3,}'" "$file" 2>/dev/null | \
        grep -cE '[A-Za-z]{3}' 2>/dev/null) || true
    total=$((total + ${n:-0}))

    # 7. placeholder="..." or placeholder='...' attributes with plain text
    #    Handle optional whitespace around '=' and both single/double quotes
    n=$(grep -oE 'placeholder[[:space:]]*=[[:space:]]*"[^"$<{%][^"]{3,}"' "$file" 2>/dev/null | \
        grep -cE '[A-Za-z]{3}' 2>/dev/null) || true
    total=$((total + ${n:-0}))
    n=$(grep -oE "placeholder[[:space:]]*=[[:space:]]*'[^'\$<{%][^']{3,}'" "$file" 2>/dev/null | \
        grep -cE '[A-Za-z]{3}' 2>/dev/null) || true
    total=$((total + ${n:-0}))

    # 8. JavaScript alert("text") or confirm("text") with string literals
    n=$(grep -cE "(alert|confirm)\(['\"][A-Za-z]" "$file" 2>/dev/null) || true
    total=$((total + ${n:-0}))

    echo "$total"
}

# ── Main Scan ─────────────────────────────────────────────────────────────────
echo "=== CARLOS EMR i18n Coverage Audit ==="
echo "Scanning: $WEBAPP_DIR"
echo "Output:   $OUTPUT_FILE"
echo

# Write CSV header
echo "file_path,domain,category,i18n_count,hardcoded_count,legacy_bundle_count,has_fmt_taglib" \
    > "$OUTPUT_FILE"

total=0
count_full=0
count_partial=0
count_none=0
count_legacy=0

while IFS= read -r -d '' file; do
    total=$((total + 1))

    # Path relative to project root
    rel_path="${file#"${PROJECT_ROOT}/"}"
    # Normalise Windows-style backslashes if present
    rel_path="${rel_path//\\//}"

    # Domain = first subdirectory under the configured webapp dir (e.g. "admin", "demographic")
    # Derived from file path using WEBAPP_DIR variable so --webapp-dir overrides work correctly
    path_in_webapp="${file#"${WEBAPP_DIR}/"}"
    path_in_webapp="${path_in_webapp//\\//}"
    domain="${path_in_webapp%%/*}"
    if [ -z "$domain" ] || [ "$domain" = "$path_in_webapp" ]; then
        domain="root"
    fi

    # ── Count fmt:message uses (i18n coverage) ──────────────────────────────
    i18n_count=$(grep -c 'fmt:message' "$file" 2>/dev/null) || true
    i18n_count="${i18n_count:-0}"

    # ── Detect legacy inline bundle pattern ─────────────────────────────────
    # Pattern: fmt:setBundle appears on the SAME line as fmt:message
    # (rather than once at the page level, before the HTML)
    legacy_count=$(grep -cE 'fmt:setBundle[^/]*/>[[:space:]]*<fmt:message' "$file" 2>/dev/null) || true
    legacy_count="${legacy_count:-0}"

    # ── Check for fmt taglib declaration ────────────────────────────────────
    if grep -qE 'taglib[^>]*(jakarta\.tags\.fmt|java\.sun\.com/jsp/jstl/fmt)' "$file" 2>/dev/null; then
        has_fmt_taglib="yes"
    else
        has_fmt_taglib="no"
    fi

    # ── Count hardcoded text instances ──────────────────────────────────────
    hc_count=$(count_hardcoded "$file")

    # ── Categorize ──────────────────────────────────────────────────────────
    if [ "$i18n_count" -gt 0 ] && [ "$hc_count" -eq 0 ]; then
        category="full"
        count_full=$((count_full + 1))
    elif [ "$i18n_count" -gt 0 ]; then
        category="partial"
        count_partial=$((count_partial + 1))
    else
        category="none"
        count_none=$((count_none + 1))
    fi

    [ "$legacy_count" -gt 0 ] && count_legacy=$((count_legacy + 1))

    # Escape commas in file path (should not occur, but be safe)
    safe_path="${rel_path//,/;}"

    echo "$safe_path,$domain,$category,$i18n_count,$hc_count,$legacy_count,$has_fmt_taglib" \
        >> "$OUTPUT_FILE"

    if (( total % 100 == 0 )); then
        echo "  Processed $total files..."
    fi

done < <(find "$WEBAPP_DIR" \( -name "*.jsp" -o -name "*.jspf" \) -type f -print0 | sort -z)

# ── Summary ───────────────────────────────────────────────────────────────────
pct() { printf '%d' "$(( $1 * 100 / ($2 > 0 ? $2 : 1) ))"; }

echo
echo "════════════════════════════════════════════════════════"
echo " Coverage Summary"
echo "════════════════════════════════════════════════════════"
printf " %-26s %5d\n"        "Total JSP/JSPF files:"  "$total"
printf " %-26s %5d  (%s%%)\n" "Fully i18n'd (full):"   "$count_full"    "$(pct $count_full $total)"
printf " %-26s %5d  (%s%%)\n" "Partially i18n'd:"      "$count_partial" "$(pct $count_partial $total)"
printf " %-26s %5d  (%s%%)\n" "No i18n coverage:"      "$count_none"    "$(pct $count_none $total)"
printf " %-26s %5d\n"        "Legacy bundle pattern:"  "$count_legacy"
echo

echo " By Domain (none / partial / full):"
echo "────────────────────────────────────────────────────────"

# Parse CSV to produce per-domain breakdown, sorted alphabetically
awk -F',' 'NR > 1 {
    d  = $2
    c  = $3
    counts[d SUBSEP c]++
    domains[d] = 1
} END {
    for (d in domains) {
        none    = ((d SUBSEP "none")    in counts) ? counts[d SUBSEP "none"]    : 0
        partial = ((d SUBSEP "partial") in counts) ? counts[d SUBSEP "partial"] : 0
        full    = ((d SUBSEP "full")    in counts) ? counts[d SUBSEP "full"]    : 0
        tot     = none + partial + full
        printf " %-28s none=%-4d partial=%-4d full=%-4d  total=%d\n",
            d":", none, partial, full, tot
    }
}' "$OUTPUT_FILE" | sort

echo
echo " High-priority domains (none + partial files only):"
echo "────────────────────────────────────────────────────────"
for domain in admin provider demographic appointment tickler; do
    count=$(awk -F',' -v d="$domain" 'NR > 1 && $2 == d && ($3 == "none" || $3 == "partial") {n++} END {print n+0}' "$OUTPUT_FILE")
    printf "   %-18s %d files need i18n work\n" "$domain:" "$count"
done

echo
echo " Top 20 files with most hardcoded text:"
echo "────────────────────────────────────────────────────────"
awk -F',' 'NR > 1 && $5 > 0 {print $5, $1}' "$OUTPUT_FILE" | \
    sort -rn | head -20 | \
    awk '{printf "   %-6s  %s\n", $1, $2}'

echo
echo "════════════════════════════════════════════════════════"
echo " Report saved to: $OUTPUT_FILE"
echo
echo " Columns: file_path, domain, category, i18n_count,"
echo "          hardcoded_count, legacy_bundle_count, has_fmt_taglib"
echo "════════════════════════════════════════════════════════"

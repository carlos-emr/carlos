#!/bin/bash
#
# check-i18n-properties.sh
#
# Compares keys across all oscarResources_*.properties locale files and reports:
#   - Keys present in English but missing from other locales
#   - Keys present in non-English locales but absent from English (orphaned)
#   - ISO 8859-1 encoding compliance violations (raw non-ASCII bytes)
#
# This is a pure-bash implementation; it does not require the legacy
# utils/tasks/src/ResourceCompareTask.java (which depends on a deprecated
# Google Translate API).
#
# Usage (run from project root):
#   ./scripts/check-i18n-properties.sh
#   ./scripts/check-i18n-properties.sh --show-missing
#   ./scripts/check-i18n-properties.sh --show-orphaned
#   ./scripts/check-i18n-properties.sh --output report.txt
#
# Exit codes:
#   0  All locale files are in sync and encoding is clean
#   1  Discrepancies or encoding violations detected

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESOURCES_DIR="${PROJECT_ROOT}/src/main/resources"
ENGLISH_BASE="oscarResources_en.properties"

# Report output (stdout by default; override with --output)
OUTPUT_FILE=""
SHOW_MISSING=false
SHOW_ORPHANED=false
MAX_LIST=50   # max keys to list per locale before truncating

# ── Argument Parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --show-missing)   SHOW_MISSING=true;    shift ;;
        --show-orphaned)  SHOW_ORPHANED=true;   shift ;;
        --output)         OUTPUT_FILE="$2";     shift 2 ;;
        --max-list)       MAX_LIST="$2";        shift 2 ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --show-missing    Print each missing key (can be long)"
            echo "  --show-orphaned   Print each orphaned key"
            echo "  --output FILE     Write report to FILE instead of stdout"
            echo "  --max-list N      Limit key listings to N items (default: 50)"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            echo "Use --help for usage." >&2
            exit 1
            ;;
    esac
done

# ── Output Routing ────────────────────────────────────────────────────────────
if [ -n "$OUTPUT_FILE" ]; then
    exec > "$OUTPUT_FILE"
fi

# ── Helpers ───────────────────────────────────────────────────────────────────

# Extract sorted unique keys from a Java .properties file.
# Skips comment lines (# or !), blank lines, and continuation lines (leading whitespace).
# Handles both = and : separators.
extract_keys() {
    local file="$1"
    grep -Ev '^[[:space:]]*[#!]|^[[:space:]]*$' "$file" 2>/dev/null | \
    grep -Ev '^[[:space:]]' | \
    sed -E 's/[[:space:]]*[=:[:space:]].*//' | \
    sed 's/[[:space:]]*$//' | \
    grep -v '^$' | \
    sort -u
}

# Count raw non-ASCII bytes in a file (indicates potential UTF-8 mis-encoding).
# Java Properties.load() expects ISO 8859-1; raw bytes > 0x7F should be \uXXXX escapes.
count_non_ascii_lines() {
    local file="$1"
    LC_ALL=C grep -cP '[\x80-\xFF]' "$file" 2>/dev/null || echo 0
}

# Locale label from filename: oscarResources_fr.properties → "fr"
locale_of() {
    local fname
    fname="$(basename "$1")"
    fname="${fname#oscarResources_}"
    echo "${fname%.properties}"
}

# ── Locate Files ─────────────────────────────────────────────────────────────
ENGLISH_FILE="${RESOURCES_DIR}/${ENGLISH_BASE}"

if [ ! -f "$ENGLISH_FILE" ]; then
    echo "ERROR: English properties file not found: $ENGLISH_FILE" >&2
    exit 1
fi

# Collect all locale files (excluding English)
mapfile -t LOCALE_FILES < <(find "$RESOURCES_DIR" -name "oscarResources_*.properties" \
    ! -name "$ENGLISH_BASE" -type f | sort)

if [ ${#LOCALE_FILES[@]} -eq 0 ]; then
    echo "ERROR: No non-English locale files found in $RESOURCES_DIR" >&2
    exit 1
fi

# ── Main Report ───────────────────────────────────────────────────────────────
echo "=== CARLOS EMR i18n Properties Key Audit ==="
echo "Resources dir: $RESOURCES_DIR"
echo "English base:  $ENGLISH_BASE"
echo "Locales found: $(printf '%s ' "${LOCALE_FILES[@]}" | xargs -n1 basename | tr '\n' ' ')"
echo

# Build English key set (temp file for efficiency)
TMP_EN=$(mktemp)
extract_keys "$ENGLISH_FILE" > "$TMP_EN"
EN_KEY_COUNT=$(wc -l < "$TMP_EN")

printf "English keys: %d\n\n" "$EN_KEY_COUNT"

# ── Per-Locale Comparison ─────────────────────────────────────────────────────
overall_ok=true

echo "════════════════════════════════════════════════════════"
echo " Key Coverage by Locale"
echo "════════════════════════════════════════════════════════"

declare -A missing_counts
declare -A orphan_counts

for locale_file in "${LOCALE_FILES[@]}"; do
    locale=$(locale_of "$locale_file")
    TMP_LC=$(mktemp)
    extract_keys "$locale_file" > "$TMP_LC"
    lc_key_count=$(wc -l < "$TMP_LC")

    # Keys in English missing from this locale
    TMP_MISSING=$(mktemp)
    comm -23 "$TMP_EN" "$TMP_LC" > "$TMP_MISSING"
    missing_count=$(wc -l < "$TMP_MISSING")

    # Keys in this locale missing from English (orphaned)
    TMP_ORPHAN=$(mktemp)
    comm -13 "$TMP_EN" "$TMP_LC" > "$TMP_ORPHAN"
    orphan_count=$(wc -l < "$TMP_ORPHAN")

    missing_counts["$locale"]=$missing_count
    orphan_counts["$locale"]=$orphan_count

    # Coverage percentage
    if [ "$EN_KEY_COUNT" -gt 0 ]; then
        covered=$((lc_key_count - missing_count < 0 ? 0 : lc_key_count - missing_count))
        pct=$(( (EN_KEY_COUNT - missing_count) * 100 / EN_KEY_COUNT ))
    else
        pct=100
    fi

    # Status indicator
    if [ "$missing_count" -eq 0 ] && [ "$orphan_count" -eq 0 ]; then
        status="OK"
    else
        status="NEEDS ATTENTION"
        overall_ok=false
    fi

    printf " %-10s  keys=%-6d  missing=%-5d  orphaned=%-5d  coverage=%3d%%  [%s]\n" \
        "$locale" "$lc_key_count" "$missing_count" "$orphan_count" "$pct" "$status"

    # Optionally list missing keys
    if [ "$SHOW_MISSING" = true ] && [ "$missing_count" -gt 0 ]; then
        echo "   Missing from ${locale} (showing up to ${MAX_LIST}):"
        head -"$MAX_LIST" "$TMP_MISSING" | sed 's/^/     /'
        if [ "$missing_count" -gt "$MAX_LIST" ]; then
            echo "     ... and $((missing_count - MAX_LIST)) more"
        fi
    fi

    # Optionally list orphaned keys
    if [ "$SHOW_ORPHANED" = true ] && [ "$orphan_count" -gt 0 ]; then
        echo "   Orphaned in ${locale} (showing up to ${MAX_LIST}):"
        head -"$MAX_LIST" "$TMP_ORPHAN" | sed 's/^/     /'
        if [ "$orphan_count" -gt "$MAX_LIST" ]; then
            echo "     ... and $((orphan_count - MAX_LIST)) more"
        fi
    fi

    rm -f "$TMP_LC" "$TMP_MISSING" "$TMP_ORPHAN"
done

rm -f "$TMP_EN"

# ── Encoding Compliance Check ─────────────────────────────────────────────────
echo
echo "════════════════════════════════════════════════════════"
echo " ISO 8859-1 Encoding Compliance"
echo "════════════════════════════════════════════════════════"
echo " Java Properties.load() requires ISO 8859-1 encoding."
echo " Non-ASCII characters must use \\uXXXX unicode escapes."
echo " Non-ASCII bytes in the file indicate UTF-8 mis-encoding."
echo

encoding_clean=true
all_files=("$ENGLISH_FILE" "${LOCALE_FILES[@]}")

for props_file in "${all_files[@]}"; do
    locale=$(locale_of "$props_file")
    non_ascii=$(count_non_ascii_lines "$props_file")
    if [ "$non_ascii" -gt 0 ]; then
        printf " %-20s  ENCODING VIOLATION: %d line(s) with raw non-ASCII bytes\n" \
            "${locale}:" "$non_ascii"
        echo "   Fix: Replace raw chars with \\uXXXX escapes (e.g. é → \\u00e9)"
        encoding_clean=false
        overall_ok=false
    else
        printf " %-20s  OK\n" "${locale}:"
    fi
done

# ── Cross-Locale Missing Key Details ─────────────────────────────────────────
echo
echo "════════════════════════════════════════════════════════"
echo " Keys Missing Across All Non-English Locales (Universal Gaps)"
echo "════════════════════════════════════════════════════════"
echo " These keys exist in English but are untranslated in ALL other locales."
echo " Add them with English value as placeholder and mark: # TODO: translate"
echo

# Rebuild temp files for this cross-locale analysis
TMP_EN2=$(mktemp)
extract_keys "$ENGLISH_FILE" > "$TMP_EN2"
TMP_UNIVERSAL=$(mktemp)
cp "$TMP_EN2" "$TMP_UNIVERSAL"

for locale_file in "${LOCALE_FILES[@]}"; do
    TMP_LC2=$(mktemp)
    extract_keys "$locale_file" > "$TMP_LC2"
    # Keep only keys missing from this locale too
    TMP_NEW=$(mktemp)
    comm -13 "$TMP_LC2" "$TMP_UNIVERSAL" > "$TMP_NEW"
    mv "$TMP_NEW" "$TMP_UNIVERSAL"
    rm -f "$TMP_LC2"
done

universal_missing=$(wc -l < "$TMP_UNIVERSAL")
echo " Keys missing from ALL non-English locales: $universal_missing"
if [ "$universal_missing" -gt 0 ]; then
    echo " (showing up to $MAX_LIST)"
    head -"$MAX_LIST" "$TMP_UNIVERSAL" | sed 's/^/   /'
    if [ "$universal_missing" -gt "$MAX_LIST" ]; then
        echo "   ... and $((universal_missing - MAX_LIST)) more"
        echo "   Run with --show-missing for full listings per locale."
    fi
fi
rm -f "$TMP_EN2" "$TMP_UNIVERSAL"

# ── Summary ───────────────────────────────────────────────────────────────────
echo
echo "════════════════════════════════════════════════════════"
echo " Summary"
echo "════════════════════════════════════════════════════════"
echo
echo " Missing key counts by locale:"
for locale_file in "${LOCALE_FILES[@]}"; do
    locale=$(locale_of "$locale_file")
    mc="${missing_counts[$locale]:-0}"
    oc="${orphan_counts[$locale]:-0}"
    printf "   %-10s  missing=%-5d  orphaned=%d\n" "$locale:" "$mc" "$oc"
done
echo
if [ "$overall_ok" = true ]; then
    echo " All locale files are in sync and encoding is clean."
    EXIT_CODE=0
else
    echo " Issues detected. Resolve missing/orphaned keys and encoding violations."
    echo " Use --show-missing and --show-orphaned for detailed key lists."
    [ -n "$OUTPUT_FILE" ] && echo " Report written to: $OUTPUT_FILE"
    EXIT_CODE=1
fi
echo "════════════════════════════════════════════════════════"

exit $EXIT_CODE

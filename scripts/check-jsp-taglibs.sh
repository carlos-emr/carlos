#!/bin/bash

# check-jsp-taglibs.sh
#
# Checks JSP/JSPF/TAG files for:
#   1. Missing taglib declarations (original behaviour)
#   2. i18n-specific issues (--check-i18n flag or always when run standalone):
#      a. fmt: tag used without fmt:setBundle declaration
#      b. Hardcoded lang="en" on <html> tags (must be dynamic locale)
#      c. Repeated inline fmt:setBundle declarations (should be consolidated)
#
# Usage:
#   ./scripts/check-jsp-taglibs.sh                  # taglib check only
#   ./scripts/check-jsp-taglibs.sh --check-i18n     # taglib + i18n checks
#   ./scripts/check-jsp-taglibs.sh --i18n-only      # i18n checks only

echo "=== Comprehensive JSP Taglib Checker ==="
echo

# tags: regex pattern matching either legacy java.sun.com or modern Jakarta namespace URIs
declare -A tags=(
    # JSTL tags (legacy java.sun.com and Jakarta namespaces)
    ["fmt"]="(http://java\\.sun\\.com/jsp/jstl/fmt|jakarta\\.tags\\.fmt)"
    ["c"]="(http://java\\.sun\\.com/jsp/jstl/core|jakarta\\.tags\\.core)"
    ["fn"]="(http://java\\.sun\\.com/jsp/jstl/functions|jakarta\\.tags\\.functions)"
    ["sql"]="(http://java\\.sun\\.com/jsp/jstl/sql|jakarta\\.tags\\.sql)"
    ["x"]="(http://java\\.sun\\.com/jsp/jstl/xml|jakarta\\.tags\\.xml)"
)

# Recommended Jakarta namespace URIs for suggestions
declare -A tag_uris=(
    ["fmt"]="jakarta.tags.fmt"
    ["c"]="jakarta.tags.core"
    ["fn"]="jakarta.tags.functions"
    ["sql"]="jakarta.tags.sql"
    ["x"]="jakarta.tags.xml"
)

# Patterns for taglib include files
TAGLIB_INCLUDES="taglibs\.jsp|taglibs\.jspf|common-taglibs\.jsp|common-tags\.jsp"

# Strip HTML and JSP comment blocks before running structural checks.
strip_template_comments() {
    # Use Perl instead of sed ranges so one-line comments do not delete
    # everything until the next later comment block in the file.
    perl -0pe 's/<!--.*?-->//gs; s/<%--.*?--%>//gs' "$1"
}

resolve_include_path() {
    local file="$1"
    local include_path="$2"

    if [[ "$include_path" = /* ]]; then
        printf '%s\n' "./src/main/webapp${include_path}"
    else
        printf '%s\n' "$(dirname "$file")/$include_path"
    fi
}

has_inherited_bundle() {
    local file="$1"
    local include_path resolved_path

    while IFS= read -r include_path; do
        resolved_path=$(resolve_include_path "$file" "$include_path")
        if [ -f "$resolved_path" ] && grep -q 'fmt:setBundle' "$resolved_path"; then
            return 0
        fi
    done < <(
        strip_template_comments "$file" | \
            grep -oE '<%@[[:space:]]*include[^>]+file[[:space:]]*=[[:space:]]*"[^"]+"|<jsp:include[^>]+page[[:space:]]*=[[:space:]]*"[^"]+"' 2>/dev/null | \
            sed -E 's/.*(file|page)[[:space:]]*=[[:space:]]*"([^"]+)"/\2/'
    )

    return 1
}

# ── Argument Parsing ──────────────────────────────────────────────────────────
CHECK_I18N=false
I18N_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --check-i18n)  CHECK_I18N=true ;;
        --i18n-only)   I18N_ONLY=true; CHECK_I18N=true ;;
    esac
done

found_issues=0
total_checked=0
i18n_issues=0

echo "Searching for JSP/JSPF/TAG files..."

while IFS= read -r -d '' file; do
    ((total_checked++))
    missing=()
    file_i18n_issues=()

    # Check for direct includes (both styles)
    has_direct_include=$(strip_template_comments "$file" | grep -E '<%@\s*include.*'"${TAGLIB_INCLUDES}" || true)
    has_jsp_include=$(strip_template_comments "$file" | grep -E '<jsp:include.*'"${TAGLIB_INCLUDES}" || true)
    has_taglib_include=""
    [ -n "$has_direct_include" ] || [ -n "$has_jsp_include" ] && has_taglib_include="yes"

    # ── Standard Taglib Declaration Check ────────────────────────────────────
    if [ "$I18N_ONLY" = false ]; then
        for prefix in "${!tags[@]}"; do
            # - Handles whitespace variations: <c:, < c:, <c :
            # - Case insensitive for the tag (though usually lowercase)
            # - Matches in attributes too: <div class="${c:...}">
            if grep -qiE "<\s*${prefix}\s*:|[\$\{]${prefix}:" "$file"; then

                # Skip if taglibs file is included
                if [ -n "$has_taglib_include" ]; then
                    continue
                fi

                # Check if taglib is declared (and not commented out) — match either URI form
                if ! strip_template_comments "$file" | grep -qE "taglib.*${tags[$prefix]}"; then
                    missing+=("$prefix")
                fi
            fi
        done

        if [ ${#missing[@]} -gt 0 ]; then
            echo "$file"
            echo "   Missing taglib(s): ${missing[*]}"
            echo "   Add these declarations at the top:"
            for prefix in "${missing[@]}"; do
                echo "      <%@ taglib uri=\"${tag_uris[$prefix]}\" prefix=\"$prefix\" %>"
            done
            echo
            ((found_issues++))
        fi
    fi

    # ── i18n-Specific Checks ─────────────────────────────────────────────────
    if [ "$CHECK_I18N" = true ]; then

        # i18n Check 1: fmt: tag used but no fmt:setBundle declared locally or via an include
        if grep -q 'fmt:message' "$file" && ! grep -q 'fmt:setBundle' "$file" && ! has_inherited_bundle "$file"; then
            file_i18n_issues+=("MISSING fmt:setBundle: file uses fmt:message but has no fmt:setBundle declaration")
            file_i18n_issues+=("  Fix: Add <fmt:setBundle basename=\"oscarResources\"/> after taglib declarations, before <!DOCTYPE>")
        fi

        # i18n Check 2: Hardcoded lang="en" on <html> tag (must be dynamic)
        if grep -qE '<html[^>]+lang="en"[^>]*>' "$file"; then
            file_i18n_issues+=("HARDCODED lang attribute: <html lang=\"en\"> should be dynamic")
            file_i18n_issues+=("  Fix: Change to <html lang=\"\${pageContext.request.locale.language}\">")
        fi

        # i18n Check 3: Repeated inline fmt:setBundle (legacy pattern to consolidate)
        inline_bundle_count=$(grep -cE 'fmt:setBundle[^/]*/>[[:space:]]*<fmt:message' "$file" 2>/dev/null || true)
        inline_bundle_count="${inline_bundle_count:-0}"
        if [ "$inline_bundle_count" -gt 0 ]; then
            file_i18n_issues+=("LEGACY BUNDLE PATTERN: $inline_bundle_count inline fmt:setBundle+fmt:message pair(s) found")
            file_i18n_issues+=("  Fix: Move fmt:setBundle to a single declaration before <!DOCTYPE> and remove inline occurrences")
            file_i18n_issues+=("  See: docs/I18N-STANDARDS.md#bundle-declaration-rule")
        fi

        if [ ${#file_i18n_issues[@]} -gt 0 ]; then
            echo "$file (i18n)"
            for issue in "${file_i18n_issues[@]}"; do
                echo "   $issue"
            done
            echo
            ((i18n_issues++))
        fi
    fi

done < <(find . \( -name "*.jsp" -o -name "*.jspf" -o -name "*.tag" \) -type f -print0)

echo "════════════════════════════════════════"
echo "Files checked: $total_checked"
if [ "$I18N_ONLY" = false ]; then
    echo "Files with missing taglib declarations: $found_issues"
fi
if [ "$CHECK_I18N" = true ]; then
    echo "Files with i18n issues: $i18n_issues"
fi
echo

if [ "$I18N_ONLY" = false ] && [ $found_issues -eq 0 ]; then
    echo "All taglib declarations are present!"
elif [ "$I18N_ONLY" = false ] && [ $found_issues -gt 0 ]; then
    echo "Please add missing taglib declarations"
fi
if [ "$CHECK_I18N" = true ] && [ $i18n_issues -eq 0 ]; then
    echo "All i18n checks passed!"
elif [ "$CHECK_I18N" = true ] && [ $i18n_issues -gt 0 ]; then
    echo "Please resolve i18n issues (see docs/I18N-STANDARDS.md)"
fi
if [ "$found_issues" -gt 0 ] || [ "$i18n_issues" -gt 0 ]; then
    exit 1
fi

# Optional: Check for unused taglibs (reverse check)
if [ ! -t 0 ] || [ ! -t 1 ]; then
    exit 0
fi

echo
read -p "Check for unused taglib declarations? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo
    echo "=== Checking for Unused Taglibs ==="
    echo

    unused_count=0
    while IFS= read -r -d '' file; do
        # Skip taglib include files themselves (they're meant to be included, not use tags)
        if echo "$file" | grep -qE "${TAGLIB_INCLUDES}"; then
            continue
        fi

        unused=()

        for prefix in "${!tags[@]}"; do
            # Check if taglib is declared (either URI form)
            if grep -qE "taglib.*${tags[$prefix]}" "$file"; then
                # Check if tag is actually used
                if ! grep -qiE "<\s*${prefix}\s*:|[\$\{]${prefix}:" "$file"; then
                    unused+=("$prefix")
                fi
            fi
        done

        if [ ${#unused[@]} -gt 0 ]; then
            echo "$file"
            echo "   Unused taglib(s): ${unused[*]}"
            echo
            ((unused_count++))
        fi
    done < <(find . \( -name "*.jsp" -o -name "*.jspf" -o -name "*.tag" \) -type f -print0)

    echo "Files with unused taglibs: $unused_count"
fi

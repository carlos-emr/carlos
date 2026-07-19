#!/bin/bash

# check-jsp-taglibs.sh

echo "=== Comprehensive JSP Taglib Checker ==="
echo

declare -A tags=(
    # JSTL tags
    ["fmt"]="http://java.sun.com/jsp/jstl/fmt"
    ["c"]="http://java.sun.com/jsp/jstl/core"
    ["fn"]="http://java.sun.com/jsp/jstl/functions"
    ["sql"]="http://java.sun.com/jsp/jstl/sql"
    ["x"]="http://java.sun.com/jsp/jstl/xml"
)

# Patterns for taglib include files
TAGLIB_INCLUDES="taglibs\.jsp|taglibs\.jspf|common-taglibs\.jsp|common-tags\.jsp"

found_issues=0
total_checked=0

echo "Searching for JSP/JSPF/TAG files..."

while IFS= read -r -d '' file; do
    ((total_checked++))
    missing=()
    
    # Check for direct includes (both styles)
    has_direct_include=$(grep -E '<%@\s*include.*'"${TAGLIB_INCLUDES}" "$file")
    has_jsp_include=$(grep -E '<jsp:include.*'"${TAGLIB_INCLUDES}" "$file")
    has_taglib_include=""
    [ -n "$has_direct_include" ] || [ -n "$has_jsp_include" ] && has_taglib_include="yes"
    
    for prefix in "${!tags[@]}"; do
        # - Handles whitespace variations: <c:, < c:, <c :
        # - Case insensitive for the tag (though usually lowercase)
        # - Matches in attributes too: <div class="${c:...}">
        if grep -qiE "<\s*${prefix}\s*:|[\$\{]${prefix}:" "$file"; then
            
            # Skip if taglibs file is included
            if [ -n "$has_taglib_include" ]; then
                continue
            fi
            
            # Check if taglib is declared (and not commented out)
            if ! grep -v '<!--' "$file" | grep -q "taglib.*${tags[$prefix]}"; then
                missing+=("$prefix")
            fi
        fi
    done
    
    if [ ${#missing[@]} -gt 0 ]; then
        echo "$file"
        echo "   Missing taglib(s): ${missing[*]}"
        echo "   Add these declarations at the top:"
        for prefix in "${missing[@]}"; do
            echo "      <%@ taglib uri=\"${tags[$prefix]}\" prefix=\"$prefix\" %>"
        done
        echo
        ((found_issues++))
    fi
done < <(find . \( -name "*.jsp" -o -name "*.jspf" -o -name "*.tag" \) -type f -print0)

echo "════════════════════════════════════════"
echo "Files checked: $total_checked"
echo "Files with issues: $found_issues"

if [ $found_issues -eq 0 ]; then
    echo "All taglib declarations are present!"
else
    echo "Please add missing taglib declarations"
fi

# Optional: Check for unused taglibs (reverse check)
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
            # Check if taglib is declared
            if grep -q "taglib.*${tags[$prefix]}" "$file"; then
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
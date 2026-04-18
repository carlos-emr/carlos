#!/usr/bin/env bash
#
# check-encoder-null-safety.sh
#
# CI guard against two silent-failure classes introduced by the OWASP encoder
# migration (PR #1787):
#
#   Class A — missing taglib directive:
#     A JSP that uses <e:forXxx> without declaring the
#     owasp.encoder.jakarta.advanced taglib compiles clean but silently drops
#     the tag output. PR #1821 mass-fixed 311 such files; nothing prevents
#     recurrence.
#
#   Class B — null-to-"null" rendering:
#     Encode.forHtmlContent(null) returns the literal 4-character string
#     "null". Any <e:forXxx value='<%= nullableExpr %>'/> renders "null" where
#     the original <c:out> rendered empty.
#
# This script fails the build if a JSP contains any of:
#   1. An <e:forXxx> tag in a file that doesn't declare the
#      owasp.encoder.jakarta taglib.
#   2. Any new <e:forXxx> or ${e:forXxx(...)} call at all
#      (the carlos wrappers exist — use them).
#   3. Any new <%= Encode.forXxx(...) %> scriptlet call at all
#      (use SafeEncode.forXxx instead).
#
# Intentional exceptions are listed one-per-line in
# scripts/lint/encode-null-safety-allowlist.txt. Entries are glob patterns
# matched against the repo-relative file path.
#
# Exit code: 0 if clean, 1 if any violation found (excluding allowlisted files).

set -euo pipefail

REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ALLOWLIST_FILE="${REPO_ROOT}/scripts/lint/encode-null-safety-allowlist.txt"
JSP_ROOT="${REPO_ROOT}/src/main/webapp"

# Read allowlist, ignoring blank lines and #-comments.
allowlist=()
if [ -f "$ALLOWLIST_FILE" ]; then
    while IFS= read -r line; do
        # strip comments and whitespace
        trimmed="${line%%#*}"
        trimmed="${trimmed#"${trimmed%%[![:space:]]*}"}"
        trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
        [ -z "$trimmed" ] && continue
        allowlist+=("$trimmed")
    done < "$ALLOWLIST_FILE"
fi

is_allowlisted() {
    local relpath="$1"
    for pattern in "${allowlist[@]}"; do
        # shellcheck disable=SC2053
        if [[ "$relpath" == $pattern ]]; then
            return 0
        fi
    done
    return 1
}

violations=0
bugA_hits=0
bugB_tag_hits=0
bugB_el_hits=0
bugB_scriptlet_hits=0

# ---------------------------------------------------------------------------
# Class A: <e:for*> tag in a file missing the owasp.encoder.jakarta taglib.
# ---------------------------------------------------------------------------
while IFS= read -r file; do
    [ -z "$file" ] && continue
    relpath="${file#"$REPO_ROOT/"}"
    if is_allowlisted "$relpath"; then
        continue
    fi
    # Does the file use <e:for*> at all?
    if ! grep -qE '<e:for[A-Za-z]+(\s|/>)' "$file" 2>/dev/null; then
        continue
    fi
    # Does it declare the owasp.encoder.jakarta taglib?
    if grep -qE 'taglib[^>]*uri="owasp\.encoder\.jakarta' "$file"; then
        continue
    fi
    echo "ERROR [Class A — missing taglib]: $relpath uses <e:forXxx> but does not declare"
    echo "       <%@ taglib uri=\"owasp.encoder.jakarta.advanced\" prefix=\"e\" %>."
    echo "       Add the directive, OR migrate the tags to <carlos:encode>."
    bugA_hits=$((bugA_hits + 1))
    violations=$((violations + 1))
done < <(find "$JSP_ROOT" -type f \( -name '*.jsp' -o -name '*.jspf' \) 2>/dev/null | sort)

# ---------------------------------------------------------------------------
# Class B.1: Any use of <e:forXxx> or ${e:forXxx(...)} anywhere.
# (The carlos wrappers exist — new code should use them.)
# ---------------------------------------------------------------------------
while IFS= read -r file; do
    [ -z "$file" ] && continue
    relpath="${file#"$REPO_ROOT/"}"
    if is_allowlisted "$relpath"; then
        continue
    fi
    if grep -qE '<e:for[A-Za-z]+\s' "$file" 2>/dev/null; then
        count=$(grep -cE '<e:for[A-Za-z]+\s' "$file" 2>/dev/null || echo 0)
        echo "ERROR [Class B — <e:forXxx> tag]: $relpath ($count site(s))"
        echo "       Use <carlos:encode value=\"...\" context=\"...\"/> — it null-coalesces."
        bugB_tag_hits=$((bugB_tag_hits + count))
        violations=$((violations + 1))
    fi
    if grep -qE '\$\{\s*e:for[A-Za-z]+\(' "$file" 2>/dev/null; then
        count=$(grep -cE '\$\{\s*e:for[A-Za-z]+\(' "$file" 2>/dev/null || echo 0)
        echo "ERROR [Class B — \${e:forXxx} EL function]: $relpath ($count site(s))"
        echo "       Use \${carlos:forXxx(...)} — it null-coalesces."
        bugB_el_hits=$((bugB_el_hits + count))
        violations=$((violations + 1))
    fi
done < <(find "$JSP_ROOT" -type f \( -name '*.jsp' -o -name '*.jspf' \) 2>/dev/null | sort)

# ---------------------------------------------------------------------------
# Class B.2: <%= Encode.forXxx(...) %> scriptlet calls.
# (Use SafeEncode.forXxx(...) instead — Encode.* returns "null" for null input.)
# ---------------------------------------------------------------------------
while IFS= read -r file; do
    [ -z "$file" ] && continue
    relpath="${file#"$REPO_ROOT/"}"
    if is_allowlisted "$relpath"; then
        continue
    fi
    if grep -qE '<%=\s*Encode\.for[A-Za-z]+\(' "$file" 2>/dev/null; then
        count=$(grep -cE '<%=\s*Encode\.for[A-Za-z]+\(' "$file" 2>/dev/null || echo 0)
        echo "ERROR [Class B — Encode.forXxx scriptlet]: $relpath ($count site(s))"
        echo "       Use SafeEncode.forXxx(...) — it null-coalesces."
        bugB_scriptlet_hits=$((bugB_scriptlet_hits + count))
        violations=$((violations + 1))
    fi
done < <(find "$JSP_ROOT" -type f \( -name '*.jsp' -o -name '*.jspf' \) 2>/dev/null | sort)

echo ""
echo "========================================"
echo "Encoder null-safety lint summary"
echo "========================================"
echo "Class A (missing taglib)               : $bugA_hits file(s)"
echo "Class B (<e:forXxx> tag leftover)       : $bugB_tag_hits site(s)"
echo "Class B (\${e:forXxx} EL leftover)       : $bugB_el_hits site(s)"
echo "Class B (Encode.forXxx scriptlet leftover): $bugB_scriptlet_hits site(s)"
echo "Total violating files                  : $violations"
if [ $violations -eq 0 ]; then
    echo "Result: PASS"
    exit 0
else
    echo "Result: FAIL — see errors above."
    echo ""
    echo "If a violation is intentional, add the file path to"
    echo "  scripts/lint/encode-null-safety-allowlist.txt"
    echo "(glob patterns supported; one entry per line)."
    exit 1
fi

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
# Fails the build if any non-commented JSP/JSPF content contains:
#   1. <e:forXxx> without the owasp.encoder.jakarta taglib declared.
#   2. <e:forXxx> or ${e:forXxx(...)} anywhere (use <carlos:encode> / ${carlos:forXxx}).
#   3. <%= Encode.forXxx(...) %> scriptlet (use SafeEncode.forXxx).
#
# Intentional exceptions go in scripts/lint/encode-null-safety-allowlist.txt.
#
# Thin bash wrapper around the Python implementation for easy CI invocation.

set -euo pipefail
exec python3 "$(dirname "$0")/check-encoder-null-safety.py" "$@"

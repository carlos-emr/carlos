#!/usr/bin/env bash
#
# check-encoder-null-safety.sh
#
# CI guard against the silent-failure classes introduced by the OWASP encoder
# migration (PR #1787):
#
#   Class A — missing taglib directive:
#     A JSP that uses <e:forXxx> without declaring the OWASP encoder taglib
#     compiles clean, and Jasper writes the tag out as literal text.
#     PR #1821 mass-fixed 311 such files.
#
#   Class B — null-to-"null" rendering:
#     Encode.forHtmlContent(null) returns the literal 4-character string
#     "null". Any <e:forXxx value='<%= nullableExpr %>'/> renders "null" where
#     the original <c:out> rendered empty.
#
#   Class D — OWASP encoder JSP taglib declared:
#     org.owasp.encoder:encoder-jakarta-jsp is no longer shipped (#5477), so
#     a page declaring its taglib fails JSP translation.
#
# Fails the build if any non-commented JSP-family content (.jsp, .jspf,
# .jspx, .tag, .tagf, .tagx) contains:
#   1. <e:forXxx> without the owasp.encoder.jakarta taglib declared.
#   2. <e:forXxx>, or an e:forXxx(...) call anywhere inside ${...} / #{...},
#      nested calls included (use <carlos:encode> / ${carlos:forXxx}).
#   3. <%= Encode.forXxx(...) %> scriptlet (use SafeEncode.forXxx).
#   4. forHtmlContent inside an HTML attribute value (Class C).
#   5. An OWASP encoder taglib declaration (Class D; not allowlistable).
#
# Intentional Class A-C exceptions go in
# scripts/lint/encode-null-safety-allowlist.txt.
#
# Thin bash wrapper around the Python implementation for easy CI invocation.

set -euo pipefail
exec python3 "$(dirname "$0")/check-encoder-null-safety.py" "$@"

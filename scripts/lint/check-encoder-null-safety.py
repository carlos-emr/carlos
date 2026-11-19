#!/usr/bin/env python3
"""
check-encoder-null-safety.py

CI guard against the silent-failure classes introduced by the OWASP encoder
migration (PR #1787):

  Class A — missing taglib directive:
    A JSP that uses <e:forXxx> without declaring a taglib for the "e" prefix
    compiles clean, and Jasper writes the tag out as literal template text
    instead of the encoded value. PR #1821 mass-fixed 311 such files.

  Class B — null-to-"null" rendering:
    Encode.forHtmlContent(null) returns the literal 4-character string
    "null". Any <e:forXxx value='<%= nullableExpr %>'/> renders "null" where
    the original <c:out> rendered empty.

  Class D — OWASP encoder JSP taglib declared:
    CARLOS no longer ships org.owasp.encoder:encoder-jakarta-jsp (#5477), so
    a JSP that declares its taglib (owasp.encoder.jakarta[.advanced], or the
    legacy OWASP_Java_Encoder_Project URI) fails JSP translation at runtime.

Fails the build if any non-commented JSP-family content (.jsp, .jspf, .jspx,
.tag, .tagf, .tagx) contains:
  1. <e:forXxx> tag in a file without the owasp.encoder.jakarta taglib
     directive.
  2. Any <e:forXxx> tag, or any e:forXxx(...) call anywhere inside an EL
     expression (${...} or #{...}), including one nested as the argument of
     another function such as ${carlos:forJavaScript(e:forUriComponent(x))}.
     Use the CARLOS null-safe wrappers (<carlos:encode>, ${carlos:forXxx}).
  3. Any <%= Encode.forXxx(...) %> scriptlet — use SafeEncode.forXxx.
  4. forHtmlContent inside an HTML attribute value (Class C).
  5. A declaration of the OWASP encoder JSP taglib (Class D). The allowlist
     cannot exempt this: the library is not in the WAR, so the page would
     fail to translate whatever the allowlist says.

Comments (<%-- ... --%>) are stripped before pattern matching, because they
don't execute. Intentional runtime exceptions live in
scripts/lint/encode-null-safety-allowlist.txt (one glob per line).

Exit code: 0 if clean, 1 if any violation.
"""
from __future__ import annotations

import fnmatch
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
ALLOWLIST_FILE = REPO_ROOT / "scripts" / "lint" / "encode-null-safety-allowlist.txt"
JSP_ROOT = REPO_ROOT / "src" / "main" / "webapp"

# JSP-family sources Jasper translates. .jspx and .tag/.tagf/.tagx accept the same
# taglib directives, EL and encoder calls as .jsp/.jspf.
JSP_SUFFIXES = (".jsp", ".jspf", ".jspx", ".tag", ".tagf", ".tagx")

JSP_COMMENT_RE = re.compile(r"<%--.*?--%>", re.DOTALL)
TAG_RE = re.compile(r"<e:for[A-Za-z]+(?:\s|/>)")
# Start of an EL expression: immediate ${ or deferred #{. A backslash-escaped
# \${ / \#{ is literal template text in JSP and is not evaluated.
EL_START_RE = re.compile(r"(?<!\\)[$#]\{")
# An OWASP e:forXxx( function call. The word boundary keeps another prefix that
# merely ends in "e" (node:forX, some_e:forX) from matching, while an operator
# or bracket in front of it (a-e:forX, (e:forX) still counts. Applied only to
# EL expression bodies, so a call nested as an argument of another function is
# caught as well as one leading the expression (#5477: the earlier pattern,
# \$\{\s*e:for..., let ${carlos:forJavaScript(e:forUriComponent(x))} through).
EL_E_CALL_RE = re.compile(r"\be:for[A-Za-z]+\s*\(")
ENCODE_SCRIPTLET_RE = re.compile(r"<%=\s*Encode\.for[A-Za-z]+\s*\(")
# Class C — context misuse: forHtmlContent inside an HTML attribute value.
# `forHtmlContent` does NOT escape `"` or `'`, so a value containing a quote
# breaks the markup. Several sites were fixed by hand in
# `billingONCorrection.jsp`; this regex catches the pattern so it can't
# recur on a future JSP migration.
#
# The earlier shape only matched when the EL expression was the FIRST thing
# inside the attribute value (e.g. `value="${carlos:forHtmlContent(x)}"`).
# Real-world misuse like `value="prefix-${carlos:forHtmlContent(x)}"` or
# `class="foo ${carlos:forHtmlContent(x)}"` would silently pass — the very
# recurrence vector the rule exists to prevent. The current shape matches
# `carlos:forHtmlContent(` anywhere inside an attribute value, by treating
# anything between the opening quote and the EL marker as opaque content.
HTML_ATTR_CONTENT_MISUSE_RE = re.compile(
    r"""=\s*(["'])(?:(?!\1).)*?\$\{\s*carlos:forHtmlContent\s*\(""",
    re.DOTALL,
)
HTML_ATTR_CARLOS_TAG_RE = re.compile(
    r"""=\s*(["'])(?:(?!(?:\1|>)).)*?<carlos:encode\b(?P<tag>.*?)/>""",
    re.DOTALL,
)
CARLOS_TAG_CONTEXT_RE = re.compile(r"""context\s*=\s*(["'])(?P<context>[^"']+)\1""")
TAGLIB_DECL_RE = re.compile(
    r"""<%@\s*taglib\s+[^%>]*uri\s*=\s*["']owasp\.encoder\.jakarta""",
    re.IGNORECASE,
)
# Class D — any taglib declaration whose URI names the OWASP encoder JSP
# library: the Jakarta editions (owasp.encoder.jakarta and
# owasp.encoder.jakarta.advanced) and the legacy javax-era URI. Covers the
# standard directive, the XML-syntax directive and a jspx/tagx xmlns binding
# (optionally urn:jsptld:-prefixed).
TAGLIB_DIRECTIVE_RE = re.compile(
    r"""<%@\s*taglib\b(?P<attrs>.*?)%>|<jsp:directive\.taglib\b(?P<xattrs>.*?)>""",
    re.DOTALL | re.IGNORECASE,
)
URI_ATTR_RE = re.compile(r"""\buri\s*=\s*(["'])(?P<uri>.*?)\1""", re.DOTALL | re.IGNORECASE)
XMLNS_RE = re.compile(r"""\bxmlns:[\w.-]+\s*=\s*(["'])(?P<uri>.*?)\1""", re.DOTALL)
OWASP_TAGLIB_URI_RE = re.compile(
    r"""owasp\.encoder\.jakarta|OWASP_Java_Encoder_Project""", re.IGNORECASE
)


def load_allowlist() -> list[str]:
    patterns: list[str] = []
    if ALLOWLIST_FILE.exists():
        for raw in ALLOWLIST_FILE.read_text().splitlines():
            # strip comments and whitespace
            trimmed = raw.split("#", 1)[0].strip()
            if trimmed:
                patterns.append(trimmed)
    return patterns


def is_allowlisted(rel: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(rel, p) for p in patterns)


def el_expression_bodies(text: str):
    """Yield the body of every ${...} / #{...} EL expression in ``text``.

    Scans to the brace that closes each expression, so a ``}`` inside an EL
    string literal or a nested map/set literal does not end it early. An
    expression left unterminated runs to the end of the text: that is
    conservative (more text is searched, never less).
    """
    pos = 0
    length = len(text)
    while True:
        start = EL_START_RE.search(text, pos)
        if start is None:
            return
        i = start.end()
        depth = 1
        quote = None
        while i < length:
            ch = text[i]
            if quote is not None:
                if ch == "\\":
                    i += 2
                    continue
                if ch == quote:
                    quote = None
            elif ch in "'\"":
                quote = ch
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        yield text[start.end():i]
        pos = i + 1


def count_el_e_calls(text: str) -> int:
    """Count e:forXxx( calls anywhere inside EL expressions, nested or not."""
    return sum(len(EL_E_CALL_RE.findall(body)) for body in el_expression_bodies(text))


def owasp_taglib_declarations(text: str) -> list[str]:
    """Return the URIs of every OWASP encoder taglib declaration in ``text``."""
    uris: list[str] = []
    for directive in TAGLIB_DIRECTIVE_RE.finditer(text):
        attrs = directive.group("attrs")
        if attrs is None:
            attrs = directive.group("xattrs")
        for uri in URI_ATTR_RE.finditer(attrs):
            if OWASP_TAGLIB_URI_RE.search(uri.group("uri")):
                uris.append(uri.group("uri"))
    for binding in XMLNS_RE.finditer(text):
        if OWASP_TAGLIB_URI_RE.search(binding.group("uri")):
            uris.append(binding.group("uri"))
    return uris


def count_html_context_tag_attr_misuse(text: str) -> int:
    hits = 0
    for match in HTML_ATTR_CARLOS_TAG_RE.finditer(text):
        context = CARLOS_TAG_CONTEXT_RE.search(match.group("tag"))
        if context is None or context.group("context").lower() == "html":
            hits += 1
    return hits


def main() -> int:
    allowlist = load_allowlist()
    violations = 0
    classA_files = 0
    classB_tag_sites = 0
    classB_el_sites = 0
    classB_scriptlet_sites = 0
    classC_attr_sites = 0
    classD_files = 0

    for path in sorted(JSP_ROOT.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix not in JSP_SUFFIXES:
            continue
        rel = str(path.relative_to(REPO_ROOT))
        try:
            raw = path.read_text(encoding="utf-8", errors="surrogateescape")
        except OSError as exc:
            # An unreadable source must not pass silently as "clean".
            print(f"ERROR [unreadable]: {rel}: {exc}")
            violations += 1
            continue

        # Strip JSP comments before pattern matching — they don't execute.
        text = JSP_COMMENT_RE.sub("", raw)

        # Class D: the OWASP encoder JSP taglib is declared. Checked before
        # the allowlist: the library is not shipped (#5477), so no exception
        # can make such a page translate.
        owasp_uris = owasp_taglib_declarations(text)
        if owasp_uris:
            print(f"ERROR [Class D — OWASP encoder taglib declared]: {rel}")
            print(f"       Declares {', '.join(sorted(set(owasp_uris)))}.")
            print(
                "       org.owasp.encoder:encoder-jakarta-jsp is not in the WAR (#5477):"
                " remove the directive and use <carlos:encode> / ${carlos:forXxx(...)}"
                " with <%@ taglib uri=\"carlos\" prefix=\"carlos\" %>."
            )
            classD_files += 1
            violations += 1

        if is_allowlisted(rel, allowlist):
            continue

        # Class A: tag used without matching taglib declaration.
        if TAG_RE.search(text):
            if not TAGLIB_DECL_RE.search(text):
                print(f"ERROR [Class A — missing taglib]: {rel}")
                print(
                    "       Uses <e:forXxx> with no taglib bound to the e prefix;"
                    " Jasper writes the tag out as literal text."
                )
                print(
                    "       Migrate the tag to <carlos:encode value=\"...\" context=\"...\"/>"
                    " (the OWASP encoder taglib is no longer shipped, #5477)."
                )
                classA_files += 1
                violations += 1

        # Class B: tag leftover (use <carlos:encode>).
        tag_hits = len(TAG_RE.findall(text))
        if tag_hits:
            print(f"ERROR [Class B — <e:forXxx> tag]: {rel} ({tag_hits} site(s))")
            print("       Use <carlos:encode value=\"...\" context=\"...\"/>.")
            classB_tag_sites += tag_hits
            violations += 1

        # Class B: EL function leftover (use ${carlos:forXxx}).
        el_hits = count_el_e_calls(text)
        if el_hits:
            print(f"ERROR [Class B — ${{e:forXxx}} EL function]: {rel} ({el_hits} site(s))")
            print("       Use ${carlos:forXxx(...)}, including where e:forXxx(...) is nested"
                  " inside another function call.")
            classB_el_sites += el_hits
            violations += 1

        # Class B: Encode.forXxx scriptlet leftover (use SafeEncode.forXxx).
        scriptlet_hits = len(ENCODE_SCRIPTLET_RE.findall(text))
        if scriptlet_hits:
            print(
                f"ERROR [Class B — Encode.forXxx scriptlet]: {rel} ({scriptlet_hits} site(s))"
            )
            print("       Use SafeEncode.forXxx(...).")
            classB_scriptlet_sites += scriptlet_hits
            violations += 1

        # Class C: forHtmlContent used inside an HTML attribute value. The
        # `forHtmlContent` encoder does NOT escape quotes; a value containing
        # `"` or `'` breaks the markup. Use forHtmlAttribute for value="..."
        # contexts.
        attr_hits = len(HTML_ATTR_CONTENT_MISUSE_RE.findall(text))
        attr_hits += count_html_context_tag_attr_misuse(text)
        if attr_hits:
            print(
                f"ERROR [Class C — forHtmlContent in attribute context]: {rel} ({attr_hits} site(s))"
            )
            print("       Replace with ${carlos:forHtmlAttribute(...)} for value=\"...\" contexts.")
            classC_attr_sites += attr_hits
            violations += 1

    print()
    print("========================================")
    print("Encoder null-safety lint summary")
    print("========================================")
    print(f"Class A (missing taglib)                   : {classA_files} file(s)")
    print(f"Class B (<e:forXxx> tag leftover)          : {classB_tag_sites} site(s)")
    print(f"Class B (${{e:forXxx}} EL leftover)          : {classB_el_sites} site(s)")
    print(f"Class B (Encode.forXxx scriptlet leftover) : {classB_scriptlet_sites} site(s)")
    print(f"Class C (forHtmlContent in attr context)   : {classC_attr_sites} site(s)")
    print(f"Class D (OWASP encoder taglib declared)    : {classD_files} file(s)")
    print(f"Total violating files                      : {violations}")
    if violations == 0:
        print("Result: PASS")
        return 0
    else:
        print("Result: FAIL — see errors above.")
        print()
        print("If a Class A-C violation is intentional, add the file path (or a glob")
        print("pattern) to scripts/lint/encode-null-safety-allowlist.txt. Class D")
        print("cannot be allowlisted.")
        return 1


if __name__ == "__main__":
    sys.exit(main())

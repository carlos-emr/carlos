#!/usr/bin/env python3
"""
check-encoder-null-safety.py

CI guard against two silent-failure classes introduced by the OWASP encoder
migration (PR #1787):

  Class A — missing taglib directive:
    A JSP that uses <e:forXxx> without declaring the
    owasp.encoder.jakarta.advanced taglib compiles clean but silently drops
    the tag output. PR #1821 mass-fixed 311 such files; nothing prevents
    recurrence.

  Class B — null-to-"null" rendering:
    Encode.forHtmlContent(null) returns the literal 4-character string
    "null". Any <e:forXxx value='<%= nullableExpr %>'/> renders "null" where
    the original <c:out> rendered empty.

Fails the build if any non-commented JSP/JSPF content contains:
  1. <e:forXxx> tag in a file without the owasp.encoder.jakarta taglib
     directive.
  2. Any <e:forXxx> or ${e:forXxx(...)} usage anywhere — use the CARLOS
     null-safe wrappers (<carlos:encode>, ${carlos:forXxx}).
  3. Any <%= Encode.forXxx(...) %> scriptlet — use SafeEncode.forXxx.

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

JSP_COMMENT_RE = re.compile(r"<%--.*?--%>", re.DOTALL)
TAG_RE = re.compile(r"<e:for[A-Za-z]+(?:\s|/>)")
EL_FN_RE = re.compile(r"\$\{\s*e:for[A-Za-z]+\s*\(")
ENCODE_SCRIPTLET_RE = re.compile(r"<%=\s*Encode\.for[A-Za-z]+\s*\(")
TAGLIB_DECL_RE = re.compile(
    r"""<%@\s*taglib\s+[^%>]*uri\s*=\s*["']owasp\.encoder\.jakarta""",
    re.IGNORECASE,
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


def main() -> int:
    allowlist = load_allowlist()
    violations = 0
    classA_files = 0
    classB_tag_sites = 0
    classB_el_sites = 0
    classB_scriptlet_sites = 0

    for path in sorted(JSP_ROOT.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix not in (".jsp", ".jspf"):
            continue
        rel = str(path.relative_to(REPO_ROOT))
        if is_allowlisted(rel, allowlist):
            continue
        try:
            raw = path.read_text(encoding="utf-8", errors="surrogateescape")
        except OSError:
            continue

        # Strip JSP comments before pattern matching — they don't execute.
        text = JSP_COMMENT_RE.sub("", raw)

        # Class A: tag used without matching taglib declaration.
        if TAG_RE.search(text):
            if not TAGLIB_DECL_RE.search(text):
                print(f"ERROR [Class A — missing taglib]: {rel}")
                print(
                    "       Uses <e:forXxx> but does not declare"
                    " <%@ taglib uri=\"owasp.encoder.jakarta.advanced\" prefix=\"e\" %>."
                )
                print("       Either add the directive or migrate tags to <carlos:encode>.")
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
        el_hits = len(EL_FN_RE.findall(text))
        if el_hits:
            print(f"ERROR [Class B — ${{e:forXxx}} EL function]: {rel} ({el_hits} site(s))")
            print("       Use ${carlos:forXxx(...)}.")
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

    print()
    print("========================================")
    print("Encoder null-safety lint summary")
    print("========================================")
    print(f"Class A (missing taglib)                   : {classA_files} file(s)")
    print(f"Class B (<e:forXxx> tag leftover)          : {classB_tag_sites} site(s)")
    print(f"Class B (${{e:forXxx}} EL leftover)          : {classB_el_sites} site(s)")
    print(f"Class B (Encode.forXxx scriptlet leftover) : {classB_scriptlet_sites} site(s)")
    print(f"Total violating files                      : {violations}")
    if violations == 0:
        print("Result: PASS")
        return 0
    else:
        print("Result: FAIL — see errors above.")
        print()
        print("If a violation is intentional, add the file path (or a glob pattern)")
        print("to scripts/lint/encode-null-safety-allowlist.txt.")
        return 1


if __name__ == "__main__":
    sys.exit(main())

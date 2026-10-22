#!/usr/bin/env python3
"""
annotate-improper-unicode.py — suppress the Find Security Bugs ``IMPROPER_UNICODE``
detector per-site with a justified ``@SuppressFBWarnings`` annotation plus an adjacent
human-readable ``//`` comment.

WHY THIS EXISTS
---------------
``IMPROPER_UNICODE`` is an *informational* FindSecBugs detector. Per its source
(``ImproperHandlingUnicodeDetector``) it fires on the *method name alone*, regardless of
whether a ``Locale`` is supplied:

  * ``String.equalsIgnoreCase(...)``                         -> always
  * ``String.toLowerCase(...)`` / ``toUpperCase(...)``       -> when the enclosing method
                                                               also does a ``String.equals``
                                                               or ``indexOf`` comparison
  * ``Normalizer.normalize(...)`` / ``URI.toASCIIString()``
    / ``IDN.toASCII(...)``                                   -> always

Because the trigger is the *call*, the alert cannot be cleared by editing code (adding
``Locale.ROOT`` does not help; ``Normalizer`` trips it too). The agreed disposition is a
per-site ``@SuppressFBWarnings`` carrying a justification, mirrored by an inline ``//``
comment at the same site.

WHAT IT DOES
------------
For every ``.java`` under ``--root`` (default ``src/main/java``), it parses the file with
tree-sitter (Java 21 grammar — ``javalang`` cannot handle modern syntax), replicates the
detector's trigger rules, and for each flagged enclosing declaration (method / constructor /
field / type) inserts, immediately above it:

    // FindSecBugs IMPROPER_UNICODE: <reason>. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "<reason>")

and ensures ``import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;`` is present.
Sites in known security-sensitive files get the trust-path justification when their enclosing
declaration references trust-path tokens (auth, role/access/right decisions, scheme/host checks,
OAuth callbacks, file-extension allowlists). Everything else gets the benign justification.

USAGE
-----
    python3 scripts/lint/annotate-improper-unicode.py --dry-run            # inventory only
    python3 scripts/lint/annotate-improper-unicode.py --root src/main/java # apply
    ISSUE=1234 python3 scripts/lint/annotate-improper-unicode.py --only <path>...  # subset

The change is purely additive (comment + annotation + one import); no behavior change.
Re-runnable: a declaration already carrying ``@SuppressFBWarnings`` is left untouched.
"""
from __future__ import annotations

import argparse
import os
import re
from dataclasses import dataclass

import tree_sitter_java
from tree_sitter import Language, Parser

JAVA = Language(tree_sitter_java.language())

# --- detector replication -------------------------------------------------------------
ALWAYS = {"equalsIgnoreCase", "toASCIIString", "toASCII"}
CASE_MAP = {"toLowerCase", "toUpperCase"}
# `normalize` only counts as Normalizer.normalize; matched separately via receiver text.

ANNOTATABLE = {
    "method_declaration",
    "constructor_declaration",
    "compact_constructor_declaration",
    "field_declaration",
    "class_declaration",
    "interface_declaration",
    "enum_declaration",
    "record_declaration",
    "annotation_type_declaration",
}

IMPORT_LINE = "import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;"

REASON_BENIGN = ("case-insensitive comparison of an internal/domain value "
                 "(status/flag/enum/MIME/code); not a security or authorization decision")
REASON_SECURITY = ("case-fold in a trust path; locale-safe hardening tracked in {issue}")

# Files whose case-folding participates in (or guards) a trust decision. Matched by basename.
# A *site* in one of these files is only classified security-relevant when its enclosing
# declaration also references a trust token (below) — other methods in the same file (e.g.
# EForm's many benign equalsIgnoreCase comparisons) stay benign.
SECURITY_FILES = {
    "OAuth1SignatureVerifierImplementation.java",
    "OAuth1ParamParser.java",
    "OscarRequestTokenService.java",
    "ManageDocument2Action.java",
    "DocumentUpload2Action.java",
    "SRFaxProviderClient.java",
    "EForm.java",
    "SecurityInfoManagerImpl.java",
    "CaseManagementManagerImpl.java",
    "CaseManagementIssue.java",
    "AuthenticationInInterceptor.java",
    "AuthorizeResource.java",
    "LocalOnlyUserAgent.java",
    "EctDisplayAction.java",
}
TRUST_TOKEN = re.compile(
    r"scheme|https?|\bhost\b|content-?type|x-www-form|\.pdf|getRequestURI|oauth"
    r"|signature|redirect|callback|\bIDN\b|toASCII|auth(?:entication|orization|z)?"
    r"|privilege|role|rights?|access|security|ssrf|wadl",
    re.IGNORECASE,
)


@dataclass
class Target:
    node_type: str
    insert_row: int          # 0-based line to insert above
    indent: str
    security: bool


def text(node, src: bytes) -> str:
    return src[node.start_byte:node.end_byte].decode("utf8", "replace")


def walk(node):
    yield node
    for c in node.children:
        yield from walk(c)


def enclosing(node):
    """Nearest ancestor we can legally hang an annotation on."""
    n = node.parent
    while n is not None:
        if n.type in ANNOTATABLE:
            return n
        n = n.parent
    return None


TYPE_DECLS = {"class_declaration", "interface_declaration", "enum_declaration",
              "record_declaration", "annotation_type_declaration"}


def in_lambda(node) -> bool:
    """True if the call lives in a lambda body — its bytecode lands in a synthetic
    ``lambda$...`` method that a method-level @SuppressFBWarnings cannot cover, so such
    sites must be suppressed at the enclosing class level instead."""
    n = node.parent
    while n is not None:
        if n.type == "lambda_expression":
            return True
        if n.type in ("method_declaration", "constructor_declaration",
                      "compact_constructor_declaration"):
            return False
        n = n.parent
    return False


def enclosing_type(node):
    n = node.parent
    while n is not None:
        if n.type in TYPE_DECLS:
            return n
        n = n.parent
    return None


def method_scope(node):
    """Nearest enclosing method/constructor body, for the toLowerCase/equals proximity rule."""
    n = node.parent
    while n is not None:
        if n.type in ("method_declaration", "constructor_declaration",
                      "compact_constructor_declaration"):
            return n
        n = n.parent
    return None


def has_string_comparison(scope, src: bytes) -> bool:
    """The detector flags toLowerCase/toUpperCase only when the (enclosing) method also does a
    String comparison. SpotBugs works on bytecode, where ``equals``/``indexOf`` AND a string
    ``switch`` (which compiles to ``hashCode()``+``equals()``) all count. Mirror that here."""
    if scope is None:
        return False
    for n in walk(scope):
        if n.type in ("switch_expression", "switch_statement"):
            return True
        if n.type == "method_invocation":
            name = n.child_by_field_name("name")
            if name is not None and text(name, src) in ("equals", "indexOf"):
                return True
    return False


def already_suppressed(decl, src: bytes) -> bool:
    """True only when the declaration already suppresses IMPROPER_UNICODE.

    Other @SuppressFBWarnings values should not make this script silently skip a
    Unicode finding; if a future mixed site needs annotation merging, it should be
    visible in review instead of disappearing from the inventory.
    """
    for c in decl.children:
        if c.type == "modifiers":
            for m in c.children:
                if m.type in ("annotation", "marker_annotation"):
                    nm = m.child_by_field_name("name")
                    if nm is not None and text(nm, src).split(".")[-1] == "SuppressFBWarnings":
                        if "IMPROPER_UNICODE" in text(m, src):
                            return True
    return False


def find_targets(src: bytes, security_file: bool):
    parser = Parser()
    parser.language = JAVA
    tree = parser.parse(src)
    root = tree.root_node
    decls = {}  # decl.id -> Target  (dedupe per declaration)
    for n in walk(root):
        if n.type != "method_invocation":
            continue
        name_node = n.child_by_field_name("name")
        if name_node is None:
            continue
        mname = text(name_node, src)
        triggered = False
        if mname in ALWAYS:
            triggered = True
        elif mname == "normalize":
            obj = n.child_by_field_name("object")
            if obj is not None and "Normalizer" in text(obj, src):
                triggered = True
        elif mname in CASE_MAP:
            triggered = has_string_comparison(method_scope(n), src)
        if not triggered:
            continue
        decl = enclosing_type(n) if in_lambda(n) else enclosing(n)
        if decl is None or already_suppressed(decl, src):
            continue
        if decl.id in decls:
            continue
        row = decl.start_point[0]
        line = src.split(b"\n")[row].decode("utf8", "replace")
        indent = line[:len(line) - len(line.lstrip())]
        # A site is trust-path only inside a sensitive file AND when its enclosing
        # declaration actually references a trust token (scheme/host/extension/etc.).
        security = security_file and bool(TRUST_TOKEN.search(text(decl, src)))
        decls[decl.id] = Target(decl.type, row, indent, security)
    return list(decls.values())


def has_import(lines) -> bool:
    return any(line.strip() == IMPORT_LINE for line in lines)


def import_insert_row(lines) -> int:
    last_import = -1
    package_row = -1
    for i, line in enumerate(lines):
        s = line.strip()
        if s.startswith("import "):
            last_import = i
        elif s.startswith("package ") and package_row < 0:
            package_row = i
    if last_import >= 0:
        return last_import + 1
    if package_row >= 0:
        return package_row + 1
    return 0


def reason_for(t: "Target", issue: str) -> str:
    if t.security:
        return REASON_SECURITY.format(issue=("#" + issue if issue else "the hardening issue"))
    return REASON_BENIGN


def apply_file(path: str, issue: str, dry: bool):
    with open(path, "rb") as fh:
        raw = fh.read()
    security_file = os.path.basename(path) in SECURITY_FILES
    targets = find_targets(raw, security_file)
    if not targets:
        return 0, 0
    sec_sites = sum(1 for t in targets if t.security)
    if dry:
        return len(targets), sec_sites

    content = raw.decode("utf8")
    newline = "\r\n" if "\r\n" in content else "\n"
    lines = content.replace("\r\n", "\n").split("\n")
    # Insert annotations bottom-up so earlier row indices stay valid.
    for t in sorted(targets, key=lambda x: x.insert_row, reverse=True):
        reason = reason_for(t, issue)
        block = [
            f"{t.indent}// FindSecBugs IMPROPER_UNICODE: {reason}. See docs/static-analysis-workflows.md",
            f'{t.indent}@SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "{reason}")',
        ]
        lines[t.insert_row:t.insert_row] = block
    if not has_import(lines):
        r = import_insert_row(lines)
        lines[r:r] = [IMPORT_LINE]
    with open(path, "w", encoding="utf8", newline=newline) as fh:
        fh.write("\n".join(lines))
    return len(targets), sec_sites


def iter_java(root, only):
    if only:
        for p in only:
            yield p
        return
    for dirpath, _, names in os.walk(root):
        for nm in names:
            if nm.endswith(".java"):
                yield os.path.join(dirpath, nm)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="src/main/java")
    ap.add_argument("--only", nargs="*", help="explicit file list (overrides --root walk)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    issue = os.environ.get("ISSUE", "").lstrip("#")

    total_sites = total_files = total_sec = 0
    sec_file_list = []
    for path in iter_java(args.root, args.only):
        n, sec = apply_file(path, issue, args.dry_run)
        if n:
            total_sites += n
            total_files += 1
            total_sec += sec
            if sec:
                sec_file_list.append((path, sec, n))
    mode = "DRY-RUN" if args.dry_run else "APPLIED"
    print(f"[{mode}] {total_sites} annotation sites across {total_files} files "
          f"({total_sec} trust-path sites; issue={issue or 'UNSET'}).")
    if sec_file_list:
        print("  Trust-path sites (security justification):")
        for p, sec, n in sorted(sec_file_list):
            print(f"    {sec}/{n}  {p}")


if __name__ == "__main__":
    main()

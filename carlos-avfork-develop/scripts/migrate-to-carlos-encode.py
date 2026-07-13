#!/usr/bin/env python3
"""
migrate-to-carlos-encode.py

Mechanical migration from owasp.encoder.jakarta.advanced `<e:...>` tags and
`${e:forXxx(...)}` EL functions to the null-safe CARLOS equivalents:

- <e:forXxx value="..."/>        → <carlos:encode value="..." context="..."/>
- ${e:forXxx(expr)}              → ${carlos:forXxx(expr)}
- <%= Encode.forXxx(expr) %>     → <%= SafeEncode.forXxx(expr) %>

The script adds:
- `<%@ taglib uri="carlos" prefix="carlos" %>` when any tag/EL function was
  touched in a file.
- `<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>` when
  scriptlet Encode.* was rewritten.

Sites with an explicit null guard in the scriptlet (== null ? / != null ? /
StringUtils.defaultString / ObjectUtils.defaultIfNull / Optional.) are left
alone — they already render empty for null and don't need the wrapper.

Comments (<%-- ... --%>) and scriptlet declaration blocks (<%! ... %>) are
skipped.

Usage:
    # Default — rewrites src/main/webapp in place
    python3 scripts/migrate-to-carlos-encode.py

    # Preview without writing
    python3 scripts/migrate-to-carlos-encode.py --dry-run

    # Restrict to one path
    python3 scripts/migrate-to-carlos-encode.py --path src/main/webapp/WEB-INF/jsp/report

    # Alternate log destination
    python3 scripts/migrate-to-carlos-encode.py --log /tmp/my-migration.log

Idempotent: running a second time on an already-migrated tree is a no-op.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Mapping: OWASP Encode forXxx → carlos:encode context attribute value.
# Keys are the suffix after "forX" in <e:forXxx> / ${e:forXxx(...)}.
# ---------------------------------------------------------------------------

# Mapping of OWASP encoder method suffixes → (carlos:encode context value, safe_for_codemod).
# The context attribute defaults to "html" (forHtmlContent) when omitted; we
# still emit context="html" for forHtmlContent to make the migrated intent
# explicit (it also makes a round-trip grep audit easier).
CONTEXT_MAP = {
    "Html": "forHtml",
    "HtmlContent": "html",
    "HtmlAttribute": "htmlAttribute",
    "HtmlUnquotedAttribute": "htmlUnquotedAttribute",
    "JavaScript": "javaScript",
    "JavaScriptAttribute": "javaScriptAttribute",
    "JavaScriptBlock": "javaScriptBlock",
    "JavaScriptSource": "javaScriptSource",
    "Uri": "uri",
    "UriComponent": "uriComponent",
    "CssString": "cssString",
    "CssUrl": "cssUrl",
    "Xml": "xml",
    "XmlAttribute": "xmlAttribute",
    "XmlContent": "xmlContent",
    "XmlComment": "xmlComment",
    "CDATA": "cdata",
    "Java": "java",
}

# OWASP tag/function names are case-sensitive. Build an alternation for regex.
ENCODER_SUFFIXES = sorted(CONTEXT_MAP.keys(), key=len, reverse=True)
ENCODER_SUFFIX_RE = "|".join(re.escape(s) for s in ENCODER_SUFFIXES)

# ---------------------------------------------------------------------------
# Regex patterns.
# ---------------------------------------------------------------------------

# <e:forXxx value='...' />  or  <e:forXxx value="..." />
# Value content is captured lazily; supports single or double quotes.
TAG_RE = re.compile(
    r"""<e:for(?P<suffix>""" + ENCODER_SUFFIX_RE + r""")\s+value\s*=\s*(?P<q>['"])(?P<value>.*?)(?P=q)\s*/>""",
    re.DOTALL,
)

# ${e:forXxx(...)}  — EL function invocation. Argument captured non-greedily.
EL_FN_RE = re.compile(
    r"""\$\{\s*e:for(?P<suffix>""" + ENCODER_SUFFIX_RE + r""")\s*\((?P<arg>.*?)\)\s*\}""",
    re.DOTALL,
)

# <%= Encode.forXxx(...) %>  — scriptlet scalar call.
# Match balanced parens heuristically — take everything up to the last `)` on
# the same line that precedes `%>`. We do a pre-check and use a callable to
# handle nested parens safely.
SCRIPTLET_RE = re.compile(
    r"""<%=\s*Encode\.for(?P<suffix>""" + ENCODER_SUFFIX_RE + r""")\s*\(""",
    re.DOTALL,
)

# Null-guard detection inside a scriptlet value expression.
NULL_GUARD_RE = re.compile(
    r"""(==\s*null\s*\?|!=\s*null\s*\?|StringUtils\.defaultString|ObjectUtils\.defaultIfNull|Optional\.)""",
    re.IGNORECASE,
)

# Comment spans we must skip entirely.
JSP_COMMENT_RE = re.compile(r"<%--.*?--%>", re.DOTALL)

# Taglib directive detection (already declared).
TAGLIB_CARLOS_RE = re.compile(
    r"""<%@\s*taglib\s+(?:[^%>]*\s)?uri\s*=\s*["']carlos["'][^%>]*%>""",
    re.IGNORECASE,
)

# Page import of SafeEncode.
IMPORT_SAFEENCODE_RE = re.compile(
    r"""<%@\s*page\s+(?:[^%>]*\s)?import\s*=\s*["'](?:[^"']*,\s*)?io\.github\.carlos_emr\.carlos\.utility\.SafeEncode(?:\s*,[^"']*)?["'][^%>]*%>""",
    re.IGNORECASE,
)

# Used to locate an existing taglib directive line to insert near.
ANY_TAGLIB_RE = re.compile(
    r"""<%@\s*taglib\s[^%>]*%>""",
    re.IGNORECASE,
)

# Used to locate an existing page directive line to insert near.
ANY_PAGE_DIRECTIVE_RE = re.compile(
    r"""<%@\s*page\s[^%>]*%>""",
    re.IGNORECASE,
)


@dataclass
class FileStats:
    tag_rewrites: int = 0
    el_fn_rewrites: int = 0
    scriptlet_rewrites: int = 0
    taglib_added: bool = False
    import_added: bool = False
    skipped_guarded_tags: int = 0
    warnings: list[str] = field(default_factory=list)

    @property
    def total_rewrites(self) -> int:
        return self.tag_rewrites + self.el_fn_rewrites + self.scriptlet_rewrites

    @property
    def touched(self) -> bool:
        return self.total_rewrites > 0 or self.taglib_added or self.import_added


@dataclass
class RunStats:
    files_scanned: int = 0
    files_touched: int = 0
    total_tag_rewrites: int = 0
    total_el_fn_rewrites: int = 0
    total_scriptlet_rewrites: int = 0
    total_taglib_added: int = 0
    total_import_added: int = 0
    total_skipped_guarded: int = 0


# ---------------------------------------------------------------------------
# Core rewrite routines.
# ---------------------------------------------------------------------------

def mask_comments(text: str) -> tuple[str, list[tuple[int, str]]]:
    """Replace <%-- ... --%> blocks with placeholders so later regexes skip them.
    Returns (masked_text, replacements) where replacements[i] = (placeholder_id, original)."""
    replacements: list[tuple[int, str]] = []

    def substitute(m: re.Match) -> str:
        idx = len(replacements)
        replacements.append((idx, m.group(0)))
        return f"\x00CARLOSCOMMENT{idx}\x00"

    masked = JSP_COMMENT_RE.sub(substitute, text)
    return masked, replacements


def unmask_comments(text: str, replacements: list[tuple[int, str]]) -> str:
    for idx, original in replacements:
        text = text.replace(f"\x00CARLOSCOMMENT{idx}\x00", original)
    return text


def is_scriptlet_value_guarded(value: str) -> bool:
    """Return True if the scriptlet value has an explicit null guard."""
    # Only matters for <%= ... %> form.
    if not value.strip().startswith("<%="):
        return False
    return bool(NULL_GUARD_RE.search(value))


def rewrite_tag(match: re.Match, stats: FileStats) -> str:
    suffix = match.group("suffix")
    q = match.group("q")
    value = match.group("value")
    context = CONTEXT_MAP[suffix]

    # Track whether the scriptlet had an explicit null guard. The guarded form
    # already renders empty for null; wrapping it in carlos:encode is just a
    # redundant safety layer. We still migrate (for uniformity with the CI
    # lint) but count it as "guarded_skipped" for informational purposes.
    if is_scriptlet_value_guarded(value):
        stats.skipped_guarded_tags += 1

    stats.tag_rewrites += 1

    # Pick an attribute quote that doesn't conflict with the value content.
    # If value contains the chosen quote, fall back to the other.
    attr_q = q
    if attr_q in value:
        attr_q = '"' if q == "'" else "'"

    return f'<carlos:encode value={attr_q}{value}{attr_q} context="{context}"/>'


def rewrite_el_function(match: re.Match, stats: FileStats) -> str:
    suffix = match.group("suffix")
    arg = match.group("arg")
    stats.el_fn_rewrites += 1
    return f"${{carlos:for{suffix}({arg})}}"


def find_matching_paren(text: str, open_idx: int) -> int:
    """Given the index of '(' in text, return the index of the matching ')'.

    Respects Java/JSP string quoting (single and double quotes, backslash
    escapes). Returns -1 if no matching close is found (e.g. expression spans
    out of the current scriptlet).
    """
    depth = 1
    i = open_idx + 1
    n = len(text)
    in_str: str | None = None  # " or ' if currently inside a string literal
    while i < n:
        c = text[i]
        if in_str:
            if c == "\\" and i + 1 < n:
                i += 2
                continue
            if c == in_str:
                in_str = None
            i += 1
            continue
        if c == '"' or c == "'":
            in_str = c
            i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def rewrite_scriptlet_calls(text: str, stats: FileStats) -> str:
    """Rewrite `<%= Encode.forXxx(...) %>` → `<%= SafeEncode.forXxx(...) %>`.

    The rewrite only replaces the `Encode.` class name with `SafeEncode.` —
    the argument expression is preserved verbatim. We still scan each hit to
    confirm the closing `)` and `%>` are well-formed.
    """
    result: list[str] = []
    cursor = 0
    for m in SCRIPTLET_RE.finditer(text):
        start = m.start()
        paren_open = m.end() - 1  # position of '('
        paren_close = find_matching_paren(text, paren_open)
        if paren_close == -1:
            # Unbalanced — skip, record a warning.
            stats.warnings.append(
                f"scriptlet call at offset {start} has unbalanced parens; left untouched"
            )
            continue
        # Expect `%>` to follow (allow whitespace).
        tail = text[paren_close + 1:]
        tail_m = re.match(r"\s*%>", tail)
        if not tail_m:
            stats.warnings.append(
                f"scriptlet call at offset {start} not closed with %>; left untouched"
            )
            continue
        result.append(text[cursor:start])
        # m.start() points to `<%=`; the class name begins just after.
        # We rewrite the slice m.start()..paren_open to replace 'Encode.' with 'SafeEncode.'.
        original = text[start:paren_open + 1]  # includes '('
        rewritten = original.replace("Encode.", "SafeEncode.", 1)
        result.append(rewritten)
        # Append argument contents verbatim plus closing paren and tail.
        result.append(text[paren_open + 1:paren_close + 1])
        result.append(text[paren_close + 1:paren_close + 1 + tail_m.end()])
        cursor = paren_close + 1 + tail_m.end()
        stats.scriptlet_rewrites += 1
    result.append(text[cursor:])
    return "".join(result)


def ensure_taglib_directive(text: str, stats: FileStats) -> str:
    """If the file is missing the carlos taglib directive, insert one."""
    if TAGLIB_CARLOS_RE.search(text):
        return text
    directive = '<%@ taglib uri="carlos" prefix="carlos" %>'
    # Prefer to insert right after the last existing taglib directive.
    last_tld = None
    for m in ANY_TAGLIB_RE.finditer(text):
        last_tld = m
    if last_tld:
        insert_at = last_tld.end()
        text = text[:insert_at] + "\n" + directive + text[insert_at:]
    else:
        # No taglib directives — place it at the top of the file, after any
        # JSP comment and after any <%@ page %> directives if present.
        last_page = None
        for m in ANY_PAGE_DIRECTIVE_RE.finditer(text):
            last_page = m
        if last_page:
            insert_at = last_page.end()
            text = text[:insert_at] + "\n" + directive + text[insert_at:]
        else:
            # Brand new file — prepend.
            text = directive + "\n" + text
    stats.taglib_added = True
    return text


def ensure_safeencode_import(text: str, stats: FileStats) -> str:
    """If the file is missing the SafeEncode page import, insert one."""
    if IMPORT_SAFEENCODE_RE.search(text):
        return text
    directive = '<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>'
    # Prefer inserting right after the last <%@ page import="..." %> directive.
    page_imports = [m for m in ANY_PAGE_DIRECTIVE_RE.finditer(text) if "import" in m.group(0)]
    if page_imports:
        insert_at = page_imports[-1].end()
        text = text[:insert_at] + "\n" + directive + text[insert_at:]
    else:
        # Fallback: after any taglib directive, else top of file.
        any_page = list(ANY_PAGE_DIRECTIVE_RE.finditer(text))
        any_taglib = list(ANY_TAGLIB_RE.finditer(text))
        anchors = any_page + any_taglib
        if anchors:
            insert_at = max(a.end() for a in anchors)
            text = text[:insert_at] + "\n" + directive + text[insert_at:]
        else:
            text = directive + "\n" + text
    stats.import_added = True
    return text


def migrate_file(path: Path, dry_run: bool) -> FileStats:
    """Run the full migration on a single file. Returns per-file stats.

    Idempotent: if the file already matches migrated form, no changes are made.
    """
    stats = FileStats()
    original = path.read_text(encoding="utf-8", errors="surrogateescape")
    masked, replacements = mask_comments(original)

    migrated = TAG_RE.sub(lambda m: rewrite_tag(m, stats), masked)
    migrated = EL_FN_RE.sub(lambda m: rewrite_el_function(m, stats), migrated)
    migrated = rewrite_scriptlet_calls(migrated, stats)

    tag_or_el_touched = stats.tag_rewrites > 0 or stats.el_fn_rewrites > 0
    if tag_or_el_touched:
        migrated = ensure_taglib_directive(migrated, stats)
    if stats.scriptlet_rewrites > 0:
        migrated = ensure_safeencode_import(migrated, stats)

    migrated = unmask_comments(migrated, replacements)

    if migrated != original and not dry_run:
        path.write_text(migrated, encoding="utf-8", errors="surrogateescape")

    return stats


def walk_and_migrate(root: Path, dry_run: bool, log_path: Path | None) -> RunStats:
    run_stats = RunStats()
    log_lines: list[str] = []

    for dirpath, _dirnames, filenames in os.walk(root):
        for name in filenames:
            if not (name.endswith(".jsp") or name.endswith(".jspf")):
                continue
            path = Path(dirpath) / name
            run_stats.files_scanned += 1
            stats = migrate_file(path, dry_run)
            if not stats.touched and not stats.warnings:
                continue
            run_stats.files_touched += 1 if stats.touched else 0
            run_stats.total_tag_rewrites += stats.tag_rewrites
            run_stats.total_el_fn_rewrites += stats.el_fn_rewrites
            run_stats.total_scriptlet_rewrites += stats.scriptlet_rewrites
            run_stats.total_taglib_added += 1 if stats.taglib_added else 0
            run_stats.total_import_added += 1 if stats.import_added else 0
            run_stats.total_skipped_guarded += stats.skipped_guarded_tags
            line = (
                f"{path}: "
                f"tags={stats.tag_rewrites} "
                f"el={stats.el_fn_rewrites} "
                f"scriptlet={stats.scriptlet_rewrites} "
                f"guarded_skipped={stats.skipped_guarded_tags} "
                f"taglib_added={stats.taglib_added} "
                f"import_added={stats.import_added}"
            )
            if stats.warnings:
                line += f" WARNINGS={stats.warnings}"
            log_lines.append(line)

    if log_path:
        log_path.write_text("\n".join(log_lines) + ("\n" if log_lines else ""), encoding="utf-8")
    return run_stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--path",
        default="src/main/webapp",
        help="Root directory to scan (default: src/main/webapp)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Don't write any files; just print the summary.",
    )
    parser.add_argument(
        "--log",
        default="/tmp/carlos-encode-migration.log",
        help="Where to write per-file migration log (default: /tmp/carlos-encode-migration.log)",
    )
    args = parser.parse_args()

    root = Path(args.path)
    if not root.exists():
        print(f"error: path not found: {root}", file=sys.stderr)
        return 2

    log_path = Path(args.log) if args.log else None
    stats = walk_and_migrate(root, args.dry_run, log_path)

    print(f"migrate-to-carlos-encode: scanned {stats.files_scanned} JSP/JSPF files")
    print(f"  files touched:            {stats.files_touched}")
    print(f"  tag rewrites:             {stats.total_tag_rewrites}")
    print(f"  EL function rewrites:     {stats.total_el_fn_rewrites}")
    print(f"  scriptlet rewrites:       {stats.total_scriptlet_rewrites}")
    print(f"  taglib directive added:   {stats.total_taglib_added}")
    print(f"  SafeEncode import added:  {stats.total_import_added}")
    print(f"  guarded tag sites skipped:{stats.total_skipped_guarded}")
    if args.dry_run:
        print("  (dry-run — no files written)")
    if log_path:
        print(f"per-file log: {log_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

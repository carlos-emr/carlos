#!/usr/bin/env python3
"""generate_manifests.py — regenerate the OSCAR 19 import manifests.

Parses the OSCAR 19 schema sources (a Bitbucket oscaremr/oscar checkout) and
the CARLOS Flyway migration set, diffs them, deep-merges the hand-curated
overlays (overrides_schema.py / overrides_props.py in this directory), and
writes the shipped manifest modules:

    debian/assets/carlos_ctl/o19map_schema.py
    debian/assets/carlos_ctl/o19map_props.py

plus the generated-data block inside debian/assets/carlos_ctl/o19_preflight.py
(rewritten between its BEGIN/END GENERATED DATA markers; the file is skipped
with a note while it does not exist yet).

The generated modules are DATA — never edit them by hand. Curation lives in
the overrides files, which survive regeneration. Any O19 table not classified
by the overlay is emitted with class "unknown", which the manifest integrity
test (debian/assets/carlos_ctl/tests/test_manifest_integrity.py) fails on —
forcing every table to be consciously classified before the manifest ships.

Usage:
    python3 scripts/migration/o19/generate_manifests.py --oscar-src /path/to/oscar
    python3 scripts/migration/o19/generate_manifests.py --oscar-src /path/to/oscar --check

--check regenerates in memory and exits non-zero if the committed outputs
differ (drift detection for reviews).

The CARLOS migration directory is only ever READ.
"""

from __future__ import annotations

import argparse
import datetime
import importlib.util
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

REPO_ROOT = Path(__file__).resolve().parents[3]
CTL_DIR = REPO_ROOT / "debian" / "assets" / "carlos_ctl"
MIGRATION_DIR = REPO_ROOT / "database" / "mysql" / "migration"

# O19 schema sources, relative to --oscar-src, in load order. Data/ICD scripts
# are included because some of them CREATE tables (icd9.sql creates `icd9`,
# which exists in both systems — omitting these made icd9 look CARLOS-only).
O19_SQL_SOURCES = [
    "database/mysql/oscarinit.sql",
    "database/mysql/oscarinit_on.sql",
    "database/mysql/oscardata.sql",
    "database/mysql/oscardata_on.sql",
    "database/mysql/icd9.sql",
    "database/mysql/icd10.sql",
    "database/mysql/measurementMapData.sql",
    "database/mysql/caisi/*.sql",
    "database/mysql/caisi/updates/*.sql",
    "database/mysql/olis/olisinit.sql",
    "database/mysql/updates/*.sql",
]

O19_PROPERTIES = "src/main/resources/oscar_mcmaster.properties"
CARLOS_PROPERTIES = REPO_ROOT / "src" / "main" / "resources" / "carlos.properties"

MARKER_BEGIN = "# === BEGIN GENERATED DATA (generate_manifests.py) ==="
MARKER_END = "# === END GENERATED DATA ==="

COLUMN_KEYWORDS = {
    "primary", "unique", "key", "index", "constraint", "foreign",
    "fulltext", "check", "spatial",
}


# --------------------------------------------------------------------------
# SQL DDL parsing (quote- and paren-aware; regexes only locate statements)
# --------------------------------------------------------------------------

def _walk_parens(text: str, open_idx: int) -> int:
    """Return index just past the ')' matching the '(' at open_idx.

    Respects '...', "..." and `...` quoting with backslash escapes, so
    parentheses inside string literals or quoted identifiers do not count.
    """
    depth = 0
    i = open_idx
    n = len(text)
    while i < n:
        c = text[i]
        if c in ("'", '"', "`"):
            quote = c
            i += 1
            while i < n:
                if text[i] == "\\" and quote != "`":
                    i += 2
                    continue
                if text[i] == quote:
                    # '' / "" style escaped quote inside literal
                    if quote != "`" and i + 1 < n and text[i + 1] == quote:
                        i += 2
                        continue
                    break
                i += 1
            i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError("unbalanced parentheses at offset {}".format(open_idx))


def _split_top_level(body: str) -> List[str]:
    """Split on commas not nested in parens or quotes."""
    parts: List[str] = []
    depth = 0
    cur: List[str] = []
    i = 0
    n = len(body)
    while i < n:
        c = body[i]
        if c in ("'", '"', "`"):
            quote = c
            cur.append(c)
            i += 1
            while i < n:
                cur.append(body[i])
                if body[i] == "\\" and quote != "`":
                    i += 1
                    if i < n:
                        cur.append(body[i])
                    i += 1
                    continue
                if body[i] == quote:
                    if quote != "`" and i + 1 < n and body[i + 1] == quote:
                        i += 1
                        cur.append(body[i])
                        i += 1
                        continue
                    break
                i += 1
            i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    parts.append("".join(cur))
    return parts


def strip_line_comments(text: str) -> str:
    """Remove `-- ` and `#` line comments outside string literals.

    initcaisi.sql carries a commented-out `-- CREATE TABLE surveyData` whose
    empty body would otherwise REPLACE the real oscarinit definition. Quote
    state is tracked so INSERT data containing '--' is left untouched.
    """
    out: List[str] = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c in ("'", '"', "`"):
            quote = c
            out.append(c)
            i += 1
            while i < n:
                out.append(text[i])
                if text[i] == "\\" and quote != "`":
                    i += 1
                    if i < n:
                        out.append(text[i])
                    i += 1
                    continue
                if text[i] == quote:
                    if quote != "`" and i + 1 < n and text[i + 1] == quote:
                        i += 1
                        out.append(text[i])
                        i += 1
                        continue
                    break
                i += 1
            i += 1
            continue
        if c == "#" or (c == "-" and text[i:i + 3] in ("-- ", "--\n", "--\r")
                        or text[i:i + 2] == "--" and i + 2 == n):
            while i < n and text[i] != "\n":
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


_CREATE_RE = re.compile(
    r"create\s+table\s+(?:if\s+not\s+exists\s+)?`?(\w+)`?\s*\(", re.I)
_DROP_RE = re.compile(r"drop\s+table\s+(?:if\s+exists\s+)?`?(\w+)`?", re.I)
_ALTER_RE = re.compile(r"alter\s+table\s+`?(\w+)`?\s+", re.I)
_RENAME_RE = re.compile(r"rename\s+table\s+`?(\w+)`?\s+to\s+`?(\w+)`?", re.I)
_INSERT_RE = re.compile(
    r"insert\s+(?:ignore\s+)?into\s+`?(\w+)`?[^;]*?values\s*", re.I)


class Schema:
    """Table -> ordered {column: type}, plus primary keys."""

    def __init__(self) -> None:
        self.tables: Dict[str, Dict[str, str]] = {}
        self.pks: Dict[str, List[str]] = {}

    def apply_create(self, name: str, body: str) -> None:
        cols: Dict[str, str] = {}
        pk: List[str] = []
        for part in _split_top_level(body):
            part = part.strip()
            if not part:
                continue
            m = re.match(r"`?(\w+)`?\s*(.*)", part, re.S)
            if not m:
                continue
            first = m.group(1)
            if first.lower() in COLUMN_KEYWORDS:
                pm = re.match(r"primary\s+key\s*\((.*)\)", part, re.I | re.S)
                if pm:
                    pk = [c.strip().strip("`").split("(")[0]
                          for c in pm.group(1).split(",")]
                continue
            ctype = re.sub(r"\s+", " ", m.group(2)).strip().rstrip(",")
            inline_pk = re.search(r"\bprimary\s+key\b", ctype, re.I)
            if inline_pk and not pk:
                pk = [first]
            cols[first] = ctype
        # a later CREATE for the same table (updates re-creating) replaces it
        self.tables[name] = cols
        if pk:
            self.pks[name] = pk
        elif name in self.pks:
            del self.pks[name]

    def apply_alter(self, name: str, clause_text: str) -> None:
        if name not in self.tables:
            # ALTER against a table this source set never created (e.g. a
            # BC-only table altered by a shared update script) — ignore.
            return
        cols = self.tables[name]
        for clause in _split_top_level(clause_text):
            clause = clause.strip().rstrip(";").strip()
            m = re.match(r"add\s+(?:column\s+)?`?(\w+)`?\s+(.+)", clause,
                         re.I | re.S)
            if m and m.group(1).lower() not in COLUMN_KEYWORDS:
                cols[m.group(1)] = re.sub(r"\s+", " ", m.group(2)).strip()
                continue
            m = re.match(r"drop\s+(?:column\s+)?`?(\w+)`?\s*$", clause, re.I)
            if m and m.group(1).lower() not in (
                    "primary", "index", "key", "foreign"):
                cols.pop(m.group(1), None)
                continue
            m = re.match(r"change\s+(?:column\s+)?`?(\w+)`?\s+`?(\w+)`?\s+(.+)",
                         clause, re.I | re.S)
            if m:
                old, new, ctype = m.group(1), m.group(2), m.group(3)
                if old in cols:
                    # preserve position: rebuild dict with rename in place
                    rebuilt: Dict[str, str] = {}
                    for k, v in cols.items():
                        if k == old:
                            rebuilt[new] = re.sub(r"\s+", " ", ctype).strip()
                        else:
                            rebuilt[k] = v
                    self.tables[name] = rebuilt
                    cols = rebuilt
                continue
            # MODIFY changes type only — column set is unaffected; other
            # clauses (indexes, engine, charset) don't affect the column map.

    def feed(self, text: str) -> None:
        # Statements MUST be applied in document order: dump-style sources
        # (the CARLOS V1 baseline included) pair `DROP TABLE IF EXISTS x`
        # with the `CREATE TABLE x` that follows it — phase-ordered
        # application (all CREATEs, then all DROPs) would delete every table
        # a file both drops and creates. CREATE bodies are extracted by
        # paren-walking so nothing inside string literals is misread, and
        # the cursor skips past each body so statement text inside INSERTed
        # strings is never re-matched.
        patterns = (("create", _CREATE_RE), ("drop", _DROP_RE),
                    ("rename", _RENAME_RE), ("alter", _ALTER_RE))
        i = 0
        n = len(text)
        while i < n:
            best: Optional[Tuple[int, str, "re.Match[str]"]] = None
            for kind, rx in patterns:
                m = rx.search(text, i)
                if m and (best is None or m.start() < best[0]):
                    best = (m.start(), kind, m)
            if best is None:
                break
            _, kind, m = best
            if kind == "create":
                open_idx = m.end() - 1
                try:
                    close = _walk_parens(text, open_idx)
                except ValueError:
                    i = m.end()
                    continue
                self.apply_create(m.group(1), text[open_idx + 1:close - 1])
                i = close
            elif kind == "drop":
                self.tables.pop(m.group(1), None)
                self.pks.pop(m.group(1), None)
                i = m.end()
            elif kind == "rename":
                old, new = m.group(1), m.group(2)
                if old in self.tables:
                    self.tables[new] = self.tables.pop(old)
                    if old in self.pks:
                        self.pks[new] = self.pks.pop(old)
                i = m.end()
            else:  # alter
                stmt_end = text.find(";", m.end())
                if stmt_end == -1:
                    stmt_end = n
                self.apply_alter(m.group(1), text[m.end():stmt_end])
                i = stmt_end + 1


def count_insert_rows(text: str) -> Dict[str, int]:
    """Count VALUES tuples per table (extended INSERTs counted per tuple)."""
    counts: Dict[str, int] = {}
    for m in _INSERT_RE.finditer(text):
        table = m.group(1)
        i = m.end()
        # walk tuples: '(' ... ')' [, '(' ... ')']* until ';'
        n = len(text)
        rows = 0
        while i < n:
            while i < n and text[i] in " \r\n\t":
                i += 1
            if i >= n or text[i] != "(":
                break
            i = _walk_parens(text, i)
            rows += 1
            while i < n and text[i] in " \r\n\t":
                i += 1
            if i < n and text[i] == ",":
                i += 1
                continue
            break
        counts[table] = counts.get(table, 0) + rows
    return counts


def read_sql(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def load_schema(files: List[Path]) -> Schema:
    schema = Schema()
    for f in files:
        schema.feed(strip_line_comments(read_sql(f)))
    return schema


def expand_sources(base: Path, patterns: List[str]) -> List[Path]:
    out: List[Path] = []
    for pat in patterns:
        if "*" in pat:
            out.extend(sorted(base.glob(pat)))
        else:
            p = base / pat
            if p.is_file():
                out.append(p)
            else:
                print("warning: missing O19 source {}".format(p),
                      file=sys.stderr)
    return out


# --------------------------------------------------------------------------
# properties parsing
# --------------------------------------------------------------------------

def parse_properties(path: Path) -> Dict[str, str]:
    """Active (uncommented) key=value pairs; last occurrence wins."""
    out: Dict[str, str] = {}
    for raw in path.read_text(encoding="latin-1").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        m = re.match(r"([A-Za-z0-9_.\-]+)\s*[=:]\s*(.*)$", line)
        if m:
            out[m.group(1)] = m.group(2).strip()
    return out


# --------------------------------------------------------------------------
# overlay loading and manifest assembly
# --------------------------------------------------------------------------

def load_module(path: Path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)  # type: ignore[union-attr]
    return mod


def default_nondefault_expr(coltype: str, col: str) -> str:
    t = coltype.lower()
    if re.match(r"(tiny|small|medium|big)?int|decimal|double|float|numeric", t):
        return "s.`{0}` IS NOT NULL AND s.`{0}` <> 0".format(col)
    return "s.`{0}` IS NOT NULL AND s.`{0}` <> ''".format(col)


def build_tables(o19: Schema, carlos: Schema, ov) -> Dict[str, dict]:
    tables: Dict[str, dict] = {}
    shared = sorted(set(o19.tables) & set(carlos.tables))
    o19_only = sorted(set(o19.tables) - set(carlos.tables))

    merge_keys = dict(ov.CLASS_MERGE)
    reference = set(ov.CLASS_REFERENCE)
    replace_seed = set(getattr(ov, "REPLACE_SEED", ()))
    archive_patient = set(ov.ARCHIVE_PATIENT)
    archive_other = set(ov.ARCHIVE_OTHER)
    drop = set(ov.DROP)
    renames = dict(getattr(ov, "RENAMES", {}))          # table -> {target: source}
    value_exprs = dict(getattr(ov, "VALUE_EXPRS", {}))  # table -> {target: expr}
    b3 = set(ov.B3_COLUMNS)                             # {(table, col)}
    b3_exprs = dict(getattr(ov, "B3_NONDEFAULT_EXPRS", {}))
    chunk_tables = set(ov.CHUNK_TABLES)
    charset_scan = dict(ov.CHARSET_SCAN)

    for t in shared:
        entry: Dict[str, object] = {}
        if t in reference:
            entry["class"] = "reference"
            tables[t] = entry
            continue
        if t in merge_keys:
            entry["class"] = "merge"
            entry["merge_keys"] = list(merge_keys[t])
            for k in merge_keys[t]:
                if k not in carlos.tables[t]:
                    raise SystemExit(
                        "merge key {}.{} not a CARLOS column".format(t, k))
            pk = carlos.pks.get(t) or []
            if (len(pk) == 1 and pk[0] not in merge_keys[t]
                    and "int" in carlos.tables[t].get(pk[0], "").lower()):
                # integer surrogate id off the natural key: the ETL must
                # reassign it on appended rows instead of copying the
                # clinic's id (which may collide with a CARLOS seed row).
                entry["surrogate_pk"] = pk[0]
        else:
            entry["class"] = "copy"
            if t in replace_seed:
                entry["replace_seed"] = True
        t_ren = renames.get(t, {})
        cols: List[str] = []
        ren_out: Dict[str, str] = {}
        for target in carlos.tables[t]:
            source = t_ren.get(target, target)
            if source in o19.tables[t]:
                cols.append(target)
                if source != target:
                    ren_out[target] = source
        entry["cols"] = cols
        if ren_out:
            entry["renames"] = ren_out
        mapped_sources = {t_ren.get(c, c) for c in cols}
        dropped: Dict[str, dict] = {}
        for source_col, coltype in o19.tables[t].items():
            if source_col in mapped_sources:
                continue
            d: Dict[str, object] = {
                "nondefault": b3_exprs.get(
                    (t, source_col),
                    default_nondefault_expr(coltype, source_col)),
            }
            if (t, source_col) in b3:
                d["b3"] = True
            dropped[source_col] = d
        if dropped:
            entry["dropped"] = dropped
        if t in value_exprs:
            entry["value_exprs"] = dict(value_exprs[t])
        if t in chunk_tables:
            pk = carlos.pks.get(t) or o19.pks.get(t) or []
            if len(pk) != 1:
                raise SystemExit(
                    "chunk table {} needs a single-column PK, found {}"
                    .format(t, pk))
            entry["chunk_by"] = pk[0]
        if t in charset_scan:
            for c in charset_scan[t]:
                if c not in carlos.tables[t] or c not in o19.tables[t]:
                    raise SystemExit(
                        "charset_scan column {}.{} missing on one side"
                        .format(t, c))
            entry["charset_scan"] = list(charset_scan[t])
        tables[t] = entry

    # overlay entries naming tables that exist on neither side are stale
    # (typo, or the table moved between patch levels) — surface them.
    all_names = set(o19.tables) | set(carlos.tables)
    for bucket, names in (("ARCHIVE_PATIENT", archive_patient),
                          ("ARCHIVE_OTHER", archive_other),
                          ("DROP", drop), ("CLASS_REFERENCE", reference),
                          ("REPLACE_SEED", replace_seed),
                          ("CLASS_MERGE", set(merge_keys)),
                          ("CHUNK_TABLES", chunk_tables)):
        for name in sorted(names - all_names):
            print("warning: overlay {} names unknown table {}"
                  .format(bucket, name), file=sys.stderr)

    for t in o19_only:
        if t in archive_patient:
            tables[t] = {"class": "archive", "patient_data": True,
                         "accept_class": "archived-forms"}
        elif t in archive_other:
            tables[t] = {"class": "archive"}
        elif t in drop:
            tables[t] = {"class": "drop"}
        else:
            tables[t] = {"class": "unknown"}
    return tables


# --------------------------------------------------------------------------
# emission
# --------------------------------------------------------------------------

def _fmt(obj, indent: int = 0) -> str:
    """Deterministic, diff-friendly repr wrapped for the 100-col house style."""
    pad = "    " * indent
    if isinstance(obj, dict):
        if not obj:
            return "{}"
        lines = ["{"]
        for k in obj:
            lines.append("{}    {!r}: {},".format(
                pad, k, _fmt(obj[k], indent + 1)))
        lines.append(pad + "}")
        return "\n".join(lines)
    if isinstance(obj, list):
        if not obj:
            return "[]"
        one = "[" + ", ".join(repr(x) for x in obj) + "]"
        if len(one) + len(pad) <= 96:
            return one
        lines = ["["]
        for x in obj:
            lines.append("{}    {!r},".format(pad, x))
        lines.append(pad + "]")
        return "\n".join(lines)
    return repr(obj)


GENERATED_HEADER = """\
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
# GENERATED by scripts/migration/o19/generate_manifests.py — DO NOT EDIT.
# Curation lives in scripts/migration/o19/overrides_schema.py /
# overrides_props.py; edit those and regenerate.
"""


def emit_schema_module(tables, carlos: Schema, seed_counts, ov,
                       o19_commit: str) -> str:
    copy_tables = sorted(t for t, e in tables.items()
                         if e["class"] in ("copy", "merge"))
    carlos_columns = {t: list(carlos.tables[t]) for t in copy_tables}
    seeded = {t: seed_counts[t] for t in sorted(seed_counts)
              if t in copy_tables and seed_counts[t] > 0}
    out = [GENERATED_HEADER]
    out.append('"""OSCAR 19 -> CARLOS schema manifest (Ontario profile)."""\n')
    out.append("SCHEMA_MAP_VERSION = {!r}".format(ov.SCHEMA_MAP_VERSION))
    out.append("O19_PROFILE = 'on'")
    out.append("O19_SOURCE_COMMIT = {!r}".format(o19_commit))
    out.append("GENERATED_AT = {!r}\n".format(
        datetime.date.today().isoformat()))
    out.append("TABLES = " + _fmt(tables) + "\n")
    out.append("CARLOS_COLUMNS = " + _fmt(carlos_columns) + "\n")
    out.append("# rows the CARLOS Flyway migrations seed into copy/merge-class"
               " tables (P0 pristine\n# sweep compares live counts against"
               " these; every other copy-class table must be empty)")
    out.append("SEED_ROW_COUNTS = " + _fmt(seeded) + "\n")
    out.append("CARLOSDOC_SEED_DELETES = "
               + _fmt(list(ov.CARLOSDOC_SEED_DELETES)) + "\n")
    out.append("SEED_PROVIDER_NO = {!r}".format(ov.SEED_PROVIDER_NO))
    out.append("SEED_USER_NAME = {!r}".format(ov.SEED_USER_NAME))
    return "\n".join(out) + "\n"


def emit_props_module(o19_defaults, ov) -> str:
    out = [GENERATED_HEADER]
    out.append('"""OSCAR 19 -> CARLOS properties manifest."""\n')
    out.append("PROPS_MAP_VERSION = {!r}\n".format(ov.PROPS_MAP_VERSION))
    out.append("# active keys of the stock O19 oscar_mcmaster.properties —"
               " the baseline-diff\n# reference: clinic keys equal to these"
               " defaults are ignored (CARLOS defaults win)")
    out.append("O19_DEFAULTS = " + _fmt(dict(sorted(o19_defaults.items())))
               + "\n")
    out.append("KEYS = " + _fmt(dict(sorted(ov.KEYS.items()))) + "\n")
    out.append("PREFIX_RULES = " + _fmt(list(ov.PREFIX_RULES)) + "\n")
    return "\n".join(out) + "\n"


def emit_preflight_data(tables, ov) -> str:
    known = {t: e["class"] for t, e in sorted(tables.items())}
    patient = sorted(t for t, e in tables.items() if e.get("patient_data"))
    b3_cols: Dict[str, Dict[str, str]] = {}
    for t, e in sorted(tables.items()):
        for col, d in e.get("dropped", {}).items():
            if d.get("b3"):
                b3_cols.setdefault(t, {})[col] = d["nondefault"]
    lines = [MARKER_BEGIN]
    lines.append("SCHEMA_MAP_VERSION = {!r}".format(ov.SCHEMA_MAP_VERSION))
    lines.append("PATIENT_DATA_TABLES = " + _fmt(patient))
    lines.append("KNOWN_TABLES = " + _fmt(known))
    lines.append("B3_FLAGGED_COLUMNS = " + _fmt(b3_cols))
    lines.append("DROPPED_PROP_PREFIXES = "
                 + _fmt(list(ov.PREFLIGHT_DROPPED_PROP_PREFIXES)))
    lines.append(MARKER_END)
    return "\n".join(lines)


def rewrite_markers(path: Path, block: str) -> bool:
    text = path.read_text(encoding="utf-8")
    b = text.find(MARKER_BEGIN)
    e = text.find(MARKER_END)
    if b == -1 or e == -1 or e < b:
        raise SystemExit(
            "{}: generated-data markers missing or malformed".format(path))
    new = text[:b] + block + text[e + len(MARKER_END):]
    if new != text:
        path.write_text(new, encoding="utf-8")
        return True
    return False


# --------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--oscar-src", required=True,
                    help="path to an oscaremr/oscar (OSCAR 19) checkout")
    ap.add_argument("--check", action="store_true",
                    help="verify committed outputs match; write nothing")
    args = ap.parse_args()

    oscar = Path(args.oscar_src)
    if not (oscar / "database/mysql/oscarinit.sql").is_file():
        return ap.error("--oscar-src does not look like an OSCAR checkout")

    here = Path(__file__).resolve().parent
    ov_schema = load_module(here / "overrides_schema.py")
    ov_props = load_module(here / "overrides_props.py")

    o19 = load_schema(expand_sources(oscar, O19_SQL_SOURCES))
    carlos_files = sorted((MIGRATION_DIR / "common").glob("*.sql")) + \
        sorted((MIGRATION_DIR / "on").glob("*.sql"))
    carlos = load_schema(carlos_files)

    seed_counts: Dict[str, int] = {}
    for f in carlos_files:
        for t, n in count_insert_rows(read_sql(f)).items():
            seed_counts[t] = seed_counts.get(t, 0) + n

    commit = "unknown"
    head = oscar / ".git"
    if head.exists():
        import subprocess
        cp = subprocess.run(["git", "-C", str(oscar), "rev-parse", "HEAD"],
                            capture_output=True, text=True)
        if cp.returncode == 0:
            commit = cp.stdout.strip()

    tables = build_tables(o19, carlos, ov_schema)
    unknown = sorted(t for t, e in tables.items() if e["class"] == "unknown")
    if unknown:
        print("NOTE: {} O19-only tables are UNCLASSIFIED (class 'unknown'):"
              .format(len(unknown)), file=sys.stderr)
        for t in unknown:
            print("  " + t, file=sys.stderr)
        print("classify them in overrides_schema.py — the integrity test "
              "fails while any remain.", file=sys.stderr)

    o19_defaults = parse_properties(oscar / O19_PROPERTIES)

    schema_out = emit_schema_module(tables, carlos, seed_counts, ov_schema,
                                    commit)
    props_out = emit_props_module(o19_defaults, ov_props)
    preflight_block = emit_preflight_data(tables, ov_schema)

    targets = [
        (CTL_DIR / "o19map_schema.py", schema_out),
        (CTL_DIR / "o19map_props.py", props_out),
    ]
    if args.check:
        rc = 0
        for path, content in targets:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                print("DRIFT: {} differs from regenerated content".format(path))
                rc = 1
        return rc

    for path, content in targets:
        path.write_text(content, encoding="utf-8")
        print("wrote {}".format(path))

    preflight = CTL_DIR / "o19_preflight.py"
    if preflight.is_file():
        if rewrite_markers(preflight, preflight_block):
            print("rewrote generated-data block in {}".format(preflight))
        else:
            print("generated-data block in {} already current".format(preflight))
    else:
        print("note: {} does not exist yet — generated-data block skipped"
              .format(preflight))
    return 0


if __name__ == "__main__":
    sys.exit(main())

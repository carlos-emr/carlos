#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
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
import hashlib
import importlib.util
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
# the message bundle a carried sign-line template's ${...} tokens resolve
# against, on each side of the migration
O19_RESOURCE_BUNDLE = "src/main/resources/oscarResources_en.properties"
CARLOS_RESOURCE_BUNDLE = (REPO_ROOT / "src" / "main" / "resources"
                          / "oscarResources_en.properties")
# CARLOS data-normalisation script the importer replays on imported rows
# (Flyway runs on the stock deploy before the import and never sees them)
PREVENTION_TYPE_SCRIPT = (
    REPO_ROOT / "database" / "mysql" / "updates"
    / "update-2026-03-10-standardize-prevention-types.sql")
PREVENTION_ITEMS_XML = (REPO_ROOT / "src" / "main" / "resources" / "oscar"
                        / "prevention" / "PreventionItems.xml")
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
        if c == "#" or (c == "-"
                        and text[i:i + 3] in ("-- ", "--\t", "--\n", "--\r")
                        or text[i:i + 2] == "--" and i + 2 == n):
            while i < n and text[i] != "\n":
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


_CREATE_RE = re.compile(
    r"create\s+table\s+(if\s+not\s+exists\s+)?`?(\w+)`?\s*\(", re.I)
_DROP_RE = re.compile(r"drop\s+table\s+(?:if\s+exists\s+)?`?(\w+)`?", re.I)
_ALTER_RE = re.compile(r"alter\s+table\s+`?(\w+)`?\s+", re.I)
_RENAME_RE = re.compile(r"rename\s+table\s+`?(\w+)`?\s+to\s+`?(\w+)`?", re.I)
_INSERT_RE = re.compile(
    r"insert\s+(ignore\s+)?into\s+`?(\w+)`?[^;]*?values\s*", re.I)


class Statement:
    """One DDL statement located by walk_ddl().

    `body` is the CREATE's parenthesised column list or the ALTER's clause
    text; `sql` is the statement's source text, which the MariaDB oracle in
    verify_ddl_parse.py replays verbatim.
    """

    __slots__ = ("kind", "table", "body", "sql", "if_not_exists", "new_name")

    def __init__(self, kind: str, table: str, body: str = "", sql: str = "",
                 if_not_exists: bool = False, new_name: str = "") -> None:
        self.kind = kind
        self.table = table
        self.body = body
        self.sql = sql
        self.if_not_exists = if_not_exists
        self.new_name = new_name


def walk_ddl(text: str):
    """Yield the CREATE/DROP/RENAME/ALTER statements of `text`, in order.

    Document order is not a nicety: dump-style sources (the CARLOS V1
    baseline included) pair `DROP TABLE IF EXISTS x` with the `CREATE TABLE
    x` that follows it, so phase-ordered application -- all CREATEs, then
    all DROPs -- would delete every table a file both drops and creates.

    CREATE bodies are extracted by paren-walking so nothing inside a string
    literal is misread, and the cursor skips past each body so statement
    text inside an INSERTed string is never re-matched.

    Kept separate from Schema so the parse has two consumers: the model
    below, and verify_ddl_parse.py, which replays the same statements
    through a real MariaDB and compares. One walk, so the oracle cannot
    drift from the thing it verifies.
    """
    patterns = (("create", _CREATE_RE), ("drop", _DROP_RE),
                ("rename", _RENAME_RE), ("alter", _ALTER_RE))
    i = 0
    n = len(text)
    while i < n:
        # the earliest match across all four patterns, keyed on the offset
        # alone so nothing else in the tuple is ever compared
        found = [(m.start(), kind, m)
                 for kind, rx in patterns
                 for m in (rx.search(text, i),) if m]
        if not found:
            return
        _, kind, m = min(found, key=lambda hit: hit[0])
        if kind == "create":
            open_idx = m.end() - 1
            try:
                close = _walk_parens(text, open_idx)
            except ValueError:
                i = m.end()
                continue
            yield Statement("create", m.group(2),
                            body=text[open_idx + 1:close - 1],
                            sql=text[m.start():close],
                            if_not_exists=bool(m.group(1)))
            i = close
        elif kind == "drop":
            yield Statement("drop", m.group(1), sql=text[m.start():m.end()])
            i = m.end()
        elif kind == "rename":
            yield Statement("rename", m.group(1),
                            sql=text[m.start():m.end()], new_name=m.group(2))
            i = m.end()
        else:  # alter
            stmt_end = text.find(";", m.end())
            if stmt_end == -1:
                stmt_end = n
            yield Statement("alter", m.group(1),
                            body=text[m.end():stmt_end],
                            sql=text[m.start():stmt_end])
            i = stmt_end + 1


_POSITION_RE = re.compile(r"\s+(?:(first)|after\s+`?(\w+)`?)\s*$", re.I)


def split_column_position(ctype: str) -> Tuple[str, Optional[str]]:
    """Split a trailing ``FIRST`` / ``AFTER <col>`` off a column definition.

    Returns ``(type_text, position)`` where position is None, "FIRST", or the
    column to sit after. Leaving the clause inside the type text was wrong
    twice over: the column landed at the end of the table rather than where
    MySQL puts it, and "AFTER id" travelled on as part of the declared type,
    which default_nondefault_expr() and the surrogate-key check then read.
    """
    m = _POSITION_RE.search(ctype)
    if not m:
        return ctype, None
    return ctype[:m.start()].rstrip(), ("FIRST" if m.group(1) else m.group(2))


def place_column(cols: Dict[str, str], name: str, ctype: str,
                 position: Optional[str]) -> Dict[str, str]:
    """Insert or move `name` per MySQL's FIRST / AFTER positioning.

    Column ORDER is not cosmetic here: it is what the manifest emits as each
    table's `cols` list, so a generator that always appends describes a table
    the clinic does not have. 27 ALTERs in the OSCAR 19 corpus position a
    column this way -- `vacancy.vacancyName AFTER id`,
    `demographic.preferred_lang AFTER chart_no`, `formConsult.t_name2 AFTER
    t_name` among them.

    An AFTER naming a column the model does not carry falls back to
    appending; a real server would refuse the statement outright, so there is
    no correct position to choose and appending at least keeps the column.
    """
    if position is None and name in cols:
        cols[name] = ctype                     # plain redefinition, in place
        return cols
    rest = [(k, v) for k, v in cols.items() if k != name]
    if position == "FIRST":
        return dict([(name, ctype)] + rest)
    anchor = None
    if position is not None:
        lower = position.lower()
        anchor = next((k for k, _v in rest if k.lower() == lower), None)
    if anchor is None:
        return dict(rest + [(name, ctype)])
    out: Dict[str, str] = {}
    for k, v in rest:
        out[k] = v
        if k == anchor:
            out[name] = ctype
    return out


def resolve_pk_case(pk: List[str], cols: Dict[str, str]) -> List[str]:
    """Bind each PRIMARY KEY name to the column it actually names.

    MySQL resolves a column reference case-insensitively, so
    ``ID int(10) ... PRIMARY KEY (id)`` -- which OSCAR's update-hsfo.sql
    writes for three tables -- has `ID` as its primary key, not `id`.
    Recording the clause's spelling verbatim left `pks[t][0]` naming a
    column absent from `tables[t]`, and the surrogate-PK detection in
    build_tables() looks the name up in that dict: the miss reads as "no
    integer surrogate key", so no id map is built and no child foreign
    key is remapped through one. Harmless for the three tables that hit
    it today (all archive-class, which carry neither), and silent if one
    ever became copy- or merge-class.

    A name matching no column is dropped: MySQL would refuse the CREATE
    outright, so carrying it forward could only mislead.
    """
    by_lower = {c.lower(): c for c in cols}
    return [by_lower[c.lower()] for c in pk if c.lower() in by_lower]


class Schema:
    """Table -> ordered {column: type}, plus primary keys.

    if_not_exists_mode:
      "skip"  — a guarded CREATE on an existing table is a no-op (exact
                MySQL semantics; right for the deterministic CARLOS side).
      "union" — a guarded CREATE merges columns the table does not have
                yet. Right for the O19 side, whose patch history re-issues
                tables through overlapping scripts: the manifest should be
                the SUPERSET of live schemas, with per-site stragglers
                handled by the ETL's runtime column intersection and the
                preflight unknown-column flow.
    """

    def __init__(self, if_not_exists_mode: str = "skip") -> None:
        self.tables: Dict[str, Dict[str, str]] = {}
        self.pks: Dict[str, List[str]] = {}
        self.if_not_exists_mode = if_not_exists_mode

    def apply_create(self, name: str, body: str,
                     if_not_exists: bool = False) -> None:
        # CREATE TABLE IF NOT EXISTS on an existing table is a NO-OP in
        # MySQL — the restore migrations (V1.0.5/V1.0.6) re-issue old
        # definitions guarded this way, and replacing the baseline's
        # fuller definition with them silently dropped columns.
        merge_into: Optional[Dict[str, str]] = None
        if name in self.tables:
            if self.if_not_exists_mode == "union":
                # patch-soup model: a re-issued CREATE (guarded or not) on a
                # live database never removes columns — an unguarded one
                # simply errors and a guarded one no-ops — so the parsed
                # schema unions columns instead of replacing the table.
                merge_into = self.tables[name]
            elif if_not_exists:
                return
        cols: Dict[str, str] = {}
        pk: List[str] = []
        for part in _split_top_level(body):
            part = part.strip()
            if not part:
                continue
            m = re.match(r"(`?)(\w+)`?\s*(.*)", part, re.S)
            if not m:
                continue
            quoted, first = m.group(1) == "`", m.group(2)
            # A BACKTICKED first token is a column name, never a
            # constraint keyword -- MySQL requires the quoting precisely
            # so a column may be called `key`, `index` or `check`. Without
            # this, such a column vanished from the map entirely: not
            # copied, not listed as dropped, not shadow-captured, and
            # invisible to the unruled-rename gate, which is a silent
            # data drop rather than a parse warning. O19 has one live
            # case (`key` in phr_document_ext), harmless today only
            # because that table is archive-class and carries no column
            # map.
            if not quoted and first.lower() in COLUMN_KEYWORDS:
                pm = re.match(r"primary\s+key\s*\((.*)\)", part, re.I | re.S)
                if pm:
                    # `col`(20) -- MySQL allows an index prefix in a
                    # PRIMARY KEY. Split the prefix off FIRST: stripping
                    # backticks first leaves the closing one attached to
                    # the name (`col` -> col`(20) -> col`).
                    pk = [c.strip().split("(")[0].strip().strip("`")
                          for c in pm.group(1).split(",")]
                continue
            ctype = re.sub(r"\s+", " ", m.group(3)).strip().rstrip(",")
            inline_pk = re.search(r"\bprimary\s+key\b", ctype, re.I)
            if inline_pk and not pk:
                pk = [first]
            cols[first] = ctype
        if merge_into is not None:
            existing_lower = {c.lower() for c in merge_into}
            for c, ctype in cols.items():
                if c.lower() not in existing_lower:
                    merge_into[c] = ctype
            return
        # a later unguarded CREATE (updates re-creating) replaces it
        self.tables[name] = cols
        pk = resolve_pk_case(pk, cols)
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
            # parenthesized multi-column form: ADD (a INT, b VARCHAR(5))
            m = re.match(r"add\s+(?:column\s+)?\((.+)\)\s*$", clause,
                         re.I | re.S)
            if m:
                for part in _split_top_level(m.group(1)):
                    pm = re.match(r"\s*`?(\w+)`?\s+(.+)", part, re.S)
                    if pm and pm.group(1).lower() not in COLUMN_KEYWORDS:
                        cols[pm.group(1)] = re.sub(
                            r"\s+", " ", pm.group(2)).strip()
                continue
            # `ALTER TABLE x DROP PRIMARY KEY` -- ignored entirely until the
            # MariaDB oracle compared keys on ALTERs, which is how seven
            # tables in the O19 update history (rschedule, scheduledate,
            # reportTemplates, hash_audit, log, billingperclimit,
            # msgDemoMap) kept a key the clinic's database does not have.
            # The generic DROP COLUMN branch below never sees this: its
            # keyword guard excludes "primary".
            if re.match(r"drop\s+primary\s+key\s*$", clause, re.I):
                self.pks.pop(name, None)
                continue
            # ...and its mirror: `ADD PRIMARY KEY (col, ...)`, which the
            # ADD COLUMN branch below never reaches because its keyword
            # guard excludes "primary". reportTemplates drops the key and
            # re-adds it this way in the same statement.
            # The optional name between `KEY` and `(` is an index name:
            # `ADD PRIMARY KEY billcenter_code(billcenter_code)`
            # (update-2012-10-30.sql, the single occurrence in the corpus).
            # MySQL accepts it and then discards it -- a primary key is
            # always called PRIMARY -- so it is skipped, not recorded.
            pm = re.match(r"add\s+(?:constraint\s+`?\w+`?\s+)?primary\s+"
                          r"key(?:\s+`?\w+`?)?\s*\((.*)\)\s*$",
                          clause, re.I | re.S)
            if pm:
                added = [c.strip().split("(")[0].strip().strip("`")
                         for c in pm.group(1).split(",")]
                added = resolve_pk_case(added, cols)
                if added:
                    self.pks[name] = added
                continue
            m = re.match(r"add\s+(?:column\s+)?(?:if\s+not\s+exists\s+)?"
                         r"`?(\w+)`?\s+(.+)", clause, re.I | re.S)
            if m and m.group(1).lower() not in COLUMN_KEYWORDS:
                ctype, position = split_column_position(
                    re.sub(r"\s+", " ", m.group(2)).strip())
                cols = place_column(cols, m.group(1), ctype, position)
                self.tables[name] = cols
                # ...and the other half of the same pair: five of those
                # seven files drop the key and then add a column that
                # declares the new one inline
                if re.search(r"\bprimary\s+key\b", ctype, re.I):
                    self.pks[name] = [m.group(1)]
                continue
            m = re.match(r"drop\s+(?:column\s+)?`?(\w+)`?\s*$", clause, re.I)
            if m and m.group(1).lower() not in (
                    "primary", "index", "key", "foreign"):
                # a column reference is case-insensitive in MySQL, so
                # `DROP COLUMN name` drops a column declared `NAME`
                victim = next((c for c in cols
                               if c.lower() == m.group(1).lower()), None)
                if victim is not None:
                    del cols[victim]
                continue
            m = re.match(r"change\s+(?:column\s+)?`?(\w+)`?\s+`?(\w+)`?\s+(.+)",
                         clause, re.I | re.S)
            if m:
                old, new, ctype = m.group(1), m.group(2), m.group(3)
                # `change name NAME ...` renames the column declared `name`:
                # the reference is case-insensitive even though the result
                # is not. Matching case-sensitively left the old spelling
                # standing (update-2012-11-11.sql on vacancy_template).
                old = next((c for c in cols if c.lower() == old.lower()), old)
                if old in cols and name in self.pks:
                    # the key follows the column: leaving the old spelling
                    # in self.pks names a column that no longer exists, and
                    # the surrogate-key detection looks that name up in
                    # tables[t] -- the same miss resolve_pk_case() fixes for
                    # CREATE, one statement later
                    self.pks[name] = [new if c == old else c
                                      for c in self.pks[name]]
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
        # A DROP COLUMN takes the column out of the primary key too, and a
        # key naming no column would be looked up in tables[t] and missed.
        # Re-resolving against the final column set covers every clause that
        # removes or re-spells one, including any added later.
        if name in self.pks:
            resolved = resolve_pk_case(self.pks[name], cols)
            if resolved:
                self.pks[name] = resolved
            else:
                del self.pks[name]

    def feed(self, text: str) -> None:
        """Apply every DDL statement in `text`, in document order."""
        for stmt in walk_ddl(text):
            self.apply(stmt)

    def apply(self, stmt: "Statement") -> None:
        """Apply one walked statement to the model."""
        if stmt.kind == "create":
            self.apply_create(stmt.table, stmt.body,
                              if_not_exists=stmt.if_not_exists)
        elif stmt.kind == "drop":
            self.tables.pop(stmt.table, None)
            self.pks.pop(stmt.table, None)
        elif stmt.kind == "rename":
            old, new = stmt.table, stmt.new_name
            if old in self.tables:
                self.tables[new] = self.tables.pop(old)
                if old in self.pks:
                    self.pks[new] = self.pks.pop(old)
        else:  # alter
            self.apply_alter(stmt.table, stmt.body)


def count_insert_rows(text: str) -> Dict[str, int]:
    """Count VALUES tuples per table (extended INSERTs counted per tuple).

    INSERT IGNORE tuples count too: forward migrations seed whole tables
    that way (V1.0.5's lst_* lookups, bed_type), and skipping them left
    those copy-class tables with a floor of 0 that every Flyway-built
    target violates. A re-added row that a later migration deletes is
    accounted for by SEED_COUNT_DELETIONS, not by the counter."""
    counts: Dict[str, int] = {}
    for m in _INSERT_RE.finditer(text):
        table = m.group(2)
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


def _unquote_sql(body: str) -> str:
    """Undo SQL string-literal quoting: backslash-escaped characters
    (`\\'`, `\\\\`; any other `\\x` becomes `x`, which is what the seeds
    contain) and doubled `''`. Not a full MySQL unescaper — it serves the
    role-name and prevention-code columns only."""
    out = []
    i = 0
    n = len(body)
    while i < n:
        c = body[i]
        if c == "\\" and i + 1 < n:
            out.append(body[i + 1])
            i += 2
        elif c == "'" and i + 1 < n and body[i + 1] == "'":
            out.append("'")
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def seed_string_column(text: str, table: str, index: int) -> List[str]:
    """The `index`-th quoted field of every VALUES tuple inserted into
    `table` (e.g. secRole.role_name), in file order."""
    out: List[str] = []
    for m in _INSERT_RE.finditer(text):
        if m.group(2) != table:
            continue
        i = m.end()
        n = len(text)
        while i < n:
            while i < n and text[i] in " \r\n\t":
                i += 1
            if i >= n or text[i] != "(":
                break
            end = _walk_parens(text, i)
            fields = _split_top_level(text[i + 1:end - 1])
            if len(fields) > index:
                f = fields[index].strip()
                if len(f) >= 2 and f[0] == f[-1] and f[0] in "'\"":
                    out.append(_unquote_sql(f[1:-1]))
            i = end
            while i < n and text[i] in " \r\n\t":
                i += 1
            if i < n and text[i] == ",":
                i += 1
                continue
            break
    return out


_PREVENTION_MAP_RE = re.compile(
    r"UPDATE\s+preventions\s+SET\s+prevention_type\s*=\s*'([^']+)'\s+"
    r"WHERE\s+prevention_type\s*=\s*'([^']+)'\s*;", re.I)


def parse_prevention_type_map(text: str) -> Dict[str, str]:
    """legacy prevention_type -> canonical code, from the direct-mapping
    UPDATE statements of the standardize-prevention-types script (the
    script's catch-all section uses a different statement shape and is
    deliberately not replicated by the importer)."""
    out: Dict[str, str] = {}
    for canonical, legacy in _PREVENTION_MAP_RE.findall(text):
        if legacy in out and out[legacy] != canonical:
            raise SystemExit("prevention type {!r} mapped twice ({} / {})"
                             .format(legacy, out[legacy], canonical))
        out[legacy] = canonical
    return out


_PREVENTION_ITEM_RE = re.compile(r"<item\b[^>]*?\bname=\"([^\"]+)\"", re.S)


def parse_prevention_items(text: str) -> List[str]:
    """The canonical prevention type codes PreventionItems.xml declares."""
    return sorted(set(_PREVENTION_ITEM_RE.findall(text)))


#: Java root scanned for the entity mappings behind `PRIMITIVE_COLUMNS`.
JAVA_ROOT = REPO_ROOT / "src" / "main" / "java"

_TABLE_RE = re.compile(r"@Table\s*\(\s*name\s*=\s*\"([^\"]+)\"")
#: a field declaration whose Java type is a PRIMITIVE. `char` is included
#: for completeness; `String`, the boxed types and every entity reference
#: are deliberately absent, because those can hold null.
_PRIMITIVE_FIELD_RE = re.compile(
    r"(?:private|protected|public)\s+(?:final\s+)?"
    r"(?:boolean|byte|short|int|long|float|double|char)\s+(\w+)\s*[;=]")
_COLUMN_NAME_RE = re.compile(r"@Column\s*\([^)]*\bname\s*=\s*\"([^\"]+)\"")


def _blank_literals(text: str) -> str:
    """`text` with comments and string/char literals blanked to spaces,
    same length. Used only to count brace depth: a `{` inside a comment
    or a string would otherwise move every field after it into an
    imaginary method body."""
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                out[i] = " "
                i += 1
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            out[i] = out[i + 1] = " "
            i += 2
            while i < n and not (text[i] == "*" and i + 1 < n
                                 and text[i + 1] == "/"):
                if text[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "
                if i + 1 < n:
                    out[i + 1] = " "
                i += 2
        elif c in "\"'":
            quote = c
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\":
                    out[i] = " "
                    i += 1
                if i < n and text[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                i += 1
        else:
            i += 1
    return "".join(out)


def _depths(text: str) -> List[int]:
    """Brace depth BEFORE the character at each offset."""
    blanked = _blank_literals(text)
    depth = 0
    out = []
    for c in blanked:
        out.append(depth)
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
    return out


def scan_primitive_columns(java_root: Path) -> Dict[str, List[str]]:
    """{table (as the entity spells it): [column, ...]} for every column
    a CARLOS JPA entity maps to a JAVA PRIMITIVE.

    Insertability is not readability. Hibernate cannot hydrate NULL into
    an `int` field: it throws `IllegalArgumentException: Can not set int
    field ... to null value` the first time anything loads the row. So a
    NULL the schema permits is still a row the application cannot read,
    and the ETL has to supply a value for these columns whatever the
    column's own nullability says. Measured, not theorised: a migrated
    clinic's schedule answered HTTP 500 for every provider holding a
    message, because `messagelisttbl.destinationFacilityId` --
    nullable, and absent from the OSCAR 19 dump entirely -- reached
    `MessageList.destinationFacilityId`, an `int`.

    Deliberately conservative, because a column wrongly included would
    have the import write a type zero where the clinic meant NULL:

    * only a file with exactly ONE `@Table` is read -- an inner entity
      class would make the field-to-table attribution a guess;
    * only declarations at CLASS BODY depth count, so a local variable
      inside a method cannot be mistaken for a field (`boolean matches
      = ...` in an `equals` override is the shape that would);
    * `static` and `@Transient` fields are skipped;
    * a column name the CARLOS schema does not carry is dropped by the
      caller.

    A column missed here is no worse than today's behaviour."""
    out: Dict[str, List[str]] = {}
    for path in sorted(java_root.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        tables = _TABLE_RE.findall(text)
        if len(tables) != 1:
            continue
        depths = _depths(text)
        columns = []
        for m in _PRIMITIVE_FIELD_RE.finditer(text):
            # depth 1 is the class body: depth 0 is outside the class and
            # depth 2+ is inside a method, a constructor or an initializer
            if depths[m.start()] != 1:
                continue
            # the annotations attached to this field: everything back to
            # the previous statement or block boundary
            head = text[:m.start()]
            cut = max(head.rfind(";"), head.rfind("}"), head.rfind("{"))
            annotations = head[cut + 1:]
            if "@Transient" in annotations or " static " in m.group(0):
                continue
            named = _COLUMN_NAME_RE.search(annotations)
            columns.append(named.group(1) if named else m.group(1))
        if columns:
            out.setdefault(tables[0], []).extend(columns)
    return {t: sorted(set(cols)) for t, cols in out.items()}


def read_sql(path: Path) -> str:
    """Read one SQL source. Undecodable bytes are replaced rather than
    fatal: the O19 tree carries latin1-era files, and a stray byte in a
    comment must not stop the whole generation."""
    return path.read_text(encoding="utf-8", errors="replace")


def load_schema(files: List[Path],
                if_not_exists_mode: str = "skip") -> Schema:
    """Parse a list of SQL files into one `Schema`, applying CREATEs and
    ALTERs in the order given.

    Order matters: the O19 `updates/*.sql` patches redefine tables the
    base init created, so the caller passes the base files first."""
    schema = Schema(if_not_exists_mode)
    for f in files:
        schema.feed(strip_line_comments(read_sql(f)))
    return schema


def flyway_version(path: Path) -> Tuple[int, ...]:
    """Numeric Flyway version of V<major>[.<minor>...]__desc.sql: the
    migrations must be applied in VERSION order (V1.0.10 after V1.0.9),
    which a lexicographic sort gets wrong."""
    m = re.match(r"V(\d+(?:\.\d+)*)__", path.name)
    if not m:
        raise SystemExit("not a Flyway migration file name: {}".format(path))
    return tuple(int(x) for x in m.group(1).split("."))


def carlos_migration_files(dirs: List[Path]) -> List[Path]:
    """All migration files of the given directories as ONE list in Flyway
    version order (the baseline V1 first, province and common deltas
    interleaved by version exactly as Flyway applies them)."""
    files = [f for d in dirs for f in d.glob("V*__*.sql")]
    return sorted(files, key=lambda f: (flyway_version(f), f.name))


def expand_sources(base: Path, patterns: List[str]) -> List[Path]:
    """Resolve the configured source patterns against `base`, globs sorted
    for determinism.

    A missing NON-glob file warns rather than dying: the source list
    spans several OSCAR 19 patch levels and not every checkout carries
    every file."""
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
    """Active key=value pairs with java.util.Properties semantics (the same
    parser the props phase uses, so the baseline diff compares like with
    like); last occurrence wins."""
    sys.path.insert(0, str(REPO_ROOT / "debian" / "assets"))
    from carlos_ctl.o19props import parse_properties_text
    return dict(parse_properties_text(
        path.read_text(encoding="latin-1")))


# keys whose stock value is a credential. Their defaults are NEVER emitted
# into the shipped manifest (a plaintext password in a package is a
# finding, whatever its origin); the props phase always surfaces such a
# key for review instead of baseline-diffing it.
SECRET_KEY_RE = re.compile(
    r"(^|[._])(password|passwd|pass|secret|api_key|apikey|conformancekey|"
    r"token|pgp_key|user|username|userid)$", re.I)


def is_secret_key(key: str) -> bool:
    """Credential-shaped key names (passwords, keys, tokens, and the account
    names that pair with them). Decided by NAME only — the props overlay's
    dispositions never participate, so a carry-secret prefix such as
    `email.` still lets plain settings (host, port) keep their harmless
    stock defaults in the baseline."""
    return SECRET_KEY_RE.search(key) is not None


def split_secret_defaults(defaults: Dict[str, str]
                          ) -> Tuple[Dict[str, str], List[str]]:
    """(defaults without secret keys, sorted secret key names)."""
    kept = {k: v for k, v in defaults.items() if not is_secret_key(k)}
    secret = sorted(k for k in defaults if is_secret_key(k))
    return kept, secret


# --------------------------------------------------------------------------
# overlay loading and manifest assembly
# --------------------------------------------------------------------------

def load_module(path: Path):
    """Import an overlay module from an explicit path.

    Both Optionals are checked rather than silenced: importlib returns
    spec=None for a path it has no loader for, and spec.loader=None for a
    namespace package. The `# type: ignore` that used to sit here turned
    "you pointed --oscar-src at the wrong thing" into an AttributeError
    on None, several frames from the cause.
    """
    spec = importlib.util.spec_from_file_location(path.stem, path)
    if spec is None or spec.loader is None:
        raise SystemExit(
            "generator: cannot import {0} as a Python module — expected a "
            "curated overlay (.py) file".format(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def integer_column(definition: str) -> bool:
    """Whether a stored column DEFINITION begins with an integer type.

    `Schema.tables[t][col]` is the whole definition after the name --
    type, NOT NULL, DEFAULT and COMMENT, whitespace-collapsed -- so a
    substring test for "int" reads `varchar(20) COMMENT 'appointment
    slug'`, `DEFAULT 'internal'` and `enum('Uninterested', ...)` as
    integers. Two columns in the CARLOS schema already carry "int" in
    their definition text without being integers
    (appointment_status.short_letters, form_hsfo2_visit.PtView); neither
    is a single-column primary key today, so this was latent.

    It decides whether a merge table's primary key is a SURROGATE the
    ETL must reassign. Read wrong for a string key, the refusal on the
    next branch is skipped and `surrogate_pk` is emitted for a varchar:
    `idmap_statements` then builds `old_id BIGINT NOT NULL PRIMARY KEY`
    and inserts the string into it, where under the ETL's `sql_mode=''`
    non-numeric codes all coerce to 0 -- a duplicate-key abort mid-merge,
    or, for numeric-prefixed codes, distinct keys collapsing onto one id
    and every child row remapped through the map re-pointed at the wrong
    parent. Anchored like `default_nondefault_expr` beside it."""
    return bool(re.match(r"(tiny|small|medium|big)?int\b|integer\b",
                         definition.strip().lower()))


def default_nondefault_expr(coltype: str, col: str) -> str:
    """The predicate that decides whether a dropped column actually holds
    anything -- numeric columns compared against 0, everything else
    against the empty string, both guarded on NULL.

    This is what lets the preflight say "dropped, and empty in your
    data" rather than warning about every dropped column."""
    t = coltype.lower()
    if re.match(r"(tiny|small|medium|big)?int|decimal|double|float|numeric", t):
        return "s.`{0}` IS NOT NULL AND s.`{0}` <> 0".format(col)
    return "s.`{0}` IS NOT NULL AND s.`{0}` <> ''".format(col)


#: The FK naming convention both schemas use: `<parent>_id`, `<parent>Id`,
#: `<parent>_no`, `<parent>No`. Anchored at the END of the name on purpose --
#: a rule that merely looked for a parent's name anywhere in a column
#: (`provider.specialty`, `billingdetail.diagnostic_code`) flags fifteen
#: columns of the shipped schema that are not keys at all.
FK_SUFFIX_RE = re.compile(r"(_?id|_?no)$", re.IGNORECASE)


def fk_parent_by_name(col, parents):
    """The table `col` names by the FK naming convention, or None.

    `parents` maps a FOLDED table name (case and underscores removed) to
    the table. A bare `id` / `no` names nothing. This sees names, not
    semantics: a key spelled outside the convention (`tickler.category_id`
    into tickler_category, `consultationRequests.serviceId`) is invisible
    here and is ruled in FK_REMAP by hand."""
    m = FK_SUFFIX_RE.search(col)
    if not m or m.start() == 0:
        return None
    return parents.get(col[:m.start()].replace("_", "").lower())


def renumbered_parents(tables):
    """folded name -> table, for every table whose ids the import does
    NOT keep: `reference` (CARLOS's seed stands, the clinic's rows never
    land) and `merge` with a surrogate id (appended rows are renumbered).
    A copy-class parent keeps its ids; so does a merge on its own PK."""
    return {
        t.replace("_", "").lower(): t
        for t, e in tables.items()
        if e.get("class") == "reference"
        or (e.get("class") == "merge" and e.get("surrogate_pk"))}


def flagged_fk_columns(tables):
    """(child, column, parent) for every copied or merged column that
    names a renumbered parent other than its own table -- ruled or not.
    Reads `cols` only: an unfilled CARLOS-only column and a dropped O19
    column carry no id into the target and are never flagged."""
    parents = renumbered_parents(tables)
    out = []
    for t, e in sorted(tables.items()):
        if e.get("class") not in ("copy", "merge"):
            continue
        for col in e.get("cols") or ():
            parent = fk_parent_by_name(col, parents)
            if parent and parent != t:
                out.append((t, col, parent))
    return out


class TableRules(object):
    """Every overlay ruling `build_tables` consults, resolved once.

    The classification and the six checks that follow it read the same
    sixteen buckets. Passing them one by one made each helper's signature
    a transcription of this class, which is how a helper ends up reading a
    bucket it was never meant to see."""

    def __init__(self, ov):
        self.ov = ov
        self.merge_keys = dict(ov.CLASS_MERGE)
        self.reference = set(ov.CLASS_REFERENCE)
        self.archive_shared = set(getattr(ov, "ARCHIVE_SHARED", ()))
        self.replace_seed = set(getattr(ov, "REPLACE_SEED", ()))
        self.archive_patient = set(ov.ARCHIVE_PATIENT)
        self.archive_other = set(ov.ARCHIVE_OTHER)
        self.drop = set(ov.DROP)
        # table -> {target: source}
        self.renames = dict(getattr(ov, "RENAMES", {}))
        # table -> {target: expr}
        self.value_exprs = dict(getattr(ov, "VALUE_EXPRS", {}))
        # child -> {col: parent}
        self.fk_remap = dict(getattr(ov, "FK_REMAP", {}))
        self.b3 = set(ov.B3_COLUMNS)                     # {(table, col)}
        self.b3_exprs = dict(getattr(ov, "B3_NONDEFAULT_EXPRS", {}))
        self.chunk_tables = set(ov.CHUNK_TABLES)
        self.charset_scan = dict(ov.CHARSET_SCAN)
        self.merge_exclude = dict(getattr(ov, "MERGE_EXCLUDE", {}))
        self.startup_rows = list(getattr(ov, "STARTUP_CREATED_ROWS", []))
        self.not_renames = dict(getattr(ov, "NOT_RENAMES", {}))
        self.not_renamed_tables = dict(
            getattr(ov, "NOT_RENAMED_TABLES", {}))
        # (child, col) -> why it is NOT a key into a renumbered parent
        self.not_fk = dict(getattr(ov, "NOT_FK", {}))


def _shared_table_class(t, carlos, r):
    """The class ruling for one shared table, and the merge keys that
    come with it.

    Returns the entry; `reference` and `archive` are complete as
    they stand, everything else goes on to be given columns."""
    reference, archive_shared = r.reference, r.archive_shared
    merge_keys, merge_exclude = r.merge_keys, r.merge_exclude
    replace_seed = r.replace_seed

    entry: Dict[str, object] = {}
    if t in reference:
        entry["class"] = "reference"
        return entry
    if t in archive_shared:
        return {"class": "archive"}
    if t in merge_keys:
        entry["class"] = "merge"
        entry["merge_keys"] = list(merge_keys[t])
        if t in merge_exclude:
            entry["merge_exclude"] = merge_exclude[t]
        for k in merge_keys[t]:
            if k not in carlos.tables[t]:
                raise SystemExit(
                    "merge key {}.{} not a CARLOS column".format(t, k))
        pk = carlos.pks.get(t) or []
        if (len(pk) == 1 and pk[0] not in merge_keys[t]
                and integer_column(carlos.tables[t].get(pk[0], ""))):
            # integer surrogate id off the natural key: the ETL must
            # reassign it on appended rows instead of copying the
            # clinic's id (which may collide with a CARLOS seed row).
            entry["surrogate_pk"] = pk[0]
        elif pk and set(pk) != set(merge_keys[t]):
            # no surrogate to reassign: the anti-join key must BE the
            # primary key, or two rows differing only in a non-key
            # column would both try to insert the same PK
            raise SystemExit(
                "merge keys for {} must equal its primary key {} (no "
                "surrogate id to reassign); got {}".format(
                    t, pk, merge_keys[t]))
    else:
        entry["class"] = "copy"
        if t in replace_seed:
            entry["replace_seed"] = True
    return entry


def _shared_table_columns(t, o19, carlos, r):
    """(cols, renames, mapped_sources) for one shared table: which
    CARLOS columns the dump can fill, and from which O19 column."""
    renames, value_exprs = r.renames, r.value_exprs

    t_ren = renames.get(t, {})
    # MySQL column names are case-insensitive: `displayOrder` and
    # `displayorder` are the same column, so matching must fold case
    # (the ETL still SELECTs the O19 side's actual spelling).
    o19_by_lower = {c.lower(): c for c in o19.tables[t]}
    cols: List[str] = []
    ren_out: Dict[str, str] = {}
    mapped_sources = set()
    for target in carlos.tables[t]:
        source = o19_by_lower.get(t_ren.get(target, target).lower())
        if source is not None:
            cols.append(target)
            mapped_sources.add(source)
            if source != target:
                ren_out[target] = source
    # a value_exprs target the dump lacks (a CARLOS-added column such
    # as pharmacyInfo.uid or tickler.creation_date) is still copied —
    # from its expression, not a source column — so it joins the map
    for col in value_exprs.get(t, {}):
        if col in carlos.tables[t] and col not in cols:
            cols.append(col)
    return cols, ren_out, mapped_sources


def _dropped_columns(t, o19, mapped_sources, r):
    """The O19 columns of one shared table that CARLOS has no home
    for, each with the predicate that decides whether a row holds
    anything but the column's default."""
    b3, b3_exprs = r.b3, r.b3_exprs

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
    return dropped


def _apply_table_rules(t, entry, o19, carlos, cols, r):
    """The remaining per-table overlay rulings: synthesized values,
    id remapping, the chunk key and the charset scan list."""
    value_exprs, fk_remap = r.value_exprs, r.fk_remap
    merge_keys = r.merge_keys
    chunk_tables, charset_scan = r.chunk_tables, r.charset_scan

    if t in value_exprs:
        entry["value_exprs"] = dict(value_exprs[t])
    if t in fk_remap:
        for col, parent in fk_remap[t].items():
            if col not in cols:
                raise SystemExit("fk_remap {}.{} is not a copied column"
                                 .format(t, col))
            if parent not in merge_keys or parent not in carlos.tables:
                raise SystemExit(
                    "fk_remap {}.{} names {} which is not a merge-class "
                    "table".format(t, col, parent))
        entry["fk_remap"] = dict(fk_remap[t])
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


def classify_shared_table(t, o19, carlos, r):
    """One shared table's manifest entry: its class, the columns the dump
    can fill, the columns it cannot, and the overlay rulings that apply.

    Split out of `build_tables` so a single table's fate is nameable. The
    order matters and is the original's: the class decides whether
    columns are even asked for, and `dropped` is what the rename refusals
    downstream read."""
    entry = _shared_table_class(t, carlos, r)
    if entry["class"] in ("reference", "archive"):
        return entry
    cols, ren_out, mapped_sources = _shared_table_columns(t, o19, carlos, r)
    entry["cols"] = cols
    if ren_out:
        entry["renames"] = ren_out
    dropped = _dropped_columns(t, o19, mapped_sources, r)
    if dropped:
        entry["dropped"] = dropped
    _apply_table_rules(t, entry, o19, carlos, cols, r)
    return entry


def _warn_unknown_overlay_tables(o19, carlos, r):
    """Overlay entries naming a table neither side has: a warning, not
    a refusal -- a patch level that lacks the table is ordinary."""
    archive_patient, archive_other = r.archive_patient, r.archive_other
    drop, reference = r.drop, r.reference
    replace_seed, merge_keys = r.replace_seed, r.merge_keys
    archive_shared, chunk_tables = r.archive_shared, r.chunk_tables

    # overlay entries naming tables that exist on neither side are stale
    # (typo, or the table moved between patch levels) — surface them.
    all_names = set(o19.tables) | set(carlos.tables)
    for bucket, names in (("ARCHIVE_PATIENT", archive_patient),
                          ("ARCHIVE_OTHER", archive_other),
                          ("DROP", drop), ("CLASS_REFERENCE", reference),
                          ("REPLACE_SEED", replace_seed),
                          ("CLASS_MERGE", set(merge_keys)),
                          ("ARCHIVE_SHARED", archive_shared),
                          ("CHUNK_TABLES", chunk_tables)):
        for name in sorted(names - all_names):
            print("warning: overlay {} names unknown table {}"
                  .format(bucket, name), file=sys.stderr)


def _refuse_inverted_buckets(o19, carlos, r):
    """A bucket meaning 'CARLOS has no such table' that now names one
    CARLOS does have."""
    archive_patient, archive_other = r.archive_patient, r.archive_other
    drop = r.drop

    # ... but a name that exists on BOTH sides while sitting in a bucket
    # that means "CARLOS does not have this table" is not a typo to
    # warn about, it is a ruling that has silently inverted. These three
    # buckets are read ONLY in the o19_only loop below, so the day a
    # Flyway migration adds a CARLOS table of that name -- which is
    # exactly what V1.0.5__restore_live_legacy_common_tables.sql does for
    # a living -- the table stops being O19-only, falls through to the
    # `else` that assigns class "copy", and "removed-module
    # infrastructure, do not migrate" quietly becomes "copy every clinic
    # row into the live CARLOS table". Nothing warned, and --check still
    # passed on the next regeneration. Refuse instead: the maintainer
    # decides whether the new CARLOS table wants the clinic's rows.
    inverted = sorted(
        (bucket, name)
        for bucket, names in (("ARCHIVE_PATIENT", archive_patient),
                              ("ARCHIVE_OTHER", archive_other),
                              ("DROP", drop))
        for name in names & set(o19.tables) & set(carlos.tables))
    if inverted:
        raise SystemExit(
            "overlay bucket(s) that mean \"CARLOS has no such table\" now "
            "name a table CARLOS DOES have. Left alone each becomes class "
            "\"copy\" with no warning, so a removed module's rows would be "
            "copied into the live schema. Re-rule each in "
            "overrides_schema.py -- move it to CLASS_REFERENCE / "
            "ARCHIVE_SHARED / CLASS_MERGE, or delete the entry to accept "
            "the copy deliberately:\n  " + "\n  ".join(
                "{0} names {1}, which now exists on both sides".format(
                    bucket, name) for bucket, name in inverted))


def _refuse_stale_column_rules(carlos, tables, r):
    """Column-level overlay entries that no longer describe the diff."""
    ov = r.ov
    b3, value_exprs = r.b3, r.value_exprs
    merge_exclude, startup_rows = r.merge_exclude, r.startup_rows

    # column-level overlay entries that no longer describe the diff are
    # errors, not warnings: a stale B3 flag silently un-blocks a workflow
    # the clinic may still use, a stale VALUE_EXPR silently stops
    # synthesizing a required column
    for t, col in sorted(b3):
        if col not in tables.get(t, {}).get("dropped", {}):
            raise SystemExit(
                "B3_COLUMNS names {}.{}, which is not a dropped column of a "
                "shared table (stale entry)".format(t, col))
    for t, exprs in sorted(value_exprs.items()):
        if tables.get(t, {}).get("class") not in ("copy", "merge"):
            raise SystemExit(
                "VALUE_EXPRS names {}, which is not a copy/merge table"
                .format(t))
        for col in exprs:
            if col not in carlos.tables[t]:
                raise SystemExit(
                    "VALUE_EXPRS names {}.{}, not a CARLOS column"
                    .format(t, col))
    for t in getattr(ov, "CREDENTIAL_TABLES", ()):
        if tables.get(t, {}).get("class") != "copy":
            raise SystemExit(
                "CREDENTIAL_TABLES names {}, which is not a copy-class "
                "table".format(t))
    for t in merge_exclude:
        if tables.get(t, {}).get("class") != "merge":
            raise SystemExit(
                "MERGE_EXCLUDE names {}, which is not a merge-class table"
                .format(t))
    for t, _where in startup_rows:
        # the seed script deletes these before the id-intact copy; only a
        # copy-class table can hold them (a merge table keeps its seeds)
        if tables.get(t, {}).get("class") != "copy":
            raise SystemExit(
                "STARTUP_CREATED_ROWS names {}, which is not a copy-class "
                "table".format(t))


def _refuse_unruled_fk_columns(tables, r):
    """A copied id into a table whose ids the import does not keep.

    This is the one relationship that crosses manifest classes, and the
    class rulings are curated by hand with nothing else reading them
    together: `consentType` was ruled `reference` (CARLOS's seed stands)
    while `Consent.consent_type_id` was copied raw -- and the two seeds
    disagree on id 1, so every clinic's integrator consent arrived filed
    as the demonstration consent. P7 passed: the value 1 was copied
    faithfully, and faithfulness to the manifest is all it can check. A
    correct copy of a now-wrong reference is invisible downstream, which
    is why the refusal lives here, where the ruling is made."""
    flagged = flagged_fk_columns(tables)
    flagged_keys = {(t, col) for t, col, _p in flagged}
    for (t, col), reason in sorted(r.not_fk.items()):
        # `"   "` is truthy, and a ruling whose reason is whitespace is
        # the refusal being switched off quietly
        if not isinstance(reason, str) or not reason.strip():
            raise SystemExit(
                "NOT_FK[{!r}, {!r}] has no reason; a ruling without one is "
                "not a ruling".format(t, col))
        if (t, col) not in flagged_keys:
            raise SystemExit(
                "NOT_FK names {}.{}, which is not an id-shaped column into "
                "a reference or surrogate-id parent (stale entry)"
                .format(t, col))
        if col in tables.get(t, {}).get("fk_remap", {}):
            raise SystemExit(
                "NOT_FK names {}.{}, which FK_REMAP also remaps -- one "
                "ruling, not both".format(t, col))
    unruled = []
    for t, col, parent in flagged:
        if col in tables[t].get("fk_remap", {}) or (t, col) in r.not_fk:
            continue
        why = ("reference: ids never land"
               if tables[parent]["class"] == "reference"
               else "merge, surrogate id: ids may move")
        unruled.append("{}.{} -> {} ({})".format(t, col, parent, why))
    if unruled:
        raise SystemExit(
            "unruled foreign key(s) into a table whose ids the import does "
            "not keep: a copied column named like <parent>_id / <parent>Id "
            "whose parent is reference-class (CARLOS's seed stands, the "
            "clinic's ids never land) or merge-class with a surrogate id "
            "(appended rows are renumbered). Copied raw, the id points at "
            "whichever CARLOS row happens to hold it. Rule each in "
            "overrides_schema.py, as FK_REMAP[child] = {column: parent} (a "
            "reference-class parent must move to CLASS_MERGE first -- "
            "nothing maps ids for a table whose rows are ignored) or as a "
            "NOT_FK entry with a reason:\n  " + "\n  ".join(unruled))


def _refuse_unruled_column_renames(o19, carlos, tables, r):
    """A column dropped on one side while a column on the other goes
    unwritten -- how a rename hides."""
    not_renames = r.not_renames

    # -- renames: a decision, never an accident ---------------------------
    # Columns are matched by NAME (case-folded), so a genuine rename is
    # invisible twice over: the O19 column falls into `dropped` and the
    # CARLOS column silently takes its default. Each half looks
    # deliberate alone. The signature is the CO-OCCURRENCE, so refuse to
    # emit a manifest while any table has both an unmatched O19 column
    # and an unfilled CARLOS column that nobody has ruled on.
    for (t, col), ruling in sorted(not_renames.items()):
        if (not isinstance(ruling, (tuple, list)) or len(ruling) != 2):
            raise SystemExit(
                "NOT_RENAMES[{!r}, {!r}] must be (reason, the CARLOS "
                "columns the ruling covers)".format(t, col))
        reason, covered = ruling
        # `"   "` is truthy, and a ruling whose reason is whitespace is
        # not a ruling -- it is the refusal being switched off quietly
        if not isinstance(reason, str) or not reason.strip():
            raise SystemExit(
                "NOT_RENAMES[{!r}, {!r}] has no reason; a ruling without "
                "one is not a ruling".format(t, col))
        # the list check comes first: a non-sequence (a bare int, None)
        # makes the generator expression raise TypeError, which is a
        # traceback where a maintainer should get the refusal that names
        # the entry and says what shape it must take
        if (not isinstance(covered, (tuple, list))
                or not all(isinstance(c, str) for c in covered)):
            raise SystemExit(
                "NOT_RENAMES[{!r}, {!r}] must name the covered CARLOS "
                "columns as a sequence of names".format(t, col))
        if col not in tables.get(t, {}).get("dropped", {}):
            # a table pair filed here reads as a column ruling and would
            # die with a confusing "stale entry"; name the right namespace
            hint = (" -- a table pair belongs in NOT_RENAMED_TABLES"
                    if col in carlos.tables or col in o19.tables else "")
            raise SystemExit(
                "NOT_RENAMES names {}.{}, which is not a dropped column of "
                "a shared table (stale entry){}".format(t, col, hint))
    unruled = []
    for t, entry in sorted(tables.items()):
        dropped = entry.get("dropped") or {}
        if not dropped:
            continue
        mapped = set(entry.get("cols") or ())
        unfilled = [c for c in carlos.tables.get(t, ()) if c not in mapped]
        if not unfilled:
            continue
        for col in sorted(dropped):
            if (t, col) in not_renames:
                # a ruling covers the CARLOS columns it was written
                # against, and only those. CARLOS is the side still
                # moving: a Flyway migration that adds a column to an
                # already-ruled table would otherwise face this drop
                # with nobody having looked at the pair.
                covered = list(not_renames[(t, col)][1])
                if sorted(covered) != sorted(unfilled):
                    unruled.append(
                        "{0}: the ruling on O19 {0}.{1} covers CARLOS "
                        "{0}.{{{2}}}, but the unwritten columns are now "
                        "{0}.{{{3}}}".format(
                            t, col, ", ".join(covered), ", ".join(unfilled)))
                continue
            unruled.append(
                "{0}: O19 {0}.{1} is dropped while CARLOS {0}.{{{2}}} "
                "is never written".format(t, col, ", ".join(unfilled)))
    if unruled:
        raise SystemExit(
            "unruled possible rename(s) -- a column dropped on one side "
            "while a column on the other side goes unwritten is how a "
            "rename hides. Rule each in overrides_schema.py, as "
            "RENAMES[table][carlos_col] = o19_col or as a NOT_RENAMES "
            "entry giving a reason and the CARLOS columns it covers "
            "(a ruling whose covered columns no longer match is listed "
            "here too, and is re-ruled the same way):\n  "
            + "\n  ".join(unruled))


def _refuse_unruled_table_renames(o19, carlos, o19_only, r):
    """The same question one level up: an O19-only table archived while
    a CARLOS table with almost the same columns keeps its seed."""
    not_renamed_tables = r.not_renamed_tables

    # The same question one level up: a table renamed between O19 and
    # CARLOS classifies as O19-only `archive` while its CARLOS twin keeps
    # its Flyway seed, and nothing says so. Jaccard rather than a
    # containment ratio: `intersection / min(len)` makes any four-column
    # audit table "match" every larger one that happens to have id/date.
    #
    # Ruled pairs go in NOT_RENAMED_TABLES, a namespace of its own: the
    # column loop above reads element 2 of every NOT_RENAMES key as a
    # dropped column name, so filing a table pair there would be rejected
    # as a stale entry and the escape hatch would not exist.
    o19_only_named = {t: {c.lower() for c in o19.tables[t]}
                      for t in o19_only if len(o19.tables[t]) >= 3}
    carlos_only = {t: {c.lower() for c in carlos.tables[t]}
                   for t in set(carlos.tables) - set(o19.tables)
                   if len(carlos.tables[t]) >= 3}
    candidates = {}
    for a, ca in sorted(o19_only_named.items()):
        for b, cb in sorted(carlos_only.items()):
            union = ca | cb
            if not union:
                continue
            overlap = len(ca & cb) / float(len(union))
            if overlap >= 0.70:
                candidates[(a, b)] = (overlap, len(ca), len(cb))
    for pair, reason in sorted(not_renamed_tables.items()):
        if not isinstance(reason, str) or not reason.strip():
            raise SystemExit(
                "NOT_RENAMED_TABLES[{!r}] has no reason; a ruling without "
                "one is not a ruling".format(pair))
        # a ruling for a pair the detector no longer raises is dead
        # weight that would silently cover a FUTURE pair of the same
        # names, so it is an error rather than a warning
        if pair not in candidates:
            raise SystemExit(
                "NOT_RENAMED_TABLES names {}, which is no longer a "
                "flagged O19-only/CARLOS-only twin (stale entry)"
                .format(pair))
    twins = [
        "{0} (O19-only, {1} cols) ~ {2} (CARLOS-only, {3} cols): {4:.0f}% "
        "of their column names agree".format(a, na, b, nb, overlap * 100)
        for (a, b), (overlap, na, nb) in sorted(candidates.items())
        if (a, b) not in not_renamed_tables]
    if twins:
        raise SystemExit(
            "possible table rename(s): an O19-only table archived while a "
            "CARLOS table with almost the same columns keeps its seed is "
            "how a table rename hides. Rule each in overrides_schema.py "
            "as a NOT_RENAMED_TABLES entry keyed (o19_table, "
            "carlos_table) with a reason:\n  " + "\n  ".join(twins))


def _classify_o19_only(tables, o19_only, r):
    """Tables only OSCAR 19 has: archived, dropped, or unknown."""
    archive_patient, archive_other = r.archive_patient, r.archive_other
    drop = r.drop

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


def build_tables(o19: Schema, carlos: Schema, ov) -> Dict[str, dict]:
    """Classify every table into the manifest: `copy`, `merge`, `archive`,
    `reference` or `seed`, with its column mapping, chunk key, dropped
    columns and renames.

    Shared tables are classified by the overlays; tables only OSCAR 19
    has become archive. The overlays decide policy, this decides shape.

    The refusals below run AFTER every shared table is classified because
    each reads the finished entries -- a stale B3 flag is one that names
    no dropped column, and an unruled rename is a dropped column facing
    an unwritten one. They are ordered cheapest-refusal-first only so the
    maintainer sees the simplest problem first."""
    r = TableRules(ov)
    shared = sorted(set(o19.tables) & set(carlos.tables))
    o19_only = sorted(set(o19.tables) - set(carlos.tables))

    tables = {}
    for t in shared:
        tables[t] = classify_shared_table(t, o19, carlos, r)

    _warn_unknown_overlay_tables(o19, carlos, r)
    _refuse_inverted_buckets(o19, carlos, r)
    _refuse_stale_column_rules(carlos, tables, r)
    _refuse_unruled_fk_columns(tables, r)
    _refuse_unruled_column_renames(o19, carlos, tables, r)
    _refuse_unruled_table_renames(o19, carlos, o19_only, r)
    _classify_o19_only(tables, o19_only, r)
    return tables


# --------------------------------------------------------------------------
# emission
# --------------------------------------------------------------------------

def _fmt(obj, indent: int = 0, pair_width: Optional[int] = None) -> str:
    """Deterministic, diff-friendly repr wrapped for the 100-col house
    style. `pair_width` additionally puts a dict value on its own line
    when `key: value` would exceed it — the generated block inside
    o19_preflight.py lives in a hand-written 79-column file, and two
    long SQL predicates in it were the file's only lint findings."""
    pad = "    " * indent
    if isinstance(obj, dict):
        if not obj:
            return "{}"
        lines = ["{"]
        for k in obj:
            value = _fmt(obj[k], indent + 1, pair_width)
            one = "{}    {!r}: {},".format(pad, k, value)
            if (pair_width and len(one) > pair_width
                    and "\n" not in value):
                lines.append("{}    {!r}:".format(pad, k))
                lines.append("{}        {},".format(pad, value))
            else:
                lines.append(one)
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


def content_version(base: str, content) -> str:
    """The overlay's token with a short digest of the content it
    describes appended.

    The ETL ledger refuses a --resume across a changed token, which is
    the whole point of having one — but a token maintained by hand drifts
    the moment someone changes a classification and forgets to bump it,
    and then two package builds with DIFFERENT tables share a version
    and a resume across them is accepted. Deriving the suffix from the
    content makes that impossible to forget. The base stays the
    hand-maintained token so the shape remains deliberately
    non-CalVer."""
    digest = hashlib.sha256(
        repr(content).encode("utf-8")).hexdigest()[:8]
    return "{0}+{1}".format(base, digest)


def schema_map_version(tables, ov) -> str:
    """The manifest's version string: the curated `SCHEMA_MAP_VERSION`
    plus a digest of the generated tables, so any drift in the output
    changes the version the ledger binds a run to."""
    return content_version(ov.SCHEMA_MAP_VERSION, sorted(tables.items()))


def emit_schema_module(tables, carlos: Schema, seed_counts, ov,
                       o19_commit: str, extras: Optional[Dict] = None) -> str:
    """Render `o19map_schema.py` in full.

    Output is deterministic -- sorted throughout and carrying the O19
    source commit rather than a wall-clock stamp -- so an unchanged
    input regenerates byte-identical output and `--check` means
    something."""
    extras = extras or {}
    copy_tables = sorted(t for t, e in tables.items()
                         if e["class"] in ("copy", "merge"))
    carlos_columns = {t: list(carlos.tables[t]) for t in copy_tables}
    seeded = {t: seed_counts[t] for t in sorted(seed_counts)
              if t in copy_tables and seed_counts[t] > 0}
    out = [GENERATED_HEADER]
    out.append('"""OSCAR 19 -> CARLOS schema manifest (Ontario profile)."""\n')
    out.append("SCHEMA_MAP_VERSION = {!r}".format(
        schema_map_version(tables, ov)))
    out.append("O19_PROFILE = 'on'")
    # provenance is the O19 commit only — no wall-clock stamp, so --check
    # compares content, not the day it was generated
    out.append("O19_SOURCE_COMMIT = {!r}\n".format(o19_commit))
    out.append("TABLES = " + _fmt(tables) + "\n")
    out.append("CARLOS_COLUMNS = " + _fmt(carlos_columns) + "\n")
    out.append("# rows the CARLOS Flyway migrations seed into copy/merge-class"
               " tables, counted from\n# literal VALUES tuples. The P0 pristine"
               " sweep requires copy-class tables to hold\n# EXACTLY these"
               " rows (else none) and merge-class tables AT LEAST these rows:"
               " merge\n# tables are CARLOS reference seeds that later"
               " migrations may also grow via\n# INSERT ... SELECT, which no"
               " static count can see; clinical data never lives there.")
    out.append("SEED_ROW_COUNTS = " + _fmt(seeded) + "\n")
    out.append("# copy-class tables the P0 pristine sweep tolerates rows "
               "in (all REPLACE_SEED)")
    out.append("PRISTINE_TOLERATED_TABLES = "
               + _fmt(list(ov.PRISTINE_TOLERATED_TABLES)) + "\n")
    out.append("# tables the import cannot run without (o19etl "
               "pre-checks and the roles step)")
    out.append("REQUIRED_TABLES = " + _fmt(list(ov.REQUIRED_TABLES)) + "\n")
    out.append("CARLOSDOC_SEED_DELETES = "
               + _fmt(list(ov.CARLOSDOC_SEED_DELETES)) + "\n")
    out.append("SEED_PROVIDER_NO = {!r}".format(ov.SEED_PROVIDER_NO))
    out.append("SEED_USER_NAME = {!r}".format(ov.SEED_USER_NAME))
    out.append("# copy-class tables whose rows are credentials (OAuth consumer"
               " secrets, signing\n# keys): copied verbatim, named in the ETL"
               " report under a rotate/verify advisory")
    out.append("CREDENTIAL_TABLES = "
               + _fmt(list(getattr(ov, "CREDENTIAL_TABLES", []))) + "\n")
    out.append("# rows the webapp creates on its first start (the OSCAR"
               " program, the seeded\n# clinician's membership, the default"
               " site): tolerated by the P0 sweep on a booted\n# host and"
               " deleted by the seed script before the clinic's rows copy")
    out.append("STARTUP_CREATED_ROWS = "
               + _fmt(list(getattr(ov, "STARTUP_CREATED_ROWS", []))) + "\n")
    out.append("# role names the CARLOS Flyway seed defines (secRole); any"
               " other imported role\n# is clinic-custom and gets its"
               " CARLOS-era grants from a template stock role")
    out.append("STOCK_ROLE_NAMES = "
               + _fmt(list(extras.get("stock_role_names", []))) + "\n")
    out.append("ROLE_TEMPLATE_MIN_JACCARD = {!r}\n".format(
        getattr(ov, "ROLE_TEMPLATE_MIN_JACCARD", 0.3)))
    out.append("# legacy preventions.prevention_type spellings -> Health"
               " Canada code, from\n# database/mysql/updates/update-2026-03-10-"
               "standardize-prevention-types.sql; the\n# roles post-step"
               " applies them to imported rows (Flyway never sees clinic"
               " data)")
    out.append("PREVENTION_TYPE_MAP = "
               + _fmt(dict(sorted(extras.get("prevention_type_map",
                                             {}).items()))) + "\n")
    out.append("# prevention type codes PreventionItems.xml renders; any"
               " other imported code shows\n# as an unconfigured prevention"
               " and is reported")
    out.append("KNOWN_PREVENTION_TYPES = "
               + _fmt(list(extras.get("known_prevention_types", []))) + "\n")
    out.append("# columns a CARLOS JPA entity maps to a JAVA PRIMITIVE."
               " Hibernate cannot hydrate\n# NULL into one -- it throws"
               " `Can not set int field ... to null value` and the page"
               "\n# reading the row answers HTTP 500 -- so the ETL supplies"
               " a value for these\n# whatever the column's own nullability"
               " says (see o19etl.primitive_fallback).")
    out.append("PRIMITIVE_COLUMNS = "
               + _fmt(primitive_columns(copy_tables, carlos, extras)) + "\n")
    return "\n".join(out).rstrip("\n") + "\n"


def primitive_columns(copy_tables: List[str], carlos: Schema,
                      extras: Dict) -> Dict[str, List[str]]:
    """The entity scan narrowed to what the manifest can act on: tables
    the import writes, and columns the CARLOS schema actually has.

    Matched case-INSENSITIVELY and emitted in the SCHEMA's spelling,
    because an entity and a migration do not always agree on case and
    the ETL looks these up against information_schema."""
    scanned = extras.get("primitive_columns", {})
    lowered = {t.lower(): cols for t, cols in scanned.items()}
    out: Dict[str, List[str]] = {}
    for table in copy_tables:
        declared = lowered.get(table.lower())
        if not declared:
            continue
        have = {c.lower(): c for c in carlos.tables[table]}
        cols = sorted({have[d.lower()] for d in declared
                       if d.lower() in have})
        if cols:
            out[table] = cols
    return out


def undisposed_property_keys(o19_defaults, ov):
    """Stock O19 property keys the overlay classifies neither by an exact
    KEYS entry nor by a PREFIX_RULES prefix.

    Curation is what decides whether a clinic's setting is carried,
    renamed, dropped or surfaced for review. A key nobody has ruled on
    is not "left alone" — it silently falls off the migration, and the
    operator never sees it in the props report either. The count is 0
    today; this exists so it stays that way, because the gap is
    invisible from the generated manifest itself.
    """
    prefixes = [prefix for prefix, _spec in ov.PREFIX_RULES]
    return sorted(key for key in o19_defaults
                  if key not in ov.KEYS
                  and not any(key.startswith(p) for p in prefixes))


def overlay_disposition(key, ov) -> str:
    """The overlay's disposition letter for one key, resolved the way the
    props phase resolves it: exact KEYS first, then the ordered
    PREFIX_RULES. The shipped `o19props.disposition` cannot be reused
    here — it reads the generated module this function helps build."""
    spec = ov.KEYS.get(key)
    if spec is not None:
        return spec["d"]
    for prefix, pspec in ov.PREFIX_RULES:
        if key.startswith(prefix):
            return pspec["d"]
    return "unknown"


def divergent_carlos_defaults(o19_defaults, carlos_defaults, ov):
    """CARLOS's own default for every `carry` key whose stock O19 default
    says something DIFFERENT.

    Baseline-diff (plan §8.1 rule 1) drops a clinic key whose value
    equals the O19 default so the CARLOS default wins. That inference is
    only behaviour-neutral while both products ship the same default;
    for these keys they do not, so the clinic's consultation, lab and UI
    behaviour flips at cutover. The props phase reports each one instead
    of returning before anything is recorded. Only `carry` keys qualify:
    a translate/deploy-owned/dropped-flag key is never copied verbatim
    anyway, and CARLOS owning its value there is the documented intent.
    """
    return {key: carlos_defaults[key]
            for key, o19_value in o19_defaults.items()
            if key in carlos_defaults
            and carlos_defaults[key] != o19_value
            and overlay_disposition(key, ov) == "carry"}


def bundle_key_renames(o19_bundle, carlos_bundle, ov):
    """O19 message-bundle key -> its CARLOS spelling, for the namespaces
    the overlay declares renamed.

    Only keys whose CARLOS twin actually EXISTS are emitted: the props
    phase treats anything it cannot map as needs-review, because a
    sign-line token that resolves to nothing silently drops words from
    the signed clinical note. Refuses a declared prefix that matches no
    O19 key (stale curation) or whose old spelling still resolves in
    CARLOS (then nothing needed rewriting and the rule is wrong).
    """
    renames = {}
    for old_prefix, new_prefix in getattr(ov, "BUNDLE_PREFIX_RENAMES", ()):
        matched = [k for k in o19_bundle if k.startswith(old_prefix)]
        if not matched:
            raise SystemExit(
                "generator: BUNDLE_PREFIX_RENAMES prefix {0!r} matches no "
                "key of the OSCAR 19 message bundle — stale curation"
                .format(old_prefix))
        still_live = [k for k in carlos_bundle if k.startswith(old_prefix)]
        if still_live:
            raise SystemExit(
                "generator: BUNDLE_PREFIX_RENAMES prefix {0!r} still "
                "resolves in the CARLOS bundle ({1} key(s)) — nothing "
                "needs rewriting".format(old_prefix, len(still_live)))
        for key in matched:
            target = new_prefix + key[len(old_prefix):]
            if target in carlos_bundle:
                renames[key] = target
    return renames


def emit_props_module(o19_defaults, ov, carlos_defaults=None,
                      bundle_renames=None) -> str:
    """Render `o19map_props.py`.

    Refuses outright if any stock OSCAR 19 property key has no
    disposition: an undisposed key drops out of the migration with
    nobody having decided that, so it is a generation failure rather
    than a warning. Credential-bearing stock defaults are never
    emitted."""
    undisposed = undisposed_property_keys(o19_defaults, ov)
    if undisposed:
        raise SystemExit(
            "generator: {0} stock OSCAR 19 property key(s) have no "
            "disposition in overrides_props.py — every key needs an exact "
            "KEYS entry or a matching PREFIX_RULES prefix, or it drops out "
            "of the migration without anyone deciding that:\n  ".format(
                len(undisposed)) + "\n  ".join(undisposed))
    defaults, secret_keys = split_secret_defaults(o19_defaults)
    out = [GENERATED_HEADER]
    out.append('"""OSCAR 19 -> CARLOS properties manifest."""\n')
    out.append("PROPS_MAP_VERSION = {!r}\n".format(
        content_version(ov.PROPS_MAP_VERSION,
                        (sorted(ov.KEYS.items()),
                         list(ov.PREFIX_RULES)))))
    out.append("# active keys of the stock O19 oscar_mcmaster.properties —"
               " the baseline-diff\n# reference: clinic keys equal to these"
               " defaults are ignored (CARLOS defaults win)")
    out.append("O19_DEFAULTS = " + _fmt(dict(sorted(defaults.items())))
               + "\n")
    out.append("# stock keys whose value is a credential: their defaults are"
               " deliberately not\n# shipped — the props phase always"
               " surfaces these for review instead of\n# baseline-diffing"
               " them")
    out.append("SECRET_DEFAULT_KEYS = " + _fmt(secret_keys) + "\n")
    out.append("# CARLOS's own default for the `carry` keys where it"
               " DIFFERS from the O19 default:\n# the baseline diff leaves"
               " such a key untouched (CARLOS wins), which silently flips"
               "\n# the clinic's behaviour at cutover unless the props"
               " report names it")
    out.append("CARLOS_DEFAULTS = " + _fmt(dict(sorted(
        divergent_carlos_defaults(defaults, carlos_defaults or {},
                                  ov).items()))) + "\n")
    out.append("# oscarResources bundle keys CARLOS renamed, verified"
               " against the CARLOS bundle:\n# a `rewrite: bundle` value's"
               " ${...} tokens are remapped through this before the value"
               "\n# is carried, and a token that is not a key here is"
               " refused (needs-review)")
    out.append("BUNDLE_KEY_RENAMES = "
               + _fmt(dict(sorted((bundle_renames or {}).items()))) + "\n")
    out.append("KEYS = " + _fmt(dict(sorted(ov.KEYS.items()))) + "\n")
    out.append("PREFIX_RULES = " + _fmt(list(ov.PREFIX_RULES)) + "\n")
    return "\n".join(out).rstrip("\n") + "\n"


def emit_preflight_data(tables, ov, props_ov,
                        extras: Optional[Dict] = None) -> str:
    """Render the generated block of `o19_preflight.py` (table classes,
    patient tables, dropped columns and their emptiness predicates).

    The preflight ships as one standalone file for the clinic's server,
    so its manifest data is inlined between markers rather than
    imported."""
    extras = extras or {}
    known = {t: e["class"] for t, e in sorted(tables.items())}
    patient = sorted(t for t, e in tables.items() if e.get("patient_data"))
    b3_cols: Dict[str, Dict[str, str]] = {}
    for t, e in sorted(tables.items()):
        for col, d in e.get("dropped", {}).items():
            if d.get("b3"):
                # ETL predicates address the staging alias `s.`; preflight
                # queries the table directly, so strip the alias.
                b3_cols.setdefault(t, {})[col] = \
                    d["nondefault"].replace("s.`", "`")
    charset = {t: e["charset_scan"] for t, e in sorted(tables.items())
               if e.get("charset_scan")}
    lines = [MARKER_BEGIN]
    lines.append("SCHEMA_MAP_VERSION = {!r}".format(
        schema_map_version(tables, ov)))
    lines.append("REQUIRED_TABLES = " + _fmt(list(ov.REQUIRED_TABLES)))
    lines.append("PATIENT_DATA_TABLES = " + _fmt(patient))
    lines.append("KNOWN_TABLES = " + _fmt(known))
    lines.append("B3_FLAGGED_COLUMNS = " + _fmt(b3_cols, pair_width=79))
    lines.append("CHARSET_SCAN = " + _fmt(charset))
    # DERIVED from the properties overlay, never maintained beside it:
    # the same list prunes the clinic's `property` TABLE, and the
    # hand-written copy had drifted six prefixes behind the file rules —
    # so hsfo_* keys were dropped from oscar.properties while the
    # matching property rows survived the import and CARLOS read them
    # back.
    lines.append("DROPPED_PROP_PREFIXES = " + _fmt(
        [p for p, spec in props_ov.PREFIX_RULES
         if spec.get("d") == "dropped-flag"]))
    # …and the keys classified by NAME rather than by prefix. Without
    # these the same drift reappears one level down: the key is dropped
    # from oscar.properties while its `property` table row survives the
    # import and CARLOS reads it back.
    lines.append("DROPPED_PROP_KEYS = " + _fmt(
        sorted(k for k, spec in props_ov.KEYS.items()
               if spec.get("d") == "dropped-flag")))
    lines.append("STOCK_ROLE_NAMES = "
                 + _fmt(list(extras.get("stock_role_names", []))))
    lines.append("LEGACY_PREVENTION_TYPES = "
                 + _fmt(sorted(extras.get("prevention_type_map", {}))))
    lines.append(MARKER_END)
    return "\n".join(lines)


def rewrite_markers(path: Path, block: str) -> bool:
    """Replace the text between the generated-data markers in `path`.
    Returns whether the file changed. Missing or malformed markers are
    fatal -- writing the block somewhere else would corrupt the file."""
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
    """Generate (or, with `--check`, verify) the manifest modules from an
    OSCAR 19 checkout plus the curated overlays.

    `--check` regenerates in memory and exits non-zero on drift, which
    is what pins a hand-edited manifest. Returns the process exit
    code."""
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

    o19 = load_schema(expand_sources(oscar, O19_SQL_SOURCES),
                      if_not_exists_mode="union")
    carlos_files = carlos_migration_files(
        [MIGRATION_DIR / "common", MIGRATION_DIR / "on"])
    carlos = load_schema(carlos_files)

    # comments are stripped first: a `-- note` between VALUES tuples would
    # otherwise end the tuple walk early and under-count the seed
    seed_counts: Dict[str, int] = {}
    stock_role_names: List[str] = []
    for f in carlos_files:
        text = strip_line_comments(read_sql(f))
        for t, n in count_insert_rows(text).items():
            seed_counts[t] = seed_counts.get(t, 0) + n
        # secRole VALUES (role_no, role_name, description)
        stock_role_names.extend(seed_string_column(text, "secRole", 1))
    # rows a later migration DELETEs again are not part of the floor
    for t, n in sorted(getattr(ov_schema, "SEED_COUNT_DELETIONS",
                               {}).items()):
        if seed_counts.get(t, 0) < n:
            raise SystemExit(
                "SEED_COUNT_DELETIONS: {} subtracts {} from a seed of {} "
                "rows (stale entry)".format(t, n, seed_counts.get(t, 0)))
        seed_counts[t] -= n
    extras = {
        "primitive_columns": scan_primitive_columns(JAVA_ROOT),
        "stock_role_names": sorted(set(stock_role_names)),
        "prevention_type_map": parse_prevention_type_map(
            read_sql(PREVENTION_TYPE_SCRIPT)),
        "known_prevention_types": parse_prevention_items(
            PREVENTION_ITEMS_XML.read_text(encoding="utf-8")),
    }
    if not extras["stock_role_names"]:
        raise SystemExit("no secRole seed rows found in the CARLOS "
                         "migrations")
    if not extras["primitive_columns"]:
        # an empty scan would silently disarm the NULL-into-a-primitive
        # protection for every table at once
        raise SystemExit("no JPA entity primitives found under {}"
                         .format(JAVA_ROOT))
    if not extras["prevention_type_map"]:
        raise SystemExit("no prevention type mappings parsed from {}"
                         .format(PREVENTION_TYPE_SCRIPT))

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
    # the file the deb installs as /etc/carlos-emr/carlos.properties: what
    # actually wins for any key the import leaves out of the fragment
    carlos_defaults = parse_properties(CARLOS_PROPERTIES)
    bundle_renames = bundle_key_renames(
        parse_properties(oscar / O19_RESOURCE_BUNDLE),
        parse_properties(CARLOS_RESOURCE_BUNDLE), ov_props)

    schema_out = emit_schema_module(tables, carlos, seed_counts, ov_schema,
                                    commit, extras)
    props_out = emit_props_module(o19_defaults, ov_props, carlos_defaults,
                                  bundle_renames)
    preflight_block = emit_preflight_data(tables, ov_schema, ov_props,
                                          extras)

    for t, e in tables.items():
        for col, parent in e.get("fk_remap", {}).items():
            if not tables.get(parent, {}).get("surrogate_pk"):
                raise SystemExit(
                    "fk_remap {}.{} -> {}: the parent has no surrogate PK "
                    "to remap".format(t, col, parent))

    targets = [
        (CTL_DIR / "o19map_schema.py", schema_out),
        (CTL_DIR / "o19map_props.py", props_out),
    ]
    preflight = CTL_DIR / "o19_preflight.py"
    if args.check:
        rc = 0
        for path, content in targets:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                print("DRIFT: {} differs from regenerated content".format(path))
                rc = 1
        # the embedded preflight data is generated too — drift there is
        # exactly the stale-classification case --check exists to catch
        if preflight.is_file():
            text = preflight.read_text(encoding="utf-8")
            b, e = text.find(MARKER_BEGIN), text.find(MARKER_END)
            current = text[b:e + len(MARKER_END)] if b != -1 and e > b else ""
            if current != preflight_block:
                print("DRIFT: generated-data block in {} differs".format(
                    preflight))
                rc = 1
        return rc

    for path, content in targets:
        path.write_text(content, encoding="utf-8")
        print("wrote {}".format(path))

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

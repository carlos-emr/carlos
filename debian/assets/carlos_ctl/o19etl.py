# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""ETL phase of the OSCAR 19 importer (experimental): manifest-driven copy
from the o19_import staging schema into the live CARLOS schema, plus the
o19_archive capture of everything CARLOS no longer models.

Statement GENERATION is pure (unit-tested against golden SQL); EXECUTION
goes through the caller-supplied query callable with a session prelude of
sql_log_bin=0, FOREIGN_KEY_CHECKS=0, sql_mode='' (the bulk copy is
deliberately outside the binlog — the pre/post backups bracket it, and P0
refused replicas).

Sanitizer policy (migration plan §4.3): zero dates NULL out only where the
target column is nullable; enum values outside the target's set fall to the
column default WITH a report line; over-length values ERROR before any copy
— truncating PHI silently is never acceptable. CARLOS-added NOT NULL
columns without defaults abort with the list of columns needing a
value_exprs curation entry, before anything is written.

Surrogate ids: a merge-class table whose natural key is not its PK gets
fresh AUTO_INCREMENT ids for appended rows. Every such table therefore
records an old->new id map in o19_archive (<table>__idmap), and child
tables declared in the manifest's `fk_remap` read their foreign key
through that map — a clinic-defined lookup list keeps its items.

Everything the manifest does not know is still preserved: staging tables
absent from the manifest are archived whole (unknown-as-archive), and
columns the manifest does not map on known tables are shadow-captured.
"""

import json
import os
import re
import time
from typing import Callable, Dict, List, Optional, Sequence, Tuple

from . import o19map_schema
from .util import sql_escape, warn

# a copy is windowed over the id RANGE, so a sparse id space costs
# windows rather than rows. Beyond this the range is not a table's id
# space any more and building the list alone would exhaust memory.
MAX_CHUNK_WINDOWS = 200000


CHUNK_ROWS = 50000


def chunk_span_refusal(table: str, chunk_col: str, lo: int,
                       hi: int) -> Optional[str]:
    """The refusal text when a chunked table's id range is too wide to
    window, or None when it is copyable.

    Called BEFORE the table's first target write (the replace_seed DELETE
    and the ``started`` checkpoint both follow it). The id space comes
    from the dump, so one row with a BIGINT id near the type's ceiling
    asks for ~1.8e14 windows and the process dies of memory exhaustion at
    P4, mid-write, with the OOM killer free to take mariadbd with it.
    Refusing after the DELETE would have destroyed the target's rows and
    contradicted this message's own "nothing has been written" promise.
    """
    span = (hi - lo) // CHUNK_ROWS + 1
    if span <= MAX_CHUNK_WINDOWS:
        return None
    return ("{0}: its {1} column spans {2}..{3}, which needs {4} copy "
            "windows — the id space is far larger than the table. Nothing "
            "has been written for this table, but earlier tables in this "
            "run have been: restore the pre-import snapshot, renumber or "
            "remove the outlying row(s) in the source, re-export, and "
            "start over. (A re-exported dump cannot be offered to this "
            "workspace with --restage once the ETL has copied from the "
            "one staged earlier.)".format(
                table, chunk_col, lo, hi, span))


def absent_table_disposition(table: str, cls: str,
                             tolerated: Sequence[str],
                             in_target: bool) -> Tuple[bool, str]:
    """(clear the target's rows?, the report note) for a manifest table
    this dump does not contain.

    P0 lets a PRISTINE_TOLERATED_TABLES table through the stock-deploy
    gate holding rows (this deploy's own audit trail) ONLY because the
    copy deletes them before the clinic's id-intact rows land. When the
    dump has no such table that copy never runs, so those rows would end
    up interleaved with the clinic's history under CARLOS — hence the
    clear, even though there is nothing to copy in.

    Both the class and the target's own schema are checked because this
    is generic over PRISTINE_TOLERATED_TABLES: an entry of some other
    class would otherwise be emptied below the `cls in (copy, merge)`
    test that decides whether anything is reported at all, destroying
    target rows silently.

    The note is returned whether or not this run is the one that does the
    delete. `absent_tables` is rebuilt from scratch on every run and the
    whole block is re-emitted, so deriving it from "did I just clear it"
    dropped the line on every --resume — taking, out of the shareable
    report, the one fact the P0 tolerance rests on.
    """
    if table in tolerated and cls in ("copy", "merge") and in_target:
        return True, " (absent: the target's own rows were cleared)"
    if table in o19map_schema.SEED_ROW_COUNTS:
        return False, " (seeded: CARLOS defaults stand)"
    return False, ""


def absent_table_plan(table: str, cls: str, tolerated: Sequence[str],
                      in_target: bool,
                      already_cleared: bool) -> Tuple[bool, Optional[str]]:
    """(run the DELETE now?, the report line or None) for a manifest table
    this dump does not carry.

    The ledger gates the DELETE and NOTHING else. `absent_tables` is
    rebuilt from scratch on every run while the ledger remembers across
    them, so conditioning the report line on it drops the line from every
    --resume -- and with it the one fact P0's tolerance rests on. That
    regression shipped once and three source-text guards failed to pin
    it, because it lived at the call site rather than in a function.
    Deciding both here is what makes it testable: the line comes back
    whether or not this run is the one that clears.
    """
    clear, note = absent_table_disposition(table, cls, tolerated, in_target)
    line = "{0}{1}".format(table, note) if cls in ("copy", "merge") else None
    return (clear and not already_cleared), line


REPAIR_TEMPLATE = ("CONVERT(BINARY CONVERT({0} USING latin1) USING utf8mb4)")


def double_encoded_predicate(expr: str) -> str:
    """Row predicate: `expr` holds UTF-8 text that went through a latin1
    hop (mojibake such as 'Ã©' for 'é'). Byte-ALIGNED and lossless by
    construction: the value must round-trip down to latin1 unchanged
    (every character representable), those latin1 bytes must form valid
    UTF-8 (converting them back to bytes loses nothing), and the value
    must contain a non-ASCII character. A substring match on a hex dump
    ('%C383%') is not aligned — it flags '1,800' — and re-encoding a whole
    column corrupts every correctly stored 'é'; hence per-row repair."""
    # normalise to utf8mb4 FIRST: staged O19 tables are usually latin1
    # (the MySQL 5.x default), and BINARY-comparing a latin1 value with
    # its utf8mb4 re-encoding compares different byte strings — which
    # silently marked every mojibake row as clean
    u = "CONVERT({0} USING utf8mb4)".format(expr)
    down = "CONVERT({0} USING latin1)".format(u)
    # "contains a non-ASCII character" as a byte-vs-character length
    # test (the same clause the standalone preflight uses on the old
    # servers, whose Spencer regex engine misreads a \x class)
    return ("{0} IS NOT NULL AND LENGTH({1}) <> CHAR_LENGTH({1}) AND "
            "BINARY CONVERT({2} USING utf8mb4) = BINARY {1} AND "
            "BINARY CONVERT(CONVERT(BINARY {2} USING utf8mb4) USING binary) "
            "= BINARY {2}".format(expr, u, down))


# rows that LOOK double-encoded (an 'Ã'/'Â' lead byte followed by a
# continuation-range character) but fail the lossless predicate: mixed or
# thrice-encoded text the standard repair cannot round-trip (B8)
MOJIBAKE_MARKER_RE = "'[\\\\x{C3}\\\\x{C2}][\\\\x{80}-\\\\x{BF}]'"


def repair_expr(expr: str) -> str:
    """Per-row conditional latin1->utf8mb4 repair: rows that are not
    provably double-encoded pass through untouched."""
    return "CASE WHEN {0} THEN {1} ELSE {2} END".format(
        double_encoded_predicate(expr), REPAIR_TEMPLATE.format(expr), expr)


# --------------------------------------------------------------------------
# introspection
# --------------------------------------------------------------------------

def introspect_columns(query, schema: str) -> Dict[str, Dict[str, dict]]:
    """{table: {column: {type, column_type, nullable, char_len, default,
    extra}}} from information_schema."""
    out: Dict[str, Dict[str, dict]] = {}
    rows = query(
        "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, "
        "IS_NULLABLE, IFNULL(CHARACTER_MAXIMUM_LENGTH, 0), "
        "IFNULL(COLUMN_DEFAULT, '\\0NONE'), EXTRA, "
        "IFNULL(CHARACTER_OCTET_LENGTH, 0) "
        "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '{0}'"
        .format(schema))
    for r in rows:
        if len(r) < 8:
            continue
        t, c, dtype, ctype, nullable, char_len, default, extra = r[:8]
        octet_len = int(r[8] or 0) if len(r) > 8 else 0
        has_default = default not in ("\\0NONE", "\0NONE")
        # MariaDB quotes string defaults in information_schema ('x'),
        # MySQL does not; a literal NULL default is "no value"
        value: Optional[str] = default if has_default else None
        if value is not None and len(value) >= 2 \
                and value[0] == "'" and value[-1] == "'":
            value = value[1:-1]
        if value is not None and value.upper() == "NULL":
            value = None
        out.setdefault(t, {})[c] = {
            "type": dtype.lower(),
            "column_type": ctype,
            "nullable": nullable.upper() == "YES",
            "char_len": int(char_len or 0),
            # TEXT/BLOB capacity is in BYTES: a same-declared column is
            # not the same capacity once the charset widens, which is
            # exactly the latin1 -> utf8mb4 move this import makes
            "octet_len": octet_len,
            "has_default": has_default,
            "default": value,
            "auto_increment": "auto_increment" in extra.lower(),
        }
    return out


def _sql_str(value: str) -> str:
    """SQL string-literal escaping for the statement builders below.

    One line, kept as a local name because seventeen call sites read better
    with it; the escape and its reasoning live in util.sql_escape."""
    return sql_escape(value)


#: the identifier shape the import accepts from a clinic dump: every table
#: and column name of the OSCAR 19 schema (and every vendor-fork addition
#: seen so far) is plain ASCII word characters. Anything else is refused
#: by the ETL pre-checks before the first write — a name is never a
#: reason to start improvising SQL quoting.
IDENTIFIER_RE = re.compile(r"^[A-Za-z0-9_$]+$")


def ident(name: str) -> str:
    """Backtick-quote an identifier with embedded backticks doubled. Every
    name that reaches SQL from the STAGED dump (table and column names
    the manifest does not know) goes through here: the statements run as
    the database root, so a crafted name must stay a name."""
    return "`" + str(name).replace("`", "``") + "`"


def unsafe_identifiers(src_info: Dict[str, Dict[str, dict]]) -> List[str]:
    """Staged table/column names outside IDENTIFIER_RE, as 'table' or
    'table.column' — the pre-check refuses the dump when any exist."""
    bad = []
    for table in sorted(src_info):
        if not IDENTIFIER_RE.match(table):
            bad.append(table)
        for column in sorted(src_info[table]):
            if not IDENTIFIER_RE.match(column):
                bad.append("{0}.{1}".format(table, column))
    return bad


ADMIN_USER_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.@\-]{0,29}$")


def validate_admin_user(name: Optional[str]) -> str:
    """The break-glass user name is interpolated into account SQL run as
    database root and must fit security.user_name (varchar 30): plain
    characters only, no quoting tricks."""
    if not name or not ADMIN_USER_RE.match(name):
        raise ValueError(
            "--admin-user must start with a letter or digit and run to "
            "at most 30 characters of letters, digits, '_', '.', '@' or "
            "'-' (got {0!r})".format(name))
    return name


def enum_values(column_type: str) -> List[str]:
    """The literals of an `enum(...)` column type, with their SQL escapes
    left as written. Empty for any other type, so callers can ask
    unconditionally."""
    m = re.match(r"enum\((.*)\)$", column_type, re.I | re.S)
    if not m:
        return []
    return re.findall(r"'((?:[^'\\]|\\.)*)'", m.group(1))


# --------------------------------------------------------------------------
# pure statement generation
# --------------------------------------------------------------------------

def idmap_table(parent: str) -> str:
    """Name of the archive-schema table holding `parent`'s old-id -> new-id
    map, written when a surrogate key had to be renumbered."""
    return "{0}__idmap".format(parent)


def source_expr(table_entry: dict, target_col: str,
                repaired: Optional[set] = None,
                archive_schema: Optional[str] = None,
                nullable: Optional[bool] = None) -> str:
    """The SELECT expression feeding one target column.

    A column listed in the entry's `fk_remap` ({column: parent_table})
    reads through the parent's id map when archive_schema is given. An id
    the map does not know (the reference was already dangling in O19)
    becomes NULL on a nullable target — never the raw value, which on the
    target may denote an unrelated CARLOS SEED row of the same id — and
    keeps the raw value only where the column is NOT NULL (reported)."""
    ve = table_entry.get("value_exprs", {})
    if target_col in ve:
        return ve[target_col]
    src = table_entry.get("renames", {}).get(target_col, target_col)
    # `ident`, not a bare backtick slot. Requirement B routes DUMP-SUPPLIED
    # column names through `renames` (with_archived_columns), so this is
    # one of the places a clinic's own fork can put a name into SQL. The
    # pre-check at run_etl refuses anything outside [A-Za-z0-9_$] before
    # the first write, and that gate still stands -- but this module's own
    # rule is that every name reaching SQL from the staged dump is
    # doubled here, and a second line of defence costs nothing.
    expr = "s.{0}".format(ident(src))
    if repaired and target_col in repaired:
        expr = repair_expr(expr)
    parent = table_entry.get("fk_remap", {}).get(target_col)
    if parent and archive_schema:
        lookup = "(SELECT m.new_id FROM `{0}`.`{1}` m WHERE m.old_id = {2})" \
            .format(archive_schema, idmap_table(parent), expr)
        expr = lookup if nullable else "IFNULL({0}, {1})".format(
            lookup, expr)
    return expr


def sanitize_expr(expr: str, dst_info: dict) -> str:
    """Wrap zero-date and enum sanitizers around a source expression."""
    dtype = dst_info["type"]
    if dtype in ("date", "datetime", "timestamp") and dst_info["nullable"]:
        zero = "0000-00-00" if dtype == "date" else "0000-00-00 00:00:00"
        expr = "NULLIF({0}, '{1}')".format(expr, zero)
    if dtype == "enum":
        values = enum_values(dst_info["column_type"])
        if values:
            # a NULL source stays NULL on a nullable target (NULL IN (...)
            # is NULL and would otherwise fall to the default branch)
            null_case = ("WHEN {0} IS NULL THEN NULL ".format(expr)
                         if dst_info["nullable"] else "")
            expr = "CASE {0}WHEN {1} IN ({2}) THEN {1} ELSE {3} END".format(
                null_case, expr,
                ", ".join("'{0}'".format(v) for v in values),
                enum_fallback(dst_info, values))
    return expr


def enum_fallback(dst_info: dict, values: List[str]) -> str:
    """SQL literal an out-of-set enum value falls to: the column's own
    DEFAULT when it has one (DEFAULT is not addressable inside CASE, so
    the introspected value is inlined), else NULL when nullable, else the
    first member (MySQL's own implicit choice)."""
    default = dst_info.get("default")
    if dst_info.get("has_default") and default is not None:
        return "'{0}'".format(_sql_str(default))
    if dst_info["nullable"]:
        return "NULL"
    return "'{0}'".format(values[0])


def enum_fallback_count_sql(table: str, entry: dict, src_schema: str,
                            dst_cols: Dict[str, dict],
                            repaired: Optional[set] = None,
                            archive_schema: Optional[str] = None
                            ) -> List[Tuple[str, str]]:
    """(column, COUNT-sql) pairs counting the source rows whose enum value
    is outside the target's set — reported so the fallback is never
    silent (the rows themselves still copy)."""
    out = []
    for c in entry["cols"]:
        info = dst_cols.get(c)
        if not info or info["type"] != "enum":
            continue
        values = enum_values(info["column_type"])
        if not values:
            continue
        expr = source_expr(entry, c, repaired, archive_schema)
        # a NULL source value on a NOT NULL target falls back as well
        null_clause = ("" if info["nullable"]
                       else " OR {0} IS NULL".format(expr))
        out.append((c, "SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE ({2} IS "
                       "NOT NULL AND {2} NOT IN ({3})){4}".format(
                           src_schema, table, expr,
                           ", ".join("'{0}'".format(v) for v in values),
                           null_clause)))
    return out


def copy_statement(table: str, entry: dict, src_schema: str,
                   dst_schema: str, dst_cols: Dict[str, dict],
                   repaired: Optional[set] = None,
                   window: Optional[Tuple[int, int]] = None,
                   archive_schema: Optional[str] = None) -> str:
    """The `INSERT ... SELECT` that copies one manifest table from staging
    into the target.

    Only the manifest's columns are named, each through `source_expr`
    (rename, curated expression, id remap or charset repair) and then
    `sanitize_expr`, which rewrites a zero date to NULL on a nullable
    target and folds an enum value the target's set does not carry to its
    fallback. Width is NOT rewritten here: an overlong value is counted
    before the copy and refused, never silently truncated. `window`
    restricts it to one id range for a chunked table; `repaired` names
    the columns whose double-encoded text this run rewrites."""
    cols = entry["cols"]
    archived = entry.get("archived_cols") or {}
    targets = ", ".join("`{0}`".format(c) for c in cols)
    # an `import_archived_` column is a verbatim copy of a source column
    # into a target column of the source's own type: nothing to sanitize,
    # and sanitizing anyway would rewrite a zero date to NULL and make
    # the archive differ from what the clinic had
    exprs = ", ".join(
        source_expr(entry, c, repaired, archive_schema,
                    dst_cols[c]["nullable"])
        if c in archived else
        sanitize_expr(source_expr(entry, c, repaired, archive_schema,
                                  dst_cols[c]["nullable"]),
                      dst_cols[c])
        for c in cols)
    sql = ("INSERT INTO `{0}`.`{1}` ({2}) SELECT {3} FROM `{4}`.`{1}` s"
           .format(dst_schema, table, targets, exprs, src_schema))
    if window:
        chunk = entry["chunk_by"]
        sql += (" WHERE s.`{0}` > {1} AND s.`{0}` <= {2}"
                .format(chunk, window[0], window[1]))
    return sql


def merge_key_exprs(entry: dict, archive_schema: Optional[str] = None,
                    dst_cols: Optional[Dict[str, dict]] = None
                    ) -> List[str]:
    """The source-side expression for each merge key — what the insert
    actually stores, so the anti-join, the insert and the id map all
    agree on what a row's key is."""
    out = []
    for k in entry["merge_keys"]:
        expr = source_expr(entry, k, None, archive_schema,
                           dst_cols[k]["nullable"] if dst_cols else None)
        if dst_cols and k in dst_cols:
            expr = sanitize_expr(expr, dst_cols[k])
        out.append(expr)
    return out


def merge_join(entry: dict, archive_schema: Optional[str] = None,
               dst_cols: Optional[Dict[str, dict]] = None,
               dst_alias: str = "d") -> str:
    """Natural-key join between the target alias (default d) and source
    alias s. The source side carries the SAME expression the insert
    stores (id remap, zero-date NULLIF, enum fallback)."""
    exprs = merge_key_exprs(entry, archive_schema, dst_cols)
    return " AND ".join(
        "{0}.`{1}` <=> {2}".format(dst_alias, k, expr)
        for k, expr in zip(entry["merge_keys"], exprs))


def merge_statement(table: str, entry: dict, src_schema: str,
                    dst_schema: str, dst_cols: Dict[str, dict],
                    repaired: Optional[set] = None,
                    archive_schema: Optional[str] = None) -> str:
    """Anti-join on the natural key: CARLOS seed rows win, clinic-added
    rows append. A surrogate integer PK is left out of the insert so
    AUTO_INCREMENT assigns fresh ids (clinic ids could collide with
    seeds); rows are appended in source-id order so the id map can pair
    them deterministically (idmap_statements).

    The NOT EXISTS reads the table this statement inserts into, which
    raises a fair question: does it see rows the same statement just
    added? On MariaDB 10.11 it does NOT -- verified by running it, not by
    reading the manual, in
    scripts/migration/o19/verify_merge_semantics.py. So a clinic table
    holding twins on the natural key copies BOTH of them, which is the
    behaviour this wants: the seed is what the clinic's row loses to, and
    the clinic's own duplicates are the clinic's data. Deduplicating them
    here would be silent loss.

    That does leave two live rows sharing a key, and children are remapped
    through the id map -- so idmap_statements pairs twin n with target
    twin n rather than collapsing both onto the first. The same script
    checks that every source id ends up mapped, across four seed/staging
    shapes; removing the surplus-twin fallback breaks three of them."""
    surrogate = entry.get("surrogate_pk")
    archived = entry.get("archived_cols") or {}
    cols = [c for c in entry["cols"] if c != surrogate]
    targets = ", ".join("`{0}`".format(c) for c in cols)
    exprs = ", ".join(
        source_expr(entry, c, repaired, archive_schema,
                    dst_cols[c]["nullable"])
        if c in archived else
        sanitize_expr(source_expr(entry, c, repaired, archive_schema,
                                  dst_cols[c]["nullable"]),
                      dst_cols[c])
        for c in cols)
    sql = ("INSERT INTO `{0}`.`{1}` ({2}) SELECT {3} FROM `{4}`.`{1}` s "
           "WHERE NOT EXISTS (SELECT 1 FROM `{0}`.`{1}` d WHERE {5})"
           .format(dst_schema, table, targets, exprs, src_schema,
                   merge_join(entry, archive_schema, dst_cols)))
    if entry.get("merge_exclude"):
        # rows of removed modules the merge must not carry (manifest
        # MERGE_EXCLUDE; the predicate addresses the staging alias s)
        sql += " AND NOT ({0})".format(entry["merge_exclude"])
    if surrogate:
        sql += " ORDER BY s.`{0}`".format(surrogate)
    return sql


def archived_backfill_statement(table: str, entry: dict, src_schema: str,
                                dst_schema: str,
                                dst_cols: Dict[str, dict],
                                archive_schema: Optional[str] = None
                                ) -> Optional[str]:
    """The UPDATE that carries a merge table's `import_archived_` values
    onto rows the merge did NOT insert, or None when there are none.

    A merge keeps CARLOS's row on a shared natural key, so a clinic row
    with a target twin never passes through `merge_statement` -- and its
    unmapped columns would be the one population requirement B still
    orphaned. The join is the merge's own natural-key join, so it pairs
    exactly the rows the anti-join rejected.

    Idempotent by construction: it assigns the same source value every
    time, and `merge_exclude` rows are left alone here as they are
    there."""
    archived = entry.get("archived_cols") or {}
    if not archived:
        return None
    sets = ", ".join(
        "d.`{0}` = s.`{1}`".format(target, source)
        for target, source in sorted(archived.items()))
    sql = ("UPDATE `{0}`.`{1}` d JOIN `{2}`.`{1}` s ON {3} SET {4}"
           .format(dst_schema, table, src_schema,
                   merge_join(entry, archive_schema, dst_cols), sets))
    if entry.get("merge_exclude"):
        sql += " WHERE NOT ({0})".format(entry["merge_exclude"])
    return sql


def merge_overridden_count_sql(table: str, entry: dict, src_schema: str,
                               dst_schema: str, dst_cols: Dict[str, dict],
                               archive_schema: Optional[str] = None) -> str:
    """Staging rows of a merge table that will NOT become live rows: the
    positive of `merge_statement`'s anti-join (a target twin already
    holds the natural key, so CARLOS's row wins) plus the `merge_exclude`
    rows the merge is told to leave behind.

    Must be counted BEFORE the merge runs. Afterwards every staging row
    has a target twin -- including the ones the merge itself inserted --
    so the same query answers "all of them" and the distinction is gone.
    """
    sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE EXISTS (SELECT 1 "
           "FROM `{2}`.`{1}` d WHERE {3})".format(
               src_schema, table, dst_schema,
               merge_join(entry, archive_schema, dst_cols)))
    if entry.get("merge_exclude"):
        sql += " OR ({0})".format(entry["merge_exclude"])
    return sql


def merge_missing_count_sql(table: str, entry: dict, src_schema: str,
                            dst_schema: str, dst_cols: Dict[str, dict],
                            archive_schema: Optional[str] = None,
                            exclude: Optional[str] = None) -> str:
    """Staging rows of a merge table that have NO target twin on the
    natural key — the reverse of the merge's own anti-join, so after the
    merge the count must be 0 (excluded removed-module rows aside).
    `exclude` is a further staging-side predicate (alias `s`) for rows a
    later step deliberately removes from the target again."""
    sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE NOT EXISTS (SELECT 1 "
           "FROM `{2}`.`{1}` d WHERE {3})".format(
               src_schema, table, dst_schema,
               merge_join(entry, archive_schema, dst_cols)))
    for predicate in (entry.get("merge_exclude"), exclude):
        if predicate:
            sql += " AND NOT ({0})".format(predicate)
    return sql


def pruned_property_predicate(prefixes: Sequence[str],
                              keys: Sequence[str] = ()) -> str:
    """Staging-side (alias `s`) predicate for the removed-module
    `property` rows the roles post-step prunes from the target after the
    merge (o19roles.property_prune_statements, same LIKE and equality
    shapes): the reverse parity must not expect their twins."""
    parts = ["s.`name` LIKE '{0}%'".format(
        _sql_str(p).replace("_", "\\_").replace("%", "\\%"))
        for p in prefixes]
    parts += ["s.`name` = '{0}'".format(_sql_str(k)) for k in keys]
    return " OR ".join(parts) if parts else "FALSE"


NUMERIC_TYPES = ("tinyint", "smallint", "mediumint", "int", "integer",
                 "bigint", "decimal", "numeric", "float", "double", "real")
STRING_TYPES = ("char", "varchar", "tinytext", "text", "mediumtext",
                "longtext", "enum")
#: what the server would accept as a number without coercion; anything
#: else becomes 0 under sql_mode='' (the ETL executor), silently. The dot
#: is a bracket class: inside a SQL string literal the server consumes a
#: backslash before the regex engine sees the pattern, so `\.` would
#: match any character
NUMERIC_LITERAL_SQL_RE = ("^[[:space:]]*[-+]?([0-9]+([.][0-9]*)?|"
                          "[.][0-9]+)([eE][-+]?[0-9]+)?[[:space:]]*$")


def coercion_precheck_sql(table: str, entry: dict, src_schema: str,
                          dst_cols: Dict[str, dict],
                          src_cols: Dict[str, dict]) -> List[Tuple[str, str]]:
    """(column, COUNT-sql) pairs for columns that are text in the dump and
    numeric in CARLOS: every non-empty value must parse as a number, or the
    copy would store 0 for it. A non-zero count is a pre-check refusal."""
    out = []
    renames = entry.get("renames", {})
    for c in entry.get("cols", []):
        d = dst_cols.get(c)
        s = src_col(src_cols, renames.get(c, c))
        if not d or not s:
            continue
        if d["type"] in NUMERIC_TYPES and s["type"] in STRING_TYPES:
            if c in entry.get("value_exprs", {}):
                # the manifest already rewrites this column; the refusal
                # names curating one as the remedy, so honour it
                continue
            src_name = renames.get(c, c)
            sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE s.`{2}` IS NOT "
                   "NULL AND TRIM(s.`{2}`) <> '' AND s.`{2}` NOT REGEXP "
                   "'{3}'".format(src_schema, table, src_name,
                                  NUMERIC_LITERAL_SQL_RE))
            out.append((c, sql))
    return out


def idmap_statements(table: str, entry: dict, src_schema: str,
                     dst_schema: str, archive_schema: str,
                     dst_cols: Optional[Dict[str, dict]] = None) -> List[str]:
    """old->new surrogate id map for a merged table. Every source row is
    paired with its target row on the natural key; rows that SHARE a
    natural key (twins) are paired in id order on both sides
    (ROW_NUMBER), so the second twin maps to the second appended row
    instead of both collapsing onto the first. Rebuilt deterministically;
    empty list for tables without a surrogate PK."""
    pk = entry.get("surrogate_pk")
    if not pk:
        return []
    name = idmap_table(table)
    keys = entry["merge_keys"]
    join = merge_join(entry, archive_schema, dst_cols)

    def build(scratch: str) -> List[str]:
        return [
            "CREATE TABLE `{0}`.`{1}` (old_id BIGINT NOT NULL PRIMARY KEY, "
            "new_id BIGINT NOT NULL)".format(archive_schema, scratch),
            # twin n maps to target twin n; a surplus source twin (its key was
            # already satisfied by a CARLOS seed, so the anti-join appended
            # nothing for it) falls back to the target's first row for that key
            # — every source id a child references gets a deterministic map
            # The SOURCE side partitions by the expressions the insert
            # stores, not by the raw columns: a key rewritten by value_exprs,
            # a zero-date NULLIF, an enum fallback or an fk_remap puts two
            # source rows in different partitions while the target holds them
            # as one — and both would then map to the same new id.
            "INSERT INTO `{0}`.`{1}` (old_id, new_id) SELECT s.`{2}`, "
            "COALESCE(d.`{2}`, d1.`{2}`) "
            "FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY {9} ORDER BY "
            "s.`{2}`) AS rn FROM `{4}`.`{5}` s) s LEFT JOIN (SELECT *, "
            "ROW_NUMBER() OVER (PARTITION BY {3} ORDER BY `{2}`) AS rn FROM "
            "`{6}`.`{5}`) d ON "
            "{7} AND s.rn = d.rn LEFT JOIN (SELECT *, ROW_NUMBER() OVER "
            "(PARTITION BY {3} ORDER BY `{2}`) AS rn FROM `{6}`.`{5}`) d1 "
            "ON {8} AND d1.rn = 1 WHERE COALESCE(d.`{2}`, d1.`{2}`) IS NOT "
            "NULL".format(
                archive_schema, scratch, pk,
                ", ".join("`{0}`".format(k) for k in keys),
                src_schema, table, dst_schema, join,
                merge_join(entry, archive_schema, dst_cols, dst_alias="d1"),
                ", ".join(merge_key_exprs(entry, archive_schema, dst_cols))),
        ]
    return rebuild_statements(archive_schema, name, build)


def fk_unmapped_count_sql(table: str, entry: dict, src_schema: str,
                          archive_schema: str) -> List[Tuple[str, str, str]]:
    """(column, parent, COUNT-sql): source rows whose foreign key names an
    id the parent's map does not know — an already-dangling O19
    reference, reported because it becomes NULL (nullable) or is kept
    raw (NOT NULL)."""
    out = []
    for col, parent in sorted(entry.get("fk_remap", {}).items()):
        source_name = entry.get("renames", {}).get(col, col)
        out.append((col, parent,
                    "SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE s.`{2}` IS "
                    "NOT NULL AND NOT EXISTS (SELECT 1 FROM `{3}`.`{4}` m "
                    "WHERE m.old_id = s.`{2}`)".format(
                        src_schema, table, source_name, archive_schema,
                        idmap_table(parent))))
    return out


def idmap_changed_count_sql(table: str, archive_schema: str) -> str:
    """Counts the rows a table's id map actually renumbered -- the figure
    the report gives for "ids that had to move"."""
    return ("SELECT COUNT(*) FROM `{0}`.`{1}` WHERE old_id <> new_id"
            .format(archive_schema, idmap_table(table)))


def window_delete_statement(table: str, entry: dict, dst_schema: str,
                            window: Tuple[int, int]) -> str:
    """Clear one PK window on the target before re-copying it: a resumed
    chunked copy re-runs the first unconfirmed window, which may already
    hold rows if the crash landed between the insert and its checkpoint."""
    return ("DELETE FROM `{0}`.`{1}` WHERE `{2}` > {3} AND `{2}` <= {4}"
            .format(dst_schema, table, entry["chunk_by"],
                    window[0], window[1]))


def etl_order(tables: Dict[str, dict]) -> List[str]:
    """Manifest tables in name order, except that a table named as an
    fk_remap parent always precedes the child that reads its id map."""
    order: List[str] = []
    seen = set()
    stack = set()

    def visit(name: str) -> None:
        if name in seen or name not in tables:
            return
        if name in stack:
            return  # curation cycle — leave name order to decide
        stack.add(name)
        for parent in sorted(set(tables[name].get("fk_remap", {}).values())):
            visit(parent)
        stack.discard(name)
        seen.add(name)
        order.append(name)

    for name in sorted(tables):
        visit(name)
    return order


#: Prefix for the copies that keep OSCAR 19 data inside the LIVE CARLOS
#: schema when the schema has no home for it: a table CARLOS does not have
#: arrives as `import_archived_<table>`, so nothing the dump carried is
#: reachable only from a schema the nightly backup does not dump and
#: `--cleanup` is free to drop.
#:
#: 16 characters onto the longest archived table name (37) is 53, and the
#: swap suffixes below take it to 58 -- inside MySQL's 64-character
#: identifier limit. `o19_archive` still gets its own untouched copy; this
#: is a second home, not a replacement for it.
ARCHIVED_PREFIX = "import_archived_"


def archived_table(table: str) -> str:
    """The live-schema name under which `table` is preserved."""
    return ARCHIVED_PREFIX + table


#: Suffixes for the build-aside swap below. Short on purpose: the longest
#: archived table name is 37 characters and the longest shadow suffix is
#: `__unknown_cols`, so `<37>__unknown_cols__old` is 56 -- comfortably
#: inside MySQL's 64-character identifier limit, which `__superseded`
#: would have sat exactly on.
REBUILD_NEW = "__new"
REBUILD_OLD = "__old"

#: Every suffix this tool appends to a table name before the rebuild
#: suffixes go on. `<table>__unknown_cols__new` is the longest identifier
#: it constructs; a manifest test measures the margin against the real
#: table names, and `oversized_preserved_names` bounds the ones a dump
#: brings.
SHADOW_SUFFIXES = ("", "__dropped", "__unknown_cols", "__idmap")


def rebuild_statements(archive_schema: str, final: str,
                       build: Callable[[str], List[str]]) -> List[str]:
    """Rebuild `archive_schema`.`final` without it ever ceasing to exist.

    The obvious shape -- DROP the old copy, then CREATE and fill a new one
    -- leaves nothing at all if anything between the DROP and the INSERT
    fails, and that is a table holding the clinic's only copy of records
    CARLOS has no home for. The ETL ledger makes it recoverable while
    staging survives, but `--cleanup` drops staging, so "recoverable" has
    an expiry date.

    So: build beside it, then swap atomically. `RENAME TABLE a TO b, c TO
    a` needs both sides to exist, which is what the CREATE ... IF NOT
    EXISTS immediately before it guarantees; the leftover `__old` is only
    ever the superseded copy, because the rename is atomic and there is
    no state in which it holds the sole one.

    `build` receives the scratch name and returns the statements that
    create and fill a table under it -- LIKE + INSERT for a whole-table
    archive, a single CREATE ... AS SELECT for a shadow capture.
    """
    new = ident(final + REBUILD_NEW)
    old = ident(final + REBUILD_OLD)
    live = ident(final)
    return (
        # our own scratch names, never the live copy: a leftover `__new`
        # is a failed build, a leftover `__old` a swap that landed but
        # whose cleanup did not
        ["DROP TABLE IF EXISTS `{0}`.{1}".format(archive_schema, new),
         "DROP TABLE IF EXISTS `{0}`.{1}".format(archive_schema, old)]
        + build(final + REBUILD_NEW)
        + ["CREATE TABLE IF NOT EXISTS `{0}`.{1} LIKE `{0}`.{2}".format(
            archive_schema, live, new),
           "RENAME TABLE `{0}`.{1} TO `{0}`.{2}, `{0}`.{3} TO `{0}`.{1}"
           .format(archive_schema, live, old, new),
           "DROP TABLE IF EXISTS `{0}`.{1}".format(archive_schema, old)])


def archive_statements(table: str, src_schema: str,
                       archive_schema: str,
                       dest_table: Optional[str] = None) -> List[str]:
    # unknown (unclassified) tables reach here under the dump's own
    # names: quoted, never trusted
    """DROP/CREATE/INSERT that copy one staging table verbatim into
    `archive_schema`, under `dest_table` when it differs from the source
    name.

    Used for the tables CARLOS has no home for (removed modules, dropped
    columns, the OSCAR 19 token tables) and for unclassified tables the
    dump carries under names this tool never chose -- hence `ident`,
    which doubles an embedded backtick rather than trusting the name.

    Called twice per preserved table: once into `o19_archive` under the
    source name (the verification copy), once into the live schema under
    `import_archived_<table>` (the copy the clinic keeps and the nightly
    backup dumps)."""
    src = ident(table)
    dest = dest_table or table

    def build(scratch: str) -> List[str]:
        name = ident(scratch)
        return [
            "CREATE TABLE `{0}`.{1} LIKE `{2}`.{3}".format(
                archive_schema, name, src_schema, src),
            "INSERT INTO `{0}`.{1} SELECT * FROM `{2}`.{3}".format(
                archive_schema, name, src_schema, src),
        ]
    return rebuild_statements(archive_schema, dest, build)


def preserve_statements(table: str, src_schema: str, archive_schema: str,
                        dst_schema: str) -> List[str]:
    """Both copies of one preserved table: `o19_archive`.`<table>` and
    `<target>`.`import_archived_<table>`.

    Ordered archive-first on purpose. Each half is its own build-aside
    swap, so neither is ever absent while it is the only copy; running
    the archive half first means the live twin is built while a second
    copy already exists outside staging."""
    return (archive_statements(table, src_schema, archive_schema)
            + archive_statements(table, src_schema, dst_schema,
                                 archived_table(table)))


def _context_cols(entry: dict) -> List[str]:
    """The row-identifying columns a shadow capture carries alongside the
    captured values (the chunk PK, else the first mapped columns)."""
    if entry.get("chunk_by"):
        return [entry["chunk_by"]]
    return [c for c in entry["cols"][:3]]


def shadow_statements(table: str, entry: dict, src_schema: str,
                      archive_schema: str,
                      src_cols: Dict[str, dict],
                      notes: Optional[List[str]] = None) -> List[str]:
    """Capture dropped-column values (+ the row's PK context) into
    o19_archive.<table>__dropped, only for rows with non-default data.

    The VERIFICATION copy, no longer the only one: requirement B puts
    every dropped column on the live table as `import_archived_<col>`,
    with the source type and every row. This capture is kept because the
    o19_archive schema is what the clinic's CSV export is rendered from,
    and because its `nondefault` predicate is the record of which rows
    the curation call was actually about.

    Reads the manifest's own view of the table, never the entry the
    archived columns were folded into -- otherwise a preserved column
    stops being "dropped" the moment it is preserved.

    A dropped column this dump does not carry (lower patch level) is
    skipped WITH a note; the remaining dropped columns are still
    captured — never the whole table silently."""
    dropped = dict(entry.get("dropped", {}))
    if not dropped:
        return []
    # case-folded for the same reason archived_column_plan folds: a dump
    # that spells the column differently HAS it, and reporting it absent
    # would drop it from the capture
    have = {c.lower() for c in src_cols}
    absent = sorted(c for c in dropped if c.lower() not in have)
    for c in absent:
        dropped.pop(c)
        if notes is not None:
            notes.append("{0}.{1}: dropped column absent from this dump — "
                         "nothing to capture".format(table, c))
    if not dropped:
        return []
    # context columns under their STAGED (source) names: a renamed column
    # such as `isactive` -> `isActive` must be read as the dump spells it
    # or it silently drops out of the capture
    renames = entry.get("renames", {})
    # resolved through the dump's own spelling, like the captured columns
    # above and like unknown_column_shadow_statements: a context column
    # the dump cases differently is still THERE, and dropping it would
    # leave the captured values with nothing to join back to
    lower = {c.lower(): c for c in src_cols}
    context = [lower[renames.get(c, c).lower()]
               for c in _context_cols(entry)
               if renames.get(c, c).lower() in lower]
    select_cols = list(dict.fromkeys(context + sorted(dropped)))
    predicate = " OR ".join(
        "({0})".format(d["nondefault"]) for d in dropped.values())
    shadow = "{0}__dropped".format(table)
    cols = ", ".join("s.`{0}`".format(c) for c in select_cols)

    def build(scratch: str) -> List[str]:
        return ["CREATE TABLE `{0}`.{1} AS SELECT {2} FROM `{3}`.`{4}` s "
                "WHERE {5}".format(archive_schema, ident(scratch), cols,
                                   src_schema, table, predicate)]
    return rebuild_statements(archive_schema, shadow, build)


def unknown_columns(entry: dict, src_cols: Dict[str, dict]) -> List[str]:
    """Staged columns the manifest neither copies, renames nor lists as
    dropped (vendor-fork additions) — matched case-insensitively."""
    known = {entry.get("renames", {}).get(c, c).lower()
             for c in entry.get("cols", [])}
    known.update(c.lower() for c in entry.get("dropped", {}))
    return sorted(c for c in src_cols if c.lower() not in known)


def archived_column_plan(entry: dict, src_cols: Dict[str, dict]
                         ) -> List[Tuple[str, str, str]]:
    """(source column, `import_archived_` target, source column type) for
    every column of one staged table that the manifest has no home for.

    Two populations, and neither reached the live schema before:

    * the manifest's curated `dropped` columns -- captured until now only
      as a `__dropped` shadow, and only for rows whose value was
      non-default, joined back by three columns chosen as a row
      identifier with no guarantee they are a key;
    * `unknown_columns` -- the columns a clinic's own fork added, which
      the manifest has never seen.

    A dropped column this dump does not carry is not in the plan (there
    is nothing to preserve); `shadow_statements` reports that case.

    The target keeps the SOURCE type verbatim, which is what makes the
    copy of it lossless: no widening, no truncation, and nothing for
    `sanitize_expr` to correct."""
    # case-insensitively, like `effective_entry` and `unknown_columns`:
    # a dump spelling a dropped column `programno` would otherwise fall
    # out of the plan AND out of unknown_columns (which folds case and so
    # counts it as known), and be preserved nowhere at all. The TARGET
    # name keeps the manifest's spelling so it is stable across dumps;
    # MySQL resolves the source column name case-insensitively either
    # way, so the emitted SQL is unaffected.
    lower = {c.lower(): info for c, info in src_cols.items()}
    out = []
    for col in sorted(set(entry.get("dropped", {}))
                      | set(unknown_columns(entry, src_cols))):
        info = lower.get(col.lower())
        if not info:
            continue
        out.append((col, archived_column(col), info["column_type"]))
    return out


#: MySQL's identifier limit, and what the prefixes leave of it. A
#: preserved TABLE also carries a rebuild suffix (`__old`/`__new`), so it
#: has five fewer characters to work with than a preserved column.
IDENTIFIER_LIMIT = 64
MAX_PRESERVED_TABLE = (IDENTIFIER_LIMIT - len(ARCHIVED_PREFIX)
                       - len(REBUILD_OLD))
MAX_PRESERVED_COLUMN = IDENTIFIER_LIMIT - len(ARCHIVED_PREFIX)


def oversized_preserved_names(src_info: Dict[str, Dict[str, dict]],
                              preserved_tables: Sequence[str],
                              column_plans: Dict[str, Sequence[str]]
                              ) -> List[str]:
    """Names whose `import_archived_` form would not fit an identifier.

    The manifest's own names are far inside the limit (the longest
    preserved table is 37 characters, the longest dropped column 25), but
    a clinic's fork names its own tables and columns, and those arrive
    unclassified. Failing on the ALTER halfway through the loop would
    leave a half-preserved import; this is checked before the first
    write, next to `unsafe_identifiers`, whose remedy it shares: rename
    in the source and re-export.

    Returns one problem string per offending name, empty when all fit."""
    out = []
    for table in sorted(preserved_tables):
        if len(table) > MAX_PRESERVED_TABLE:
            out.append(
                "{0}: preserving it as {1}{0} would exceed MySQL's "
                "{2}-character identifier limit (max {3} characters for a "
                "preserved table) — rename it in the source and re-export"
                .format(table, ARCHIVED_PREFIX, IDENTIFIER_LIMIT,
                        MAX_PRESERVED_TABLE))
    for table in sorted(column_plans):
        for col in sorted(column_plans[table]):
            if len(col) > MAX_PRESERVED_COLUMN:
                out.append(
                    "{0}.{1}: preserving it as {2}{1} would exceed MySQL's "
                    "{3}-character identifier limit (max {4} characters "
                    "for a preserved column) — rename it in the source and "
                    "re-export".format(table, col, ARCHIVED_PREFIX,
                                       IDENTIFIER_LIMIT,
                                       MAX_PRESERVED_COLUMN))
    return out


#: MySQL's hard ceiling on the sum of a row's declared column widths.
#: BLOB/TEXT contribute only their pointer, which is why a table can
#: declare far more than this in text and still be legal. InnoDB's
#: separate ~8126-byte IN-ROW limit is softer (DYNAMIC row format, the
#: server default here, pushes long variable-length values off-page) and
#: several CARLOS tables already rely on that, so it is not the gate.
MAX_ROW_BYTES = 65535

#: bytes per character assumed when measuring a declared width. CARLOS
#: runs utf8mb4, which is also what the charset repair converts INTO, so
#: 4 is what MySQL itself will use for the columns this adds. A latin1
#: column is over-measured by this, which is the safe direction for a
#: check that refuses before writing.
CHARSET_MAX_LEN = 4

_INT_WIDTHS = (("tinyint", 1), ("smallint", 2), ("mediumint", 3),
               ("bigint", 8), ("int", 4), ("float", 4), ("double", 8),
               ("datetime", 8), ("timestamp", 4), ("date", 3),
               ("time", 3), ("year", 1), ("bit", 8))


def column_bytes(column_type: str) -> int:
    """The declared width one column contributes to the row limit.

    An estimate on purpose, and deliberately not a re-implementation of
    MySQL's own arithmetic: it decides a refusal, and the alternative to
    an estimate here is the ALTER failing half-way through the table
    loop with the import already part-written."""
    t = (column_type or "").lower().strip()
    m = re.match(r"(var)?char\s*\(\s*(\d+)", t)
    if m:
        # a VARCHAR also stores a 1-2 byte length prefix
        return int(m.group(2)) * CHARSET_MAX_LEN + (2 if m.group(1) else 0)
    for name, size in (("tinytext", 9), ("tinyblob", 9),
                       ("mediumtext", 11), ("mediumblob", 11),
                       ("longtext", 12), ("longblob", 12)):
        if name in t:
            return size
    if "text" in t or "blob" in t:
        return 10
    m = re.match(r"(?:decimal|numeric)\s*\(\s*(\d+)", t)
    if m:
        return int(m.group(1)) // 2 + 2
    for name, size in _INT_WIDTHS:
        if t.startswith(name):
            return size
    if t.startswith("enum") or t.startswith("set"):
        return 2
    # BINARY/VARBINARY declare a byte length like their character
    # cousins, and a fork's VARBINARY(60000) is precisely the column that
    # would slip past this gate if it fell through to the fixed default
    m = re.match(r"(var)?binary\s*\(\s*(\d+)", t)
    if m:
        return int(m.group(2)) + (2 if m.group(1) else 0)
    # a type this does not know, but which declares a length, is measured
    # by that length rather than guessed at: over-measuring only makes
    # the refusal more cautious, under-measuring lets the ALTER through
    m = re.search(r"\(\s*(\d+)", t)
    if m:
        return int(m.group(1))
    return 8            # unknown and unsized: a middling fixed width


def oversized_rows(table: str, dst_cols: Dict[str, dict],
                   plan: Sequence[Tuple[str, str, str]]) -> Optional[str]:
    """Why this table cannot take its `import_archived_` columns, or None.

    MySQL refuses an `ALTER TABLE ... ADD COLUMN` that would push the
    row's declared width past `MAX_ROW_BYTES`, and it refuses it at the
    ALTER -- which in the table loop means part-way through an import.
    Checked before the first write instead.

    Not a theoretical limit for a clinic that forked its schema: the
    manifest's own curated columns leave every CARLOS table under a
    quarter of the ceiling (the widest, formLabReq07, reaches 17 KB of
    65 KB), but the columns a fork added are unbounded and arrive
    unclassified."""
    # only the columns still to be ADDED: on a resume the target already
    # carries some of them, and counting those twice (once in dst_cols,
    # once in the plan) would refuse a table that in fact has room
    pending = [(s, t, c) for s, t, c in plan if t not in dst_cols]
    if not pending:
        return None
    current = sum(column_bytes(c.get("column_type"))
                  for c in dst_cols.values())
    added = sum(column_bytes(ctype) for _src, _target, ctype in pending)
    if current + added <= MAX_ROW_BYTES:
        return None
    plan = pending
    return ("{0}: preserving {1} column(s) on it would take the row from "
            "roughly {2} to {3} bytes of declared width, past MySQL's "
            "{4}-byte row limit ({5}). Their values are still captured to "
            "the archive schema, but the live column cannot be added: "
            "narrow or remove these columns in the source and re-export, "
            "or migrate this table's fork columns by hand afterwards."
            .format(table, len(plan), current, current + added,
                    MAX_ROW_BYTES,
                    ", ".join(t for _s, t, _c in plan)))


def archived_column(col: str) -> str:
    """The live-schema column under which a source column is preserved."""
    return ARCHIVED_PREFIX + col


def add_archived_column_statements(table: str, dst_schema: str,
                                   plan: Sequence[Tuple[str, str, str]],
                                   dst_cols: Dict[str, dict]) -> List[str]:
    """The ALTERs that add one table's `import_archived_` columns.

    Always NULLable and never defaulted: `missing_required_columns`
    aborts the whole run on a NOT NULL target column the copy does not
    fill, and a row the dump has no value for must read as "no value",
    not as a fabricated one.

    MySQL 8 has no `ADD COLUMN IF NOT EXISTS`, so a column already there
    is skipped from the introspected schema -- which is also what makes a
    resumed run a no-op rather than a duplicate-column error. The COMMENT
    is for the operator reading `SHOW CREATE TABLE` a year from now: it
    names the OSCAR 19 column the values came from."""
    out = []
    for src_col, target, coltype in plan:
        if target in dst_cols:
            continue
        out.append(
            "ALTER TABLE `{0}`.`{1}` ADD COLUMN `{2}` {3} NULL COMMENT "
            "'OSCAR 19 {1}.{4} preserved by import-o19'".format(
                dst_schema, table, target, coltype, src_col))
    return out


def with_archived_columns(entry: dict, plan: Sequence[Tuple[str, str, str]]
                          ) -> dict:
    """`entry` with its `import_archived_` columns folded into the copy.

    The mapping mechanism is the manifest's own: `renames` is
    target -> source, exactly the direction `source_expr` reads, so the
    prefixed column is filled by `s.<source>` with no new machinery.
    `archived_cols` records which targets they are, because those are the
    ones that must be copied VERBATIM -- `sanitize_expr` would rewrite a
    zero date to NULL, and an archived value that differs from the source
    is not an archive."""
    if not plan:
        return entry
    out = dict(entry)
    out["cols"] = list(entry.get("cols", [])) + [t for _s, t, _c in plan]
    renames = dict(entry.get("renames", {}))
    renames.update({t: s for s, t, _c in plan})
    out["renames"] = renames
    out["archived_cols"] = {t: s for s, t, _c in plan}
    return out


def unknown_column_shadow_statements(table: str, entry: dict,
                                     src_schema: str, archive_schema: str,
                                     src_cols: Dict[str, dict]) -> List[str]:
    """Shadow-capture vendor-fork columns (o19_archive.<table>__unknown_cols)
    for every row where any of them holds a value — the verification copy
    behind the `unknown-as-archive` sign-off.

    The live copy is `import_archived_<col>` on the table itself, which
    carries every row rather than only the ones with a value. Takes the
    manifest's view of the entry, for the reason `shadow_statements`
    gives."""
    extra = unknown_columns(entry, src_cols)
    if not extra:
        return []
    # case-insensitively, like effective_entry: a dump spelling the key
    # differently would otherwise drop the row identifier and leave the
    # captured values with nothing to join back to
    lower = {c.lower(): c for c in src_cols}
    context = [lower[entry.get("renames", {}).get(c, c).lower()]
               for c in _context_cols(entry)
               if entry.get("renames", {}).get(c, c).lower() in lower]
    select_cols = list(dict.fromkeys(context + extra))
    shadow = "{0}__unknown_cols".format(table)
    # the extra columns are the dump's own names: quoted, never trusted
    cols = ", ".join("s." + ident(c) for c in select_cols)
    predicate = " OR ".join("s.{0} IS NOT NULL".format(ident(c))
                            for c in extra)

    def build(scratch: str) -> List[str]:
        return ["CREATE TABLE `{0}`.{1} AS SELECT {2} FROM `{3}`.`{4}` s "
                "WHERE {5}".format(archive_schema, ident(scratch), cols,
                                   src_schema, table, predicate)]
    return rebuild_statements(archive_schema, shadow, build)


def chunk_windows(lo: int, hi: int, size: int = CHUNK_ROWS
                  ) -> List[Tuple[int, int]]:
    """(exclusive-low, inclusive-high] PK windows covering [lo, hi]."""
    if hi < lo:
        return []
    windows = []
    start = lo - 1
    while start < hi:
        end = min(start + size, hi)
        windows.append((start, end))
        start = end
    return windows


# --- seed reconciliation ---------------------------------------------------

ADMIN_COLUMN_CANDIDATES = ("provider_no", "providerNo", "user_name")


def seed_admin_statements(dst_schema: str, admin_user: str,
                          admin_provider_no: str, password_hash: str,
                          pin: str) -> List[str]:
    """Strictly-ordered start of the seed script: create the break-glass
    admin (mirroring the seeded clinician's roles) BEFORE any seed row is
    deleted."""
    seed_pn = _sql_str(o19map_schema.SEED_PROVIDER_NO)
    user = _sql_str(validate_admin_user(admin_user))
    pn = _sql_str(admin_provider_no)
    return [
        # provider row cloned from the seeded clinician, new id + name
        "INSERT INTO `{0}`.provider (provider_no, last_name, first_name, "
        "provider_type, specialty, status, lastUpdateUser, lastUpdateDate) "
        "SELECT '{1}', 'Admin', '{2}', provider_type, specialty, '1', "
        "'{1}', NOW() FROM `{0}`.provider WHERE provider_no = '{3}'"
        .format(dst_schema, pn, user, seed_pn),
        "INSERT INTO `{0}`.security (user_name, password, provider_no, "
        "pin, forcePasswordReset, lastUpdateUser, lastUpdateDate) VALUES "
        "('{1}', '{2}', '{3}', '{4}', 1, '{3}', NOW())"
        .format(dst_schema, user, _sql_str(password_hash), pn,
                _sql_str(pin)),
        # roles cloned ACTIVE: hasPrivilege counts only activeyn = 1 rows,
        # and the column's default is NULL (an inert admin, found in M8)
        "INSERT INTO `{0}`.secUserRole (provider_no, role_name, orgcd, "
        "activeyn, lastUpdateDate) SELECT '{1}', role_name, "
        "IFNULL(orgcd, 'R0000001'), 1, NOW() FROM "
        "`{0}`.secUserRole WHERE provider_no = '{2}'"
        .format(dst_schema, pn, seed_pn),
    ]


def admin_user_conflicts_sql(src_schema: str, admin_user: str) -> str:
    """COUNT of clinic logins that already use the break-glass name:
    security.user_name is UNIQUE, so a collision would abort the copy
    mid-run (and a retry would keep failing)."""
    return ("SELECT COUNT(*) FROM `{0}`.security WHERE user_name = '{1}'"
            .format(src_schema, _sql_str(validate_admin_user(admin_user))))


def seed_admin_cleanup_statements(dst_schema: str, admin_user: str,
                                  admin_provider_no: str) -> List[str]:
    """Remove a partially created break-glass admin (an interrupted first
    attempt) so the seed script can re-run from the top. Targets only the
    admin's own identity — the clinic's rows are not yet in the target
    when this runs, and provider_no is above the clinic's range anyway."""
    user = _sql_str(validate_admin_user(admin_user))
    pn = _sql_str(admin_provider_no)
    return [
        "DELETE FROM `{0}`.secUserRole WHERE provider_no = '{1}'"
        .format(dst_schema, pn),
        "DELETE FROM `{0}`.security WHERE user_name = '{1}' AND "
        "provider_no = '{2}'".format(dst_schema, user, pn),
        "DELETE FROM `{0}`.provider WHERE provider_no = '{1}'"
        .format(dst_schema, pn),
    ]


def seed_delete_statements(dst_schema: str) -> List[str]:
    """The deletes that remove the deploy's own seeded clinician
    (`carlosdoc`) and everything hanging off it, so the clinic's own
    provider rows land id-intact."""
    return ["DELETE FROM `{0}`.`{1}` WHERE {2}".format(dst_schema, t, where)
            for t, where in o19map_schema.CARLOSDOC_SEED_DELETES]


def seed_group_tables() -> List[str]:
    """The tables `seed_delete_statements` touches, deduplicated and
    sorted. The copy loop consults this to use the admin-preserving
    retry delete instead of the generic one."""
    return sorted({t for t, _ in o19map_schema.CARLOSDOC_SEED_DELETES})


def seed_group_retry_delete(table: str, dst_schema: str, admin_user: str,
                            admin_provider_no: str,
                            dst_cols: Dict[str, dict]) -> str:
    """Re-clear a seed-group table WITHOUT touching the break-glass admin
    (the generic delete-and-recopy retry would wipe it)."""
    if "user_name" in dst_cols:
        keep = "user_name <> '{0}'".format(_sql_str(admin_user))
    elif "provider_no" in dst_cols:
        keep = "provider_no <> '{0}'".format(_sql_str(admin_provider_no))
    elif "providerNo" in dst_cols:
        keep = "providerNo <> '{0}'".format(_sql_str(admin_provider_no))
    else:
        keep = "1=1"
    return "DELETE FROM `{0}`.`{1}` WHERE {2}".format(
        dst_schema, table, keep)


def force_reset_statement(dst_schema: str) -> str:
    """Sets `forcePasswordReset` on every carried login: OSCAR 19 password
    hashes come across, so each provider is made to choose a new one at
    first sign-in under CARLOS."""
    return ("UPDATE `{0}`.security SET forcePasswordReset = 1"
            .format(dst_schema))


# --------------------------------------------------------------------------
# pre-checks (loud, before any write)
# --------------------------------------------------------------------------

def missing_required_columns(entry: dict,
                             dst_cols: Dict[str, dict]) -> List[str]:
    """CARLOS-added NOT NULL columns without a default and without a
    value_exprs entry: the insert would fail — abort with curation
    guidance instead of failing mid-copy."""
    copied = set(entry["cols"])
    ve = set(entry.get("value_exprs", {}))
    out = []
    for col, info in dst_cols.items():
        if col in copied or col in ve:
            continue
        if (not info["nullable"] and not info["has_default"]
                and not info["auto_increment"]):
            out.append(col)
    return sorted(out)


def credential_rows(plain, src_schema: str,
                    src_info: Dict[str, Dict[str, dict]]
                    ) -> List[Tuple[str, int]]:
    """(table, rows) for every CREDENTIAL_TABLES table the dump carries
    with at least one row."""
    out = []
    for table in getattr(o19map_schema, "CREDENTIAL_TABLES", ()):
        if table not in src_info:
            continue
        n = int(plain("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
            src_schema, table))[0][0])
        if n:
            out.append((table, n))
    return out


BYTE_CAPACITY_TYPES = ("tinytext", "text", "mediumtext", "longtext",
                       "tinyblob", "blob", "mediumblob", "longblob")


def src_col(src_cols: Dict[str, dict], name: str) -> Optional[dict]:
    """The staged column, matched the way effective_entry decides a
    column is present: case-insensitively. MySQL resolves column names
    case-insensitively, so a dump spelling a column differently still
    copies — and a case-sensitive lookup here would silently skip the
    guard for exactly that column."""
    got = src_cols.get(name)
    if got is not None:
        return got
    lower = name.lower()
    for have, info in src_cols.items():
        if have.lower() == lower:
            return info
    return None


def overlength_precheck_sql(table: str, entry: dict, src_schema: str,
                            dst_cols: Dict[str, dict],
                            src_cols: Dict[str, dict],
                            repaired: Optional[set] = None,
                            archive_schema: Optional[str] = None
                            ) -> List[Tuple[str, str]]:
    """(column, COUNT-sql) pairs for target text columns that cannot hold
    what the source has — a non-zero count must ERROR, never truncate.
    Counted on the REPAIRED text where a charset repair applies ('Ã©' is
    two characters, 'é' one): the text that will be stored is what must
    fit.

    TEXT and BLOB capacity is in BYTES, so a same-declared column is not
    the same capacity when the charset widens: a latin1 `text` holds
    65535 characters, a utf8mb4 `text` 65535 bytes. Those columns are
    always measured, in bytes; sized types (VARCHAR/CHAR) keep the
    character comparison and are only checked when they actually
    narrow."""
    out = []
    for c in entry["cols"]:
        d = dst_cols.get(c)
        s = src_col(src_cols, entry.get("renames", {}).get(c, c))
        if not d or not s:
            continue
        expr = source_expr(entry, c, repaired, archive_schema)
        if d["type"] in BYTE_CAPACITY_TYPES:
            cap = d.get("octet_len") or d["char_len"]
            if not cap:
                continue
            # a BLOB is bytes, not text: converting it through utf8mb4
            # would measure a re-encoding of binary data (and could
            # reject a perfectly valid scanned document). Only the TEXT
            # family is measured as the target will store it.
            measure = ("OCTET_LENGTH({0})".format(expr)
                       if d["type"].endswith("blob")
                       else "LENGTH(CONVERT({0} USING utf8mb4))".format(expr))
            sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE {2} > {3}"
                   .format(src_schema, table, measure, cap))
            out.append((c, sql))
        elif d["char_len"] and s["char_len"] \
                and d["char_len"] < s["char_len"]:
            sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE "
                   "CHAR_LENGTH({2}) > {3}".format(
                       src_schema, table, expr, d["char_len"]))
            out.append((c, sql))
    return out


# --------------------------------------------------------------------------
# execution driver
# --------------------------------------------------------------------------

def progress_path(state_dir: str) -> str:
    """Path of the ETL checkpoint ledger inside a run's workspace."""
    return os.path.join(state_dir, "etl-progress.json")


def load_progress(state_dir: str, dump_sha256: Optional[str] = None,
                  schema_map_version: Optional[str] = None) -> Dict:
    """The ETL checkpoint ledger, BOUND to the staged dump's digest and to
    the manifest that classified it. Readers that only inspect the ledger
    pass neither; the ETL passes both and refuses to continue a ledger
    that belongs to a different dump or manifest (a mid-import target
    cannot be "started over" — the remedy is the pre-import snapshot). A
    ledger with table marks but no digest is untrusted and reset."""
    from .util import die
    try:
        with open(progress_path(state_dir), encoding="utf-8") as fh:
            progress = json.load(fh)
    except FileNotFoundError:
        progress = {"tables": {}}
    except (OSError, ValueError) as exc:
        die("cannot read the ETL ledger {0} ({1}) — the target may hold a "
            "partial copy: restore the pre-import snapshot and start over"
            .format(progress_path(state_dir), exc))
    progress.setdefault("tables", {})
    if dump_sha256:
        recorded = progress.get("dump_sha256")
        if recorded and recorded != dump_sha256:
            die("the ETL ledger in {0} belongs to a different dump "
                "(sha256 {1}...) than the one staged now ({2}...). The "
                "target may already hold rows from the first dump: "
                "restore the pre-import snapshot and start over."
                .format(state_dir, recorded[:12], dump_sha256[:12]))
        if not recorded and (progress["tables"]
                             or progress.get("admin_provider_no")):
            # the ledger carries writes it cannot attribute to a dump: a
            # reset would re-enter the seed block over a target that
            # already holds the admin — same remedy as a foreign dump
            die("the ETL ledger in {0} records writes but no dump digest; "
                "it cannot be trusted to resume. The target may already "
                "hold rows: restore the pre-import snapshot and start "
                "over.".format(state_dir))
        progress["dump_sha256"] = dump_sha256
    if schema_map_version:
        recorded = progress.get("schema_map_version")
        if not recorded and (progress["tables"]
                             or progress.get("admin_provider_no")):
            die("the ETL ledger in {0} carries table marks but no manifest "
                "version — its classification cannot be trusted. Restore "
                "the pre-import snapshot and start over.".format(state_dir))
        if recorded and recorded != schema_map_version:
            die("the ETL ledger was written under manifest {0}; this "
                "package carries {1}. Tables marked done were classified "
                "differently — restore the pre-import snapshot and start "
                "over with one package version.".format(
                    recorded, schema_map_version))
        progress["schema_map_version"] = schema_map_version
    return progress


def save_progress(state_dir: str, progress: Dict) -> None:
    # 0600 like the private text files (the roles ledger plans carry
    # provider numbers), and fsynced: a rename that lands before the data
    # would leave a ledger of zeroes describing writes that did happen
    """Persist the ETL ledger at 0600, durably.

    0600 because the roles plans it carries name provider numbers, and
    fsynced through `durable_json` because a rename landing before the
    data would leave a ledger of zeroes describing writes that did
    happen -- which a resume would then redo."""
    from . import o19import
    o19import.durable_json(progress_path(state_dir), progress)


# The numeric code decides whenever the client gave one: a bare "1054"
# substring test also matched a table name, a row id or a byte offset
# carrying those digits, and a bare "doesn't exist" also matches
# MariaDB's 1932 "doesn't exist in engine", which means a CORRUPT table.
# Either way a real failure would be classified as "absent at this patch
# level" — in the P7 spot check that silently skips a verification join,
# and in detect_repairs it would pass a failed charset scan as clean.
ABSENT_OBJECT_CODES = ("1054", "1146")
ERROR_CODE_RE = re.compile(r"ERROR\s+(\d+)")
ABSENT_OBJECT_TEXT_RE = re.compile(
    r"Unknown column|doesn't exist(?!\s+in\s+engine)", re.I)


class QueryError(RuntimeError):
    """A failed client statement: the message carries the SQL prefix for
    the operator, `stderr` the server's own error text — which is the only
    part the absent-object test may look at (the SQL can contain a patient
    id such as 1054)."""

    def __init__(self, message: str, stderr: str = ""):
        """`stderr` is kept separately because the verdict on an error
        must come from the SERVER's text: the statement in `message`
        can carry a patient identifier."""
        RuntimeError.__init__(self, message)
        self.stderr = stderr


def absent_object_error(exc: Exception) -> bool:
    """True for the server's 'no such table/column' errors — judged on
    the client's stderr alone, not on the formatted message with the
    statement text in it."""
    text = getattr(exc, "stderr", None)
    if text is None:
        text = str(exc)
    text = text or ""
    code = ERROR_CODE_RE.search(text)
    if code:
        return code.group(1) in ABSENT_OBJECT_CODES
    return bool(ABSENT_OBJECT_TEXT_RE.search(text))


def detect_repairs(query, src_schema: str, accepted,
                   notes: Optional[List[str]] = None) -> Dict[str, set]:
    """Charset scan: {table: {columns holding double-encoded rows}} — and
    hard-stop (B8) if rows look double-encoded but cannot be repaired
    losslessly, or if repair is needed but unacknowledged. The repair
    itself is applied per ROW (repair_expr), never to a whole column. A
    scan query that fails for any reason other than the column being
    absent from this dump raises (never "clean")."""
    from .util import die
    repairs: Dict[str, set] = {}
    unrepairable = []
    for table, entry in o19map_schema.TABLES.items():
        for col in entry.get("charset_scan", ()):
            expr = "`{0}`".format(col)
            try:
                n = int(query(
                    "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE {2}".format(
                        src_schema, table,
                        double_encoded_predicate(expr)))[0][0])
            except Exception as exc:
                # a lower patch level may lack the column/table (already
                # reported as patch-level variance); anything else is a
                # failed scan and must not pass as "clean"
                if absent_object_error(exc):
                    if notes is not None:
                        notes.append("charset scan skipped: {0}.{1} not in "
                                     "this dump".format(table, col))
                    continue
                raise RuntimeError(
                    "charset scan of {0}.{1} failed: {2}"
                    .format(table, col, exc)) from exc
            bad = int(query(
                "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE {2} IS NOT NULL AND "
                "{2} REGEXP {3} AND NOT ({4})".format(
                    src_schema, table, expr, MOJIBAKE_MARKER_RE,
                    double_encoded_predicate(expr)))[0][0])
            if bad:
                unrepairable.append("{0}.{1} ({2} row(s))".format(
                    table, col, bad))
            # Thrice-encoded text SATISFIES the predicate — it round-trips
            # — so it is not in `bad`, and one repair hop leaves it still
            # mojibake while the report says the repair handled it. The
            # repair must be a fixed point: a value that still looks
            # double-encoded after repairing is not repairable here.
            multi = int(query(
                "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE ({2}) AND ({3})"
                .format(src_schema, table,
                        double_encoded_predicate(expr),
                        double_encoded_predicate(
                            REPAIR_TEMPLATE.format(expr))))[0][0])
            if multi:
                unrepairable.append(
                    "{0}.{1} ({2} row(s) still double-encoded after one "
                    "repair — encoded more than twice)".format(
                        table, col, multi))
            if n:
                repairs.setdefault(table, set()).add(col)
                if notes is not None:
                    notes.append("{0}.{1}: {2} double-encoded row(s) "
                                 "detected".format(table, col, n))
    if unrepairable:
        die("B8: text that looks double-encoded but does not round-trip "
            "through the standard latin1->utf8mb4 repair:\n  "
            + "\n  ".join(unrepairable)
            + "\nThis needs manual investigation — no flag overrides it.")
    if repairs and "charset-repair" not in accepted:
        cols = sorted("{0}.{1}".format(t, c)
                      for t, cs in repairs.items() for c in cs)
        die("double-encoded text detected in: {0}\nRe-run with "
            "--accept charset-repair to apply the per-row latin1->utf8mb4 "
            "repair during the copy.".format(", ".join(cols)))
    return repairs


def effective_entry(table: str, entry: dict,
                    src_cols: Dict[str, dict],
                    src_tables: Optional[set] = None
                    ) -> Tuple[dict, List[str]]:
    """Intersect the manifest's column map with what the staged dump
    actually has (case-insensitive): a clinic at a lower patch level may
    lack columns the manifest superset knows. Missing sources are skipped
    WITH a report line — the target column then takes its default. An
    fk_remap whose PARENT table is absent from the dump is dropped with a
    note (no id map can exist for it) rather than left to fail."""
    have = {c.lower() for c in src_cols}
    renames = entry.get("renames", {})
    ve = entry.get("value_exprs", {})
    kept, skipped = [], []
    for c in entry.get("cols", []):
        if c in ve or renames.get(c, c).lower() in have:
            kept.append(c)
        else:
            skipped.append(c)
    notes = ["{0}.{1} absent from this dump — target default used"
             .format(table, c) for c in skipped]
    # a remap only makes sense for a column the copy actually reads from
    # the dump (a skipped or synthesized column has no source id to map)
    remap = {}
    for col, parent in entry.get("fk_remap", {}).items():
        if col in kept and col not in ve:
            remap[col] = parent
        else:
            notes.append("{0}.{1}: column absent from this dump — id "
                         "remap disabled".format(table, col))
    if src_tables is not None:
        for col, parent in sorted(remap.items()):
            if parent not in src_tables:
                notes.append("{0}.{1}: parent table {2} absent from this "
                             "dump — id remap disabled, raw ids kept"
                             .format(table, col, parent))
                del remap[col]
    if not skipped and remap == entry.get("fk_remap", {}):
        return entry, notes
    adjusted = dict(entry)
    adjusted["cols"] = kept
    if remap:
        adjusted["fk_remap"] = remap
    else:
        adjusted.pop("fk_remap", None)
    return adjusted, notes


def missing_merge_keys(entry: dict, src_cols: Dict[str, dict]) -> List[str]:
    """Merge keys the staged dump does not carry, case-insensitively.

    `effective_entry` reduces `cols` and `fk_remap` to what the dump has,
    but it deliberately does NOT reduce `merge_keys`: a merge is an
    anti-join on the natural key, and a partial key is not a smaller
    correct key -- it silently folds distinct clinic rows onto one target
    row, or appends duplicates. So a dump missing a merge key is refused
    up front rather than merged on what is left.
    """
    have = {c.lower() for c in src_cols}
    renames = entry.get("renames", {})
    return [k for k in entry.get("merge_keys", ())
            if renames.get(k, k).lower() not in have]


def normalize_table_case(plain, src_schema: str,
                         src_tables: List[str]) -> List[str]:
    """Rename staged tables whose name differs from the manifest only by
    case (a source server with lower_case_table_names=1 dumps everything
    lower-cased) to the manifest spelling, so every later lookup matches.
    Returns report lines."""
    by_lower = {t.lower(): t for t in o19map_schema.TABLES}
    present = set(src_tables)
    lines = []
    for live in sorted(src_tables):
        want = by_lower.get(live.lower())
        if want and want != live:
            if want in present:
                # both spellings exist (an old CAISI twin next to the
                # current table): the preflight blocks case twins; here
                # it is left alone rather than failing with error 1050
                lines.append("{0} left as is: {1} also exists".format(
                    live, want))
                continue
            plain("RENAME TABLE `{0}`.{1} TO `{0}`.{2}".format(
                src_schema, ident(live), ident(want)))
            lines.append("{0} -> {1}".format(live, want))
    return lines


def precheck_scope(state_dir: str) -> str:
    """How a pre-check refusal must describe what already stands. Before
    the copy has written anything the whole import is untouched; on a
    resume whose ledger records work, the earlier phases' writes remain
    and only the copy stopped, so "nothing was written" would be a lie.
    Reads the ledger without binding it to a dump or manifest, and
    never fails: it only chooses a phrase for another refusal, so an
    unreadable ledger must not replace that refusal with its own.
    A ledger that parses but is not the mapping the writer produces is
    treated as unreadable rather than as an empty one — claiming
    "nothing was written" off a shape we do not recognise is the one
    wrong answer."""
    unreadable = ("the ETL ledger could not be read, so assume earlier "
                  "writes stand")
    try:
        with open(progress_path(state_dir), encoding="utf-8") as fh:
            progress = json.load(fh)
    except FileNotFoundError:
        return "nothing was written"
    except (OSError, ValueError):
        return unreadable
    if not isinstance(progress, dict):
        return unreadable
    tables = progress.get("tables")
    # load_progress setdefaults "tables" to {} on every path, so a ledger
    # without one as a mapping is not a shape this writer produces —
    # and "nothing was written" is the one wrong answer for a shape we
    # do not recognise
    if not isinstance(tables, dict):
        return unreadable
    if progress.get("admin_provider_no") or tables:
        return "no further writes were made"
    return "nothing was written"


def etl_precheck_problems(ctx, plain, query, src_schema: str,
                          arch_schema: str,
                          src_info: Dict[str, Dict[str, dict]],
                          dst_info: Dict[str, Dict[str, dict]],
                          effective: Dict[str, dict],
                          repairs: Dict[str, set],
                          admin_user: str) -> List[str]:
    """Every reason to refuse P4, gathered BEFORE the first write.

    Returns the refusal lines; an empty list means the copy may start.
    Collecting rather than dying on the first problem is deliberate: an
    operator who has to re-export a dump wants the whole list, not one
    round trip per defect. The caller owns the die() so the phase's
    control flow stays in one place.

    Read-only by contract -- it counts and introspects, never writes,
    because the pristine-target guarantee P0 established still holds
    while it runs.
    """
    from . import o19roles  # imports this module; resolved lazily
    src, arch = src_schema, arch_schema
    problems = []
    if admin_user == o19map_schema.SEED_USER_NAME:
        problems.append("--admin-user must not be the seeded login '{0}'"
                        .format(admin_user))
    elif "security" in src_info and int(plain(
            admin_user_conflicts_sql(src, admin_user))[0][0]):
        problems.append("--admin-user '{0}' is already a clinic login in "
                        "the dump (security.user_name is unique) — choose "
                        "another name".format(admin_user))
    for table in sorted(effective):
        entry = effective[table]
        if table not in dst_info:
            problems.append("{0}: missing from target schema".format(table))
            continue
        # a manifest column the target lacks (a manifest built against a
        # different Flyway level than the one installed) would be a bare
        # KeyError inside the copy loop, after the seed block wrote
        absent = [c for c in entry.get("cols", []) if c not in dst_info[table]]
        if absent:
            problems.append("{0}: manifest column(s) not in the target "
                            "schema: {1}".format(table, ", ".join(absent)))
            continue
        gone = missing_merge_keys(entry, src_info[table])
        if gone:
            problems.append(
                "{0}: merge key column(s) absent from this dump: {1}. A "
                "merge is an anti-join on the natural key; merging on a "
                "partial key would fold distinct rows together or append "
                "duplicates. Re-export from a patch level that has them."
                .format(table, ", ".join(gone)))
        required = missing_required_columns(entry, dst_info[table])
        if required:
            problems.append(
                "{0}: NOT NULL target column(s) without default or "
                "value_exprs: {1}".format(table, ", ".join(required)))
        for col, sql in overlength_precheck_sql(
                table, entry, src, dst_info[table], src_info[table],
                repairs.get(table), arch):
            n = int(query(sql)[0][0])
            if n:
                problems.append(
                    "{0}.{1}: {2} value(s) longer than the target column — "
                    "refusing to truncate".format(table, col, n))
        for col, sql in coercion_precheck_sql(
                table, entry, src, dst_info[table], src_info[table]):
            n = int(query(sql)[0][0])
            if n:
                problems.append(
                    "{0}.{1}: {2} non-numeric value(s) in a column CARLOS "
                    "stores as a number — the copy would store 0 for them; "
                    "curate a value_exprs entry or fix the source"
                    .format(table, col, n))
    # the roles post-step's own preconditions, predicted from staging (the
    # tables involved are id-intact copies): refuse here, before the first
    # write, rather than after the whole copy
    # every plain-client statement interpolates values with backslash
    # escaping; a server mode that disables it (or ANSI_QUOTES) would turn
    # a quoted clinic value into SQL. The ETL executor pins sql_mode='';
    # the plain client runs under the server's own, so it is checked once
    mode = plain("SELECT @@SESSION.sql_mode")[0][0].upper()
    for flag in ("NO_BACKSLASH_ESCAPES", "ANSI_QUOTES"):
        if flag in mode:
            problems.append("the server's sql_mode carries {0}; the import "
                            "quotes clinic values with backslash escapes "
                            "and refuses to run under it — clear it for "
                            "the import".format(flag))
    # tables the roles post-step reads from staging: a dump without them
    # is not an OSCAR 19 clinic dump, and the copy loop would skip the
    # absent ones, leaving CARLOS's seeded rows standing in for the
    # clinic's (Facility/clinic are the ones that also gate login)
    for table in ROLES_STEP_TABLES:
        if table not in src_info and table not in ("Facility", "clinic"):
            problems.append("the dump has no {0} table — not an OSCAR 19 "
                            "clinic dump".format(table))
    if "Facility" not in src_info:
        problems.append("the dump has no Facility table — not an OSCAR 19 "
                        "clinic dump")
    elif "disabled" not in src_info["Facility"]:
        problems.append("the dump's Facility table has no `disabled` "
                        "column — a patch level older than the import "
                        "supports")
    elif not int(plain(o19roles.enabled_facility_count_sql(src))[0][0]):
        problems.append("the dump has no enabled Facility row — CARLOS "
                        "cannot log anyone in without one; enable a "
                        "Facility in the source and re-export")
    if "clinic" not in src_info:
        problems.append("the dump has no clinic table — not an OSCAR 19 "
                        "clinic dump")
    elif not int(plain(o19roles.clinic_count_sql(src))[0][0]):
        problems.append("the dump has no `clinic` row — letterheads, "
                        "requisitions and consultations dereference it")
    # OAuth consumer secrets, signing keys: copied verbatim, they keep
    # working after cutover — carrying them is a recorded sign-off
    carried = credential_rows(plain, src, src_info)
    if carried and "carry-credentials" not in ctx["accepted"]:
        problems.append("the dump carries live credentials ({0}) — they "
                        "would keep working against the migrated system; "
                        "acknowledge with --accept carry-credentials and "
                        "rotate/verify them before go-live".format(
                            ", ".join("{0}: {1} row(s)".format(t, n)
                                      for t, n in carried)))
    if ctx.get("role_templates") and "secRole" in src_info:
        stage_roles = [r[0] for r in plain(
            "SELECT role_name FROM `{0}`.secRole".format(src))]
        stage_rows = plain("SELECT roleUserGroup, objectName, privilege, "
                           "priority FROM `{0}`.secObjPrivilege".format(src))
        customs = o19roles.custom_roles(
            stage_roles, stage_rows, o19map_schema.STOCK_ROLE_NAMES)
        problems.extend(o19roles.validate_role_templates(
            ctx["role_templates"], customs, o19map_schema.STOCK_ROLE_NAMES))
    # every name requirement B is about to create, checked before the
    # first write: an ALTER or CREATE that overflows MySQL's identifier
    # limit halfway through the loop would leave a half-preserved import
    preserved = [t for t in src_info
                 if o19map_schema.TABLES.get(t, {}).get("class",
                                                        "unknown")
                 in PRESERVED_CLASSES + ("unknown",)]
    plans = {t: archived_column_plan(
        effective.get(t, o19map_schema.TABLES[t]), src_info[t])
        for t in sorted(effective)}
    problems.extend(oversized_preserved_names(
        src_info, preserved,
        {t: [c for c, _target, _type in p] for t, p in plans.items()}))
    # ... and the OTHER ceiling: an identifier that fits can still be a
    # column the row has no room for
    for table in sorted(plans):
        if table in dst_info:
            wide = oversized_rows(table, dst_info[table], plans[table])
            if wide:
                problems.append(wide)
    return problems


def enum_fallback_lines(query, src_schema: str, arch_schema: str,
                        dst_info: Dict[str, Dict[str, dict]],
                        effective: Dict[str, dict],
                        repairs: Dict[str, set]) -> List[str]:
    """Report lines for values that will fall to their column default.

    A value outside the target enum set is stored as the column default
    rather than refused -- an OSCAR 19 clinic accumulates twenty years of
    enum drift, and refusing would block every import. Counting them up
    front is what keeps that substitution visible to the operator.
    """
    src, arch = src_schema, arch_schema
    enum_lines: List[str] = []
    enum_lines = []
    for table in sorted(effective):
        for col, sql in enum_fallback_count_sql(
                table, effective[table], src, dst_info[table],
                repairs.get(table), arch):
            n = int(query(sql)[0][0])
            if n:
                enum_lines.append(
                    "{0}.{1}: {2} value(s) outside the CARLOS enum set fall "
                    "to the column default ({3})".format(
                        table, col, n,
                        enum_fallback(dst_info[table][col],
                                      enum_values(dst_info[table][col]
                                                  ["column_type"]))))
    return enum_lines


class EtlRun(object):
    """The state one P4 table pass shares between its per-class steps.

    run_etl() owns the phase's control flow; the etl_* step functions
    below own one table-class decision each. Threading this object rather
    than twenty positional arguments is not merely brevity: the ledger
    (``progress``), the running ``counts`` and the per-class report lists
    are shared MUTABLE objects, so a step records its finding into the
    very list run_etl renders at the end. A step handed copies would lose
    every line it wrote.

    Bound once, in run_etl, after the pre-checks have passed and the
    target introspection is final.
    """

    def __init__(self, ctx, progress, src_info, dst_info, effective,
                 repairs, admin_user, admin_pn, seed_group,
                 tolerated_tables, counts, kept, absent_tables):
        self.query = ctx["query_etl"]      # carries the session prelude
        self.plain = ctx["query"]
        self.src = ctx["src_schema"]
        self.dst = ctx["target_db"]
        self.arch = ctx["archive_schema"]
        self.state_dir = ctx["state_dir"]
        self.report = ctx["report"]
        self.progress = progress
        self.src_info = src_info
        self.dst_info = dst_info
        self.effective = effective
        self.repairs = repairs
        self.admin_user = admin_user
        self.admin_pn = admin_pn
        self.seed_group = seed_group
        self.tolerated_tables = tolerated_tables
        self.counts = counts
        self.absent_tables = absent_tables
        self.kept = kept
        self.idmap_lines = kept.setdefault("idmap", [])
        self.fk_lines = kept.setdefault("fk", [])
        self.drop_lines = kept.setdefault("drop", [])
        self.reference_lines = kept.setdefault("reference", [])
        self.merge_lines = kept.setdefault("merge", [])
        self.archived_col_lines = kept.setdefault("archived_cols", [])
        self.shadow_notes = kept.setdefault("shadow", [])


def etl_absent_table(run: 'EtlRun', table: str, cls: str) -> None:
    """A table the manifest knows but this dump does not carry.

    Patch-level variance, not an error -- but never silent: a seeded
    table left alone would keep CARLOS's seed rows standing in for the
    clinic's. absent_table_plan() decides whether those rows are cleared
    and what the operator is told; this step only executes that plan."""
    query, dst = run.query, run.dst
    state_dir, progress = run.state_dir, run.progress
    dst_info, tolerated_tables = run.dst_info, run.tolerated_tables
    absent_tables = run.absent_tables
    # not in this dump (patch-level variance): said so, because a
    # seeded table then keeps CARLOS's seed rows in the clinic's
    # place
    do_clear, line = absent_table_plan(
        table, cls, tolerated_tables, table in dst_info,
        bool(progress["tables"].get(table, {}).get(
            "absent_cleared")))
    if do_clear:
        query("DELETE FROM `{0}`.`{1}`".format(dst, table))
        progress["tables"].setdefault(
            table, {})["absent_cleared"] = True
        save_progress(state_dir, progress)
    if line is not None:
        absent_tables.append(line)


def etl_reference_table(run: 'EtlRun', table: str, tstate: dict) -> None:
    """CARLOS's own reference rows win -- the clinic's are still kept."""
    query, plain = run.query, run.plain
    src, arch = run.src, run.arch
    state_dir, progress = run.state_dir, run.progress
    counts, reference_lines = run.counts, run.reference_lines
    # CARLOS's own reference rows win, but the clinic's are not
    # thrown away: they go to o19_archive so a curated local code
    # can still be found afterwards. No live twin -- the table
    # exists in CARLOS already, holding CARLOS's rows.
    counts["reference"] += 1
    if not tstate.get("done"):
        for sql in archive_statements(table, src, arch):
            query(sql)
        tstate["done"] = True
        save_progress(state_dir, progress)
    n = int(plain("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
        src, table))[0][0])
    line = ("{0}: {1} row(s) kept at {2}.{0} (CARLOS reference "
            "data wins in the live table)".format(table, n, arch))
    if n and line not in reference_lines:
        reference_lines.append(line)


def etl_drop_table(run: 'EtlRun', table: str, tstate: dict) -> None:
    """Removed-module infrastructure: no live home, never destroyed."""
    query, plain = run.query, run.plain
    src, dst, arch = run.src, run.dst, run.arch
    state_dir, progress = run.state_dir, run.progress
    counts, drop_lines = run.counts, run.drop_lines
    # NOT report-only any more: these rows used to be counted and
    # then destroyed with the staging schema at --cleanup. They
    # are removed-module infrastructure CARLOS has no home for,
    # which is a reason not to give them a live table of their
    # own -- not a reason to delete the clinic's only copy.
    counts["drop"] += 1
    n = int(plain("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
        src, table))[0][0])
    if n and not tstate.get("done"):
        for sql in preserve_statements(table, src, arch, dst):
            query(sql)
        tstate["done"] = True
        save_progress(state_dir, progress)
    line = ("{0}: {1} row(s) not migrated (removed module "
            "infrastructure); preserved at {2}.{0} and {3}.{4}"
            .format(table, n, arch, dst, archived_table(table)))
    if n and line not in drop_lines:
        drop_lines.append(line)


def etl_archive_table(run: 'EtlRun', table: str, tstate: dict) -> None:
    """An O19-only table: preserved whole, both as the o19_archive
    verification copy and as the live import_archived_ twin."""
    query = run.query
    src, dst, arch = run.src, run.dst, run.arch
    state_dir, progress = run.state_dir, run.progress
    counts = run.counts
    # counted from the ledger, so a resumed run reports the same
    # figure as the first
    if tstate.get("done"):
        counts["archive"] += 1
        return
    for sql in preserve_statements(table, src, arch, dst):
        query(sql)
    tstate["done"] = True
    save_progress(state_dir, progress)
    counts["archive"] += 1


def etl_archived_columns(run: 'EtlRun', table: str, entry: dict,
                         tstate: dict, dcols: Dict[str, dict]) -> dict:
    """Requirement B's column half: give every unmapped source column a
    live home, and return the entry that names it.

    ``dcols`` is updated IN PLACE on purpose -- copy_statement indexes
    dst_cols[c] unconditionally, so a column ALTERed in here after the
    introspection at the top of run_etl would otherwise be a KeyError
    mid-import."""
    query, dst = run.query, run.dst
    state_dir, progress = run.state_dir, run.progress
    src_info = run.src_info
    archived_col_lines = run.archived_col_lines
    col_plan = archived_column_plan(entry, src_info[table])
    if col_plan:
        for sql in add_archived_column_statements(
                table, dst, col_plan, dcols):
            query(sql)
        if not tstate.get("archived_cols_added"):
            archived_col_lines.append(
                "{0}: {1}".format(table, ", ".join(
                    "{0} -> {1}".format(src_col, target)
                    for src_col, target, _t in col_plan)))
            tstate["archived_cols_added"] = True
            save_progress(state_dir, progress)
        # the introspected shape must agree with what the copy is
        # about to name, whether the ALTER ran now or on an earlier
        # attempt of this same run
        for src_col, target, _ctype in col_plan:
            dcols.setdefault(target, dict(src_info[table][src_col],
                                          nullable=True))
        entry = with_archived_columns(entry, col_plan)
    return entry


def etl_merge_table(run: 'EtlRun', table: str, entry: dict, tstate: dict,
                    dcols: Dict[str, dict], repaired) -> None:
    """Anti-join the clinic's rows onto CARLOS's seeded ones, then archive
    the clinic's whole staging table -- policy is a reason not to make a
    rejected row live, not a reason to leave it nowhere."""
    query, plain = run.query, run.plain
    src, dst, arch = run.src, run.dst, run.arch
    state_dir, progress = run.state_dir, run.progress
    counts, merge_lines = run.counts, run.merge_lines
    idmap_lines = run.idmap_lines
    if not tstate.get("done"):
        # counted BEFORE the insert and kept in the ledger: after
        # the merge every staging row has a target twin (its own
        # included), so nothing can tell afterwards which rows
        # the CARLOS seed rejected
        # Recorded ONCE, and persisted before the insert. A
        # crash after the merge but before the checkpoint
        # re-enters this branch, and by then every staging row
        # has a target twin -- its own included -- so a recount
        # would report the whole table as seed-overridden and
        # the operator's report would say the clinic lost
        # everything on it. Saving without the guard is not
        # enough: the resumed run would overwrite the saved
        # figure with that recount before ever reading it.
        if "overridden" not in tstate:
            tstate["overridden"] = int(query(
                merge_overridden_count_sql(
                    table, entry, src, dst, dcols, arch))[0][0])
            save_progress(state_dir, progress)
        query(merge_statement(table, entry, src, dst, dcols,
                              repaired, arch))
        # rows the merge kept CARLOS's copy of never passed
        # through that insert; their archived columns are filled
        # by joining on the same natural key
        backfill = archived_backfill_statement(
            table, entry, src, dst, dcols, arch)
        if backfill:
            query(backfill)
        # the id map is rebuilt with every (re)merge so child
        # tables always read a map matching the target's ids
        for sql in idmap_statements(table, entry, src, dst, arch,
                                    dcols):
            query(sql)
        if entry.get("surrogate_pk"):
            changed = int(query(idmap_changed_count_sql(
                table, arch))[0][0])
            if changed:
                idmap_lines.append(
                    "{0}: {1} row(s) received a new id (map in "
                    "{2}.{3})".format(table, changed, arch,
                                      idmap_table(table)))
                save_progress(state_dir, progress)
        tstate["done"] = True
        save_progress(state_dir, progress)
    # The clinic's whole staging table goes to o19_archive, with
    # no live twin (the live table exists and holds the merged
    # result). A merge keeps CARLOS's row on a shared natural
    # key, so the clinic's other columns on that key -- an edited
    # encounter template, a local fee on a seeded billing code,
    # a customised measurement instruction -- are dropped by
    # policy. Policy is a reason not to make them live, not a
    # reason to leave them nowhere once --cleanup drops staging.
    n = int(plain("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
        src, table))[0][0])
    if n and not tstate.get("archived"):
        for sql in archive_statements(table, src, arch):
            query(sql)
        tstate["archived"] = True
        save_progress(state_dir, progress)
    overridden = tstate.get("overridden") or 0
    if n and overridden:
        line = ("{0}: {1} of {2} clinic row(s) kept CARLOS's row "
                "on the shared key; all {2} preserved at {3}.{0}"
                .format(table, overridden, n, arch))
        if line not in merge_lines:
            merge_lines.append(line)
    counts["merge"] += 1
    counts["merge_overridden"] += overridden


def etl_copy_table(run: 'EtlRun', table: str, entry: dict, tstate: dict,
                   dcols: Dict[str, dict], repaired) -> None:
    """Copy a table id-intact, in one statement or in id-range windows.

    The windowed path is resumable at window granularity: any window may
    have committed without its checkpoint, so a run that has touched this
    table before re-deletes its first unconfirmed window before
    re-inserting it."""
    from .util import die
    query, plain = run.query, run.plain
    src, dst, arch = run.src, run.dst, run.arch
    state_dir, progress = run.state_dir, run.progress
    counts, seed_group = run.counts, run.seed_group
    admin_user, admin_pn = run.admin_user, run.admin_pn
    if entry.get("chunk_by"):
        chunk = entry["chunk_by"]
        bounds = plain(
            "SELECT IFNULL(MIN(`{0}`),0), IFNULL(MAX(`{0}`),0) "
            "FROM `{1}`.`{2}`".format(chunk, src, table))[0]
        lo, hi = int(bounds[0]), int(bounds[1])
        # a table this run has touched before (any window, even
        # the first one, may have committed without its
        # checkpoint) clears its first unconfirmed window
        resumed = bool(tstate.get("started"))
        done_through = tstate.get("done_through", lo - 1)
        # bounded BEFORE the window list is built AND before the
        # replace_seed DELETE below — see chunk_span_refusal
        refusal = chunk_span_refusal(table, entry["chunk_by"],
                                     lo, hi)
        if refusal:
            die(refusal)
        if not tstate.get("started"):
            if entry.get("replace_seed"):
                # the unchunked branch does this too: the target
                # may already hold rows this copy would collide
                # with on its id-intact insert (`log` carries the
                # deploy's own audit rows). Once a window has
                # landed, `started` is set and this never re-runs.
                query("DELETE FROM `{0}`.`{1}`".format(dst, table))
            tstate["started"] = True
            save_progress(state_dir, progress)
        windows = chunk_windows(lo, hi)
        if len(windows) > 1000:
            # id-range windows, not row windows: a sparse id space
            # means many empty client invocations, not a hang
            warn("{0}: {1} id-range windows (sparse ids) — this "
                 "table takes a while".format(table, len(windows)))
        for window in windows:
            if window[1] <= done_through:
                continue
            if resumed:
                query(window_delete_statement(table, entry, dst,
                                              window))
                resumed = False
            query(copy_statement(table, entry, src, dst, dcols,
                                 repaired, window, arch))
            tstate["done_through"] = window[1]
            save_progress(state_dir, progress)
        tstate["done"] = True
    else:
        if not tstate.get("done"):
            if table in seed_group:
                query(seed_group_retry_delete(
                    table, dst, admin_user, admin_pn or "", dcols))
            elif entry.get("replace_seed") or tstate.get("started"):
                query("DELETE FROM `{0}`.`{1}`".format(dst, table))
            tstate["started"] = True
            save_progress(state_dir, progress)
            query(copy_statement(table, entry, src, dst, dcols,
                                 repaired, None, arch))
            tstate["done"] = True
    save_progress(state_dir, progress)
    counts["copy"] += 1


def etl_post_copy(run: 'EtlRun', table: str, entry: dict, base_entry: dict,
                  tstate: dict, dcols: Dict[str, dict]) -> None:
    """What every copied or merged table owes the operator afterwards:
    the dangling foreign keys the id maps could not resolve, and the
    o19_archive shadow captures of the columns CARLOS has no home for."""
    query, report = run.query, run.report
    src, arch = run.src, run.arch
    state_dir, progress = run.state_dir, run.progress
    src_info, counts = run.src_info, run.counts
    fk_lines, shadow_notes = run.fk_lines, run.shadow_notes
    # dangling foreign keys the id maps could not resolve
    if entry.get("fk_remap") and not tstate.get("fk_reported"):
        for col, parent, sql in fk_unmapped_count_sql(table, entry, src,
                                                      arch):
            n = int(query(sql)[0][0])
            if n:
                fk_lines.append(
                    "{0}.{1}: {2} row(s) referenced a {3} id that does "
                    "not exist in the source ({4})".format(
                        table, col, n, parent,
                        "set to NULL" if dcols[col]["nullable"]
                        else "raw id kept — column is NOT NULL"))
        tstate["fk_reported"] = True
        save_progress(state_dir, progress)

    # shadow-capture dropped columns alongside the copy
    if base_entry.get("dropped") and not tstate.get("shadow_done"):
        for sql in shadow_statements(table, base_entry, src, arch,
                                     src_info[table], shadow_notes):
            query(sql)
        tstate["shadow_done"] = True
        save_progress(state_dir, progress)
    # ... and vendor-fork columns the manifest does not know at all
    if not tstate.get("unknown_shadow_done"):
        stmts = unknown_column_shadow_statements(
            table, base_entry, src, arch, src_info[table])
        for sql in stmts:
            query(sql)
        if stmts:
            counts["unknown_column_shadows"] += 1
            report("{0}: unmapped column(s) {1} shadow-captured to "
                   "{2}.{0}__unknown_cols".format(
                       table, ", ".join(unknown_columns(
                           base_entry, src_info[table])), arch))
        tstate["unknown_shadow_done"] = True
        save_progress(state_dir, progress)


def reconcile_seed_rows(ctx, query, plain, src_schema: str,
                        dst_schema: str, state_dir: str, report,
                        progress: Dict,
                        dst_info: Dict[str, Dict[str, dict]],
                        admin_user: str,
                        make_password_hash) -> Optional[str]:
    """Create the break-glass admin, then remove CARLOS's seeded rows.

    Returns the admin's provider_no (already recorded in the ledger on a
    resume). The ordering is why this is its own step: the seed clinician
    is what the admin is cloned from, so it cannot be deleted first, and
    the clinic's rows reuse the seed ids, so it cannot be deleted last.
    """
    from .util import die
    from . import o19roles  # imports this module; resolved lazily
    src, dst = src_schema, dst_schema
    # -- seed reconciliation (strictly ordered, before provider/security) --
    # Resumable in two recorded steps: admin rows inserted, then seeds
    # deleted. A retry after an interrupted insert clears the partial admin
    # first; a retry after the inserts only re-runs the (idempotent)
    # deletes — the seed clinician is still there to clone from until
    # the deletes have run. The admin's identity is bound to the ledger:
    # a different --admin-user on resume is refused, never silently
    # swapped (the retry deletes would drop the first admin's login).
    recorded_user = progress.get("admin_user")
    if recorded_user and recorded_user != admin_user:
        die("this import created break-glass admin '{0}'; --admin-user "
            "'{1}' differs. Resume with the recorded name.".format(
                recorded_user, admin_user))
    admin_pn = progress.get("admin_provider_no")
    if not progress.get("seed_done"):
        if not progress.get("seed_admin_inserted"):
            if admin_pn:
                for sql in seed_admin_cleanup_statements(dst, admin_user,
                                                         admin_pn):
                    query(sql)
            else:
                max_pn = plain(
                    "SELECT IFNULL(MAX(CAST(provider_no AS SIGNED)), 0) "
                    "FROM `{0}`.provider WHERE provider_no REGEXP "
                    "'^[0-9]+$'".format(src))[0][0]
                admin_pn = str(int(max_pn) + 1)
                if admin_pn == o19map_schema.SEED_PROVIDER_NO:
                    # a clinic whose vendor purged oscardoc leaves 999997
                    # as the high water mark; cloning the seed clinician
                    # onto its own provider_no is a duplicate-key insert
                    # AFTER the credentials file and the ledger mark are
                    # written, and the resume then deletes the rows the
                    # clone reads from
                    admin_pn = str(int(admin_pn) + 1)
                width = dst_info.get("provider", {}).get(
                    "provider_no", {}).get("char_len", 0)
                if width and len(admin_pn) > width:
                    die("cannot allocate a break-glass provider_no: the "
                        "clinic's highest numeric provider_no ({0}) leaves "
                        "no room in provider.provider_no ({1} chars). "
                        "Shorten a provider number in the source, "
                        "re-export, and re-run with --resume --restage; "
                        "nothing has been written to the target."
                        .format(max_pn, width))
            # the admin's security/secUserRole rows take auto ids — bump
            # the counters above the clinic's id range or the later
            # id-preserving copy collides with the admin's rows (found
            # live in rehearsal)
            for table, pk in (("security", "security_no"),
                              ("secUserRole", "id")):
                src_max = int(plain(
                    "SELECT IFNULL(MAX(`{0}`), 0) FROM `{1}`.`{2}`".format(
                        pk, src, table))[0][0])
                query("ALTER TABLE `{0}`.`{1}` AUTO_INCREMENT = {2}".format(
                    dst, table, src_max + 1000))
            password, pw_hash, pin = make_password_hash()
            cred_path = os.path.join(state_dir, "admin-credentials.txt")
            if os.path.exists(cred_path) and not progress.get(
                    "admin_provider_no"):
                # a previous import's break-glass password (cleanup keeps
                # the file on purpose): set aside, never overwritten
                stamp = time.strftime("%Y%m%dT%H%M%S")
                n = 0
                while True:
                    aside = "{0}.previous-{1}{2}".format(
                        cred_path, stamp, "-{0}".format(n) if n else "")
                    try:
                        # O_EXCL: a second import in the same second must
                        # not overwrite the first one's set-aside file
                        os.close(os.open(aside, os.O_WRONLY | os.O_CREAT
                                         | os.O_EXCL, 0o600))
                    except FileExistsError:
                        n += 1
                        continue
                    os.replace(cred_path, aside)
                    break
            # file-first, before any SQL touches accounts (bootstrap-admin's
            # contract: never leave a credential that exists only in memory)
            fd = os.open(cred_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC,
                         0o600)
            os.fchmod(fd, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as fh:
                fh.write("break-glass administrator (created by import-o19)\n"
                         "user: {0}\nprovider_no: {1}\npassword: {2}\n"
                         "pin: {3}\nforced password reset on first login\n"
                         .format(admin_user, admin_pn, password, pin))
            os.chmod(cred_path, 0o600)
            progress["admin_provider_no"] = admin_pn
            progress["admin_user"] = admin_user
            save_progress(state_dir, progress)
            for sql in seed_admin_statements(dst, admin_user, admin_pn,
                                             pw_hash, pin):
                query(sql)
            progress["seed_admin_inserted"] = True
            save_progress(state_dir, progress)
            report("break-glass admin '{0}' (provider {1}) created; "
                   "credentials in {2}".format(admin_user, admin_pn,
                                               cred_path))
        for sql in seed_delete_statements(dst):
            query(sql)
        # rows the webapp created on its first start (a packaged host has
        # booted before the import): the clinic's rows reuse their ids
        for sql in o19roles.startup_row_delete_statements(dst):
            query(sql)
        progress["seed_done"] = True
        save_progress(state_dir, progress)
        report("seeded clinician and startup-created rows removed")
    return admin_pn


def etl_unknown_tables(run: 'EtlRun') -> None:
    """Preserve every staged table the manifest has never heard of.

    A vendor fork's own tables land here. The unknown-as-archive sign-off
    the operator gave is a preservation promise, not permission to drop,
    so each is copied to o19_archive AND given a live import_archived_
    twin -- except an empty one, which is named in the report rather than
    silently skipped.
    """
    query, plain = run.query, run.plain
    src, dst, arch = run.src, run.dst, run.arch
    state_dir, progress = run.state_dir, run.progress
    src_info, counts = run.src_info, run.counts
    unknown_lines = run.kept.setdefault("unknown", [])
    # -- tables the manifest does not know: archived whole ------------------
    # (the unknown-as-archive sign-off is a preservation promise, not a
    # permission to drop)
    unknown_tables = sorted(t for t in src_info
                            if t not in o19map_schema.TABLES)
    for table in unknown_tables:
        tstate = progress["tables"].setdefault(table, {})
        if tstate.get("done"):
            if not tstate.get("empty"):
                counts["unknown_archived"] += 1
            continue
        n = int(plain("SELECT COUNT(*) FROM `{0}`.{1}".format(
            src, ident(table)))[0][0])
        if n == 0:
            unknown_lines.append("{0}: empty, not archived".format(table))
            tstate["empty"] = True
        else:
            for sql in preserve_statements(table, src, arch, dst):
                query(sql)
            unknown_lines.append(
                "{0}: {1} row(s) preserved at {2}.{0} and {3}.{4}".format(
                    table, n, arch, dst, archived_table(table)))
            counts["unknown_archived"] += 1
        tstate["done"] = True
        save_progress(state_dir, progress)


def report_etl_findings(run: 'EtlRun') -> None:
    """Render the per-class findings the table pass accumulated.

    Every list here is read from the ledger-backed ``kept`` dict, so a
    resumed run reports what the crashed run found as well as its own --
    the ledger's marks make the second pass skip the work, which means it
    could not re-derive these lines.
    """
    report, src_info = run.report, run.src_info
    unknown_lines = run.kept.setdefault("unknown", [])
    drop_lines, reference_lines = run.drop_lines, run.reference_lines
    archived_col_lines = run.archived_col_lines
    shadow_notes, merge_lines = run.shadow_notes, run.merge_lines
    idmap_lines, fk_lines = run.idmap_lines, run.fk_lines
    if unknown_lines:
        report("unknown (unclassified) tables:\n  "
               + "\n  ".join(unknown_lines))
    if drop_lines:
        report("removed-module tables: no CARLOS home, nothing orphaned "
               "either — their rows are preserved, not deleted:\n  "
               + "\n  ".join(drop_lines))
    if reference_lines:
        report("reference tables where CARLOS's own data wins; the "
               "clinic's rows are preserved for comparison:\n  "
               + "\n  ".join(reference_lines))
    if archived_col_lines:
        report("columns CARLOS has no home for, preserved on the live "
               "table (source type and value kept verbatim):\n  "
               + "\n  ".join(archived_col_lines))
    if shadow_notes:
        report("dropped-column capture:\n  " + "\n  ".join(shadow_notes))
    if merge_lines:
        report("clinic rows the CARLOS seed overrode on a merge table "
               "(the live table holds CARLOS's row; the clinic's is "
               "preserved, not discarded):\n  " + "\n  ".join(merge_lines))
    if idmap_lines:
        report("surrogate ids reassigned on merge (child foreign keys "
               "remapped through the id maps):\n  "
               + "\n  ".join(idmap_lines))
    if fk_lines:
        report("dangling foreign keys in the source:\n  "
               + "\n  ".join(fk_lines))
    token_tables = [t for t in getattr(o19map_schema, "CREDENTIAL_TABLES",
                                       ()) if t in src_info]
    if token_tables:
        report("API credentials copied verbatim (sign-off carry-credentials "
               "recorded when rows were present) — ROTATE/VERIFY before "
               "go-live (tokens issued by the OSCAR 19 install keep "
               "working): " + ", ".join(token_tables))


def run_etl(ctx, make_password_hash: Callable[[], Tuple[str, str, str]]):
    """Execute P4. make_password_hash() -> (password, bcrypt_hash, pin)
    so the crypto (and its bcrypt dependency) stays injectable."""
    from .util import die
    from . import o19roles  # imports this module; resolved lazily
    query = ctx["query_etl"]          # carries the session prelude
    plain = ctx["query"]
    src, dst, arch = ctx["src_schema"], ctx["target_db"], ctx["archive_schema"]
    state_dir = ctx["state_dir"]
    report = ctx["report"]

    src_info = introspect_columns(plain, src)
    # the first thing decided about the staged dump, before the staging
    # schema is touched: a table or column name outside the identifier
    # class is refused outright (root runs every statement below)
    odd = unsafe_identifiers(src_info)
    if odd:
        die("ETL pre-checks failed ({0}): the staged dump "
            .format(precheck_scope(state_dir))
            + "carries {0} table/column name(s) outside the accepted "
              "identifier class [A-Za-z0-9_$] — not an OSCAR 19 clinic "
              "dump as shipped; rename them in the source and re-export: "
              "{1}".format(len(odd),
                           ", ".join(repr(x) for x in odd[:10])))
    renamed = normalize_table_case(plain, src, list(src_info))
    if renamed:
        report("staged table names normalised to the manifest spelling "
               "(source server ran lower_case_table_names=1):\n  "
               + "\n  ".join(renamed))
        src_info = introspect_columns(plain, src)
    dst_info = introspect_columns(plain, dst)
    src_tables = set(src_info)

    patch_notes: List[str] = []
    effective: Dict[str, dict] = {}
    for table, entry in o19map_schema.TABLES.items():
        if entry["class"] in ("copy", "merge") and table in src_info:
            adjusted, notes = effective_entry(table, entry,
                                              src_info[table], src_tables)
            effective[table] = adjusted
            patch_notes.extend(notes)
    if patch_notes:
        report("patch-level variance ({0} note(s)):\n  ".format(
            len(patch_notes)) + "\n  ".join(patch_notes))

    # the charset scan first (reads only): the never-truncate pre-check
    # below must measure the text as it will be stored, i.e. repaired
    scan_notes: List[str] = []
    repairs = detect_repairs(plain, src, ctx["accepted"], scan_notes)
    if scan_notes:
        report("charset scan:\n  " + "\n  ".join(scan_notes))
    if repairs:
        report("per-row charset repair active on: " + ", ".join(
            sorted("{0}.{1}".format(t, c)
                   for t, cs in repairs.items() for c in cs)))

    # -- loud pre-checks over every table before the first write ----------
    admin_user = validate_admin_user(ctx["admin_user"])
    problems = etl_precheck_problems(
        ctx, plain, query, src, arch, src_info, dst_info, effective,
        repairs, admin_user)
    if problems:
        die("ETL pre-checks failed ({0}):\n  ".format(
            precheck_scope(state_dir)) + "\n  ".join(problems))

    # enum values outside the target set fall to the column default —
    # counted up front so the fallback is never silent
    enum_lines = enum_fallback_lines(query, src, arch, dst_info,
                                     effective, repairs)
    if enum_lines:
        report("enum fallbacks:\n  " + "\n  ".join(enum_lines))

    plain("CREATE DATABASE IF NOT EXISTS `{0}`".format(arch))
    progress = load_progress(state_dir, ctx.get("dump_sha256"),
                             o19map_schema.SCHEMA_MAP_VERSION)

    # Has the target been rewound under this ledger? The pre-import
    # restic snapshot covers the CARLOS schema and the documents tree,
    # NOT this workspace — so an operator who follows the rollback advice
    # in any of our refusals ends up with a pristine database and a
    # ledger that still says two hundred tables are done. A --resume then
    # skips every one of them, leaving CARLOS seed rows in the clinic's
    # place, and --cleanup and --restage both refuse with messages that
    # point back at the snapshot they just restored. The break-glass
    # admin is the cheapest witness: this run created it, so its absence
    # means the target is not the one this ledger describes.
    # ...but only once the INSERT is recorded. Between the ledger save
    # that names the provider_no and the seed INSERT itself there is a
    # window in which the row legitimately does not exist yet; the
    # partial-admin retry below (seed_admin_cleanup_statements) is what
    # covers that, and this witness would otherwise refuse the resume it
    # is meant to protect.
    recorded_pn = progress.get("admin_provider_no")
    if recorded_pn and progress.get("seed_admin_inserted"):
        still_there = int(plain(
            "SELECT COUNT(*) FROM `{0}`.provider WHERE provider_no = {1}"
            .format(dst, _sql_str(recorded_pn)))[0][0])
        if not still_there:
            die("the target no longer holds this import's break-glass "
                "administrator (provider_no {0}), but the ledger records "
                "its work — the database was rewound underneath it "
                "(a restored snapshot does not cover {1}). This run "
                "cannot be resumed: move {1} aside and start the import "
                "over against the restored database."
                .format(recorded_pn, state_dir))

    # -- seed reconciliation (strictly ordered, before provider/security) --
    admin_pn = reconcile_seed_rows(ctx, query, plain, src, dst, state_dir,
                                   report, progress, dst_info, admin_user,
                                   make_password_hash)
    seed_group = set(seed_group_tables())

    # -- CARLOS seed snapshot (before any clinic row lands) -----------------
    # the pristine target IS the seed; the roles post-step reads the
    # snapshot for the privilege diff, the role append and the template
    # choice, and it must predate the merges below
    if not progress.get("seed_priv_snapshot"):
        for sql in o19roles.snapshot_statements(dst, arch):
            query(sql)
        progress["seed_priv_snapshot"] = True
        save_progress(state_dir, progress)

    # -- table loop --------------------------------------------------------
    counts = {"copy": 0, "merge": 0, "archive": 0, "drop": 0,
              "reference": 0, "rows": 0, "unknown_archived": 0,
              "unknown_column_shadows": 0, "merge_overridden": 0}
    # per-table findings are persisted in the ledger as they are made, so
    # a resumed run's report still carries the lines of tables the crashed
    # run completed (they are never re-derived: the marks skip the work)
    kept = progress.setdefault("report_lines", {})
    absent_tables: List[str] = []
    # tables whose target rows P0 tolerated because this copy deletes them
    tolerated_tables = set(getattr(
        o19map_schema, "PRISTINE_TOLERATED_TABLES", ()))
    run = EtlRun(ctx, progress, src_info, dst_info, effective, repairs,
                 admin_user, admin_pn, seed_group, tolerated_tables,
                 counts, kept, absent_tables)
    for table in etl_order(o19map_schema.TABLES):
        entry = o19map_schema.TABLES[table]
        cls = entry["class"]
        if table not in src_info:
            etl_absent_table(run, table, cls)
            continue
        entry = effective.get(table, entry)
        tstate = progress["tables"].setdefault(table, {})
        if cls == "reference":
            etl_reference_table(run, table, tstate)
            continue
        if cls == "drop":
            etl_drop_table(run, table, tstate)
            continue
        if cls == "archive":
            etl_archive_table(run, table, tstate)
            continue

        dcols = dst_info[table]
        repaired = repairs.get(table)
        # the shadow captures in etl_post_copy describe the manifest's own
        # view of this table, so they read the entry BEFORE the archived
        # columns are folded in -- otherwise a vendor-fork column stops
        # being "unknown" the moment it is preserved, and the o19_archive
        # verification copy the operator was promised disappears
        base_entry = entry
        entry = etl_archived_columns(run, table, entry, tstate, dcols)
        if cls == "merge":
            etl_merge_table(run, table, entry, tstate, dcols, repaired)
        else:
            etl_copy_table(run, table, entry, tstate, dcols, repaired)
        etl_post_copy(run, table, entry, base_entry, tstate, dcols)
    # persisted for the validation report, which is written by a later
    # phase and cannot re-derive them: the ledger's marks make the second
    # pass skip the work that produced them
    kept["absent"] = list(absent_tables)
    save_progress(state_dir, progress)
    if absent_tables:
        report("manifest tables absent from this dump ({0}; patch-level "
               "variance — nothing copied for them):\n  ".format(
                   len(absent_tables)) + "\n  ".join(absent_tables))

    etl_unknown_tables(run)
    report_etl_findings(run)

    # -- roles, privileges and CARLOS-required rows (M8) -------------------
    o19roles.run_roles(ctx, progress,
                       lambda: save_progress(state_dir, progress))

    query(force_reset_statement(dst))
    report("forcePasswordReset set for every imported user")
    report("ETL complete: {0} copied, {1} merged, {2} archived, "
           "{3} reference (CARLOS wins, clinic rows kept in {6}), "
           "{4} removed-module table(s) preserved, {5} unknown table(s) "
           "preserved. Preserved tables live at {6}.<table> and "
           "{7}.{8}<table>".format(
               counts["copy"], counts["merge"], counts["archive"],
               counts["reference"], counts["drop"],
               counts["unknown_archived"], arch, dst, ARCHIVED_PREFIX))
    return counts


# --------------------------------------------------------------------------
# P7 core — row parity
# --------------------------------------------------------------------------

ADMIN_ROW_PREDICATES = {
    # the only tables that legitimately gain rows beyond the copy: the
    # break-glass admin's own identity (seeds were removed before the copy)
    "provider": "provider_no = '{pn}'",
    "security": "user_name = '{user}' AND provider_no = '{pn}'",
    "secUserRole": "provider_no = '{pn}'",
}


#: how many rows the roles step may synthesise per table where the number
#: is fixed by construction (one OSCAR program, one Rich Text Letter seed);
#: the other appended tables scale with the clinic's provider count
APPENDED_ROW_MAX = {"program": 1, "eform": 1}


def admin_row_count_sql(table: str, dst_schema: str, admin_user: str,
                        admin_provider_no: str) -> Optional[str]:
    """Counts the break-glass administrator's own rows in `table`, or None
    when that table has no such row.

    P7 parity subtracts these: the admin is created by this import and
    has no staging twin, so its rows are an expected delta rather than a
    mismatch."""
    predicate = ADMIN_ROW_PREDICATES.get(table)
    if not predicate:
        return None
    return "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE {2}".format(
        dst_schema, table, predicate.format(
            user=_sql_str(admin_user), pn=_sql_str(admin_provider_no)))


# Tables the roles post-step legitimately appends to beyond the copy
# (CARLOS-only roles, the OSCAR program, synthesised memberships and
# facility links): target rows with no staging twin on these keys are the
# tolerated delta — and only as many as the roles ledger recorded.
APPENDED_ROW_KEYS = {
    "secRole": ["role_name"],
    "program": ["name"],
    "program_provider": ["program_id", "provider_no", "role_id"],
    "provider_facility": ["provider_no", "facility_id"],
    # the Rich Text Letter v1 seed adds one row with a fresh fid
    "eform": ["fid"],
}

#: staging tables the roles post-step reads or appends to; refused by the
#: ETL pre-checks when absent from the dump
# generated from the overlay and shared with the standalone preflight,
# so the assessment refuses the same dump this pre-check does
ROLES_STEP_TABLES = tuple(o19map_schema.REQUIRED_TABLES)


def appended_row_count_sql(table: str, src_schema: str,
                           dst_schema: str) -> Optional[str]:
    """Counts target rows in `table` with no staging twin on the keys the
    roles post-step legitimately appends by, or None for a table it does
    not append to.

    Parity tolerates this delta, but only up to what the roles ledger
    recorded -- an unbounded tolerance would absorb a duplicate."""
    keys = APPENDED_ROW_KEYS.get(table)
    if not keys:
        return None
    join = " AND ".join("d.`{0}` <=> s.`{0}`".format(k) for k in keys)
    return ("SELECT COUNT(*) FROM `{0}`.`{1}` d WHERE NOT EXISTS (SELECT 1 "
            "FROM `{2}`.`{1}` s WHERE {3})".format(
                dst_schema, table, src_schema, join))


def schema_tables(plain_query, schema: str) -> set:
    """The table names `schema` currently holds."""
    return {r[0] for r in plain_query(
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE "
        "TABLE_SCHEMA = '{0}'".format(schema))}


#: Manifest classes whose rows CARLOS itself never stores, and so are
#: only ever reachable through a preserved copy. `reference` is here too:
#: the live table exists but holds CARLOS's own rows, so the clinic's are
#: preserved in `o19_archive` alone (no live twin would be meaningful --
#: the name is taken).
PRESERVED_CLASSES = ("archive", "drop", "reference")
#: preserved into `o19_archive` only, with NO `import_archived_` twin:
#: the live table already exists and holds CARLOS's own rows, so a twin
#: beside it would be a second copy of a table that is not missing.
#: `merge` is here because a clinic row whose natural key collides with a
#: CARLOS seed row is never inserted -- the seed wins by policy -- and
#: its other columns would otherwise exist nowhere once staging is
#: dropped.
ARCHIVE_ONLY_CLASSES = ("reference", "merge")


def preserved_parity(plain_query, src_schema: str, dst_schema: str,
                     archive_schema: str) -> Tuple[List[str], List[str]]:
    """(ok_lines, mismatch_lines) proving no staging row was orphaned.

    `row_parity` checks the tables CARLOS has a home for. This checks the
    rest -- archive, removed-module (`drop`), reference and the
    unclassified tables a clinic's own fork carries -- by counting each
    one in staging and in every home it is supposed to have reached:
    `o19_archive`.`<table>` always, and `<target>`.`import_archived_
    <table>` for everything but `reference`.

    Counting, not assuming. Until this existed the archive schema had
    never been row-verified at all, and `--cleanup` was allowed to drop
    staging on the strength of a verification that had not looked at it.

    An empty staging table is not preserved and is not a mismatch: there
    is nothing to lose. A table that HOLDS ROWS and has no verified home
    is the mismatch this function exists to name."""
    ok, bad = [], []
    src_tables = schema_tables(plain_query, src_schema)
    arch_tables = schema_tables(plain_query, archive_schema)
    dst_tables = schema_tables(plain_query, dst_schema)

    def count(schema: str, table: str) -> int:
        return int(plain_query("SELECT COUNT(*) FROM `{0}`.{1}".format(
            schema, ident(table)))[0][0])

    for table in sorted(src_tables):
        entry = o19map_schema.TABLES.get(table)
        cls = entry["class"] if entry else "unknown"
        if entry and cls not in PRESERVED_CLASSES + ("merge",):
            continue        # copy: row_parity's business
        src_n = count(src_schema, table)
        if src_n == 0:
            continue
        homes = [(archive_schema, table, arch_tables)]
        if cls not in ARCHIVE_ONLY_CLASSES:
            homes.append((dst_schema, archived_table(table), dst_tables))
        counts = []
        for schema, name, present in homes:
            if name not in present:
                bad.append("{0}: {1} staging row(s) and no copy at {2}.{3}"
                           .format(table, src_n, schema, name))
                counts = None
                break
            counts.append((schema, name, count(schema, name)))
        if counts is None:
            continue
        wrong = [(schema, name, n) for schema, name, n in counts
                 if n != src_n]
        if wrong:
            bad.append("{0}: staging {1} row(s), but {2}".format(
                table, src_n, "; ".join(
                    "{0}.{1} holds {2}".format(schema, name, n)
                    for schema, name, n in wrong)))
        else:
            ok.append("{0} ({1}): staging {2} -> {3}".format(
                table, cls, src_n, ", ".join(
                    "{0}.{1} {2}".format(schema, name, n)
                    for schema, name, n in counts)))
    return ok, bad


def archived_column_exclusions(table: str,
                               pruned_property_prefixes: Sequence[str] = (),
                               pruned_property_keys: Sequence[str] = ()
                               ) -> List[str]:
    """Staging-side predicates (alias `s`) for rows whose preserved
    column has no target value BY DESIGN, so the archived-column parity
    must not expect one.

    Kept separate from the query so the tolerance is a value a test can
    assert on rather than a substring of generated SQL, and so it stays
    beside the two writers it mirrors: `merge_statement` /
    `archived_backfill_statement` skip `merge_exclude` rows, and
    `o19roles.property_prune_statements` deletes the pruned target rows
    after the merge."""
    entry = o19map_schema.TABLES.get(table) or {}
    out = []
    if entry.get("merge_exclude"):
        out.append(entry["merge_exclude"])
    if table == "property" and (pruned_property_prefixes
                                or pruned_property_keys):
        out.append(pruned_property_predicate(
            pruned_property_prefixes or (), pruned_property_keys or ()))
    return out


def archived_column_parity(plain_query, src_schema: str, dst_schema: str,
                           pruned_property_prefixes: Sequence[str] = (),
                           pruned_property_keys: Sequence[str] = ()
                           ) -> Tuple[List[str], List[str]]:
    """(ok_lines, mismatch_lines) for the `import_archived_` COLUMNS.

    A row count cannot see a column: a copy that named the prefixed
    column but fed it the wrong expression -- or fed it nothing --
    passes `row_parity` unchanged. So each preserved column is counted
    where it is not NULL, on both sides, and the two must agree.

    Equality, not "at least": the rows the target holds beyond the copy
    (CARLOS seeds a merge kept, the break-glass administrator) have no
    source column to fill from, so they are NULL and contribute to
    neither side.

    The staging side subtracts the same two populations `row_parity`
    subtracts, and for the same reason -- a deliberate deletion is not a
    mismatch. Three merge tables carry preserved columns
    (`property`, `secObjectName`, `lst_gender`), and two of them are
    exactly the tables with a tolerance:

    * `property` -- the roles post-step prunes removed-module rows from
      the TARGET after the merge, taking their `import_archived_`
      values with them; their staging twins are still there.
    * `merge_exclude` rows -- never inserted by `merge_statement` and
      deliberately skipped by `archived_backfill_statement`, so they
      have no target value to count.

    Without these, a clinic whose dump has a non-null
    `property.lastUpdateDate` on any pruned key -- which is the ordinary
    case, the column being a timestamp -- fails P4 parity AFTER a
    complete and correct copy, and parity is not overridable."""
    ok, bad = [], []
    src_info = introspect_columns(plain_query, src_schema)
    dst_info = introspect_columns(plain_query, dst_schema)
    for table in sorted(dst_info):
        if table not in src_info:
            continue
        for target in sorted(dst_info[table]):
            if not target.startswith(ARCHIVED_PREFIX):
                continue
            source = target[len(ARCHIVED_PREFIX):]
            staged = {c.lower() for c in src_info[table]}
            if source.lower() not in staged:
                # the column was preserved by an earlier run against a
                # dump that carried it; this one does not, so there is
                # nothing to compare it against
                continue
            # alias `s`: both exclusion predicates are written
            # against the staging alias, like every other parity query
            src_sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE "
                       "s.`{2}` IS NOT NULL".format(
                           src_schema, table, source))
            for predicate in archived_column_exclusions(
                    table, pruned_property_prefixes, pruned_property_keys):
                src_sql += " AND NOT ({0})".format(predicate)
            src_n = int(plain_query(src_sql)[0][0])
            dst_n = int(plain_query(
                "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE `{2}` IS NOT NULL"
                .format(dst_schema, table, target))[0][0])
            line = ("{0}.{1}: {2} value(s) preserved as {3}".format(
                table, source, src_n, target))
            if src_n == dst_n:
                ok.append(line)
            else:
                bad.append("{0}.{1}: {2} non-null value(s) in staging, {3} "
                           "in {4}.{5}".format(table, source, src_n, dst_n,
                                               dst_schema, target))
    return ok, bad


def row_parity(plain_query, src_schema: str, dst_schema: str,
               admin_user: Optional[str] = None,
               admin_provider_no: Optional[str] = None,
               appended: Optional[Dict[str, int]] = None,
               dst_info: Optional[Dict[str, Dict[str, dict]]] = None,
               archive_schema: Optional[str] = None,
               pruned_property_prefixes: Optional[Sequence[str]] = None,
               pruned_property_keys: Optional[Sequence[str]] = None
               ) -> Tuple[List[str], List[str]]:
    """(ok_lines, mismatch_lines) comparing staging vs target counts for
    every copy-class table, and merge tables in reverse.

    Half the verification: the tables CARLOS has a home for. The other
    half is `preserved_parity` (archive, removed-module, reference and
    unclassified tables) and `archived_column_parity` (the columns), and
    a caller that runs only this one has not checked that nothing was
    orphaned -- see `o19import._row_parity`, which runs all three.

    The tolerated deltas are the break-glass
    admin's own rows, counted exactly on the target (provider, security,
    secUserRole), and the rows the roles post-step recorded appending
    (`appended`, from its ledger) — which must equal the target rows that
    have no staging twin. Every other table — the seed-delete tables
    included — must match to the row."""
    ok, bad = [], []
    appended = appended or {}
    src_tables = {r[0] for r in plain_query(
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE "
        "TABLE_SCHEMA = '{0}'".format(src_schema))}
    # The copy reduced every entry to the columns this dump actually
    # carries (effective_entry); parity has to compare the same shape or
    # merge_missing_count_sql emits `s.<column>` for a column a lower
    # patch level does not have. That fails AFTER the copy has completed
    # and been declared unoverridable, which is the worst possible moment
    # to discover a manifest/dump mismatch.
    src_info = introspect_columns(plain_query, src_schema)
    for table, entry in sorted(o19map_schema.TABLES.items()):
        if table not in src_tables:
            continue
        if entry["class"] in ("copy", "merge") and table in src_info:
            entry, _notes = effective_entry(table, entry, src_info[table],
                                            src_tables)
        if entry.get("fk_remap"):
            # the copy dropped remaps whose parent is absent from this
            # dump, so no id map exists for them: the parity join must
            # drop them too or it references a table that was never made
            entry = dict(entry, fk_remap={
                c: parent for c, parent in entry["fk_remap"].items()
                if parent in src_tables})
        if entry["class"] == "merge" and missing_merge_keys(
                entry, src_info.get(table, {})):
            # the ETL pre-check refuses this dump outright; parity is also
            # callable on its own, and broken SQL is a worse answer than a
            # named mismatch
            bad.append("{0}: merge key(s) {1} absent from this dump — "
                       "parity cannot check it".format(
                           table, ", ".join(missing_merge_keys(
                               entry, src_info.get(table, {})))))
            continue
        if entry["class"] == "merge" and dst_info is not None \
                and table in dst_info:
            # the reverse of the merge's anti-join: every staging row (the
            # excluded removed-module rows aside) must have a target twin;
            # the property rows the roles step pruned again are not twins
            exclude = None
            if table == "property" and (pruned_property_prefixes
                                        or pruned_property_keys):
                exclude = pruned_property_predicate(
                    pruned_property_prefixes or (),
                    pruned_property_keys or ())
            missing = int(plain_query(merge_missing_count_sql(
                table, entry, src_schema, dst_schema, dst_info[table],
                archive_schema, exclude))[0][0])
            if missing:
                bad.append("{0}: {1} staging row(s) have no target twin "
                           "after the merge".format(table, missing))
            else:
                ok.append("{0}: merged, every staging row present"
                          .format(table))
            continue
        if entry["class"] != "copy":
            continue
        src_n = int(plain_query("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
            src_schema, table))[0][0])
        dst_n = int(plain_query("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
            dst_schema, table))[0][0])
        expected = src_n
        note = ""
        if admin_user and admin_provider_no:
            sql = admin_row_count_sql(table, dst_schema, admin_user,
                                      admin_provider_no)
            if sql:
                admin_rows = int(plain_query(sql)[0][0])
                expected += admin_rows
                note = " (+{0} break-glass admin row(s))".format(admin_rows)
        if table in appended:
            recorded = int(appended[table])
            sql = appended_row_count_sql(table, src_schema, dst_schema)
            twinless = int(plain_query(sql)[0][0]) if sql else 0
            expected += recorded
            note += " (+{0} synthesised row(s))".format(recorded)
            if twinless != recorded:
                bad.append("{0}: {1} target row(s) without a staging twin, "
                           "but the roles ledger recorded {2}".format(
                               table, twinless, recorded))
                continue
            limit = APPENDED_ROW_MAX.get(table)
            if limit is not None and twinless > limit:
                # the ledger measures the same anti-join, so it agrees with
                # itself by construction; the step's own upper bound is
                # what catches a write that ran twice
                bad.append("{0}: {1} target row(s) without a staging twin, "
                           "but the roles step synthesises at most {2}"
                           .format(table, twinless, limit))
                continue
        line = "{0}: staging {1} -> target {2}{3}".format(
            table, src_n, dst_n, note)
        (ok if dst_n == expected else bad).append(line)
    return ok, bad

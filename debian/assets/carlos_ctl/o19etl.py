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
from typing import Callable, Dict, List, Optional, Tuple

from . import o19map_schema
from .util import log, warn

CHUNK_ROWS = 50000

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
    # the SQL literal must carry a DOUBLED backslash: the server's string
    # parser drops the backslash of an unknown escape, so '\x00' would reach
    # the regex engine as 'x00' (an innocent-looking, wrong character class)
    return ("{0} IS NOT NULL AND {1} REGEXP '[^\\\\x00-\\\\x7F]' AND "
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
        "IFNULL(COLUMN_DEFAULT, '\\0NONE'), EXTRA "
        "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '{0}'"
        .format(schema))
    for r in rows:
        if len(r) < 8:
            continue
        t, c, dtype, ctype, nullable, char_len, default, extra = r[:8]
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
            "has_default": has_default,
            "default": value,
            "auto_increment": "auto_increment" in extra.lower(),
        }
    return out


def _sql_str(value: str) -> str:
    """SQL string-literal escaping (mirrors dbops.sql_escape, kept local so
    the pure statement builders import nothing from the deployment)."""
    # NUL is encoded too: the client refuses a raw NUL in a statement, and
    # decoded batch values may carry one
    return (value.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\0", "\\0"))


ADMIN_USER_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.@\-]{0,29}$")


def validate_admin_user(name: Optional[str]) -> str:
    """The break-glass user name is interpolated into account SQL run as
    database root and must fit security.user_name (varchar 30): plain
    characters only, no quoting tricks."""
    if not name or not ADMIN_USER_RE.match(name):
        raise ValueError(
            "--admin-user must be 1-30 characters of letters, digits, "
            "'_', '.', '@' or '-' (got {0!r})".format(name))
    return name


def enum_values(column_type: str) -> List[str]:
    m = re.match(r"enum\((.*)\)$", column_type, re.I | re.S)
    if not m:
        return []
    return re.findall(r"'((?:[^'\\]|\\.)*)'", m.group(1))


# --------------------------------------------------------------------------
# pure statement generation
# --------------------------------------------------------------------------

def idmap_table(parent: str) -> str:
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
    expr = "s.`{0}`".format(src)
    if repaired and target_col in repaired:
        expr = repair_expr(expr)
    parent = table_entry.get("fk_remap", {}).get(target_col)
    if parent and archive_schema:
        lookup = "(SELECT m.new_id FROM `{0}`.`{1}` m WHERE m.old_id = {2})" \
            .format(archive_schema, idmap_table(parent), expr)
        expr = lookup if nullable else "IFNULL({0}, {1})".format(lookup,
                                                                  expr)
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
        out.append((c, "SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE {2} IS NOT "
                       "NULL AND {2} NOT IN ({3})".format(
                           src_schema, table, expr,
                           ", ".join("'{0}'".format(v) for v in values))))
    return out


def copy_statement(table: str, entry: dict, src_schema: str,
                   dst_schema: str, dst_cols: Dict[str, dict],
                   repaired: Optional[set] = None,
                   window: Optional[Tuple[int, int]] = None,
                   archive_schema: Optional[str] = None) -> str:
    cols = entry["cols"]
    targets = ", ".join("`{0}`".format(c) for c in cols)
    exprs = ", ".join(
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


def merge_join(entry: dict, archive_schema: Optional[str] = None,
               dst_cols: Optional[Dict[str, dict]] = None) -> str:
    """Natural-key join between target alias d and source alias s. The
    source side carries the SAME expression the insert stores (id remap,
    zero-date NULLIF, enum fallback) so anti-join, insert and id map all
    agree on what a row's key is."""
    parts = []
    for k in entry["merge_keys"]:
        expr = source_expr(entry, k, None, archive_schema,
                           dst_cols[k]["nullable"] if dst_cols else None)
        if dst_cols and k in dst_cols:
            expr = sanitize_expr(expr, dst_cols[k])
        parts.append("d.`{0}` <=> {1}".format(k, expr))
    return " AND ".join(parts)


def merge_statement(table: str, entry: dict, src_schema: str,
                    dst_schema: str, dst_cols: Dict[str, dict],
                    repaired: Optional[set] = None,
                    archive_schema: Optional[str] = None) -> str:
    """Anti-join on the natural key: CARLOS seed rows win, clinic-added
    rows append. A surrogate integer PK is left out of the insert so
    AUTO_INCREMENT assigns fresh ids (clinic ids could collide with
    seeds); rows are appended in source-id order so the id map can pair
    them deterministically (idmap_statements)."""
    surrogate = entry.get("surrogate_pk")
    cols = [c for c in entry["cols"] if c != surrogate]
    targets = ", ".join("`{0}`".format(c) for c in cols)
    exprs = ", ".join(
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
    return [
        "DROP TABLE IF EXISTS `{0}`.`{1}`".format(archive_schema, name),
        "CREATE TABLE `{0}`.`{1}` (old_id BIGINT NOT NULL PRIMARY KEY, "
        "new_id BIGINT NOT NULL)".format(archive_schema, name),
        # twin n maps to target twin n; a surplus source twin (its key was
        # already satisfied by a CARLOS seed, so the anti-join appended
        # nothing for it) falls back to the target's first row for that key
        # — every source id a child references gets a deterministic map
        "INSERT INTO `{0}`.`{1}` (old_id, new_id) SELECT s.`{2}`, "
        "COALESCE(d.`{2}`, d1.`{2}`) "
        "FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY {3} ORDER BY "
        "`{2}`) AS rn FROM `{4}`.`{5}`) s LEFT JOIN (SELECT *, ROW_NUMBER() "
        "OVER (PARTITION BY {3} ORDER BY `{2}`) AS rn FROM `{6}`.`{5}`) d ON "
        "{7} AND s.rn = d.rn LEFT JOIN (SELECT *, ROW_NUMBER() OVER "
        "(PARTITION BY {3} ORDER BY `{2}`) AS rn FROM `{6}`.`{5}`) d1 ON {8} "
        "AND d1.rn = 1 WHERE COALESCE(d.`{2}`, d1.`{2}`) IS NOT NULL".format(
            archive_schema, name, pk,
            ", ".join("`{0}`".format(k) for k in keys),
            src_schema, table, dst_schema, join, join.replace("d.`", "d1.`")),
    ]


def fk_unmapped_count_sql(table: str, entry: dict, src_schema: str,
                          archive_schema: str) -> List[Tuple[str, str, str]]:
    """(column, parent, COUNT-sql): source rows whose foreign key names an
    id the parent's map does not know — an already-dangling O19
    reference, reported because it becomes NULL (nullable) or is kept
    raw (NOT NULL)."""
    out = []
    for col, parent in sorted(entry.get("fk_remap", {}).items()):
        src_col = entry.get("renames", {}).get(col, col)
        out.append((col, parent,
                    "SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE s.`{2}` IS "
                    "NOT NULL AND NOT EXISTS (SELECT 1 FROM `{3}`.`{4}` m "
                    "WHERE m.old_id = s.`{2}`)".format(
                        src_schema, table, src_col, archive_schema,
                        idmap_table(parent))))
    return out


def idmap_changed_count_sql(table: str, archive_schema: str) -> str:
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


def archive_statements(table: str, src_schema: str,
                       archive_schema: str) -> List[str]:
    return [
        "DROP TABLE IF EXISTS `{0}`.`{1}`".format(archive_schema, table),
        "CREATE TABLE `{0}`.`{1}` LIKE `{2}`.`{1}`".format(
            archive_schema, table, src_schema),
        "INSERT INTO `{0}`.`{1}` SELECT * FROM `{2}`.`{1}`".format(
            archive_schema, table, src_schema),
    ]


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

    A dropped column this dump does not carry (lower patch level) is
    skipped WITH a note; the remaining dropped columns are still
    captured — never the whole table silently."""
    dropped = dict(entry.get("dropped", {}))
    if not dropped:
        return []
    absent = sorted(c for c in dropped if c not in src_cols)
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
    context = [renames.get(c, c) for c in _context_cols(entry)
               if renames.get(c, c) in src_cols]
    select_cols = list(dict.fromkeys(context + sorted(dropped)))
    predicate = " OR ".join(
        "({0})".format(d["nondefault"]) for d in dropped.values())
    shadow = "{0}__dropped".format(table)
    cols = ", ".join("s.`{0}`".format(c) for c in select_cols)
    return [
        "DROP TABLE IF EXISTS `{0}`.`{1}`".format(archive_schema, shadow),
        "CREATE TABLE `{0}`.`{1}` AS SELECT {2} FROM `{3}`.`{4}` s "
        "WHERE {5}".format(archive_schema, shadow, cols, src_schema,
                           table, predicate),
    ]


def unknown_columns(entry: dict, src_cols: Dict[str, dict]) -> List[str]:
    """Staged columns the manifest neither copies, renames nor lists as
    dropped (vendor-fork additions) — matched case-insensitively."""
    known = {entry.get("renames", {}).get(c, c).lower()
             for c in entry.get("cols", [])}
    known.update(c.lower() for c in entry.get("dropped", {}))
    return sorted(c for c in src_cols if c.lower() not in known)


def unknown_column_shadow_statements(table: str, entry: dict,
                                     src_schema: str, archive_schema: str,
                                     src_cols: Dict[str, dict]) -> List[str]:
    """Shadow-capture vendor-fork columns (o19_archive.<table>__unknown_cols)
    for every row where any of them holds a value — the preservation the
    `unknown-as-archive` sign-off promises."""
    extra = unknown_columns(entry, src_cols)
    if not extra:
        return []
    context = [c for c in _context_cols(entry)
               if entry.get("renames", {}).get(c, c) in src_cols]
    context = [entry.get("renames", {}).get(c, c) for c in context]
    select_cols = list(dict.fromkeys(context + extra))
    shadow = "{0}__unknown_cols".format(table)
    cols = ", ".join("s.`{0}`".format(c) for c in select_cols)
    predicate = " OR ".join("s.`{0}` IS NOT NULL".format(c) for c in extra)
    return [
        "DROP TABLE IF EXISTS `{0}`.`{1}`".format(archive_schema, shadow),
        "CREATE TABLE `{0}`.`{1}` AS SELECT {2} FROM `{3}`.`{4}` s "
        "WHERE {5}".format(archive_schema, shadow, cols, src_schema,
                           table, predicate),
    ]


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
    return ["DELETE FROM `{0}`.`{1}` WHERE {2}".format(dst_schema, t, where)
            for t, where in o19map_schema.CARLOSDOC_SEED_DELETES]


def seed_group_tables() -> List[str]:
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


def overlength_precheck_sql(table: str, entry: dict, src_schema: str,
                            dst_cols: Dict[str, dict],
                            src_cols: Dict[str, dict]) -> List[Tuple[str, str]]:
    """(column, COUNT-sql) pairs for target text columns narrower than
    their source — a non-zero count must ERROR, never truncate."""
    out = []
    for c in entry["cols"]:
        d = dst_cols.get(c)
        s = src_cols.get(entry.get("renames", {}).get(c, c))
        if not d or not s:
            continue
        if d["char_len"] and s["char_len"] \
                and d["char_len"] < s["char_len"]:
            sql = ("SELECT COUNT(*) FROM `{0}`.`{1}` s WHERE "
                   "CHAR_LENGTH({2}) > {3}".format(
                       src_schema, table, source_expr(entry, c),
                       d["char_len"]))
            out.append((c, sql))
    return out


# --------------------------------------------------------------------------
# execution driver
# --------------------------------------------------------------------------

def _progress_path(state_dir: str) -> str:
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
        with open(_progress_path(state_dir), encoding="utf-8") as fh:
            progress = json.load(fh)
    except (OSError, ValueError):
        progress = {"tables": {}}
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
            warn("etl progress ledger carries no dump digest — "
                 "discarding it")
            progress = {"tables": {}}
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
    # 0600 like the private text files: the roles ledger plans carry
    # provider numbers
    tmp = _progress_path(state_dir) + ".tmp"
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(progress, fh)
    os.replace(tmp, _progress_path(state_dir))


ABSENT_OBJECT_MARKERS = ("Unknown column", "doesn't exist", "1054", "1146")


class QueryError(RuntimeError):
    """A failed client statement: the message carries the SQL prefix for
    the operator, `stderr` the server's own error text — which is the only
    part the absent-object test may look at (the SQL can contain a patient
    id such as 1054)."""

    def __init__(self, message: str, stderr: str = ""):
        RuntimeError.__init__(self, message)
        self.stderr = stderr


def _absent_object_error(exc: Exception) -> bool:
    """True for the server's 'no such table/column' errors — judged on
    the client's stderr alone, not on the formatted message with the
    statement text in it."""
    text = getattr(exc, "stderr", None)
    if text is None:
        text = str(exc)
    return any(m in text for m in ABSENT_OBJECT_MARKERS)


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
                if _absent_object_error(exc):
                    if notes is not None:
                        notes.append("charset scan skipped: {0}.{1} not in "
                                     "this dump".format(table, col))
                    continue
                raise RuntimeError("charset scan of {0}.{1} failed: {2}"
                                   .format(table, col, exc))
            bad = int(query(
                "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE {2} IS NOT NULL AND "
                "{2} REGEXP {3} AND NOT ({4})".format(
                    src_schema, table, expr, MOJIBAKE_MARKER_RE,
                    double_encoded_predicate(expr)))[0][0])
            if bad:
                unrepairable.append("{0}.{1} ({2} row(s))".format(
                    table, col, bad))
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
                    src_tables: Optional[set] = None) -> Tuple[dict, List[str]]:
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
            plain("RENAME TABLE `{0}`.`{1}` TO `{0}`.`{2}`".format(
                src_schema, live, want))
            lines.append("{0} -> {1}".format(live, want))
    return lines


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

    # -- loud pre-checks over every table before the first write ----------
    admin_user = validate_admin_user(ctx["admin_user"])
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
        required = missing_required_columns(entry, dst_info[table])
        if required:
            problems.append(
                "{0}: NOT NULL target column(s) without default or "
                "value_exprs: {1}".format(table, ", ".join(required)))
        for col, sql in overlength_precheck_sql(
                table, entry, src, dst_info[table], src_info[table]):
            n = int(query(sql)[0][0])
            if n:
                problems.append(
                    "{0}.{1}: {2} value(s) longer than the target column — "
                    "refusing to truncate".format(table, col, n))
    # the roles post-step's own preconditions, predicted from staging (the
    # tables involved are id-intact copies): refuse here, before the first
    # write, rather than after the whole copy
    from . import o19roles
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
    if ctx.get("role_templates") and "secRole" in src_info:
        stage_roles = [r[0] for r in plain(
            "SELECT role_name FROM `{0}`.secRole".format(src))]
        stage_rows = plain("SELECT roleUserGroup, objectName, privilege, "
                           "priority FROM `{0}`.secObjPrivilege".format(src))
        customs = o19roles.custom_roles(
            stage_roles, stage_rows, o19map_schema.STOCK_ROLE_NAMES)
        problems.extend(o19roles.validate_role_templates(
            ctx["role_templates"], customs, o19map_schema.STOCK_ROLE_NAMES))
    if problems:
        die("ETL pre-checks failed (nothing was written):\n  "
            + "\n  ".join(problems))

    scan_notes: List[str] = []
    repairs = detect_repairs(plain, src, ctx["accepted"], scan_notes)
    if scan_notes:
        report("charset scan:\n  " + "\n  ".join(scan_notes))
    if repairs:
        report("per-row charset repair active on: " + ", ".join(
            sorted("{0}.{1}".format(t, c)
                   for t, cs in repairs.items() for c in cs)))

    # enum values outside the target set fall to the column default —
    # counted up front so the fallback is never silent
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
    if enum_lines:
        report("enum fallbacks:\n  " + "\n  ".join(enum_lines))

    plain("CREATE DATABASE IF NOT EXISTS `{0}`".format(arch))
    progress = load_progress(state_dir, ctx.get("dump_sha256"),
                             o19map_schema.SCHEMA_MAP_VERSION)

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
                width = dst_info.get("provider", {}).get(
                    "provider_no", {}).get("char_len", 0)
                if width and len(admin_pn) > width:
                    die("cannot allocate a break-glass provider_no: the "
                        "clinic's highest numeric provider_no ({0}) leaves "
                        "no room in provider.provider_no ({1} chars)"
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
            # file-first, before any SQL touches accounts (bootstrap-admin's
            # contract: never leave a credential that exists only in memory)
            fd = os.open(cred_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC,
                         0o600)
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
              "unknown_column_shadows": 0}
    idmap_lines: List[str] = []
    fk_lines: List[str] = []
    drop_lines: List[str] = []
    shadow_notes: List[str] = []
    for table in etl_order(o19map_schema.TABLES):
        entry = o19map_schema.TABLES[table]
        cls = entry["class"]
        if table not in src_info:
            continue  # not in this dump (patch-level variance)
        entry = effective.get(table, entry)
        tstate = progress["tables"].setdefault(table, {})

        if cls == "reference":
            counts[cls] += 1
            continue
        if cls == "drop":
            counts[cls] += 1
            n = int(plain("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
                src, table))[0][0])
            if n:
                drop_lines.append("{0}: {1} row(s) not migrated (removed "
                                  "module infrastructure)".format(table, n))
            continue

        if cls == "archive":
            # counted from the ledger, so a resumed run reports the same
            # figure as the first
            if tstate.get("done"):
                counts["archive"] += 1
                continue
            for sql in archive_statements(table, src, arch):
                query(sql)
            tstate["done"] = True
            save_progress(state_dir, progress)
            counts["archive"] += 1
            continue

        dcols = dst_info[table]
        repaired = repairs.get(table)

        if cls == "merge":
            if not tstate.get("done"):
                query(merge_statement(table, entry, src, dst, dcols,
                                      repaired, arch))
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
                tstate["done"] = True
                save_progress(state_dir, progress)
            counts["merge"] += 1
        else:  # copy
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
                if not tstate.get("started"):
                    tstate["started"] = True
                    save_progress(state_dir, progress)
                for window in chunk_windows(lo, hi):
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
        if entry.get("dropped") and not tstate.get("shadow_done"):
            for sql in shadow_statements(table, entry, src, arch,
                                         src_info[table], shadow_notes):
                query(sql)
            tstate["shadow_done"] = True
            save_progress(state_dir, progress)
        # ... and vendor-fork columns the manifest does not know at all
        if not tstate.get("unknown_shadow_done"):
            stmts = unknown_column_shadow_statements(
                table, entry, src, arch, src_info[table])
            for sql in stmts:
                query(sql)
            if stmts:
                counts["unknown_column_shadows"] += 1
                report("{0}: unmapped column(s) {1} shadow-captured to "
                       "{2}.{0}__unknown_cols".format(
                           table, ", ".join(unknown_columns(
                               entry, src_info[table])), arch))
            tstate["unknown_shadow_done"] = True
            save_progress(state_dir, progress)

    # -- tables the manifest does not know: archived whole ------------------
    # (the unknown-as-archive sign-off is a preservation promise, not a
    # permission to drop)
    unknown_tables = sorted(t for t in src_info
                            if t not in o19map_schema.TABLES)
    unknown_lines: List[str] = []
    for table in unknown_tables:
        tstate = progress["tables"].setdefault(table, {})
        if tstate.get("done"):
            counts["unknown_archived"] += 1
            continue
        n = int(plain("SELECT COUNT(*) FROM `{0}`.`{1}`".format(
            src, table))[0][0])
        if n == 0:
            unknown_lines.append("{0}: empty, not archived".format(table))
        else:
            for sql in archive_statements(table, src, arch):
                query(sql)
            unknown_lines.append("{0}: {1} row(s) archived to {2}".format(
                table, n, arch))
            counts["unknown_archived"] += 1
        tstate["done"] = True
        save_progress(state_dir, progress)
    if unknown_lines:
        report("unknown (unclassified) tables:\n  "
               + "\n  ".join(unknown_lines))
    if drop_lines:
        report("drop-class tables holding rows (report-only, per the "
               "manifest):\n  " + "\n  ".join(drop_lines))
    if shadow_notes:
        report("dropped-column capture:\n  " + "\n  ".join(shadow_notes))
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
        report("API credentials copied verbatim — ROTATE/VERIFY before "
               "go-live (tokens issued by the OSCAR 19 install keep "
               "working): " + ", ".join(token_tables))

    # -- roles, privileges and CARLOS-required rows (M8) -------------------
    o19roles.run_roles(ctx, progress,
                       lambda: save_progress(state_dir, progress))

    query(force_reset_statement(dst))
    report("forcePasswordReset set for every imported user")
    report("ETL complete: {0} copied, {1} merged, {2} archived, "
           "{3} reference (CARLOS wins), {4} dropped (report-only), "
           "{5} unknown table(s) archived".format(
               counts["copy"], counts["merge"], counts["archive"],
               counts["reference"], counts["drop"],
               counts["unknown_archived"]))
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


def admin_row_count_sql(table: str, dst_schema: str, admin_user: str,
                        admin_provider_no: str) -> Optional[str]:
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
ROLES_STEP_TABLES = (
    "Facility", "clinic", "provider", "security", "secRole", "secUserRole",
    "secObjPrivilege", "secObjectName", "program", "program_provider",
    "provider_facility", "preventions", "eform", "property",
)


def appended_row_count_sql(table: str, src_schema: str,
                           dst_schema: str) -> Optional[str]:
    keys = APPENDED_ROW_KEYS.get(table)
    if not keys:
        return None
    join = " AND ".join("d.`{0}` <=> s.`{0}`".format(k) for k in keys)
    return ("SELECT COUNT(*) FROM `{0}`.`{1}` d WHERE NOT EXISTS (SELECT 1 "
            "FROM `{2}`.`{1}` s WHERE {3})".format(dst_schema, table,
                                                     src_schema, join))


def row_parity(plain_query, src_schema: str, dst_schema: str,
               admin_user: Optional[str] = None,
               admin_provider_no: Optional[str] = None,
               appended: Optional[Dict[str, int]] = None
               ) -> Tuple[List[str], List[str]]:
    """(ok_lines, mismatch_lines) comparing staging vs target counts for
    every copy-class table. The tolerated deltas are the break-glass
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
    for table, entry in sorted(o19map_schema.TABLES.items()):
        if entry["class"] != "copy" or table not in src_tables:
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
        line = "{0}: staging {1} -> target {2}{3}".format(
            table, src_n, dst_n, note)
        (ok if dst_n == expected else bad).append(line)
    return ok, bad

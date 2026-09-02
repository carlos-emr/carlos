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
"""

import json
import os
import re
import time
from typing import Callable, Dict, List, Optional, Tuple

from . import o19map_schema
from .util import log, warn

CHUNK_ROWS = 50000
MOJIBAKE_HEX = "C383"

REPAIR_TEMPLATE = ("CONVERT(BINARY CONVERT({0} USING latin1) USING utf8mb4)")


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
        out.setdefault(t, {})[c] = {
            "type": dtype.lower(),
            "column_type": ctype,
            "nullable": nullable.upper() == "YES",
            "char_len": int(char_len or 0),
            "has_default": default != "\\0NONE" and default != "\0NONE",
            "auto_increment": "auto_increment" in extra.lower(),
        }
    return out


def enum_values(column_type: str) -> List[str]:
    m = re.match(r"enum\((.*)\)$", column_type, re.I | re.S)
    if not m:
        return []
    return re.findall(r"'((?:[^'\\]|\\.)*)'", m.group(1))


# --------------------------------------------------------------------------
# pure statement generation
# --------------------------------------------------------------------------

def source_expr(table_entry: dict, target_col: str,
                repaired: Optional[set] = None) -> str:
    """The SELECT expression feeding one target column."""
    ve = table_entry.get("value_exprs", {})
    if target_col in ve:
        return ve[target_col]
    src = table_entry.get("renames", {}).get(target_col, target_col)
    expr = "s.`{0}`".format(src)
    if repaired and target_col in repaired:
        expr = REPAIR_TEMPLATE.format(expr)
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
            # DEFAULT is not addressable inside CASE in MySQL — out-of-set
            # values fall to NULL (nullable) or the first enum member.
            quoted = ", ".join("'{0}'".format(v) for v in values)
            fallback = ("ELSE NULL" if dst_info["nullable"]
                        else "ELSE '{0}'".format(values[0]))
            expr = "CASE WHEN {0} IN ({1}) THEN {0} {2} END".format(
                expr, quoted, fallback)
    return expr


def copy_statement(table: str, entry: dict, src_schema: str,
                   dst_schema: str, dst_cols: Dict[str, dict],
                   repaired: Optional[set] = None,
                   window: Optional[Tuple[int, int]] = None) -> str:
    cols = entry["cols"]
    targets = ", ".join("`{0}`".format(c) for c in cols)
    exprs = ", ".join(
        sanitize_expr(source_expr(entry, c, repaired), dst_cols[c])
        for c in cols)
    sql = ("INSERT INTO `{0}`.`{1}` ({2}) SELECT {3} FROM `{4}`.`{1}` s"
           .format(dst_schema, table, targets, exprs, src_schema))
    if window:
        chunk = entry["chunk_by"]
        sql += (" WHERE s.`{0}` > {1} AND s.`{0}` <= {2}"
                .format(chunk, window[0], window[1]))
    return sql


def merge_statement(table: str, entry: dict, src_schema: str,
                    dst_schema: str, dst_cols: Dict[str, dict],
                    repaired: Optional[set] = None) -> str:
    """Anti-join on the natural key: CARLOS seed rows win, clinic-added
    rows append. A surrogate integer PK is left out of the insert so
    AUTO_INCREMENT assigns fresh ids (clinic ids could collide with
    seeds); the report records that appended rows changed ids."""
    surrogate = entry.get("surrogate_pk")
    cols = [c for c in entry["cols"] if c != surrogate]
    targets = ", ".join("`{0}`".format(c) for c in cols)
    exprs = ", ".join(
        sanitize_expr(source_expr(entry, c, repaired), dst_cols[c])
        for c in cols)
    join = " AND ".join(
        "d.`{0}` <=> s.`{0}`".format(k) for k in entry["merge_keys"])
    return ("INSERT INTO `{0}`.`{1}` ({2}) SELECT {3} FROM `{4}`.`{1}` s "
            "WHERE NOT EXISTS (SELECT 1 FROM `{0}`.`{1}` d WHERE {5})"
            .format(dst_schema, table, targets, exprs, src_schema, join))


def archive_statements(table: str, src_schema: str,
                       archive_schema: str) -> List[str]:
    return [
        "DROP TABLE IF EXISTS `{0}`.`{1}`".format(archive_schema, table),
        "CREATE TABLE `{0}`.`{1}` LIKE `{2}`.`{1}`".format(
            archive_schema, table, src_schema),
        "INSERT INTO `{0}`.`{1}` SELECT * FROM `{2}`.`{1}`".format(
            archive_schema, table, src_schema),
    ]


def shadow_statements(table: str, entry: dict, src_schema: str,
                      archive_schema: str,
                      src_cols: Dict[str, dict]) -> List[str]:
    """Capture dropped-column values (+ the row's PK context) into
    o19_archive.<table>__dropped, only for rows with non-default data."""
    dropped = entry.get("dropped", {})
    if not dropped:
        return []
    pk_cols = [c for c in entry["cols"][:3]]
    if entry.get("chunk_by"):
        pk_cols = [entry["chunk_by"]]
    select_cols = list(dict.fromkeys(pk_cols + sorted(dropped)))
    missing = [c for c in select_cols if c not in src_cols]
    if missing:
        return []
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
    seed_pn = o19map_schema.SEED_PROVIDER_NO
    return [
        # provider row cloned from the seeded clinician, new id + name
        "INSERT INTO `{0}`.provider (provider_no, last_name, first_name, "
        "provider_type, specialty, status, lastUpdateUser, lastUpdateDate) "
        "SELECT '{1}', 'Admin', '{2}', provider_type, specialty, '1', "
        "'{1}', NOW() FROM `{0}`.provider WHERE provider_no = '{3}'"
        .format(dst_schema, admin_provider_no, admin_user, seed_pn),
        "INSERT INTO `{0}`.security (user_name, password, provider_no, "
        "pin, forcePasswordReset, lastUpdateUser, lastUpdateDate) VALUES "
        "('{1}', '{2}', '{3}', '{4}', 1, '{3}', NOW())"
        .format(dst_schema, admin_user, password_hash,
                admin_provider_no, pin),
        "INSERT INTO `{0}`.secUserRole (provider_no, role_name, "
        "lastUpdateDate) SELECT '{1}', role_name, NOW() FROM "
        "`{0}`.secUserRole WHERE provider_no = '{2}'"
        .format(dst_schema, admin_provider_no, seed_pn),
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
        keep = "user_name <> '{0}'".format(admin_user)
    elif "provider_no" in dst_cols:
        keep = "provider_no <> '{0}'".format(admin_provider_no)
    elif "providerNo" in dst_cols:
        keep = "providerNo <> '{0}'".format(admin_provider_no)
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


def load_progress(state_dir: str) -> Dict:
    try:
        with open(_progress_path(state_dir), encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return {"tables": {}}


def save_progress(state_dir: str, progress: Dict) -> None:
    tmp = _progress_path(state_dir) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(progress, fh)
    os.replace(tmp, _progress_path(state_dir))


def detect_repairs(query, src_schema: str, accepted) -> Dict[str, set]:
    """Charset scan: {table: {columns needing repair}} — and hard-stop
    (B8) if the repair cannot round-trip, or if repair is needed but
    unacknowledged."""
    from .util import die
    repairs: Dict[str, set] = {}
    unrepairable = []
    for table, entry in o19map_schema.TABLES.items():
        for col in entry.get("charset_scan", ()):
            try:
                n = int(query(
                    "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE HEX(`{2}`) "
                    "LIKE '%{3}%'".format(src_schema, table, col,
                                          MOJIBAKE_HEX))[0][0])
            except Exception:
                continue
            if n == 0:
                continue
            bad = int(query(
                "SELECT COUNT(*) FROM `{0}`.`{1}` WHERE `{2}` IS NOT NULL "
                "AND {3} IS NULL".format(
                    src_schema, table, col,
                    REPAIR_TEMPLATE.format("`{0}`".format(col))))[0][0])
            if bad:
                unrepairable.append("{0}.{1} ({2} rows)".format(
                    table, col, bad))
            repairs.setdefault(table, set()).add(col)
    if unrepairable:
        die("B8: double-encoded text that the standard charset repair "
            "cannot round-trip:\n  " + "\n  ".join(unrepairable)
            + "\nThis needs manual investigation — no flag overrides it.")
    if repairs and "charset-repair" not in accepted:
        cols = sorted("{0}.{1}".format(t, c)
                      for t, cs in repairs.items() for c in cs)
        die("double-encoded text detected in: {0}\nRe-run with "
            "--accept charset-repair to apply the latin1->utf8mb4 repair "
            "during the copy.".format(", ".join(cols)))
    return repairs


def effective_entry(table: str, entry: dict,
                    src_cols: Dict[str, dict]) -> Tuple[dict, List[str]]:
    """Intersect the manifest's column map with what the staged dump
    actually has (case-insensitive): a clinic at a lower patch level may
    lack columns the manifest superset knows. Missing sources are skipped
    WITH a report line — the target column then takes its default."""
    have = {c.lower() for c in src_cols}
    renames = entry.get("renames", {})
    ve = entry.get("value_exprs", {})
    kept, skipped = [], []
    for c in entry.get("cols", []):
        if c in ve or renames.get(c, c).lower() in have:
            kept.append(c)
        else:
            skipped.append(c)
    if not skipped:
        return entry, []
    adjusted = dict(entry)
    adjusted["cols"] = kept
    return adjusted, ["{0}.{1} absent from this dump — target default "
                      "used".format(table, c) for c in skipped]


def run_etl(ctx, make_password_hash: Callable[[], Tuple[str, str, str]]):
    """Execute P4. make_password_hash() -> (password, bcrypt_hash, pin)
    so the crypto (and its bcrypt dependency) stays injectable."""
    from .util import die
    query = ctx["query_etl"]          # carries the session prelude
    plain = ctx["query"]
    src, dst, arch = ctx["src_schema"], ctx["target_db"], ctx["archive_schema"]
    state_dir = ctx["state_dir"]
    report = ctx["report"]

    src_info = introspect_columns(plain, src)
    dst_info = introspect_columns(plain, dst)

    patch_notes: List[str] = []
    effective: Dict[str, dict] = {}
    for table, entry in o19map_schema.TABLES.items():
        if entry["class"] in ("copy", "merge") and table in src_info:
            adjusted, notes = effective_entry(table, entry,
                                              src_info[table])
            effective[table] = adjusted
            patch_notes.extend(notes)
    if patch_notes:
        report("patch-level variance ({0} column(s)):\n  ".format(
            len(patch_notes)) + "\n  ".join(patch_notes))

    # -- loud pre-checks over every table before the first write ----------
    problems = []
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
    if problems:
        die("ETL pre-checks failed (nothing was written):\n  "
            + "\n  ".join(problems))

    repairs = detect_repairs(plain, src, ctx["accepted"])
    if repairs:
        report("charset repair active on: " + ", ".join(
            sorted("{0}.{1}".format(t, c)
                   for t, cs in repairs.items() for c in cs)))

    plain("CREATE DATABASE IF NOT EXISTS `{0}`".format(arch))
    progress = load_progress(state_dir)

    # -- seed reconciliation (strictly ordered, before provider/security) --
    admin_user = ctx["admin_user"]
    admin_pn = progress.get("admin_provider_no")
    if not progress.get("seed_done"):
        max_pn = plain("SELECT IFNULL(MAX(CAST(provider_no AS SIGNED)), 0) "
                       "FROM `{0}`.provider WHERE provider_no REGEXP "
                       "'^[0-9]+$'".format(src))[0][0]
        admin_pn = str(int(max_pn) + 1)
        # the admin's security/secUserRole rows take auto ids — bump the
        # counters above the clinic's id range or the later id-preserving
        # copy collides with the admin's rows (found live in rehearsal)
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
        with open(cred_path, "w", encoding="utf-8") as fh:
            fh.write("break-glass administrator (created by import-o19)\n"
                     "user: {0}\nprovider_no: {1}\npassword: {2}\n"
                     "pin: {3}\nforced password reset on first login\n"
                     .format(admin_user, admin_pn, password, pin))
        os.chmod(cred_path, 0o600)
        for sql in seed_admin_statements(dst, admin_user, admin_pn,
                                         pw_hash, pin):
            query(sql)
        for sql in seed_delete_statements(dst):
            query(sql)
        progress["admin_provider_no"] = admin_pn
        progress["seed_done"] = True
        save_progress(state_dir, progress)
        report("break-glass admin '{0}' (provider {1}) created; seeded "
               "clinician removed; credentials in {2}".format(
                   admin_user, admin_pn, cred_path))
    seed_group = set(seed_group_tables())

    # -- table loop --------------------------------------------------------
    counts = {"copy": 0, "merge": 0, "archive": 0, "drop": 0,
              "reference": 0, "rows": 0}
    for table, entry in sorted(o19map_schema.TABLES.items()):
        cls = entry["class"]
        if table not in src_info:
            continue  # not in this dump (patch-level variance)
        entry = effective.get(table, entry)
        tstate = progress["tables"].setdefault(table, {})

        if cls in ("reference", "drop"):
            counts[cls] += 1
            continue

        if cls == "archive":
            if tstate.get("done"):
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
                                      repaired))
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
                done_through = tstate.get("done_through", lo - 1)
                for window in chunk_windows(lo, hi):
                    if window[1] <= done_through:
                        continue
                    query(copy_statement(table, entry, src, dst, dcols,
                                         repaired, window))
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
                                         repaired))
                    tstate["done"] = True
            save_progress(state_dir, progress)
            counts["copy"] += 1

        # shadow-capture dropped columns alongside the copy
        if entry.get("dropped") and not tstate.get("shadow_done"):
            for sql in shadow_statements(table, entry, src, arch,
                                         src_info[table]):
                query(sql)
            tstate["shadow_done"] = True
            save_progress(state_dir, progress)

    query(force_reset_statement(dst))
    report("forcePasswordReset set for every imported user")
    report("ETL complete: {0} copied, {1} merged, {2} archived, "
           "{3} reference (CARLOS wins), {4} dropped (report-only)".format(
               counts["copy"], counts["merge"], counts["archive"],
               counts["reference"], counts["drop"]))
    return counts


# --------------------------------------------------------------------------
# P7 core — row parity
# --------------------------------------------------------------------------

def row_parity(plain_query, src_schema: str, dst_schema: str
               ) -> Tuple[List[str], List[str]]:
    """(ok_lines, mismatch_lines) comparing staging vs target counts for
    every copy-class table, with the expected deltas itemized."""
    ok, bad = [], []
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
        deletes = {t for t, _ in o19map_schema.CARLOSDOC_SEED_DELETES}
        if table in ("provider", "security", "secUserRole") \
                or table in deletes:
            # + break-glass admin rows, - nothing (seeds were removed
            # before the copy); secUserRole adds one row per admin role.
            if dst_n >= expected:
                note = " (+{0} break-glass admin row(s))".format(
                    dst_n - expected)
                expected = dst_n
        line = "{0}: staging {1} -> target {2}{3}".format(
            table, src_n, dst_n, note)
        (ok if dst_n == expected else bad).append(line)
    return ok, bad

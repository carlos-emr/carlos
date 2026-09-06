#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""verify_sql_semantics.py — settle the ETL SQL behaviours that only a real
engine can answer.

Five of them: the merge anti-join's visibility of its own inserts and the
per-row charset repair, both in the P4 copy path; the M22 content digest,
whose whole point is that a check nobody has broken on purpose is not a
check; and P7's value-level comparison of the copy class and of the merge
class, where the question is not only "does it catch a change" but "does
it agree with the ETL on a FAITHFUL run" -- a check that false-alarms on
every clinic is worse than no check. The merge sections run BOTH shapes
the manifest produces, because they take different code paths: appended
rows paired through the id map when a surrogate id moved, and through the
natural key (with a `merge_exclude` clause) when there is none.
One section runs the P2 chain end to end
with the real tools -- a latin1 clinic schema measured by `o19_preflight.py`,
dumped by mysqldump exactly as the operator guide says to, restored, and
compared by `o19digest` -- because the unit tests drive each half against
canned
answers and cannot catch a disagreement about what the two halves MEAN.

`o19etl.merge_statement` is an anti-join that reads the table it inserts
into::

    INSERT INTO target (...) SELECT ... FROM staging s
    WHERE NOT EXISTS (SELECT 1 FROM target d WHERE d.key <=> s.key)

Whether the NOT EXISTS sees rows inserted by the SAME statement is an engine
question, not a reading question, and the answer changes what a clinic ends
up with: if it did see them, a clinic table holding two rows on one natural
key would silently lose one. The unit tests cannot answer it -- they assert
on generated SQL text -- so it stayed an open question on this feature until
somebody ran it.

They also cannot answer the follow-on question, which matters more: when the
clinic's own table holds twins on the natural key, does every source id still
get an id-map entry? Children are remapped through that map, so a source id
with no entry is a dangling foreign key.

This script answers both by building the real tables, running the real
generated statements, and asserting on rows. Findings on MariaDB 10.11.14
(2026-09-04):

* the anti-join does NOT see same-statement inserts, so a clinic's twins are
  both copied -- preserved, not deduplicated, which is the behaviour this
  feature wants (dropping one would be silent data loss);
* `idmap_statements` pairs twin n with target twin n and falls back to the
  target's first row for a surplus twin, so EVERY source id is mapped in all
  four scenarios below.

Usage (needs a MariaDB/MySQL the invoking user can create schemas on; the
scratch schemas are dropped and recreated on every run -- throwaway server
only, never a clinic's):

    python3 scripts/migration/o19/verify_sql_semantics.py \\
        --mysql-arg=--socket=/run/mysqld/mysqld.sock --mysql-arg=-uroot

## The charset repair

`repair_expr` rewrites a value that is provably double-encoded and leaves
everything else alone. Which values are "provably double-encoded" depends on
what MySQL means by `latin1`, and MySQL's latin1 is **CP1252**, not
ISO-8859-1: bytes 0x80-0x9F are printable symbols there, not C1 controls.
That distinction decides whether a clinic's accented names survive, so it is
checked against the server rather than argued about. (It also makes synthetic
test data easy to get wrong -- the first draft of this check built its
mojibake with Python's ISO-8859-1 and reported three false failures.)

Exit codes: 0 = every invariant held; 1 = at least one failed (printed);
2 = usage or connection error.
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

#: Scratch schema names reach SQL as identifiers, and this script DROPs
#: what it is given, so anything but a plain identifier is refused rather
#: than quoted -- an operator who typo-pastes a prefix should get an error,
#: not a dropped database.
IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_$]*$")

#: MariaDB refuses a schema name over 64 characters, and the scratch names
#: are the prefix plus a suffix -- `_arch` being the longest. Without this
#: a 60-character prefix fails as ERROR 1102 from the first DROP, which
#: reads like a broken server rather than a too-long argument.
MAX_PREFIX = 64 - len("_arch")


def prefix_problem(prefix: str) -> Optional[str]:
    """Return why `prefix` cannot be used as a scratch schema name, or
    None if it can."""
    if not IDENTIFIER_RE.match(prefix):
        return ("--prefix must be a plain identifier ({0} is not): this "
                "script DROPs the schemas built from it".format(prefix))
    if len(prefix) > MAX_PREFIX:
        return ("--prefix must be at most {0} characters ({1} is {2}): the "
                "scratch schemas append up to '_arch', and MariaDB refuses "
                "a schema name over 64".format(MAX_PREFIX, prefix,
                                               len(prefix)))
    return None


def reject_password_args(args):
    """Return the args that would carry a credential in argv.

    Any local user can read another process's argv, so the rest of this
    feature passes the password via MYSQL_PWD or a defaults file; the
    oracle should not be the one place that teaches the bad habit."""
    bad = []
    for a in args:
        if a.startswith("--password") or (
                a.startswith("-p") and not a.startswith("--")):
            bad.append(a.split("=")[0] if "=" in a else a[:2])
    return bad


REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "debian" / "assets"))

from carlos_ctl import (o19_preflight, o19digest,               # noqa: E402
                        o19etl, o19map_schema)

# One merge table, shaped as the manifest describes it: a surrogate integer
# PK, one natural key, one payload column. consultationServices is the
# canonical case -- merge on serviceDesc, fresh AUTO_INCREMENT ids.
TABLE = "consultationServices"
ENTRY = {
    "class": "merge",
    "merge_keys": ["serviceDesc"],
    "surrogate_pk": "serviceId",
    "cols": ["serviceId", "serviceDesc", "active"],
}
DDL = ("CREATE TABLE {0} (serviceId int(10) NOT NULL AUTO_INCREMENT, "
       "serviceDesc varchar(255) DEFAULT NULL, active char(1) DEFAULT NULL, "
       "PRIMARY KEY (serviceId));".format(TABLE))


class Client:
    """The mariadb command-line client, as a callable."""

    def __init__(self, cmd: str, args: List[str]) -> None:
        self.cmd = cmd
        self.args = args

    def run(self, sql: str, db: str = "mysql") -> Tuple[int, str, str]:
        argv = [self.cmd] + self.args + ["-N", "-B",
                                         "--default-character-set=utf8mb4",
                                         db]
        try:
            proc = subprocess.run(argv, input=sql.encode("utf-8", "replace"),
                                  stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE)
        except OSError as exc:
            # a missing or unexecutable --mysql-cmd: the documented exit 2,
            # not a traceback
            return 127, "", "cannot run {0}: {1}".format(self.cmd, exc)
        return (proc.returncode,
                proc.stdout.decode("utf-8", "replace"),
                proc.stderr.decode("utf-8", "replace").strip())

    def setup(self, sql: str, db: str = "mysql") -> None:
        """Run a fixture statement and STOP if it fails.

        A verifier that carries on after its scratch schema or its DDL
        failed measures whatever state was left behind -- and reports a
        pass or a fail that describes nothing. Exit 2 is this script's
        "could not run", distinct from 1, "an invariant broke"."""
        rc, _out, err = self.run(sql, db)
        if rc != 0:
            print("setup failed against `{0}`: {1}\n  {2}".format(
                db, err[:300], sql[:200]), file=sys.stderr)
            # 2, not 1: this script's 1 means "an invariant broke", which
            # is a FINDING. A fixture that would not build is not a
            # finding about the code -- it is this check not running.
            raise SystemExit(2)

    def rows(self, sql: str, db: str) -> List[List[str]]:
        rc, out, err = self.run(sql, db)
        if rc != 0:
            raise SystemExit("query failed: {0}\n  {1}".format(err[:400],
                                                               sql[:300]))
        return [line.split("\t") for line in out.split("\n") if line]


class Scenario:
    """One (seed rows, staging rows) pair and what must be true after."""

    def __init__(self, name: str, seed: str, stage: str, why: str) -> None:
        self.name = name
        self.seed = seed
        self.stage = stage
        self.why = why


SCENARIOS = [
    Scenario(
        "target empty, staging holds twins on the natural key",
        "", "(1,'Cardiology','1'),(2,'Cardiology','1'),(3,'Neurology','1')",
        "the clinic's twins are the clinic's data: both must land, because "
        "dropping one would be silent loss, and each must get its own id"),
    Scenario(
        "seed holds the key, staging holds twins on it",
        "(7,'Cardiology','1')",
        "(1,'Cardiology','1'),(2,'Cardiology','1'),(3,'Neurology','1')",
        "CARLOS's seed row wins; both source twins must still map, or a "
        "child row referencing either one dangles"),
    Scenario(
        "seed holds both keys, staging holds twins on one",
        "(7,'Cardiology','1'),(8,'Neurology','1')",
        "(1,'Cardiology','1'),(2,'Cardiology','1'),(3,'Neurology','1')",
        "nothing appends, and every source id maps to the seed row that "
        "took its place"),
    Scenario(
        "staging holds three twins, seed holds one row",
        "(7,'Cardiology','1')",
        "(1,'Cardiology','1'),(2,'Cardiology','1'),(4,'Cardiology','1')",
        "the surplus-twin fallback: more source twins than target rows"),
]


def check(client: Client, sc: Scenario, dst: str, src: str,
          arch: str) -> List[str]:
    """Run one scenario; return the invariant failures it produced."""
    # every setup statement is checked: a scenario measured against a stale
    # or half-built schema reports on something other than what it claims
    setup = [("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
              "DROP DATABASE IF EXISTS `{1}`; CREATE DATABASE `{1}`;"
              "DROP DATABASE IF EXISTS `{2}`; CREATE DATABASE `{2}`;"
              .format(dst, src, arch), "mysql"),
             (DDL, dst), (DDL, src)]
    if sc.seed:
        setup.append(("INSERT INTO {0} VALUES {1};".format(TABLE, sc.seed),
                      dst))
    setup.append(("INSERT INTO {0} VALUES {1};".format(TABLE, sc.stage), src))
    for stmt, db in setup:
        rc, _out, err = client.run(stmt, db)
        if rc != 0:
            raise SystemExit("scenario setup failed: {0}".format(err[:300]))

    def query(sql: str) -> List[List[str]]:
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)[TABLE]

    # counted BEFORE the merge, exactly as run_etl does: afterwards every
    # staging row has a target twin and the distinction is gone
    overridden = int(query(o19etl.merge_overridden_count_sql(
        TABLE, ENTRY, src, dst, dst_cols, arch))[0][0])
    # ...and so is the seed's own row set: read after the merge it would
    # include the rows the merge appended, and "appended" would be 0 for
    # every scenario -- which is exactly the false failure the first draft
    # of this script reported
    seed_ids = [r[0] for r in query(
        "SELECT serviceId FROM {0} ORDER BY serviceId".format(TABLE))]

    rc, _out, err = client.run(
        o19etl.merge_statement(TABLE, ENTRY, src, dst, dst_cols) + ";", dst)
    if rc != 0:
        return ["the merge statement failed: {0}".format(err[:200])]
    for stmt in o19etl.idmap_statements(TABLE, ENTRY, src, dst, arch,
                                        dst_cols):
        rc, _out, err = client.run(stmt + ";", dst)
        if rc != 0:
            return ["an id-map statement failed: {0}".format(err[:200])]

    src_ids = [r[0] for r in client.rows(
        "SELECT serviceId FROM {0} ORDER BY serviceId".format(TABLE), src)]
    pairs = query("SELECT old_id, new_id FROM `{0}`.`{1}` ORDER BY old_id"
                  .format(arch, o19etl.idmap_table(TABLE)))
    live = query("SELECT serviceId FROM {0} ORDER BY serviceId".format(TABLE))
    live_ids = {r[0] for r in live}

    failures = []
    mapped = {old for old, _new in pairs}
    missing = [i for i in src_ids if i not in mapped]
    if missing:
        failures.append(
            "source id(s) {0} have no id-map entry: a child row referencing "
            "one of them would dangle".format(missing))
    off_target = [(o, n) for o, n in pairs if n not in live_ids]
    if off_target:
        failures.append(
            "id-map entries point at rows that do not exist: {0}"
            .format(off_target))

    # the seed's own rows are never displaced or renumbered
    kept = {r[0] for r in query(
        "SELECT serviceId FROM {0} ORDER BY serviceId".format(TABLE))}
    if sc.seed and not set(seed_ids) <= kept:
        failures.append("the CARLOS seed lost a row: had {0}, now {1}"
                        .format(seed_ids, sorted(kept)))

    # every staging row is accounted for: it either appended a live row or
    # was counted as overridden
    appended = len(live) - len(seed_ids)
    if appended + overridden != len(src_ids):
        failures.append(
            "{0} staging row(s) but {1} appended + {2} reported overridden: "
            "the operator's report would not add up"
            .format(len(src_ids), appended, overridden))

    print("\n  {0}".format(sc.name))
    print("    why: {0}".format(sc.why))
    print("    target after : {0}".format([r[0] for r in live]))
    print("    id map       : {0}".format(
        ["{0}->{1}".format(o, n) for o, n in pairs]))
    print("    appended {0}, reported overridden {1}, staging rows {2}"
          .format(appended, overridden, len(src_ids)))
    for f in failures:
        print("    FAIL: {0}".format(f))
    return failures


def same_statement_visibility(client: Client, dst: str, src: str) -> str:
    """Does the anti-join see rows the same statement inserted?

    Reported rather than asserted: it is an engine property, and the point
    of running it is to record which one this server has.
    """
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
                 "DROP DATABASE IF EXISTS `{1}`; CREATE DATABASE `{1}`;"
                 .format(dst, src))
    client.setup(DDL, dst)
    client.setup(DDL, src)
    client.setup(
        "INSERT INTO {0} VALUES (1,'X','1'),(2,'X','1');".format(TABLE), src)

    def query(sql: str) -> List[List[str]]:
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)[TABLE]
    client.setup(
        o19etl.merge_statement(TABLE, ENTRY, src, dst, dst_cols) + ";", dst)
    n = int(query("SELECT COUNT(*) FROM {0}".format(TABLE))[0][0])
    return ("no  (both twins copied)" if n == 2
            else "YES (one twin was dropped)" if n == 1
            else "unexpected: {0} rows".format(n))


# Values whose UTF-8 bytes, misread as latin1, produce the mojibake a real
# OSCAR 19 accumulates. "plain ascii" is the control: nothing to repair, and
# nothing may change.
CHARSET_SAMPLES = ["Santé", "naïve Ünder", "€100", "Français œuvre",
                   "plain ascii"]


def check_charset_repair(client: Client, db: str) -> List[str]:
    """Repair the mojibake a MySQL-based O19 can produce; touch nothing else.

    The second property is the one that matters most: a repair that mangled
    correct text would be far worse than one that misses a case, because the
    text in question is patient names. Rows the predicate cannot prove are
    double-encoded pass through untouched by design.
    """
    try:
        return _charset_repair_body(client, db)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`;".format(db))


def _charset_repair_body(client: Client, db: str) -> List[str]:
    """The repair checks themselves; the caller owns the teardown."""
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
                 .format(db))
    client.setup("CREATE TABLE t (id int, v varchar(255)) "
                 "DEFAULT CHARSET=utf8mb4;", db)

    cases = []          # (id, stored, expected_after_repair, label)
    for good in CHARSET_SAMPLES:
        # what the old system stored when it read UTF-8 bytes as latin1,
        # latin1 being CP1252 -- which is what MySQL's latin1 is
        cases.append((len(cases) + 1, good.encode("utf-8").decode("cp1252"),
                      good, "mojibake"))
        cases.append((len(cases) + 1, good, good, "already correct"))
    values = ", ".join(
        "({0}, '{1}')".format(n, v.replace("\\", "\\\\").replace("'", "\\'"))
        for n, v, _w, _l in cases)
    client.setup("INSERT INTO t VALUES {0};".format(values), db)

    rows = client.rows("SELECT id, {0} FROM t s ORDER BY id"
                       .format(o19etl.repair_expr("s.`v`")), db)
    got = dict((int(r[0]), r[1]) for r in rows)
    failures = []
    print("\n  charset repair")
    for n, stored, want, label in cases:
        ok = got.get(n) == want
        print("    {0:<16} {1!r:<26} -> {2!r:<18} {3}".format(
            label, stored, got.get(n), "ok" if ok else "MISMATCH"))
        if not ok:
            failures.append(
                "{0} {1!r} became {2!r}, expected {3!r}".format(
                    label, stored, got.get(n), want))
    return failures


# --------------------------------------------------------------------------
# The content digest (M22)
# --------------------------------------------------------------------------
#
# The P2 transfer check compares a digest taken on the CLINIC's live latin1
# database against one taken on the restored utf8mb4 staging schema. Every
# claim it rests on is an engine question, so every one of them is run here
# rather than argued about.

#: One table carrying every rendering class the digest has to handle, plus
#: the two shapes that defeat a naive digest: NULLs that can shift between
#: columns, and rows that are exact duplicates of each other. Deliberately
#: WITHOUT a primary key, so the duplicate pair below is possible at all.
DIGEST_DDL = (
    "CREATE TABLE t ("
    " id int,"
    " name varchar(64),"          # text: differs in stored bytes per side
    " note text,"
    " a varchar(16),"             # the NULL-shift pair
    " b varchar(16),"
    " amount decimal(10,2),"      # HEX() would round this
    " when_at datetime,"
    " stamped timestamp NULL,"    # rendered in the SESSION's time zone
    " flags bit(8),"              # CONVERT() would collapse this
    " doc blob,"
    " opt enum('x','y'))")

#: `Santé` and `Ünter` are latin1-representable on purpose: the clinic
#: column IS latin1, so a character outside it would be a different test
#: (lossy storage) than the one intended (same text, different bytes).
DIGEST_ROWS = [
    "(1, 'Santé', 'note über', NULL, 'x', 1.40, "
    "'2020-01-02 03:04:05', '2020-06-01 12:00:00', b'11000011', "
    "0x00FF10, 'x')",
    "(2, 'Ünter', 'plain', '~', NULL, 2.50, "
    "'2021-05-06 07:08:09', '2021-07-02 13:00:00', b'10101010', "
    "0xDEADBEEF, 'y')",
    # the identical pair: BIT_XOR cancels them, so deleting BOTH leaves the
    # XOR lane unchanged and only the SUM lane notices
    "(3, 'Twin', 'same', 'k', 'v', 3.00, '2022-01-01 00:00:00', "
    "'2022-03-04 05:06:07', b'00001111', 0x01, 'x')",
    "(3, 'Twin', 'same', 'k', 'v', 3.00, '2022-01-01 00:00:00', "
    "'2022-03-04 05:06:07', b'00001111', 0x01, 'x')",
]

DIGEST_COLUMNS = [
    ("id", "int"), ("name", "varchar"), ("note", "text"),
    ("a", "varchar"), ("b", "varchar"), ("amount", "decimal"),
    ("when_at", "datetime"), ("stamped", "timestamp"),
    ("flags", "bit"), ("doc", "blob"), ("opt", "enum"),
]

#: (label, SQL applied to STAGING, why it must be caught)
DIGEST_SABOTAGE = [
    ("one character of a name changed",
     "UPDATE t SET name = 'Sante' WHERE id = 1",
     "the whole point: the row count is unchanged"),
    # spelled out rather than as `SET a = b, b = a`: MySQL assigns
    # left to right, so the swap form would set BOTH columns to 'x' and
    # the check would pass on a digest with no NULL marker at all
    ("a NULL moved to the next column",
     "UPDATE t SET a = 'x', b = NULL WHERE id = 1",
     "CONCAT_WS SKIPS NULLs, so without a NULL marker this hashes the "
     "same"),
    ("the identical pair deleted",
     "DELETE FROM t WHERE id = 3",
     "BIT_XOR cancels twins, so the XOR lane alone cannot see this"),
    ("the identical pair replaced by a different identical pair",
     "UPDATE t SET name = 'Other' WHERE id = 3",
     "the row COUNT is unchanged and the XOR lane cancels both pairs: "
     "this is the case only the SUM lane can see"),
    ("a BIT value changed",
     "UPDATE t SET flags = b'10101010' WHERE id = 1",
     "CONVERT renders both 0xC3 and 0xAA as '?', so a converted digest "
     "is blind to it"),
    ("a decimal changed by 0.10",
     "UPDATE t SET amount = 1.50 WHERE id = 1",
     "HEX() rounds a numeric to a longlong, so a hexed digest is blind "
     "to it"),
    ("a blob byte flipped",
     "UPDATE t SET doc = 0x00FF11 WHERE id = 1",
     "binary data must be hexed, not converted"),
    ("a timestamp moved by an hour",
     "UPDATE t SET stamped = '2020-06-01 13:00:00' WHERE id = 1",
     "a real change, as opposed to the same instant read in another time "
     "zone, which the pinned session makes invisible below"),
    ("a literal tilde promoted to NULL",
     "UPDATE t SET a = NULL WHERE id = 2",
     "the length prefix is what stops a literal '~' from imitating the "
     "NULL marker"),
]


def _digest_of(client: Client, db: str, sql: str) -> Tuple[str, ...]:
    return tuple(client.rows(sql, db)[0])


def _lane(hash_expr: str, db: str, lane: str) -> str:
    """One lane of the digest on its own, to show what it cannot see."""
    if lane == "xor":
        agg = "IFNULL(BIT_XOR(CONV(SUBSTR({0}, 17, 16), 16, 10)), 0)"
    else:
        agg = ("IFNULL(SUM(CAST(CONV(SUBSTR({0}, 1, 16), 16, 10) "
               "AS DECIMAL(30, 0))), 0)")
    return "SELECT {0} FROM `{1}`.`t`".format(agg.format(hash_expr), db)


def check_content_digest(client: Client, clinic: str,
                         stage: str) -> List[str]:
    """The digest agrees across latin1/utf8mb4, and catches every change
    in `DIGEST_SABOTAGE` (a count in prose is the half that rots first, so
    there is not one here).

    The first claim is the one the whole P2 chain rests on: the clinic's
    live database is latin1 and the restored staging schema is utf8mb4, so
    the SAME logical text has different STORED BYTES. A digest over the
    bytes would disagree on every accented row of every clinic -- which is
    the failure mode that gets a check switched off.

    The rest are the traps, each shown to be caught, and two of them shown
    to be MISSED by the rendering the digest deliberately does not use.
    """
    failures: List[str] = []
    try:
        return _content_digest_body(client, clinic, stage, failures)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(clinic, stage))


def _content_digest_body(client: Client, clinic: str, stage: str,
                         failures: List[str]) -> List[str]:
    """The digest checks themselves; the caller owns the teardown."""
    cols = [c for c, _t in DIGEST_COLUMNS]
    types = dict(DIGEST_COLUMNS)
    hashed = o19digest.row_hash_expr(cols, types)

    for db, charset in ((clinic, "latin1"), (stage, "utf8mb4")):
        client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                     "DEFAULT CHARSET={1};".format(db, charset))
        client.setup(DIGEST_DDL + " DEFAULT CHARSET={0};".format(charset), db)
        client.setup("INSERT INTO t VALUES {0};".format(
            ", ".join(DIGEST_ROWS)), db)

    print("\n  content digest")
    stored = client.rows(
        "SELECT HEX(name) FROM t WHERE id = 1", clinic)[0][0]
    stored_stage = client.rows(
        "SELECT HEX(name) FROM t WHERE id = 1", stage)[0][0]
    if stored == stored_stage:
        failures.append(
            "the two sides store the SAME bytes for 'Santé' ({0}), so "
            "this check cannot prove the charset normalisation does "
            "anything -- the fixture is wrong, not the code".format(stored))
    print("    {0:<44} clinic {1} / staging {2}".format(
        "'Sante' stored as", stored, stored_stage))

    clinic_sql = o19digest.digest_sql(clinic, "t", cols, types)
    stage_sql = o19digest.digest_sql(stage, "t", cols, types)
    baseline = _digest_of(client, clinic, clinic_sql)
    same = _digest_of(client, stage, stage_sql)
    ok = baseline == same
    print("    {0:<44} {1}".format(
        "latin1 and utf8mb4 agree", "ok" if ok else "MISMATCH"))
    if not ok:
        failures.append(
            "the same data digests differently across charsets: {0} vs "
            "{1}".format(baseline, same))

    for label, sql, why in DIGEST_SABOTAGE:
        client.setup("{0};".format(sql), stage)
        after = _digest_of(client, stage, stage_sql)
        caught = after != baseline
        print("    {0:<44} {1}".format(
            label, "caught" if caught else "MISSED"))
        if not caught:
            failures.append("{0} was NOT caught ({1})".format(label, why))
        # restore, and prove the restore took: a sabotage left in place
        # would make every later line meaningless
        client.setup("DROP TABLE t; " + DIGEST_DDL
                     + " DEFAULT CHARSET=utf8mb4; INSERT INTO t VALUES "
                     + ", ".join(DIGEST_ROWS) + ";", stage)
        if _digest_of(client, stage, stage_sql) != baseline:
            failures.append(
                "the fixture did not restore after '{0}'; every later "
                "line of this check is meaningless".format(label))
            break

    # -- the two renderings the digest deliberately does NOT use ---------
    # Each is shown to MISS a change the shipped digest catches. Without
    # this, "the digest caught it" would not distinguish the rendering
    # that earns its place from one that happened to work.
    for label, sql, wrong_sql, missed in (
            ("a BIT change", "UPDATE t SET flags = b'10101010' WHERE id = 1",
             stage_sql.replace("HEX(`flags`)",
                               "CONVERT(`flags` USING utf8mb4)"),
             "CONVERT on a BIT"),
            # 1.40 and 1.20 both round to the longlong 1, which is what
            # HEX() would hash; 1.50 would round to 2 and so would NOT
            # demonstrate the trap
            ("a 0.20 decimal change", "UPDATE t SET amount = 1.20 "
             "WHERE id = 1",
             stage_sql.replace("CONVERT(`amount` USING utf8mb4)",
                               "HEX(`amount`)"),
             "HEX on a DECIMAL")):
        before_wrong = _digest_of(client, stage, wrong_sql)
        client.setup("{0};".format(sql), stage)
        after_wrong = _digest_of(client, stage, wrong_sql)
        blind = before_wrong == after_wrong
        print("    {0:<44} {1}".format(
            "{0} is invisible to {1}".format(label, missed),
            "confirmed" if blind else "NOT CONFIRMED"))
        if not blind:
            failures.append(
                "{0} is visible to {1}, so this check no longer shows why "
                "the shipped rendering is the right one".format(label,
                                                                missed))
        client.setup("DROP TABLE t; " + DIGEST_DDL
                     + " DEFAULT CHARSET=utf8mb4; INSERT INTO t VALUES "
                     + ", ".join(DIGEST_ROWS) + ";", stage)

    # -- the same instant, read in another time zone ---------------------
    # A TIMESTAMP is STORED as UTC and RENDERED in the session's time
    # zone. The clinic's server and the CARLOS host are different machines
    # whose local time routinely differs, so a digest that did not pin the
    # session would disagree on every table carrying a TIMESTAMP -- on a
    # perfectly faithful transfer.
    shifted = "SET time_zone = '+05:30';\n" + stage_sql
    agrees = _digest_of(client, stage, shifted) == baseline
    print("    {0:<44} {1}".format(
        "a session in another time zone still agrees",
        "ok" if agrees else "MISMATCH"))
    if not agrees:
        failures.append(
            "the digest disagreed with itself across session time zones, "
            "so every clinic in another zone would fail the transfer check")
    # and the counter-proof: without the prelude, it would not
    unpinned = stage_sql.split(";\n", 1)[1]
    here = _digest_of(client, stage,
                      "SET time_zone = '+00:00';\n" + unpinned)
    there = _digest_of(client, stage,
                       "SET time_zone = '+05:30';\n" + unpinned)
    print("    {0:<44} {1}".format(
        "an unpinned session would NOT agree",
        "confirmed" if here != there else "NOT CONFIRMED"))
    if here == there:
        failures.append(
            "an unpinned digest agreed across time zones too, so this "
            "check no longer shows why the session is pinned (is there "
            "still a TIMESTAMP column in the fixture?)")

    # -- why the SUM lane exists -----------------------------------------
    # Replacing one identical PAIR with a different identical pair leaves
    # the row count alone AND cancels out of BIT_XOR (h^h = 0 either way).
    # Only the SUM lane moves. A deletion would not show this: the count
    # would catch that on its own.
    lanes = {}
    for when in ("before", "after"):
        lanes[when] = (
            client.rows(_lane(hashed, stage, "xor"), stage)[0][0],
            client.rows(_lane(hashed, stage, "sum"), stage)[0][0],
            client.rows("SELECT COUNT(*) FROM `{0}`.`t`".format(stage),
                        stage)[0][0])
        if when == "before":
            client.setup("UPDATE t SET name = 'Other' WHERE id = 3;", stage)
    (xor_b, sum_b, n_b), (xor_a, sum_a, n_a) = lanes["before"], lanes["after"]
    blind = xor_b == xor_a and n_b == n_a
    print("    {0:<44} {1}".format(
        "count and XOR both miss a swapped twin pair",
        "confirmed" if blind else "NOT CONFIRMED"))
    if xor_b != xor_a:
        failures.append(
            "the XOR lane DID see the swapped identical pair, so this "
            "check no longer shows why the SUM lane exists")
    if n_b != n_a:
        failures.append(
            "the row count changed, so this is not the count-blind case "
            "the SUM lane is there for")
    if sum_b == sum_a:
        failures.append(
            "the SUM lane did not see the swapped identical pair -- the "
            "one thing it is there for")

    failures.extend(_oversized_value_body(client, stage))
    return failures


#: the format-1 rendering of a large value: the raw HEX/CONVERT inside
#: the length-prefixed CONCAT, which is what collapsed to NULL under a
#: 16M max_allowed_packet
FORMAT_1_LARGE = (
    ("SHA2(HEX(`doc`), 256)", "HEX(`doc`)"),
    ("SHA2(CONVERT(`note` USING utf8mb4), 256)",
     "CONVERT(`note` USING utf8mb4)"),
)


def _oversized_value_body(client: Client, stage: str) -> List[str]:
    """A value the server's `max_allowed_packet` cannot hold in a CONCAT.

    CONCAT and CONCAT_WS return NULL -- warning 1301, not an error --
    when their result would exceed the setting, and the clinic's server
    (stock 16M) and the CARLOS host (packaged 1G) do not share it. This
    runs the shipped digest over two 8.4 MB documents (16.8 MB as HEX)
    and a 9 MB accented TEXT under BOTH settings and requires the same
    answer with nothing unhashed; then runs the format-1 rendering the
    same way and requires it to DISAGREE with itself, which is the defect
    reproduced; then breaks the row join on purpose and requires the
    fourth lane to count every row, which is what makes a row nobody
    hashed visible.

    Needs SUPER to move the global; a server that refuses is reported as
    skipped, not as passed."""
    failures: List[str] = []
    print("\n  a value larger than max_allowed_packet")
    prior = client.rows("SELECT @@global.max_allowed_packet", stage)[0][0]
    rc, _out, err = client.run("SET GLOBAL max_allowed_packet = 16777216;")
    if rc != 0:
        # NOT a silent skip: this script's OK line claims the packet
        # invariant was tested, and a check that did not run cannot back
        # that claim. Recorded like the end-to-end chain's skipped path.
        print("    {0:<44} NOT RUN ({1})".format(
            "cannot move max_allowed_packet", err[:60]))
        return ["the oversized-value check did not run: this server "
                "refused SET GLOBAL max_allowed_packet ({0}). Re-run "
                "against a throwaway server where the client has SUPER."
                .format(err[:200])]
    # everything from here runs inside the restore: the global is already
    # moved, so ANY exit before the finally leaves this shared server at
    # 16M for whatever runs next -- including the format-control guard
    # below, which returns early when the shipped digest is respelled
    try:
        cols = ["id", "doc", "note"]
        types = {"id": "int", "doc": "mediumblob", "note": "mediumtext"}
        sql = o19digest.digest_sql(stage, "big", cols, types)
        old = sql
        for new_form, old_form in FORMAT_1_LARGE:
            if new_form not in old:
                return ["the shipped digest no longer spells {0}; the "
                        "format-1 control cannot be built".format(new_form)]
            old = old.replace(new_form, old_form)
        # CONCAT(NULL, ...) is NULL: every row hash NULL, so the lane
        # must count every row
        broken = sql.replace("SHA2(CONCAT(", "SHA2(CONCAT(NULL, ")
        client.setup(
            "DROP TABLE IF EXISTS big; CREATE TABLE big (id int, doc "
            "mediumblob, note mediumtext) DEFAULT CHARSET=latin1; "
            "INSERT INTO big VALUES "
            "(1, REPEAT(X'AB', 8400000), REPEAT(X'E9', 9000000)), "
            "(2, REPEAT(X'AC', 8400000), REPEAT(X'E9', 9000000)), "
            "(3, X'01', 'small');", stage)
        at_16m = _digest_of(client, stage, sql)
        old_16m = _digest_of(client, stage, old)
        broken_16m = _digest_of(client, stage, broken)
        client.setup("SET GLOBAL max_allowed_packet = 1073741824;", stage)
        at_1g = _digest_of(client, stage, sql)
        old_1g = _digest_of(client, stage, old)
    finally:
        client.run("SET GLOBAL max_allowed_packet = {0};".format(prior))
        client.run("DROP TABLE IF EXISTS big;", stage)
    same = at_16m == at_1g
    print("    {0:<44} {1}".format(
        "the digest agrees under 16M and 1G",
        "ok" if same else "DIFFERS ({0} vs {1})".format(at_16m, at_1g)))
    if not same:
        failures.append("the digest of an oversized value depends on the "
                        "server's max_allowed_packet: {0} vs {1}".format(
                            at_16m, at_1g))
    whole = at_16m[3] == "0" and at_1g[3] == "0" and at_16m[0] == "3"
    print("    {0:<44} {1}".format(
        "and every row was hashed",
        "ok" if whole else "NOT ({0})".format(at_16m)))
    if not whole:
        failures.append("rows went unhashed on an oversized value: {0}"
                        .format(at_16m))
    reproduced = old_16m != old_1g
    print("    {0:<44} {1}".format(
        "control: the format-1 rendering does not",
        "reproduced" if reproduced else "NOT REPRODUCED"))
    if not reproduced:
        failures.append("the format-1 rendering agreed across settings, so "
                        "this check no longer shows the defect it guards "
                        "against (is the fixture still over 16M as HEX?)")
    counted = broken_16m[3] == "3"
    print("    {0:<44} {1}".format(
        "a NULL row hash is counted, not skipped",
        "ok" if counted else "MISSED ({0})".format(broken_16m)))
    if not counted:
        failures.append("the unhashed lane did not count NULL row hashes: "
                        "{0}".format(broken_16m))
    return failures


def check_end_to_end_transfer(client: Client, mysql_args: List[str],
                              clinic: str, stage: str) -> List[str]:
    """The whole P2 chain, with the real tools rather than fakes.

    The clinic measures its own database with `o19_preflight.py`; the
    database is dumped, restored into a staging schema, and compared
    against those numbers by `o19digest.compare_document`. The unit
    tests drive both halves against canned answers, which cannot catch a
    disagreement about what the two halves MEAN -- a column order taken
    one way here and another there, a type the clinic renders one way and
    the import the other, a dump that carries a charset neither expected.

    mysqldump is invoked exactly as docs/o19-import-deb.md tells the
    clinic to invoke it.
    """
    dump_cmd = shutil.which("mysqldump") or shutil.which("mariadb-dump")
    if not dump_cmd:
        print("\n  end-to-end transfer: SKIPPED (no mysqldump on PATH)")
        return ["end-to-end transfer check skipped: no mysqldump on PATH "
                "-- this run did not exercise the P2 chain"]

    failures: List[str] = []
    try:
        return _end_to_end_body(client, mysql_args, clinic, stage,
                                dump_cmd, failures)
    finally:
        # every exit path, including the early returns for a failed dump
        # or restore: a verifier that leaks scratch schemas makes the
        # next run measure whatever the last one left behind
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(clinic, stage))


def _end_to_end_body(client: Client, mysql_args: List[str], clinic: str,
                     stage: str, dump_cmd: str,
                     failures: List[str]) -> List[str]:
    """The chain itself; `check_end_to_end_transfer` owns the teardown."""
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=latin1;".format(clinic))
    client.setup(DIGEST_DDL + " DEFAULT CHARSET=latin1;", clinic)
    client.setup("INSERT INTO t VALUES {0};".format(
        ", ".join(DIGEST_ROWS)), clinic)
    # a second table with no explicit charset: mysqldump resolves it, and
    # whether it does is the difference between the restore matching and
    # not
    client.setup("CREATE TABLE u (id int, label varchar(32));", clinic)
    client.setup("INSERT INTO u VALUES (1, 'Café'), (2, NULL);", clinic)

    def query(sql):
        return client.rows(sql, clinic)

    document = o19_preflight.collect_digests(
        query, "'{0}'".format(clinic),
        o19_preflight.base_table_names(query, "'{0}'".format(clinic)),
        db_name=clinic)

    print("\n  end-to-end transfer (clinic -> dump -> restore -> compare)")
    if document["errors"]:
        failures.append("the clinic could not measure {0}"
                        .format(sorted(document["errors"])))
    dumped = subprocess.run(
        [dump_cmd] + mysql_args + ["--single-transaction", "--quick",
                                   "--skip-triggers", clinic],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if dumped.returncode != 0:
        print("    mysqldump failed: {0}".format(
            dumped.stderr.decode("utf-8", "replace")[:200]))
        return failures + ["mysqldump failed, so the chain was not run"]

    def restore():
        """Feed the dump to the client as BYTES.

        Not as text: mysqldump writes binary literals raw, so 0xFF inside
        a BLOB or a BIT is not valid UTF-8, and decoding the dump on the
        way through replaces it with U+FFFD. The first draft of this
        check did exactly that and reported a faithful restore as
        corrupt. `o19import._stream_dump` streams bytes for the same
        reason; this is the check catching its own harness, not the
        importer."""
        client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
                     .format(stage))
        proc = subprocess.run(
            [client.cmd] + client.args + ["--default-character-set=utf8mb4",
                                          stage],
            input=dumped.stdout, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE)
        return proc.returncode, proc.stderr.decode("utf-8", "replace")

    rc, err = restore()
    if rc != 0:
        return failures + ["restore failed: {0}".format(err[:200])]

    def staging_columns():
        out: Dict[str, List[Tuple[str, str]]] = {}
        for row in client.rows(
                "SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE "
                "FROM information_schema.COLUMNS c JOIN "
                "information_schema.TABLES tt ON tt.TABLE_SCHEMA = "
                "c.TABLE_SCHEMA AND tt.TABLE_NAME = c.TABLE_NAME "
                "WHERE c.TABLE_SCHEMA = '{0}' AND tt.TABLE_TYPE = "
                "'BASE TABLE' ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION"
                .format(stage), stage):
            out.setdefault(row[0], []).append((row[1], row[2]))
        return out

    def run_digest(sql):
        return o19digest.Digest.from_row(client.rows(sql, stage)[0])

    def compare_now():
        return o19digest.compare_document(document, staging_columns(),
                                          run_digest, schema=stage)

    result = compare_now()
    ok = (sorted(result.verified) == ["t", "u"] and not result.failed
          and not result.unverified)
    print("    {0:<44} {1}".format("a faithful restore verifies",
                                   "ok" if ok else "MISMATCH"))
    if not ok:
        failures.append(
            "a faithful restore did not verify: {0}; failed={1}; "
            "unverified={2}".format(result.summary(), result.failed,
                                    result.unverified))

    for label, sql in (("one accented character changed",
                        "UPDATE u SET label = 'Cafe' WHERE id = 1"),
                       ("a row dropped", "DELETE FROM t WHERE id = 1"),
                       ("a column dropped", "ALTER TABLE u DROP COLUMN "
                                            "label")):
        client.setup("{0};".format(sql), stage)
        broken = compare_now()
        caught = bool(broken.failed)
        print("    {0:<44} {1}".format(
            label, "caught" if caught else "MISSED"))
        if not caught:
            failures.append(
                "{0} was not caught by the end-to-end comparison".format(
                    label))
        restore()
        if compare_now().failed:
            failures.append(
                "the restore did not come back after '{0}'; every later "
                "line is meaningless".format(label))
            break

    return failures


def check_preserved_copy(client: Client, src: str, arch: str) -> List[str]:
    """The premise `preserved_content_parity` rests on, run rather than
    assumed.

    That check compares a staging table against its preserved copies by
    DIGEST and treats any difference in column list, order or type as a
    finding. That is only reasonable if `o19etl.archive_statements` --
    `CREATE TABLE ... LIKE` plus `INSERT ... SELECT *` -- really does
    produce an identical shape holding identical values, for the awkward
    columns too: a latin1 text column copied into a schema whose default
    charset is utf8mb4, a BLOB, a BIT, a zero date. If it did not, the
    check would false-alarm on every clinic, which is worse than not
    having it.
    """
    try:
        return _preserved_copy_body(client, src, arch)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(src, arch))


def _preserved_copy_body(client: Client, src: str, arch: str) -> List[str]:
    """The checks themselves; the caller owns the teardown."""
    failures: List[str] = []
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=latin1;".format(src))
    # the archive schema is created without a charset, so it takes the
    # SERVER's default (utf8mb4 here) -- which is the case that would
    # break a shape or value comparison if CREATE TABLE ... LIKE did not
    # carry the source table's own charset across
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
                 .format(arch))
    client.setup(DIGEST_DDL + " DEFAULT CHARSET=latin1;", src)
    client.setup("INSERT INTO t VALUES {0};".format(
        ", ".join(DIGEST_ROWS)), src)

    print("\n  preserved copy (archive_statements)")
    for stmt in o19etl.archive_statements("t", src, arch):
        client.setup(stmt + ";", arch)

    cols = [c for c, _t in DIGEST_COLUMNS]
    types = dict(DIGEST_COLUMNS)
    before = _digest_of(client, src,
                        o19digest.digest_sql(src, "t", cols, types))
    after = _digest_of(client, arch,
                       o19digest.digest_sql(arch, "t", cols, types))
    ok = before == after
    print("    {0:<44} {1}".format(
        "the archive copy is value-for-value equal",
        "ok" if ok else "MISMATCH"))
    if not ok:
        failures.append(
            "archive_statements produced a copy that does not digest "
            "equal to its source ({0} vs {1}) -- preserved_content_"
            "parity would false-alarm on every clinic".format(before,
                                                              after))

    shape = _column_shape(client, src, "t"), _column_shape(client, arch, "t")
    same_shape = shape[0] == shape[1]
    print("    {0:<44} {1}".format(
        "and identical in column list, order and type",
        "ok" if same_shape else "MISMATCH"))
    if not same_shape:
        failures.append(
            "CREATE TABLE ... LIKE did not reproduce the source shape: "
            "{0} vs {1}".format(shape[0], shape[1]))

    client.setup("UPDATE t SET name = 'Sante' WHERE id = 1;", arch)
    caught = _digest_of(client, arch,
                        o19digest.digest_sql(arch, "t", cols, types)) != before
    print("    {0:<44} {1}".format(
        "a corrupted archive copy is caught",
        "caught" if caught else "MISSED"))
    if not caught:
        failures.append("a changed value in the archive copy was not "
                        "caught by the digest")
    return failures


def _column_shape(client: Client, db: str, table: str) -> List[List[str]]:
    """(column, DATA_TYPE, COLUMN_TYPE, charset) in ordinal order."""
    return client.rows(
        "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, "
        "IFNULL(CHARACTER_SET_NAME, '-') FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = '{0}' AND TABLE_NAME = '{1}' "
        "ORDER BY ORDINAL_POSITION".format(db, table), db)


#: One copy table shaped like the manifest's: an id-intact primary key, a
#: renamed column, a zero-dateable date, an enum whose set narrowed, and a
#: latin1 text column the repair may touch.
COPY_DDL = (
    "CREATE TABLE c ("
    " id int NOT NULL,"
    " isActive varchar(8),"       # renamed to `isactive` on the target
    " d date,"                    # '0000-00-00' -> NULL on a nullable target
    " e varchar(8),"              # source is wider than the target's enum
    " amount decimal(10,2),"      # the target holds DECIMAL(10,4)
    " note varchar(64),"
    " PRIMARY KEY (id))")

TARGET_DDL = (
    "CREATE TABLE c ("
    " id int NOT NULL,"
    " isactive varchar(8),"
    " d date,"
    " e enum('a','b'),"
    " amount decimal(10,4),"
    " note varchar(64),"
    " PRIMARY KEY (id))")

COPY_ROWS = [
    "(1, 'Y', '2020-01-02', 'a', 1.40, 'Santé')",
    "(2, 'N', '0000-00-00', 'z', 2.50, 'plain')",   # zero date + out-of-set
    "(3, NULL, NULL, NULL, NULL, NULL)",            # every column NULL
]

COPY_ENTRY = {
    "class": "copy",
    "cols": ["id", "isactive", "d", "e", "amount", "note"],
    "renames": {"isactive": "isActive"},
}

#: (label, SQL applied to the TARGET, the id(s) it changed, why it must
#: be caught). The ids are what makes the key projection checkable: the
#: private details file is only useful if it names the rows that
#: actually differ, and a projection that named a DIFFERENT set would be
#: worse than one that named none.
COPY_SABOTAGE = [
    # accents lost, which is what a latin1 -> utf8mb4 migration produces
    # when it goes wrong -- and what MariaDB's DEFAULT utf8mb4_general_ci
    # collation calls equal ('Santé' = 'Sante' is 1 under it, as is
    # 'SMITH' = 'smith'). A plain `<=>` here reported a faithful copy.
    ("a copied value lost its accent",
     "UPDATE c SET note = 'Sante' WHERE id = 1", {"1"},
     "the row count is unchanged, every parity check still passes, and a "
     "case/accent-insensitive comparison calls the two equal"),
    ("a copied value changed case",
     "UPDATE c SET isactive = 'y' WHERE id = 1", {"1"},
     "the same collation blindness, in the other direction"),
    ("a NULL where a value was copied",
     "UPDATE c SET isactive = NULL WHERE id = 1", {"1"},
     "`=` would miss this on the NULL rows; `<=>` does not"),
    ("a value where a NULL was copied",
     "UPDATE c SET note = 'x' WHERE id = 3", {"3"},
     "the all-NULL row is the one a careless comparison skips"),
    ("the sanitized zero date un-sanitized",
     "UPDATE c SET d = '2001-01-01' WHERE id = 2", {"2"},
     "the copy wrote NULL there; anything else is not what it wrote"),
    ("the enum fallback overridden",
     "UPDATE c SET e = 'b' WHERE id = 2", {"2"},
     "the copy folded an out-of-set value to the column's fallback"),
]


def check_copy_values(client: Client, src: str, dst: str) -> List[str]:
    """`copy_content_parity`'s claim, against the engine.

    The unit tests assert on generated SQL. They cannot answer the
    question this check exists for -- does the comparison agree with the
    copy on a FAITHFUL run? -- because that depends on how the server
    stores and compares what the copy wrote. The two most likely ways to
    get a false alarm are both present in the fixture: a DECIMAL whose
    scale WIDENS on the target (1.40 stored as 1.4000), and rows that are
    entirely NULL.
    """
    try:
        return _copy_values_body(client, src, dst)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(src, dst))


def _copy_values_body(client: Client, src: str, dst: str) -> List[str]:
    """The checks themselves; the caller owns the teardown."""
    failures: List[str] = []
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=latin1;".format(src))
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=utf8mb4;".format(dst))
    # sql_mode='' is what the ETL's executor runs under: a zero date and
    # an out-of-set enum have to be STORABLE in staging for the copy to
    # have anything to sanitize
    client.setup("SET SESSION sql_mode='';" + COPY_DDL + ";", src)
    client.setup("SET SESSION sql_mode='';INSERT INTO c VALUES {0};"
                 .format(", ".join(COPY_ROWS)), src)
    client.setup(TARGET_DDL + ";", dst)

    def query(sql):
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)["c"]
    client.setup("SET SESSION sql_mode='';"
                 + o19etl.copy_statement("c", COPY_ENTRY, src, dst,
                                         dst_cols) + ";", dst)

    check = o19etl.copy_value_mismatch_sql("c", COPY_ENTRY, src, dst,
                                           dst_cols, ("id",))

    def mismatches() -> int:
        return int(client.rows(check, dst)[0][0])

    print("\n  copy values (copy_value_mismatch_sql)")
    n = mismatches()
    print("    {0:<44} {1}".format(
        "a faithful copy shows no mismatch",
        "ok" if n == 0 else "{0} FALSE ALARM(S)".format(n)))
    if n:
        failures.append(
            "the check disagreed with the copy on a faithful run ({0} "
            "row(s)) -- it would fail every clinic".format(n))
        return failures

    # the private details file names rows by running the SAME statement
    # with the key columns in place of COUNT(*). The claim below is not
    # that it returns the same NUMBER of rows -- with an identical
    # FROM/JOIN/WHERE it could hardly do otherwise -- but that the keys
    # it returns are the keys of the rows the sabotage actually touched.
    # An operator sent to the wrong rows is worse off than one sent to
    # none, so this is checked against the engine rather than reasoned
    # about.
    named = o19etl.copy_value_mismatch_sql(
        "c", COPY_ENTRY, src, dst, dst_cols, ("id",),
        select=o19etl.key_projection(("id",), "d"))

    for label, sql, ids, why in COPY_SABOTAGE:
        client.setup("SET SESSION sql_mode='';{0};".format(sql), dst)
        caught = mismatches() > 0
        print("    {0:<44} {1}".format(
            label, "caught" if caught else "MISSED"))
        if not caught:
            failures.append("{0} was NOT caught ({1})".format(label, why))
        else:
            keys = {r[0] for r in client.rows(named, dst)}
            if keys != ids:
                failures.append(
                    "'{0}': the sabotage changed id(s) {1} but the key "
                    "projection named {2} -- the details file would "
                    "point an operator at the wrong rows".format(
                        label, ", ".join(sorted(ids)),
                        ", ".join(sorted(keys)) or "nothing"))
        client.setup("SET SESSION sql_mode='';TRUNCATE TABLE c;", dst)
        client.setup("SET SESSION sql_mode='';"
                     + o19etl.copy_statement("c", COPY_ENTRY, src, dst,
                                             dst_cols) + ";", dst)
        if mismatches():
            failures.append(
                "the copy did not come back after '{0}'; every later line "
                "is meaningless".format(label))
            break
    return failures


MERGE_SRC_DDL = (
    "CREATE TABLE m ("
    " id int NOT NULL AUTO_INCREMENT,"
    " status varchar(32),"
    " descr varchar(64),"
    " note varchar(64),"          # unmapped on the target -> archived
    " PRIMARY KEY (id))")

MERGE_DST_DDL = (
    "CREATE TABLE m ("
    " id int NOT NULL AUTO_INCREMENT,"
    " status varchar(32),"
    " descr varchar(64),"
    " import_archived_note varchar(64),"
    " PRIMARY KEY (id))")

#: the CARLOS seed: one row whose natural key the clinic also uses
MERGE_SEED_ROWS = "(1, 'booked', 'Booked', NULL)"

MERGE_STAGE_ROWS = [
    # loses to the seed on 'booked'; its `note` must survive as the seed
    # row's import_archived_note (requirement B for the merge class)
    "(1, 'booked', 'Clinic booked', 'clinic note')",
    # appends, and carries an accent the target's default collation would
    # call equal to its unaccented spelling
    "(2, 'cancelled', 'Annulé', 'n2')",
    "(3, 'noshow', NULL, NULL)",
]

MERGE_ENTRY = {
    "class": "merge",
    "merge_keys": ["status"],
    "surrogate_pk": "id",
    "cols": ["id", "status", "descr", "import_archived_note"],
    "archived_cols": {"import_archived_note": "note"},
    "renames": {"import_archived_note": "note"},
}

#: (label, SQL applied to the TARGET, which claim must catch it, why)
MERGE_SABOTAGE = [
    ("a seed row was edited by the import",
     "UPDATE m SET descr = 'Clinic booked' WHERE id = 1", "seed",
     "the merge's whole policy is that CARLOS's row WINS; overwriting it "
     "with the clinic's value moves the same number of rows"),
    ("a seed row was deleted by the import",
     "DELETE FROM m WHERE id = 1", "seed",
     "an inner join would forgive this silently"),
    ("an appended row lost its accent",
     "UPDATE m SET descr = 'Annule' WHERE id = 2", "appended",
     "utf8mb4_general_ci calls the two equal, so a plain <=> passes"),
    ("an appended row changed case",
     "UPDATE m SET status = 'Cancelled' WHERE id = 2", "appended",
     "the same collation blindness, on the natural key itself"),
    ("an appended NULL became a value",
     "UPDATE m SET descr = 'x' WHERE id = 3", "appended",
     "the all-NULL payload is the row a careless comparison skips"),
    ("the archived backfill was lost",
     "UPDATE m SET import_archived_note = NULL WHERE id = 1", "backfill",
     "the clinic's dropped column would then exist nowhere once staging "
     "is dropped, which is exactly what requirement B forbids"),
]


#: The second merge SHAPE the manifest produces: no surrogate id, so
#: the natural key IS the primary key (the generator refuses any other
#: shape), and a `merge_exclude` predicate naming rows the insert must
#: not carry. It exercises a different pairing branch of
#: `merge_appended_mismatch_sql` -- the natural-key join instead of the
#: id map -- and the exclusion clause both it and the backfill check
#: append. The surrogate fixture above cannot reach either.
NATURAL_SRC_DDL = (
    "CREATE TABLE n ("
    " objectName varchar(64) NOT NULL,"
    " privilege varchar(16),"
    " note varchar(64),"          # unmapped -> archived
    " PRIMARY KEY (objectName))")

NATURAL_DST_DDL = (
    "CREATE TABLE n ("
    " objectName varchar(64) NOT NULL,"
    " privilege varchar(16),"
    " import_archived_note varchar(64),"
    " PRIMARY KEY (objectName))")

NATURAL_SEED_ROWS = "('_demographic', 'rw', NULL)"

NATURAL_STAGE_ROWS = [
    # loses to the seed; its note must reach the seed row's archived col
    "('_demographic', 'r', 'clinic note')",
    # appends, with an accent the target's collation would fold away
    "('_résumé', 'rw', 'n2')",
    # excluded by merge_exclude: the insert never carried it, so no
    # claim may be made about it
    "('_caisi_gone', 'rw', 'n3')",
]

NATURAL_ENTRY = {
    "class": "merge",
    "merge_keys": ["objectName"],
    "cols": ["objectName", "privilege", "import_archived_note"],
    "archived_cols": {"import_archived_note": "note"},
    "renames": {"import_archived_note": "note"},
    "merge_exclude": "s.`objectName` LIKE '\\_caisi\\_%'",
}

#: (label, SQL applied to the TARGET, which claim must catch it, why)
NATURAL_SABOTAGE = [
    ("a seed row was edited by the import",
     "UPDATE n SET privilege = 'r' WHERE objectName = '_demographic'",
     "seed", "the seed wins on a shared natural key, by policy"),
    ("an appended row lost its accent",
     "UPDATE n SET objectName = '_resume' WHERE objectName = '_résumé'",
     "appended",
     "the natural key IS the pairing here, so a folded accent breaks "
     "the join rather than a comparison -- and must still be reported"),
    ("the archived backfill was lost",
     "UPDATE n SET import_archived_note = NULL WHERE objectName = "
     "'_demographic'", "backfill",
     "requirement B for a natural-key merge table"),
]


class MergeShape:
    """One merge fixture: the manifest shape, its tables, and the
    sabotages each of the three claims must catch on it."""

    def __init__(self, name, table, src_ddl, dst_ddl, seed, stage,
                 entry, key, seed_cols, live, sabotage):
        self.name = name
        self.table = table
        self.src_ddl = src_ddl
        self.dst_ddl = dst_ddl
        self.seed = seed
        self.stage = stage
        self.entry = entry
        self.key = key
        self.seed_cols = seed_cols
        self.live = live            # live rows a correct merge leaves
        self.sabotage = sabotage


MERGE_SHAPES = [
    MergeShape(
        "surrogate id, one natural key", "m",
        MERGE_SRC_DDL, MERGE_DST_DDL, MERGE_SEED_ROWS, MERGE_STAGE_ROWS,
        MERGE_ENTRY, ("id",), ["id", "status", "descr"], 3,
        MERGE_SABOTAGE),
    MergeShape(
        "natural primary key, merge_exclude", "n",
        NATURAL_SRC_DDL, NATURAL_DST_DDL, NATURAL_SEED_ROWS,
        NATURAL_STAGE_ROWS, NATURAL_ENTRY, ("objectName",),
        ["objectName", "privilege"], 2, NATURAL_SABOTAGE),
]


def check_merge_values(client: Client, src: str, dst: str,
                       arch: str) -> List[str]:
    """`merge_content_parity`'s three claims, against the engine, on
    every merge SHAPE the manifest produces.

    The unit tests assert on generated SQL against a fake. They cannot
    answer the question this check exists for: after the REAL merge --
    anti-join, archived backfill, id map and pre-merge snapshot, run in
    the order `etl_merge_table` runs them -- do the three checks agree
    that a faithful merge is faithful? A check that false-alarms on
    every clinic is worse than no check, and the ways to get one are all
    in the fixtures: a seed row whose archived column the backfill
    writes AFTER the snapshot, an appended row whose id AUTO_INCREMENT
    chose, a payload that is entirely NULL, and a `merge_exclude` row
    the insert never carried.

    Two shapes, because they take different code paths: the surrogate
    fixture pairs appended rows through the ID MAP, the natural-key one
    through the key itself, and only the second exercises the exclusion
    clause.
    """
    try:
        failures = []
        for shape in MERGE_SHAPES:
            failures.extend(_merge_shape_body(client, src, dst, arch,
                                              shape))
        return failures
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF EXISTS "
                   "`{1}`; DROP DATABASE IF EXISTS `{2}`;".format(
                       src, dst, arch))


def _merge_shape_body(client: Client, src: str, dst: str, arch: str,
                      shape: MergeShape) -> List[str]:
    """One shape's three claims; the caller owns the teardown."""
    failures: List[str] = []
    t = shape.table
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=latin1;".format(src))
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=utf8mb4;".format(dst))
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=utf8mb4;".format(arch))
    client.setup(shape.src_ddl + ";", src)
    client.setup("INSERT INTO {0} VALUES {1};".format(
        t, ", ".join(shape.stage)), src)
    client.setup(shape.dst_ddl + ";", dst)

    def query(sql):
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)[t]

    def rebuild() -> None:
        """The target as the ETL leaves it: seeded, snapshotted, merged,
        backfilled, mapped -- in that order, because the snapshot must
        predate the insert and the backfill must follow it."""
        client.setup("TRUNCATE TABLE {0}; INSERT INTO {0} VALUES {1};"
                     .format(t, shape.seed), dst)
        for sql in o19etl.archive_statements(
                t, dst, arch, o19etl.preseed_table(t)):
            client.setup(sql + ";", dst)
        client.setup(o19etl.merge_statement(t, shape.entry, src, dst,
                                            dst_cols) + ";", dst)
        client.setup(o19etl.archived_backfill_statement(
            t, shape.entry, src, dst, dst_cols) + ";", dst)
        for sql in o19etl.idmap_statements(t, shape.entry, src, dst,
                                           arch, dst_cols):
            client.setup(sql + ";", dst)

    rebuild()
    claims = {
        "seed": o19etl.merge_seed_change_sql(
            t, dst, arch, dst_cols, shape.key, shape.seed_cols),
        "appended": o19etl.merge_appended_mismatch_sql(
            t, shape.entry, src, dst, arch, dst_cols, shape.key),
        "backfill": o19etl.merge_backfill_mismatch_sql(
            t, shape.entry, src, dst, arch, shape.key, dst_cols),
    }

    def findings(claim: str) -> int:
        return int(client.rows(claims[claim], dst)[0][0])

    print("\n  merge values ({0})".format(shape.name))
    # the merge must have actually done something, or every "no
    # mismatch" below is a statement about an empty result set
    live = client.rows("SELECT COUNT(*) FROM {0}".format(t), dst)[0][0]
    print("    {0:<44} {1}".format("the fixture merged", "{0} live row(s)"
                                   .format(live)))
    if int(live) != shape.live:
        return ["{0}: the fixture did not merge as expected ({1} live "
                "rows, expected {2}) -- every line below is meaningless"
                .format(shape.name, live, shape.live)]
    for claim in ("seed", "appended", "backfill"):
        n = findings(claim)
        print("    {0:<44} {1}".format(
            "a faithful merge: {0}".format(claim),
            "ok" if n == 0 else "{0} FALSE ALARM(S)".format(n)))
        if n:
            failures.append(
                "{0}: the {1} check disagreed with the merge on a "
                "faithful run ({2} row(s)) -- it would fail every "
                "clinic".format(shape.name, claim, n))
    if failures:
        return failures

    for label, sql, claim, why in shape.sabotage:
        client.setup(sql + ";", dst)
        caught = findings(claim) > 0
        print("    {0:<44} {1}".format(
            label, "caught" if caught else "MISSED"))
        if not caught:
            failures.append("{0}: {1} was NOT caught by the {2} check "
                            "({3})".format(shape.name, label, claim, why))
        rebuild()
        if any(findings(c) for c in claims):
            failures.append(
                "{0}: the merge did not come back after '{1}'; every "
                "later line is meaningless".format(shape.name, label))
            break
    return failures


# ---------------------------------------------------------------------------
# a copied id into a merged parent -- by NAME
# ---------------------------------------------------------------------------

#: `consentType` as OSCAR 19 ships it and as CARLOS seeds it, and `Consent`
#: reduced to the three columns the claim is about. The defect this proves
#: fixed: consentType was ruled `reference` while Consent.consent_type_id
#: was copied raw, and the two seeds disagree on id 1 -- so a clinic's
#: integrator consent arrived filed as CARLOS's demonstration consent, and
#: P7 passed because the value 1 was copied faithfully.
FK_PARENT_SRC_DDL = (
    "CREATE TABLE consentType ("
    " id int NOT NULL AUTO_INCREMENT,"
    " type varchar(50),"
    " name varchar(100),"
    " description text,"
    " active tinyint,"
    " PRIMARY KEY (id))")

FK_PARENT_DST_DDL = (
    "CREATE TABLE consentType ("
    " id int NOT NULL AUTO_INCREMENT,"
    " type varchar(50),"
    " name varchar(100),"
    " description text,"
    " active tinyint,"
    " providerNo varchar(6),"        # CARLOS additions, never filled
    " remoteEnabled tinyint,"
    " PRIMARY KEY (id))")

FK_CHILD_SRC_DDL = (
    "CREATE TABLE Consent ("
    " id int NOT NULL AUTO_INCREMENT,"
    " demographic_no int,"
    " consent_type_id int,"
    " PRIMARY KEY (id))")
FK_CHILD_DST_DDL = FK_CHILD_SRC_DDL

#: CARLOS's two seed rows, verbatim from on/V1.0.2__on_data.sql
FK_PARENT_SEED = (
    "(1, 'default_consent_entry', 'Demonstraton Consent', 'demo', 0, NULL, "
    "NULL), (2, 'electronic_communication_consent', 'Electronic "
    "Communication Consent', 'e-comm', 1, NULL, NULL)")

FK_PARENT_STAGE = [
    # O19's stock row: id 1 on ITS side, no twin on ours -> appended (id 3)
    "(1, 'integrator_patient_consent', 'Sunshiner frailty network', "
    "'integrator sharing', 1)",
    # collides with the seed on `type`: the seed wins, id 2 maps to 2
    "(2, 'electronic_communication_consent', 'Clinic e-comm', 'x', 1)",
    # clinic-added -> appended (id 4)
    "(3, 'research_registry', 'Research registry', 'x', 1)",
]

#: demographic -> clinic consent type; 103 references a type that never
#: existed (a dangling O19 reference)
FK_CHILD_STAGE = ["(1, 100, 1)", "(2, 101, 2)", "(3, 102, 3)",
                  "(4, 103, 9)"]

#: the shipped entries' SHAPE, hard-coded so the pin test in
#: test_generator.py detects drift rather than restating it
FK_PARENT_ENTRY = {
    "class": "merge",
    "merge_keys": ["type"],
    "surrogate_pk": "id",
    "cols": ["id", "type", "name", "description", "active"],
}
FK_CHILD_ENTRY = {
    "class": "copy",
    # the real entry copies nine columns; the claim is about one
    "cols": ["id", "demographic_no", "consent_type_id"],
    "fk_remap": {"consent_type_id": "consentType"},
}


def check_fk_remap_by_name(client: Client, src: str, dst: str,
                           arch: str) -> List[str]:
    """A copied id into a merged parent lands on the right row, judged by
    the row's NAME after the real merge + id map + copy.

    This is the claim P7 cannot make. Its value checks verify that the
    target holds what the manifest's expressions produced -- and for a
    raw copy that is the raw id, faithfully. The negative control below
    reproduces the original defect and shows `copy_value_mismatch_sql`
    returning 0 on it: a correct copy of a now-wrong reference is invisible
    downstream, which is why the guard lives in the generator.
    """
    try:
        return _fk_remap_body(client, src, dst, arch)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF EXISTS "
                   "`{1}`; DROP DATABASE IF EXISTS `{2}`;".format(
                       src, dst, arch))


def _fk_remap_body(client: Client, src: str, dst: str,
                   arch: str) -> List[str]:
    failures: List[str] = []
    for schema, charset in ((src, "latin1"), (dst, "utf8mb4"),
                            (arch, "utf8mb4")):
        client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                     "DEFAULT CHARSET={1};".format(schema, charset))
    client.setup(FK_PARENT_SRC_DDL + ";" + FK_CHILD_SRC_DDL + ";", src)
    client.setup("INSERT INTO consentType VALUES {0}; INSERT INTO Consent "
                 "VALUES {1};".format(", ".join(FK_PARENT_STAGE),
                                      ", ".join(FK_CHILD_STAGE)), src)
    client.setup(FK_PARENT_DST_DDL + ";" + FK_CHILD_DST_DDL + ";", dst)
    client.setup("INSERT INTO consentType VALUES {0};".format(
        FK_PARENT_SEED), dst)

    def query(sql):
        return client.rows(sql, dst)

    info = o19etl.introspect_columns(query, dst)
    parent_cols, child_cols = info["consentType"], info["Consent"]

    # the real sequence: etl_merge_table's snapshot, anti-join and id map,
    # then the child's copy through that map
    for sql in o19etl.archive_statements(
            "consentType", dst, arch, o19etl.preseed_table("consentType")):
        client.setup(sql + ";", dst)
    client.setup(o19etl.merge_statement(
        "consentType", FK_PARENT_ENTRY, src, dst, parent_cols) + ";", dst)
    for sql in o19etl.idmap_statements("consentType", FK_PARENT_ENTRY, src,
                                       dst, arch, parent_cols):
        client.setup(sql + ";", dst)
    client.setup(o19etl.copy_statement(
        "Consent", FK_CHILD_ENTRY, src, dst, child_cols,
        archive_schema=arch) + ";", dst)

    print("\n  copied id into a merged parent (fk_remap, by name)")
    by_name = {r[0]: r[1] for r in client.rows(
        "SELECT c.demographic_no, t.type FROM Consent c JOIN consentType t "
        "ON t.id = c.consent_type_id ORDER BY c.demographic_no", dst)}
    want = {"100": "integrator_patient_consent",
            "101": "electronic_communication_consent",
            "102": "research_registry"}
    ok = by_name == want
    print("    {0:<44} {1}".format(
        "every consent names the clinic's type",
        "ok" if ok else "WRONG: {0}".format(by_name)))
    if not ok:
        failures.append("Consent rows landed on the wrong type by name: "
                        "{0}, expected {1}".format(by_name, want))

    dangling = client.rows("SELECT consent_type_id IS NULL FROM Consent "
                           "WHERE demographic_no = 103", dst)[0][0]
    (_col, _parent, unmapped_sql), = o19etl.fk_unmapped_count_sql(
        "Consent", FK_CHILD_ENTRY, src, arch)
    reported = client.rows(unmapped_sql, dst)[0][0]
    ok = dangling == "1" and reported == "1"
    print("    {0:<44} {1}".format(
        "a dangling id becomes NULL and is reported",
        "ok" if ok else "NULL={0} reported={1}".format(dangling, reported)))
    if not ok:
        failures.append("the dangling reference was not nulled (NULL={0}) "
                        "or not reported ({1})".format(dangling, reported))

    idmap = {r[0]: r[1] for r in client.rows(
        "SELECT old_id, new_id FROM `{0}`.`{1}` ORDER BY old_id".format(
            arch, o19etl.idmap_table("consentType")), dst)}
    live = client.rows("SELECT COUNT(*) FROM consentType", dst)[0][0]
    ok = idmap == {"1": "3", "2": "2", "3": "4"} and live == "4"
    print("    {0:<44} {1}".format(
        "the seed won its twin; the rest appended",
        "ok" if ok else "map={0} live={1}".format(idmap, live)))
    if not ok:
        failures.append("id map {0} / live rows {1}; expected {{1:3, 2:2, "
                        "3:4}} and 4".format(idmap, live))

    # P7 agrees with the faithful run
    seed_n = client.rows(o19etl.merge_seed_change_sql(
        "consentType", dst, arch, parent_cols, ("id",),
        ["id", "type", "name", "description", "active"]), dst)[0][0]
    app_n = client.rows(o19etl.merge_appended_mismatch_sql(
        "consentType", FK_PARENT_ENTRY, src, dst, arch, parent_cols,
        ("id",)), dst)[0][0]
    copy_n = client.rows(o19etl.copy_value_mismatch_sql(
        "Consent", FK_CHILD_ENTRY, src, dst, child_cols, ("id",),
        archive_schema=arch), dst)[0][0]
    ok = seed_n == app_n == copy_n == "0"
    print("    {0:<44} {1}".format(
        "P7 agrees with the faithful run",
        "ok" if ok else "seed={0} appended={1} copy={2}".format(
            seed_n, app_n, copy_n)))
    if not ok:
        failures.append("a P7 value check false-alarmed on the faithful "
                        "run (seed {0}, appended {1}, copy {2})".format(
                            seed_n, app_n, copy_n))

    # negative control: the defect as shipped before the guard. Copy the
    # child RAW (no fk_remap) and watch demographic 100's integrator
    # consent become the demonstration consent -- while P7's value check
    # on that raw entry still reports nothing wrong.
    raw_entry = {k: v for k, v in FK_CHILD_ENTRY.items() if k != "fk_remap"}
    client.setup("TRUNCATE TABLE Consent;" + o19etl.copy_statement(
        "Consent", raw_entry, src, dst, child_cols) + ";", dst)
    misfiled = client.rows(
        "SELECT t.type FROM Consent c JOIN consentType t ON t.id = "
        "c.consent_type_id WHERE c.demographic_no = 100", dst)[0][0]
    blind = client.rows(o19etl.copy_value_mismatch_sql(
        "Consent", raw_entry, src, dst, child_cols, ("id",)), dst)[0][0]
    ok = misfiled == "default_consent_entry" and blind == "0"
    print("    {0:<44} {1}".format(
        "the raw copy misfiles, and P7 cannot see it",
        "reproduced" if ok else "type={0} P7={1}".format(misfiled, blind)))
    if not ok:
        failures.append(
            "the negative control did not reproduce the defect (raw copy "
            "filed demographic 100 under {0}; P7 reported {1}) -- if the "
            "raw copy no longer misfiles, the seeds have converged and "
            "this fixture's premise needs re-checking".format(
                misfiled, blind))
    return failures


#: One copy table with two columns CARLOS has no home for: a curated
#: `dropped` one and a clinic-fork one the manifest has never seen. Both
#: latin1 on the source, as every OSCAR 19 column is.
ARCHIVED_SRC_DDL = (
    "CREATE TABLE t ("
    " id int NOT NULL,"
    " name varchar(60),"
    " vendorNote text,"          # a fork column -> import_archived_vendorNote
    " legacyMark varchar(8),"    # curated `dropped` -> import_archived_…
    " PRIMARY KEY (id))")

ARCHIVED_DST_DDL = (
    "CREATE TABLE t ("
    " id int NOT NULL,"
    " name varchar(60),"
    " PRIMARY KEY (id))")

#: Row 1 is the case that decides it: a latin1 TEXT holding its full
#: 65535 characters, every one of them 'é' (0xE9), and a VARCHAR of the
#: six CP1252 bytes MySQL's latin1 maps outside ISO-8859-1. Row 2 is
#: double-encoded text (the bytes of UTF-8 'é' stored as two latin1
#: characters) -- an archive holds it AS IS, mojibake included, because
#: the clinic's row said that. Row 3 is all NULL.
ARCHIVED_ROWS = [
    "(1, 'Santé', REPEAT(X'E9', 65535), X'80818D8F909D')",
    "(2, 'plain', CONVERT(X'C3A9' USING latin1), NULL)",
    "(3, NULL, NULL, NULL)",
]

ARCHIVED_ENTRY = {
    "class": "copy",
    "cols": ["id", "name"],
    "dropped": {"legacyMark": {"nondefault": "1"}},
}


def check_archived_column_charset(client: Client, src: str,
                                  dst: str) -> List[str]:
    """An `import_archived_` column holds the source's bytes at the
    source's capacity -- the claim "copied verbatim" makes, put to the
    engine.

    The unit tests see the ALTER carry `CHARACTER SET latin1`. They
    cannot see what the review found: that without it the new column
    took the CARLOS table's utf8mb4, in which a TEXT holds 65535 BYTES
    rather than characters, so a full latin1 TEXT copied into it lost
    half its characters -- with a warning the ETL's `sql_mode=''`
    reduces to silence. The negative control below re-runs the same
    copy with the clause stripped, so the truncation this guards against
    is demonstrated rather than remembered.
    """
    try:
        return _archived_charset_body(client, src, dst)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(src, dst))


def _archived_charset_body(client: Client, src: str, dst: str) -> List[str]:
    failures: List[str] = []
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=latin1;".format(src))
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=utf8mb4;".format(dst))
    client.setup(ARCHIVED_SRC_DDL + " DEFAULT CHARSET=latin1;", src)
    client.setup("INSERT INTO t VALUES {0};".format(
        ", ".join(ARCHIVED_ROWS)), src)
    client.setup(ARCHIVED_DST_DDL + ";", dst)

    def query(sql):
        return client.rows(sql, dst)

    print("\n  archived column charset (archived_column_plan)")
    # the real path, end to end: introspect staging, plan, ALTER, fold
    # the columns into the entry, copy under the ETL's sql_mode
    src_cols = o19etl.introspect_columns(query, src)["t"]
    plan = o19etl.archived_column_plan(ARCHIVED_ENTRY, src_cols)
    carried = all("CHARACTER SET latin1" in ctype for _s, _t, ctype in plan)
    print("    {0:<44} {1}".format(
        "the plan carries the source charset",
        "ok" if carried else "MISSING"))
    if not carried:
        failures.append("archived_column_plan did not carry the staging "
                        "column's charset: {0}".format(plan))
        return failures
    targets = [t for _s, t, _c in plan]
    if sorted(targets) != ["import_archived_legacyMark",
                           "import_archived_vendorNote"]:
        failures.append("unexpected plan: {0}".format(plan))
        return failures

    def stage_and_copy(the_plan) -> None:
        dst_cols = o19etl.introspect_columns(query, dst)["t"]
        for stmt in o19etl.add_archived_column_statements(
                "t", dst, the_plan, dst_cols):
            client.setup(stmt + ";", dst)
        dst_cols = o19etl.introspect_columns(query, dst)["t"]
        entry = o19etl.with_archived_columns(ARCHIVED_ENTRY, the_plan)
        client.setup("SET SESSION sql_mode='';"
                     + o19etl.copy_statement("t", entry, src, dst,
                                             dst_cols) + ";", dst)

    def archived_charsets() -> Dict[str, str]:
        return {c: cs for c, _d, _t, cs in _column_shape(client, dst, "t")
                if c.startswith("import_archived_")}

    def unequal_bytes() -> int:
        # HEX of a latin1 column is the latin1 bytes; of a utf8mb4 one,
        # the utf8mb4 bytes -- so this is only 0 when the archived
        # column really holds what the source held, byte for byte
        return int(client.rows(
            "SELECT COUNT(*) FROM `{0}`.t d JOIN `{1}`.t s USING (id) "
            "WHERE NOT (HEX(d.import_archived_vendorNote) <=> "
            "HEX(s.vendorNote)) OR NOT (HEX(d.import_archived_legacyMark) "
            "<=> HEX(s.legacyMark))".format(dst, src), dst)[0][0])

    def full_text_length() -> int:
        return int(client.rows(
            "SELECT IFNULL(CHAR_LENGTH(import_archived_vendorNote), 0) "
            "FROM t WHERE id = 1", dst)[0][0])

    stage_and_copy(plan)
    charsets = archived_charsets()
    declared = all(cs == "latin1" for cs in charsets.values()) \
        and len(charsets) == 2
    print("    {0:<44} {1}".format(
        "the live columns are declared latin1",
        "ok" if declared else "NOT ({0})".format(charsets)))
    if not declared:
        failures.append("the archived columns did not take the source "
                        "charset: {0}".format(charsets))
    n = unequal_bytes()
    print("    {0:<44} {1}".format(
        "every archived value is byte-identical",
        "ok" if n == 0 else "{0} ROW(S) DIFFER".format(n)))
    if n:
        failures.append("{0} archived value(s) differ from the source "
                        "bytes".format(n))
    got = full_text_length()
    print("    {0:<44} {1}".format(
        "a full latin1 TEXT keeps all 65535 characters",
        "ok" if got == 65535 else "TRUNCATED to {0}".format(got)))
    if got != 65535:
        failures.append("the full TEXT arrived with {0} characters; the "
                        "archive lost data".format(got))

    # P7's copy check must agree with a faithful copy through a latin1
    # archived column -- it compares through CONVERT(... USING utf8mb4)
    # on both sides, which is what makes the two charsets comparable
    dst_cols = o19etl.introspect_columns(query, dst)["t"]
    entry = o19etl.with_archived_columns(ARCHIVED_ENTRY, plan)
    check = o19etl.copy_value_mismatch_sql("t", entry, src, dst, dst_cols,
                                           ("id",))
    alarms = int(client.rows(check, dst)[0][0])
    print("    {0:<44} {1}".format(
        "and the P7 value check agrees",
        "ok" if alarms == 0 else "{0} FALSE ALARM(S)".format(alarms)))
    if alarms:
        failures.append("copy_value_mismatch_sql disagreed with a faithful "
                        "copy on {0} row(s)".format(alarms))

    # negative control: the pre-fix ALTER, charset clause stripped, must
    # demonstrably truncate -- otherwise this fixture proves nothing
    client.setup("ALTER TABLE t DROP COLUMN import_archived_vendorNote, "
                 "DROP COLUMN import_archived_legacyMark; TRUNCATE TABLE t;",
                 dst)
    bare = [(s_, t_, c_.split(" CHARACTER SET", 1)[0]) for s_, t_, c_ in plan]
    stage_and_copy(bare)
    lost = full_text_length()
    print("    {0:<44} {1}".format(
        "control: without the charset the TEXT truncates",
        "reproduced ({0} chars)".format(lost) if lost != 65535
        else "NOT REPRODUCED"))
    if lost == 65535:
        failures.append("the charset-less column held the full TEXT; the "
                        "premise this check rests on needs re-checking "
                        "on this server ({0})".format(archived_charsets()))
    return failures


#: the real `property` shapes, because `twin_surplus` reads the class and
#: the merge keys out of the SHIPPED manifest: a synthetic table name
#: would make the tolerance untestable. CARLOS seeds a global property
#: with `provider_no` NULL (V1.0.2__on_data.sql:35546); an OSCAR 19
#: clinic writes `''` for the same thing, which is why the manifest gives
#: the key the expression `NULLIF(s.`provider_no`, '')`.
TWIN_SRC_DDL = (
    "CREATE TABLE `property` (`name` varchar(255) NOT NULL DEFAULT '', "
    "`value` varchar(2000) DEFAULT NULL, `id` int(10) NOT NULL "
    "AUTO_INCREMENT, `provider_no` varchar(6) DEFAULT '', "
    "`lastUpdateDate` datetime DEFAULT NULL, PRIMARY KEY (`id`))")

TWIN_DST_DDL = (
    "CREATE TABLE `property` (`name` varchar(255) NOT NULL DEFAULT '', "
    "`value` varchar(2000) DEFAULT NULL, `id` int(10) NOT NULL "
    "AUTO_INCREMENT, `provider_no` varchar(6) DEFAULT '', "
    "PRIMARY KEY (`id`))")

#: two clinic rows on ONE stored key: '' and NULL both normalise to NULL
TWIN_STAGE = ("('integrator_patient_consent','1',1,'', "
              "'2020-01-01 00:00:00')",
              "('integrator_patient_consent','0',2,NULL, "
              "'2021-02-02 00:00:00')")

#: the CARLOS seed row those two lose to, verbatim from the ON seed
TWIN_SEED = "('integrator_patient_consent','1',1,NULL)"


def check_normalised_twin_key(client: Client, src: str, dst: str,
                              arch: str) -> List[str]:
    """Twins are rows that land on the SAME target row -- and the merge
    JOIN decides that, not the raw column values.

    `property`'s natural key carries `NULLIF(s.`provider_no`, '')`
    because CARLOS seeds a global property with NULL where an OSCAR 19
    clinic writes `''`. A clinic holding both spellings for one property
    therefore has two rows with different RAW keys and one STORED key.
    Grouped raw, both survived the back-fill's rank-1 filter and the
    UPDATE joined both to the single seed row (assigning from whichever
    row MySQL chose), while `twin_surplus` could not even find the seed
    -- it compared `''` with NULL -- so `archived_column_parity` counted
    two source values against one live column and failed a CORRECT
    import with a mismatch no flag clears.

    The negative control at the end restores the raw grouping and shows
    that same fixture failing, so this is demonstrated rather than
    remembered."""
    try:
        return _twin_key_body(client, src, dst, arch)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF EXISTS "
                   "`{1}`; DROP DATABASE IF EXISTS `{2}`;".format(
                       src, dst, arch))


def _twin_key_body(client: Client, src: str, dst: str,
                   arch: str) -> List[str]:
    failures: List[str] = []
    for schema in (src, dst, arch):
        client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                     "DEFAULT CHARSET=utf8mb4;".format(schema))
    client.setup(TWIN_SRC_DDL + ";", src)
    client.setup("INSERT INTO `property` VALUES {0};".format(
        ", ".join(TWIN_STAGE)), src)
    client.setup(TWIN_DST_DDL + ";", dst)

    def query(sql):
        return client.rows(sql, dst)

    entry = dict(o19map_schema.TABLES["property"])
    src_cols = o19etl.introspect_columns(query, src)["property"]
    plan = o19etl.archived_column_plan(entry, src_cols)
    merged_entry = o19etl.with_archived_columns(entry, plan)

    def rebuild() -> None:
        """The target as the ETL leaves it, in the ETL's own order."""
        client.setup("DROP TABLE IF EXISTS `property`;", dst)
        client.setup(TWIN_DST_DDL + ";", dst)
        client.setup("INSERT INTO `property` VALUES {0};".format(TWIN_SEED),
                     dst)
        cols = o19etl.introspect_columns(query, dst)["property"]
        for stmt in o19etl.add_archived_column_statements(
                "property", dst, plan, cols):
            client.setup(stmt + ";", dst)
        cols = o19etl.introspect_columns(query, dst)["property"]
        for sql in o19etl.archive_statements(
                "property", dst, arch, o19etl.preseed_table("property")):
            client.setup(sql + ";", dst)
        client.setup(o19etl.merge_statement(
            "property", merged_entry, src, dst, cols, None, arch) + ";", dst)
        client.setup(o19etl.archived_backfill_statement(
            "property", merged_entry, src, dst, cols, arch) + ";", dst)

    print("\n  a normalised merge key groups its twins "
          "(merge_twin_partition)")
    rebuild()
    live = int(client.rows(
        "SELECT COUNT(*) FROM `property`", dst)[0][0])
    print("    {0:<44} {1}".format(
        "the seed won both clinic rows",
        "ok" if live == 1 else "{0} LIVE ROW(S)".format(live)))
    if live != 1:
        return ["the fixture did not merge as expected ({0} live rows, "
                "expected 1) -- every line below is meaningless"
                .format(live)]

    landed = client.rows(
        "SELECT IFNULL(DATE_FORMAT(`import_archived_lastUpdateDate`, "
        "'%Y-%m-%d'), 'NULL') FROM `property`", dst)[0][0]
    print("    {0:<44} {1}".format(
        "one value landed, and a deterministic one",
        "ok ({0})".format(landed) if landed == "2020-01-01"
        else "GOT {0}".format(landed)))
    if landed != "2020-01-01":
        failures.append("the back-fill landed {0!r}; the rank-1 order is "
                        "meant to make the winner deterministic and "
                        "non-NULL".format(landed))

    def parity():
        return o19etl.archived_column_parity(query, src, dst,
                                             archive_schema=arch)

    ok, bad = parity()
    print("    {0:<44} {1}".format(
        "P7 agrees the import is complete",
        "ok" if not bad else "{0} FALSE ALARM(S)".format(len(bad))))
    if bad:
        failures.append("archived_column_parity failed a correct import: "
                        "{0}".format("; ".join(bad)))
    elif not any("twin value(s)" in line for line in ok):
        failures.append("the surplus twin was not reported anywhere: "
                        "requirement B is that nothing leaves without "
                        "being counted ({0})".format("; ".join(ok)))

    # negative control: group by the RAW columns, as before, and the
    # same faithful import must fail -- otherwise this fixture proves
    # nothing about the grouping
    original = o19etl.merge_twin_partition

    def raw_partition(e, archive_schema=None, dst_cols=None):
        renames = e.get("renames") or {}
        return ["s.{0}".format(o19etl.ident(renames.get(k, k)))
                for k in e.get("merge_keys") or ()]

    o19etl.merge_twin_partition = raw_partition
    try:
        rebuild()
        _ok, control = parity()
    finally:
        o19etl.merge_twin_partition = original
    print("    {0:<44} {1}".format(
        "control: grouped raw, the same import fails",
        "reproduced" if control else "NOT REPRODUCED"))
    if not control:
        failures.append("the raw grouping passed this fixture, so the "
                        "check proves nothing about the normalised key")
    return failures


#: OSCAR 19's shape: every one of these is nullable, and the fixture
#: holds NULL in all of them.
REQUIRED_SRC_DDL = (
    "CREATE TABLE `t` (`id` int(10) NOT NULL, `flag` tinyint(1) DEFAULT "
    "NULL, `note` varchar(255) DEFAULT NULL, `seen` datetime DEFAULT "
    "NULL, `touched` timestamp NULL DEFAULT NULL, PRIMARY KEY (`id`))")

#: CARLOS's shape: the same columns, made required. `flag` and `note`
#: carry a DEFAULT precisely because the server IGNORES it for this
#: substitution -- see `o19etl.not_null_fallback`.
REQUIRED_DST_DDL = (
    "CREATE TABLE `t` (`id` int(10) NOT NULL, `flag` tinyint(1) NOT NULL "
    "DEFAULT 7, `note` varchar(255) NOT NULL DEFAULT 'x', `seen` "
    "datetime NOT NULL, `touched` timestamp NOT NULL DEFAULT "
    "CURRENT_TIMESTAMP, PRIMARY KEY (`id`))")

REQUIRED_ENTRY = {"class": "copy",
                  "cols": ["id", "flag", "note", "seen", "touched"]}


def check_required_column_nulls(client: Client, src: str,
                                dst: str) -> List[str]:
    """A NULL landing in a column CARLOS declares NOT NULL.

    Found by a live rehearsal: `document.restrictToProgram` and
    `professionalSpecialists.hideFromView` are nullable in OSCAR 19,
    `NOT NULL` in CARLOS, and NULL in an ordinary clinic dump. The
    copy's INSERT ... SELECT stored the type's zero silently under the
    ETL's `sql_mode=''`, while `copy_value_mismatch_sql` compared the
    target against the NULL it believed had been written -- so P4 row
    parity failed a faithful import, and parity is not overridable.

    This puts the whole claim to the engine: that the substitution is
    what `not_null_fallback` says it is (the TYPE's zero, not the
    column's DEFAULT), that a NOT NULL `timestamp` is the exception the
    check has to tolerate rather than the write suppress, and that the
    value check agrees with the copy afterwards. The negative control
    strips the coercion and shows the same faithful copy failing."""
    try:
        return _required_nulls_body(client, src, dst)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(src, dst))


def _required_nulls_body(client: Client, src: str, dst: str) -> List[str]:
    failures: List[str] = []
    for schema in (src, dst):
        client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                     "DEFAULT CHARSET=utf8mb4;".format(schema))
    client.setup(REQUIRED_SRC_DDL + ";", src)
    client.setup("INSERT INTO `t` VALUES (1,NULL,NULL,NULL,NULL);", src)
    client.setup(REQUIRED_DST_DDL + ";", dst)

    def query(sql):
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)["t"]
    print("\n  a NULL where CARLOS requires a value (not_null_fallback)")

    client.setup("SET SESSION sql_mode='';"
                 + o19etl.copy_statement("t", REQUIRED_ENTRY, src, dst,
                                         dst_cols) + ";", dst)
    stored = client.rows(
        "SELECT `flag`, CONCAT('[',`note`,']'), `seen`, "
        "`touched` > '2000-01-01' FROM `t`", dst)[0]
    # the column DEFAULTs are 7 and 'x'; the server stores neither
    ok = stored[0] == "0" and stored[1] == "[]" \
        and stored[2].startswith("0000-00-00")
    print("    {0:<44} {1}".format(
        "the type's zero lands, not the DEFAULT",
        "ok" if ok else "GOT {0}".format(stored[:3])))
    if not ok:
        failures.append("the NOT NULL substitution is not the type zero "
                        "this build assumes: {0}".format(stored[:3]))
    print("    {0:<44} {1}".format(
        "a NOT NULL timestamp takes the import's own",
        "ok" if stored[3] == "1" else "GOT {0}".format(stored[3])))
    if stored[3] != "1":
        failures.append("a NOT NULL timestamp did not take "
                        "CURRENT_TIMESTAMP; the check's tolerance rests "
                        "on that")

    check = o19etl.copy_value_mismatch_sql("t", REQUIRED_ENTRY, src, dst,
                                           dst_cols, ("id",))
    alarms = int(client.rows(check, dst)[0][0])
    print("    {0:<44} {1}".format(
        "P7 agrees with the faithful copy",
        "ok" if alarms == 0 else "{0} FALSE ALARM(S)".format(alarms)))
    if alarms:
        failures.append("copy_value_mismatch_sql disagreed with its own "
                        "copy on {0} row(s) -- this is the P4 parity "
                        "failure a live rehearsal hit".format(alarms))

    # negative control: without the coercion the check compares a NULL
    # against the stored zero and fails a correct import
    original = o19etl.not_null_fallback
    o19etl.not_null_fallback = lambda info: None
    try:
        bare = o19etl.copy_value_mismatch_sql(
            "t", REQUIRED_ENTRY, src, dst, dst_cols, ("id",))
    finally:
        o19etl.not_null_fallback = original
    control = int(client.rows(bare, dst)[0][0])
    print("    {0:<44} {1}".format(
        "control: uncoerced, the same copy fails",
        "reproduced" if control else "NOT REPRODUCED"))
    if not control:
        failures.append("the uncoerced check passed, so this fixture "
                        "proves nothing about the coercion")
    return failures


#: Every destination type a column CARLOS maps to a Java primitive
#: actually has in the shipped manifest, paired with a NON-NULL source
#: value to store in it. The point of the pairing is the row that needs
#: NO substitution: `IFNULL(expr, <literal>)` changes the EXPRESSION's
#: type, so a wrong literal can leave the write correct and still make
#: the value check disagree with it on every faithful row -- which is
#: exactly what `bit` did, failing P4 parity on a clean import.
PRIMITIVE_TYPE_FIXTURES = [
    ("c_tinyint", "tinyint(4)", "1"),
    ("c_smallint", "smallint(6)", "2"),
    ("c_mediumint", "mediumint(9)", "3"),
    ("c_int", "int(11)", "4"),
    ("c_bigint", "bigint(20)", "5"),
    ("c_float", "float", "1.5"),
    ("c_double", "double", "2.5"),
    ("c_decimal", "decimal(10,2)", "3.25"),
    ("c_bit", "bit(1)", "b'1'"),
    ("c_char", "char(1)", "'y'"),
    ("c_varchar", "varchar(16)", "'text'"),
    ("c_date", "date", "'2014-03-04'"),
    ("c_datetime", "datetime", "'2014-03-04 09:00:00'"),
    ("c_time", "time", "'09:00:00'"),
]


def check_primitive_fallback_types(client: Client, src: str,
                                   dst: str) -> List[str]:
    """The substituted literal must be right for EVERY type, on the rows
    that need no substitution as much as on the rows that do.

    A nullable column CARLOS maps to a Java primitive gets an
    `IFNULL(expr, <literal>)` wrapper so the row can be hydrated. That
    wrapper changes the expression's TYPE, and the value check is built
    from the same expression -- so a literal of the wrong type can store
    the right value and still make the check FALSE for every row whose
    source was not null. Measured: with `''` (the fallthrough literal) a
    `bit(1)` column failed P4 parity on a faithful import, on a row that
    held a value all along.

    So this drives every type the manifest actually presents, with a
    real value AND a NULL in each, and requires the check to agree with
    the copy on both."""
    try:
        return _primitive_types_body(client, src, dst)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`;".format(src, dst))


def _primitive_types_body(client: Client, src: str, dst: str) -> List[str]:
    failures: List[str] = []
    columns = ", ".join("`{0}` {1} NULL".format(name, decl)
                        for name, decl, _v in PRIMITIVE_TYPE_FIXTURES)
    names = [name for name, _d, _v in PRIMITIVE_TYPE_FIXTURES]
    for schema in (src, dst):
        client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                     "DEFAULT CHARSET=utf8mb4;".format(schema))
        client.setup("CREATE TABLE `t` (`id` int NOT NULL PRIMARY KEY, {0})"
                     ";".format(columns), schema)
    client.setup(
        "INSERT INTO `t` VALUES (1, {0});".format(
            ", ".join(v for _n, _d, v in PRIMITIVE_TYPE_FIXTURES)), src)
    client.setup(
        "INSERT INTO `t` VALUES (2, {0});".format(
            ", ".join("NULL" for _f in PRIMITIVE_TYPE_FIXTURES)), src)

    def query(sql):
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)["t"]
    # every column is primitive-mapped for this fixture: that is the
    # population the wrapper is added for
    for name in names:
        dst_cols[name]["primitive"] = True
    entry = {"class": "copy", "cols": ["id"] + names}

    print("\n  the substituted literal is right for every type")
    client.setup("SET SESSION sql_mode='';"
                 + o19etl.copy_statement("t", entry, src, dst, dst_cols)
                 + ";", dst)

    # nothing may be NULL on the target: that is what the wrapper is for
    nulls = int(client.rows(
        "SELECT COUNT(*) FROM `t` WHERE {0}".format(
            " OR ".join("`{0}` IS NULL".format(n) for n in names)),
        dst)[0][0])
    print("    {0:<44} {1}".format(
        "no primitive column lands NULL",
        "ok" if nulls == 0 else "{0} ROW(S) STILL NULL".format(nulls)))
    if nulls:
        failures.append("{0} row(s) still hold NULL in a column CARLOS "
                        "maps as a primitive".format(nulls))

    check = o19etl.copy_value_mismatch_sql("t", entry, src, dst, dst_cols,
                                           ("id",))
    alarms = int(client.rows(check, dst)[0][0])
    print("    {0:<44} {1}".format(
        "P7 agrees with the copy, both rows",
        "ok" if alarms == 0 else "{0} FALSE ALARM(S)".format(alarms)))
    if alarms:
        # name them, because "one type is wrong" is the whole finding
        for name in names:
            one = dict(entry, cols=["id", name])
            n = int(client.rows(o19etl.copy_value_mismatch_sql(
                "t", one, src, dst, dst_cols, ("id",)), dst)[0][0])
            if n:
                failures.append(
                    "{0} ({1}): the value check disagrees with the copy on "
                    "{2} row(s); the substituted literal changes the "
                    "expression's type".format(
                        name, dst_cols[name]["column_type"], n))
    return failures


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(
        description="verify the ETL's merge, id-map and charset-repair "
                    "behaviour against MariaDB")
    ap.add_argument("--mysql-cmd", default="mariadb",
                    help="client binary (default: mariadb)")
    ap.add_argument("--mysql-arg", action="append", default=[],
                    dest="mysql_args",
                    help="argument passed to the client; repeatable. Values "
                         "starting with '-' need the =form, e.g. "
                         "--mysql-arg=-uroot")
    ap.add_argument("--prefix", default="carlos_merge_verify",
                    help="scratch schema prefix; _dst/_src/_arch are DROPPED "
                         "and recreated")
    args = ap.parse_args(argv)

    leaked = reject_password_args(args.mysql_args)
    if leaked:
        print("refusing {0}: a password in the client's argv is readable by "
              "any local user. Use MYSQL_PWD or a client defaults file "
              "(--mysql-arg=--defaults-extra-file=...)."
              .format(", ".join(leaked)), file=sys.stderr)
        return 2
    problem = prefix_problem(args.prefix)
    if problem:
        print(problem, file=sys.stderr)
        return 2

    client = Client(args.mysql_cmd, args.mysql_args)
    rc, out, err = client.run("SELECT VERSION();")
    if rc != 0:
        print("cannot reach the server: {0}".format(err[:300]),
              file=sys.stderr)
        return 2
    print("server: {0}".format(out.strip()))

    dst = args.prefix + "_dst"
    src = args.prefix + "_src"
    arch = args.prefix + "_arch"

    failures: Dict[str, List[str]] = {}
    try:
        # inside the teardown from here on: same_statement_visibility
        # CREATEs _dst and _src itself, so a failure in it (or in the
        # query that follows) used to leave both behind
        print("anti-join sees same-statement inserts: {0}".format(
            same_statement_visibility(client, dst, src)))
        return _run_checks(client, args, failures, dst, src, arch)
    finally:
        # the shared scratch schemas, dropped whatever happened: each
        # check below owns its OWN schemas in a finally, but a fixture
        # that raises SystemExit (Client.setup's "could not run") used to
        # exit main() before this line and leave three databases behind
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF "
                   "EXISTS `{1}`; DROP DATABASE IF EXISTS `{2}`;".format(
                       dst, src, arch))


def _run_checks(client: Client, args, failures: Dict[str, List[str]],
                dst: str, src: str, arch: str) -> int:
    """Every check in turn; `main` owns the shared-schema teardown."""
    for sc in SCENARIOS:
        found = check(client, sc, dst, src, arch)
        if found:
            failures[sc.name] = found
    charset = check_charset_repair(client, args.prefix + "_cs")
    if charset:
        failures["charset repair"] = charset
    digest = check_content_digest(client, args.prefix + "_dgc",
                                  args.prefix + "_dgs")
    if digest:
        failures["content digest"] = digest
    chain = check_end_to_end_transfer(client, args.mysql_args,
                                      args.prefix + "_e2c",
                                      args.prefix + "_e2s")
    if chain:
        failures["end-to-end transfer"] = chain
    preserved = check_preserved_copy(client, args.prefix + "_pvs",
                                     args.prefix + "_pva")
    if preserved:
        failures["preserved copy"] = preserved
    copied = check_copy_values(client, args.prefix + "_cvs",
                               args.prefix + "_cvd")
    if copied:
        failures["copy values"] = copied
    merged = check_merge_values(client, args.prefix + "_mvs",
                                args.prefix + "_mvd",
                                args.prefix + "_mva")
    if merged:
        failures["merge values"] = merged
    remapped = check_fk_remap_by_name(client, args.prefix + "_fks",
                                      args.prefix + "_fkd",
                                      args.prefix + "_fka")
    if remapped:
        failures["fk remap"] = remapped
    archived = check_archived_column_charset(client, args.prefix + "_acs",
                                             args.prefix + "_acd")
    if archived:
        failures["archived charset"] = archived
    twins = check_normalised_twin_key(client, args.prefix + "_tks",
                                      args.prefix + "_tkd",
                                      args.prefix + "_tka")
    if twins:
        failures["normalised twin key"] = twins
    required = check_required_column_nulls(client, args.prefix + "_rns",
                                           args.prefix + "_rnd")
    if required:
        failures["required column nulls"] = required
    typed = check_primitive_fallback_types(client, args.prefix + "_pts",
                                           args.prefix + "_ptd")
    if typed:
        failures["primitive fallback types"] = typed

    if failures:
        print("\n{0} scenario(s) broke an invariant".format(len(failures)))
        for name, found in sorted(failures.items()):
            for line in found:
                print("  {0}: {1}".format(name, line))
        return 1
    print("\nOK - the seed wins, the clinic's twins are preserved, every "
          "source id is mapped, the charset repair fixes mojibake without "
          "touching correct text, the content digest agrees across "
          "latin1/utf8mb4 while catching every change put to it, and the "
          "whole P2 chain (clinic digest -> mysqldump -> restore -> "
          "compare) verifies a faithful transfer and refuses a damaged "
          "one, the preserved copies really are value-for-value equal to "
          "their source, the copy class's value check agrees with the "
          "copy on a faithful run while catching every change put to it, "
          "and the merge class's three claims -- the seed is untouched, "
          "the appended rows hold what the merge wrote, the dropped "
          "columns are backfilled -- do the same on both merge shapes "
          "(surrogate id paired through the map, and natural key with "
          "merge_exclude), and a copied id into a merged parent lands on "
          "the right row by NAME while the raw copy it replaces "
          "demonstrably does not, and an import_archived_ column holds "
          "the source's bytes at the source's capacity where the "
          "charset-less column it replaces demonstrably truncates, and "
          "the digest of a value larger than max_allowed_packet is the "
          "same under 16M and 1G where the format-1 rendering "
          "demonstrably is not, and a merge key with a normalising "
          "expression groups its twins the way the JOIN does -- one "
          "value landing on the seed row and P7 agreeing -- where the "
          "raw grouping it replaces demonstrably fails a correct "
          "import, and a NULL in a column CARLOS requires takes the "
          "TYPE's zero (never the column's DEFAULT) with the value "
          "check agreeing, where the uncoerced check it replaces "
          "demonstrably fails a faithful copy, and the literal that "
          "substitution uses is right for EVERY type the manifest "
          "presents -- on the rows that needed no substitution as much "
          "as on the rows that did")
    return 0


if __name__ == "__main__":
    sys.exit(main())

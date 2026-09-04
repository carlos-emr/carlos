#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""verify_sql_semantics.py — settle the ETL SQL behaviours that only a real
engine can answer.

Four of them: the merge anti-join's visibility of its own inserts and the
per-row charset repair, both in the P4 copy path; the M22 content digest,
whose whole point is that a check nobody has broken on purpose is not a
check; and P7's value-level comparison of the copy class, where the
question is not only "does it catch a change" but "does it agree with
the copy on a FAITHFUL run" -- a check that false-alarms on every
clinic is worse than no check. One section runs the P2 chain end to end
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
                        o19etl)

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

#: (label, SQL applied to the TARGET, why it must be caught)
COPY_SABOTAGE = [
    # accents lost, which is what a latin1 -> utf8mb4 migration produces
    # when it goes wrong -- and what MariaDB's DEFAULT utf8mb4_general_ci
    # collation calls equal ('Santé' = 'Sante' is 1 under it, as is
    # 'SMITH' = 'smith'). A plain `<=>` here reported a faithful copy.
    ("a copied value lost its accent",
     "UPDATE c SET note = 'Sante' WHERE id = 1",
     "the row count is unchanged, every parity check still passes, and a "
     "case/accent-insensitive comparison calls the two equal"),
    ("a copied value changed case",
     "UPDATE c SET isactive = 'y' WHERE id = 1",
     "the same collation blindness, in the other direction"),
    ("a NULL where a value was copied",
     "UPDATE c SET isactive = NULL WHERE id = 1",
     "`=` would miss this on the NULL rows; `<=>` does not"),
    ("a value where a NULL was copied",
     "UPDATE c SET note = 'x' WHERE id = 3",
     "the all-NULL row is the one a careless comparison skips"),
    ("the sanitized zero date un-sanitized",
     "UPDATE c SET d = '2001-01-01' WHERE id = 2",
     "the copy wrote NULL there; anything else is not what it wrote"),
    ("the enum fallback overridden",
     "UPDATE c SET e = 'b' WHERE id = 2",
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
    # with the key columns in place of COUNT(*). That it selects the same
    # rows is an engine claim, not a reading claim: a projection that
    # quietly widened or narrowed the result would point the operator at
    # the wrong rows, which is worse than pointing at none.
    named = o19etl.copy_value_mismatch_sql(
        "c", COPY_ENTRY, src, dst, dst_cols, ("id",),
        select=o19etl.key_projection(("id",), "d"))

    for label, sql, why in COPY_SABOTAGE:
        client.setup("SET SESSION sql_mode='';{0};".format(sql), dst)
        found = mismatches()
        caught = found > 0
        print("    {0:<44} {1}".format(
            label, "caught" if caught else "MISSED"))
        if not caught:
            failures.append("{0} was NOT caught ({1})".format(label, why))
        elif len(client.rows(named, dst)) != found:
            failures.append(
                "'{0}': the check counted {1} row(s) but the key "
                "projection named {2} -- the details file would point at "
                "the wrong rows".format(
                    label, found, len(client.rows(named, dst))))
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


def check_merge_values(client: Client, src: str, dst: str,
                       arch: str) -> List[str]:
    """`merge_content_parity`'s three claims, against the engine.

    The unit tests assert on generated SQL against a fake. They cannot
    answer the question this check exists for: after the REAL merge --
    anti-join, archived backfill, id map and pre-merge snapshot, run in
    the order `etl_merge_table` runs them -- do the three checks agree
    that a faithful merge is faithful? A check that false-alarms on
    every clinic is worse than no check, and the ways to get one are all
    in the fixture: a seed row whose archived column the backfill writes
    AFTER the snapshot, an appended row whose id AUTO_INCREMENT chose,
    and a payload that is entirely NULL.
    """
    try:
        return _merge_values_body(client, src, dst, arch)
    finally:
        client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF EXISTS "
                   "`{1}`; DROP DATABASE IF EXISTS `{2}`;".format(
                       src, dst, arch))


def _merge_values_body(client: Client, src: str, dst: str,
                       arch: str) -> List[str]:
    """The checks themselves; the caller owns the teardown."""
    failures: List[str] = []
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=latin1;".format(src))
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=utf8mb4;".format(dst))
    client.setup("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}` "
                 "DEFAULT CHARSET=utf8mb4;".format(arch))
    client.setup(MERGE_SRC_DDL + ";", src)
    client.setup("INSERT INTO m VALUES {0};"
                 .format(", ".join(MERGE_STAGE_ROWS)), src)
    client.setup(MERGE_DST_DDL + ";", dst)

    def query(sql):
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)["m"]

    def rebuild() -> None:
        """The target as the ETL leaves it: seeded, snapshotted, merged,
        backfilled, mapped -- in that order, because the snapshot must
        predate the insert and the backfill must follow it."""
        client.setup("TRUNCATE TABLE m; INSERT INTO m VALUES {0};"
                     .format(MERGE_SEED_ROWS), dst)
        for sql in o19etl.archive_statements(
                "m", dst, arch, o19etl.preseed_table("m")):
            client.setup(sql + ";", dst)
        client.setup(o19etl.merge_statement("m", MERGE_ENTRY, src, dst,
                                            dst_cols) + ";", dst)
        client.setup(o19etl.archived_backfill_statement(
            "m", MERGE_ENTRY, src, dst, dst_cols) + ";", dst)
        for sql in o19etl.idmap_statements("m", MERGE_ENTRY, src, dst,
                                           arch, dst_cols):
            client.setup(sql + ";", dst)

    rebuild()
    claims = {
        "seed": o19etl.merge_seed_change_sql(
            "m", dst, arch, dst_cols, ("id",),
            ["id", "status", "descr"]),
        "appended": o19etl.merge_appended_mismatch_sql(
            "m", MERGE_ENTRY, src, dst, arch, dst_cols, ("id",)),
        "backfill": o19etl.merge_backfill_mismatch_sql(
            "m", MERGE_ENTRY, src, dst, arch, ("id",)),
    }

    def findings(claim: str) -> int:
        return int(client.rows(claims[claim], dst)[0][0])

    print("\n  merge values (merge_content_parity)")
    # the merge must have actually done something, or every "no mismatch"
    # below is a statement about an empty result set
    live = client.rows("SELECT COUNT(*) FROM m", dst)[0][0]
    print("    {0:<44} {1}".format("the fixture merged", "{0} live row(s)"
                                   .format(live)))
    if int(live) != 3:
        return ["the fixture did not merge as expected ({0} live rows, "
                "expected 3) -- every line below is meaningless"
                .format(live)]
    for claim in ("seed", "appended", "backfill"):
        n = findings(claim)
        print("    {0:<44} {1}".format(
            "a faithful merge: {0}".format(claim),
            "ok" if n == 0 else "{0} FALSE ALARM(S)".format(n)))
        if n:
            failures.append(
                "the {0} check disagreed with the merge on a faithful run "
                "({1} row(s)) -- it would fail every clinic".format(
                    claim, n))
    if failures:
        return failures

    for label, sql, claim, why in MERGE_SABOTAGE:
        client.setup(sql + ";", dst)
        caught = findings(claim) > 0
        print("    {0:<44} {1}".format(
            label, "caught" if caught else "MISSED"))
        if not caught:
            failures.append("{0} was NOT caught by the {1} check ({2})"
                            .format(label, claim, why))
        rebuild()
        if any(findings(c) for c in claims):
            failures.append(
                "the merge did not come back after '{0}'; every later line "
                "is meaningless".format(label))
            break
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

    print("anti-join sees same-statement inserts: {0}".format(
        same_statement_visibility(client, dst, src)))

    failures: Dict[str, List[str]] = {}
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

    client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF EXISTS `{1}`; "
               "DROP DATABASE IF EXISTS `{2}`;".format(dst, src, arch))
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
          "columns are backfilled -- do the same")
    return 0


if __name__ == "__main__":
    sys.exit(main())

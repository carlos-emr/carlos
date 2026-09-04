#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""verify_sql_semantics.py — settle the ETL SQL behaviours that only a real
engine can answer.

Two of them, both in the P4 copy path, both previously reasoned about rather
than run: the merge anti-join's visibility of its own inserts, and the
per-row charset repair.

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

from carlos_ctl import o19etl                                    # noqa: E402

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
    client.run("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
               "DROP DATABASE IF EXISTS `{1}`; CREATE DATABASE `{1}`;"
               .format(dst, src))
    client.run(DDL, dst)
    client.run(DDL, src)
    client.run("INSERT INTO {0} VALUES (1,'X','1'),(2,'X','1');".format(TABLE),
               src)

    def query(sql: str) -> List[List[str]]:
        return client.rows(sql, dst)

    dst_cols = o19etl.introspect_columns(query, dst)[TABLE]
    client.run(o19etl.merge_statement(TABLE, ENTRY, src, dst, dst_cols) + ";",
               dst)
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
    client.run("DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;"
               .format(db))
    client.run("CREATE TABLE t (id int, v varchar(255)) "
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
    client.run("INSERT INTO t VALUES {0};".format(values), db)

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
    client.run("DROP DATABASE IF EXISTS `{0}`;".format(db))
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

    client.run("DROP DATABASE IF EXISTS `{0}`; DROP DATABASE IF EXISTS `{1}`; "
               "DROP DATABASE IF EXISTS `{2}`;".format(dst, src, arch))
    if failures:
        print("\n{0} scenario(s) broke an invariant".format(len(failures)))
        return 1
    print("\nOK - the seed wins, the clinic's twins are preserved, every "
          "source id is mapped, and the charset repair fixes mojibake "
          "without touching correct text")
    return 0


if __name__ == "__main__":
    sys.exit(main())

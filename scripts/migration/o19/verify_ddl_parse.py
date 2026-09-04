#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""verify_ddl_parse.py — check the manifest generator's DDL parse against
MariaDB's own.

generate_manifests.py reads twenty years of OSCAR 19 SQL and the CARLOS
Flyway set with a hand-written DDL reader (`walk_ddl` + `Schema`), because
no available Python SQL parser reads this corpus correctly -- see
"Why not a library" below. That reader decides which columns exist, which
means it decides what `carlos-ctl import-o19` copies, archives or leaves
behind. A column it silently misses is a column the import silently drops.

Rather than trust it, this script asks the authoritative implementation.
Every CREATE TABLE in the corpus is replayed into a scratch MariaDB schema
under a probe name; every ALTER TABLE is replayed against a probe table
built from the model's state at that point. The resulting column list and
primary key -- read from information_schema, i.e. MariaDB's own parse --
are compared with what the generator concluded from the same statement.
Any disagreement is a generator bug, and the exit status says so.

It shares `walk_ddl()` with the generator on purpose: the oracle checks how
statements are UNDERSTOOD, not how they are located, and a second locator
could drift from the one that ships.

Usage (needs a MariaDB/MySQL the invoking user can create a schema on; the
scratch schema is dropped and recreated on every run, so point it at a
throwaway server, never a clinic's):

    python3 scripts/migration/o19/verify_ddl_parse.py \\
        --oscar-src /path/to/oscar \\
        --mysql-arg=--socket=/run/mysqld/mysqld.sock --mysql-arg=-uroot

Exit codes: 0 = the parse agrees everywhere it could be compared;
1 = at least one disagreement (printed); 2 = usage or connection error.

## Why not a library

Measured on this corpus (2026-09-04), not assumed:

* **sqlglot 30.18.0**, MySQL dialect, is the strongest candidate and still
  loses data. It silently degrades 7 CREATE TABLE and 7 ALTER TABLE
  statements to opaque `Command` nodes carrying no column list -- among
  them `facility`, `bed_check_time`, `custom_filter`,
  `custom_filter_providers`, `custom_filter_assignees`, `tickler_update`
  and `tickler_comments`. Those tables would vanish from the manifest with
  no error, which is the exact failure this project's "nothing orphaned"
  requirement exists to prevent. It is also ~25s to parse the corpus
  against ~2s for the reader here.
* **simple-ddl-parser** reads the statements sqlglot loses, but returns a
  grouped result rather than an ordered statement stream, and document
  order is load-bearing here (a file that DROPs and then CREATEs the same
  table must not be applied phase-ordered). The caller would still own the
  statement walk, leaving a niche dependency doing only the column split.
* **sqlparse** tokenises and formats; it builds no DDL model at all.

So the reader stays, and this script is what makes it checkable. If a
future library version parses the whole corpus, this oracle is also how you
would prove it before switching.
"""

from __future__ import annotations

import argparse
import importlib.util
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

HERE = Path(__file__).resolve().parent

#: Scratch schema names reach SQL as identifiers. This script DROPs what it
#: is given, so a name that is not a plain identifier is refused rather than
#: quoted: there is no legitimate scratch schema called `foo`; DROP DATABASE
#: prod` -- and the operator who typo-pastes one should get an error, not a
#: dropped database.
IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_$]*$")

#: Client arguments that would put a password in the child's argv, where any
#: local user can read it. The rest of this feature refuses these too
#: (o19_preflight passes the password via MYSQL_PWD); the oracle should not
#: be the one place that teaches an operator the bad habit.
PASSWORD_ARGS = ("-p", "--password")


def reject_password_args(args):
    """Return the args that would carry a credential in argv."""
    bad = []
    for a in args:
        if a.startswith("--password") or (
                a.startswith("-p") and not a.startswith("--")):
            bad.append(a.split("=")[0] if "=" in a else a[:2])
    return bad


def load_generator():
    """Import generate_manifests.py as a module (it is a script, not a
    package, and lives beside this file)."""
    spec = importlib.util.spec_from_file_location(
        "generate_manifests", HERE / "generate_manifests.py")
    if spec is None or spec.loader is None:      # pragma: no cover - defensive
        raise SystemExit("cannot import generate_manifests.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class Client:
    """The mysql/mariadb command-line client, as a callable.

    The same shape the rest of this feature uses: statements travel on
    stdin, connection details come from the caller's own client arguments,
    and no password is ever placed in argv.
    """

    def __init__(self, cmd: str, args: List[str], db: str) -> None:
        self.cmd = cmd
        self.args = args
        self.db = db

    def run(self, sql: str, db: Optional[str] = None,
            force: bool = False) -> Tuple[int, str, str]:
        argv = [self.cmd] + self.args + ["-N", "-B",
                                         "--default-character-set=utf8mb4"]
        if force:
            argv.append("--force")
        argv.append(db if db is not None else self.db)
        try:
            proc = subprocess.run(argv, input=sql.encode("utf-8", "replace"),
                                  stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE)
        except OSError as exc:
            # a missing or unexecutable --mysql-cmd: the documented exit 2,
            # not a traceback out of the middle of a batch
            return 127, "", "cannot run {0}: {1}".format(self.cmd, exc)
        return (proc.returncode,
                proc.stdout.decode("utf-8", "replace"),
                proc.stderr.decode("utf-8", "replace").strip())

    def rows(self, sql: str) -> List[List[str]]:
        rc, out, err = self.run(sql)
        if rc != 0:
            raise SystemExit("query failed: {0}".format(err[:400]))
        return [line.split("\t") for line in out.split("\n") if line]


# a CREATE's leading `CREATE TABLE [IF NOT EXISTS] <name>`, replaced with the
# probe name so the statement can be replayed without colliding with the real
# table or with an earlier revision of itself
_CREATE_HEAD = re.compile(
    r"(?is)^create\s+table\s+(?:if\s+not\s+exists\s+)?`?\w+`?")
_ALTER_HEAD = re.compile(r"(?is)^alter\s+table\s+`?\w+`?")


#: The placeholder collect() leaves in a retargeted statement, filled in
#: when the batch is built. A literal `%` is legal in DDL (a DEFAULT of
#: '100%', a COMMENT), so the fill is a plain replace and never % formatting
#: -- which raised TypeError on exactly those statements.
PROBE_MARK = "\x00PROBE\x00"


def probe_create(sql: str, probe: str) -> str:
    """The CREATE statement, retargeted at the probe table."""
    return _CREATE_HEAD.sub("CREATE TABLE `{0}`".format(probe), sql, count=1)


def probe_alter(sql: str, probe: str) -> str:
    """The ALTER statement, retargeted at the probe table."""
    return _ALTER_HEAD.sub("ALTER TABLE `{0}`".format(probe), sql, count=1)


def scaffold(probe: str, columns: List[str],
             pk: Optional[List[str]] = None) -> str:
    """A probe table carrying `columns` in order, all one indexable type.

    The manifest records column NAMES and their order, never the declared
    types, so the scaffold deliberately drops the source types: a neutral
    varchar keeps `ADD PRIMARY KEY`, `AFTER <col>` and `CHANGE` replayable
    on statements whose original types (a bare AUTO_INCREMENT with no key,
    say) a modern server refuses outright.

    The pre-ALTER PRIMARY KEY is carried too. Without it the probe has no
    key, every ALTER's key effect is invisible, and a wrong Schema.pks
    still reports OK -- which is how a stale key after a case-insensitive
    CHANGE survived this oracle's first pass.
    """
    if not columns:
        return ""
    quoted = ["`{0}` varchar(191)".format(c.replace("`", "``"))
              for c in columns]
    if pk:
        quoted.append("PRIMARY KEY ({0})".format(", ".join(
            "`{0}`".format(c.replace("`", "``")) for c in pk)))
    return "CREATE TABLE `{0}` ({1});".format(probe, ", ".join(quoted))


def collect(gm, files: List[Path]):
    """Replay the sources; return the per-statement comparisons to make.

    Each entry is (kind, file, table, before, statement_sql,
    expected_columns, expected_pk), where `before` is None for a CREATE and
    (columns, primary key) for an ALTER -- the state the probe is built in
    so the statement has the same thing to act on that the model did.
    `expected_*` is what the generator concluded, which is what MariaDB is
    asked to confirm.
    """
    schema = gm.Schema(if_not_exists_mode="union")
    cases = []
    for f in files:
        for stmt in gm.walk_ddl(gm.strip_line_comments(gm.read_sql(f))):
            if stmt.kind == "create":
                # compared in isolation: one statement, parsed by both, so a
                # disagreement points at the column-definition reader rather
                # than at the union/skip/replace rules layered above it
                one = gm.Schema()
                one.apply_create(stmt.table, stmt.body)
                cases.append(("create", f.name, stmt.table, None,
                              probe_create(stmt.sql, PROBE_MARK),
                              list(one.tables.get(stmt.table, {})),
                              one.pks.get(stmt.table, [])))
            elif stmt.kind == "alter" and stmt.table in schema.tables:
                before = (list(schema.tables[stmt.table]),
                          list(schema.pks.get(stmt.table, [])))
                after = gm.Schema()
                after.tables[stmt.table] = dict(schema.tables[stmt.table])
                if stmt.table in schema.pks:
                    after.pks[stmt.table] = list(schema.pks[stmt.table])
                after.apply_alter(stmt.table, stmt.body)
                cases.append(("alter", f.name, stmt.table, before,
                              probe_alter(stmt.sql, PROBE_MARK),
                              list(after.tables[stmt.table]),
                              list(after.pks.get(stmt.table, []))))
            schema.apply(stmt)
    return cases


def introspect(client: Client, db: str) -> Tuple[Dict[str, List[str]],
                                                 Dict[str, List[str]]]:
    """Column lists and primary keys of every probe table, from MariaDB."""
    cols: Dict[str, List[str]] = {}
    for t, c in client.rows(
            "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA='{0}' ORDER BY TABLE_NAME, ORDINAL_POSITION"
            .format(db)):
        cols.setdefault(t, []).append(c)
    pks: Dict[str, List[str]] = {}
    for t, c in client.rows(
            "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.STATISTICS "
            "WHERE TABLE_SCHEMA='{0}' AND INDEX_NAME='PRIMARY' "
            "ORDER BY TABLE_NAME, SEQ_IN_INDEX".format(db)):
        pks.setdefault(t, []).append(c)
    return cols, pks


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(
        description="verify the manifest generator's DDL parse against "
                    "MariaDB")
    ap.add_argument("--oscar-src", required=True,
                    help="path to an oscaremr/oscar checkout")
    ap.add_argument("--mysql-cmd", default="mariadb",
                    help="client binary (default: mariadb)")
    ap.add_argument("--mysql-arg", action="append", default=[],
                    dest="mysql_args",
                    help="argument passed to the client; repeatable. Values "
                         "starting with '-' need the =form, e.g. "
                         "--mysql-arg=-uroot")
    ap.add_argument("--db", default="carlos_ddl_verify",
                    help="scratch schema, DROPPED and recreated (default: "
                         "carlos_ddl_verify)")
    ap.add_argument("--batch", type=int, default=400,
                    help="probe statements per client invocation")
    args = ap.parse_args(argv)

    gm = load_generator()
    oscar = Path(args.oscar_src)
    if not (oscar / "database/mysql/oscarinit.sql").is_file():
        print("--oscar-src does not look like an OSCAR checkout",
              file=sys.stderr)
        return 2

    leaked = reject_password_args(args.mysql_args)
    if leaked:
        print("refusing {0}: a password in the client's argv is readable by "
              "any local user. Use MYSQL_PWD or a client defaults file "
              "(--mysql-arg=--defaults-extra-file=...)."
              .format(", ".join(leaked)), file=sys.stderr)
        return 2
    if not IDENTIFIER_RE.match(args.db):
        print("--db must be a plain identifier ({0} is not): this script "
              "DROPs the schema it is given".format(args.db), file=sys.stderr)
        return 2

    client = Client(args.mysql_cmd, args.mysql_args, args.db)
    rc, _out, err = client.run(
        "DROP DATABASE IF EXISTS `{0}`; CREATE DATABASE `{0}`;".format(args.db),
        db="mysql")
    if rc != 0:
        print("cannot prepare the scratch schema: {0}".format(err[:400]),
              file=sys.stderr)
        return 2

    files = gm.expand_sources(oscar, gm.O19_SQL_SOURCES) + \
        gm.carlos_migration_files([gm.MIGRATION_DIR / "common",
                                   gm.MIGRATION_DIR / "on"])
    cases = collect(gm, files)
    print("statements to compare: {0} CREATE, {1} ALTER".format(
        sum(1 for c in cases if c[0] == "create"),
        sum(1 for c in cases if c[0] == "alter")))

    # One client invocation per batch rather than per statement: the corpus
    # carries thousands and a process spawn each would dominate the run.
    # --force continues past a statement the server rejects, which would
    # otherwise read as a generator defect -- the probe table simply keeps
    # its pre-ALTER shape. So every statement's line span in the fed script
    # is recorded, and the client's "ERROR ... at line N" is mapped back to
    # the statement it refused.
    refused_idx = set()
    script: List[str] = ["SET sql_mode='';"]
    spans: List[Tuple[int, int]] = []          # (first_line, statement index)
    batch_start = 0

    def flush() -> None:
        if len(script) <= 1:
            return
        rc, _out, err = client.run("\n".join(script), force=True)
        if rc != 0 and "at line" not in err:
            # --force returns non-zero for statement errors it continued
            # past, and those carry "at line N". Anything else -- a dropped
            # connection, a missing client -- means this batch never ran,
            # and its probes would then read as "server refused", i.e. as
            # silence. An oracle must not report OK for work it did not do.
            raise SystemExit(
                "client failed on a batch (not a statement error): {0}"
                .format(err[:400]))
        for line_no in re.findall(r"at line (\d+)", err):
            n = int(line_no)
            # the statement whose span contains this line
            hit = None
            for first, sidx in spans:
                if first <= n:
                    hit = sidx
                else:
                    break
            if hit is not None:
                refused_idx.add(hit)

    for idx, (kind, _f, _t, before, sql_tmpl, _cols, _pk) in enumerate(cases):
        probe = "p{0}".format(idx)
        pieces = []
        if kind == "alter":
            pieces.append(scaffold(probe, before[0], before[1]))
        pieces.append(sql_tmpl.replace(PROBE_MARK, probe) + ";")
        spans.append((sum(x.count("\n") + 1 for x in script) + 1, idx))
        script.extend(pieces)
        if len(script) - batch_start >= args.batch:
            flush()
            script, spans, batch_start = ["SET sql_mode='';"], [], 0
    flush()

    db_cols, db_pks = introspect(client, args.db)

    mismatches = []
    compared = refused = 0
    for idx, (kind, fname, table, _before, _sql, exp_cols, exp_pk) in \
            enumerate(cases):
        probe = "p{0}".format(idx)
        if probe not in db_cols or idx in refused_idx:
            # MariaDB refused the statement outright (a 2006 definition a
            # modern server rejects, e.g. AUTO_INCREMENT with no key, or an
            # ALTER whose column the scaffold does not carry). Not a parse
            # disagreement, and nothing to compare.
            refused += 1
            continue
        compared += 1
        got_cols = db_cols[probe]
        got_pk = db_pks.get(probe, [])
        if got_cols != exp_cols or (exp_pk is not None
                                    and list(got_pk) != list(exp_pk)):
            mismatches.append((kind, fname, table, exp_cols, got_cols,
                               exp_pk, got_pk))

    print("compared: {0}    not comparable (server refused): {1}"
          .format(compared, refused))
    if not mismatches:
        print("OK - the generator's parse agrees with MariaDB everywhere "
              "it could be compared")
        client.run("DROP DATABASE `{0}`;".format(args.db), db="mysql")
        return 0

    print("\nDISAGREEMENTS ({0}):".format(len(mismatches)))
    for kind, fname, table, exp_cols, got_cols, exp_pk, got_pk in mismatches:
        print("\n  {0} {1} ({2})".format(kind.upper(), table, fname))
        if exp_cols != got_cols:
            only_gen = [c for c in exp_cols if c not in got_cols]
            only_db = [c for c in got_cols if c not in exp_cols]
            if only_gen or only_db:
                print("    columns the generator invented: {0}"
                      .format(only_gen or "-"))
                print("    columns the generator MISSED:   {0}"
                      .format(only_db or "-"))
            else:
                print("    same columns, different order")
                print("    generator: {0}".format(exp_cols))
                print("    mariadb:   {0}".format(got_cols))
        if exp_pk is not None and list(exp_pk) != list(got_pk):
            print("    primary key: generator {0}, mariadb {1}"
                  .format(exp_pk, got_pk))
    print("\nthe scratch schema `{0}` is kept for inspection".format(args.db))
    return 1


if __name__ == "__main__":
    sys.exit(main())

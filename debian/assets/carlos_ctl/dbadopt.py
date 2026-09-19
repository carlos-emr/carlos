# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Adopting a pre-Flyway (OSCAR 19 / OpenO) database into the Flyway history.

`db-baseline` is documented as the verb that adopts an existing pre-Flyway
schema, but for most of its life it was a bare passthrough to Flyway's
`baseline` command -- and Flyway `baseline` writes ONE ROW into
`flyway_schema_history`. It never executes `V1__baseline_schema.sql`.

That is fine for the half of the genesis the forward migrations re-assert
anyway, and wrong for the rest. Stamping at 1.0.2 is an ASSERTION that common
`V1` and the province `V1.0.1`/`V1.0.2` are already satisfied by the adopted
datadir. An authentic OSCAR 19 datadir forked from the CARLOS lineage years
before those files existed, so the assertion is false in a specific, silent
way: every column that has been part of the schema since `V1` -- rather than
being added by a later forward migration -- is simply missing.

Nothing catches it. `db-migrate` reports success, `db-validate` passes (it
compares the HISTORY against the WAR's migrations, not the live schema against
either), and the install fails at runtime the first time a clinician logs in:

    Unknown column 's1_0.mfaSecret'           -> login dies
    ProviderPreference.defaultBillingLocation -> 500 immediately after login

So this module makes the assertion TRUE before it is stamped. It reads the
genesis DDL out of the deployed WAR -- the same files Flyway would have run --
and reconciles the live schema up to it with `CREATE TABLE IF NOT EXISTS` and
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`. Both are no-ops on anything already
present, which is what makes the whole pass safe to run unconditionally and
more than once.

Two further things block a real clinic's forward migrations, and both are
handled here because they are properties of the ADOPTED DATA, not of the
migrations:

  * `V1.0.5` seeds `icd10` with two statements and only the first says
    `INSERT IGNORE`. The second collides with the legacy database's own ICD-10
    reference rows. `V1.0.5` is present unchanged in published release tags, so
    it cannot be edited (that would break the checksum for every existing
    install); the colliding rows are cleared here instead, after being copied
    aside, and the migration then lays down the canonical seed it intended to.

  * `V1.0.11`/`V1.0.12` add UNIQUE indexes on the OHIP/MCEDT billing
    filenames. Legacy submission filenames (`HA036013.001`) do not encode the
    year, so a clinic that bills every January for a decade has ten genuinely
    DISTINCT, real billing-submission rows sharing one filename string. None of
    it is duplicate data and none of it is safe to delete, so the rows are
    disambiguated -- and the value actually submitted to the Ministry is kept
    in a backup table, because that string is the clinic's record of what it
    sent.

Everything this module writes is either additive (a column, a table) or
recorded before it changes (a backup table), and `--dry-run` prints the whole
plan without touching the database.
"""

import glob
import os
import re
import sys
import time

from . import config, dbops
from .util import WEBAPP, die, log, need_root, warn

# The migration set the DEPLOYED WAR carries, which is the only set whose
# checksums the application's boot gate will accept. Reading the genesis from
# anywhere else (the source tree, a downloaded Flyway CLI) would reconcile the
# schema up to a different V1 than the one this install was stamped against.
MIGRATION_ROOT = os.path.join(WEBAPP, "WEB-INF", "classes", "db", "migration")

# Where a value is parked before this module overwrites it. Prefixed rather
# than named after the table so an operator can find every one of them with a
# single SHOW TABLES LIKE.
BACKUP_PREFIX = "carlos_adopt_backup_"

# Identifier shapes accepted out of the packaged SQL. Nothing from the network
# reaches here -- these files ship inside the WAR -- but they are interpolated
# into generated DDL, so they are matched rather than trusted.
_IDENT = re.compile(r"\A[A-Za-z0-9_]+\Z")

# Prepended by `_run_script` to every script this module runs, for the same
# reasons the genesis dump and the `dbops` restore stream set them.
#
# NAMES: the genesis DDL carries utf8mb4 literals.
#
# sql_mode: NOT cosmetic. Seven genesis columns are declared
# DEFAULT '0000-00-00' / '0000-00-00 00:00:00', and under a sql_mode carrying
# NO_ZERO_DATE or TRADITIONAL those ALTER ... ADD COLUMN statements fail with
# "Invalid default value" PART WAY THROUGH, leaving a half-reconciled schema.
# The packaged drop-in sets sql_mode = "" (mariadb/60-carlos-emr.cnf), but that
# is only read at server start and db-baseline is an operator-invoked verb with
# no ordering against db-apply-settings -- so the session pins it, exactly as
# the restore stream in dbops does for the legacy eform seed.
SESSION_PRAGMAS = (
    "SET NAMES utf8mb4;",
    "SET SESSION sql_mode='';",
)

_CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`(?P<name>[^`]+)`\s*\((?P<body>.*?)\n\)(?P<tail>[^;]*);",
    re.DOTALL | re.IGNORECASE)

_CREATE_TEMPORARY = re.compile(
    r"CREATE\s+TEMPORARY\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?",
    re.IGNORECASE)

# A seed INSERT with neither IGNORE nor a column list nor a SELECT: the exact
# shape that aborts a migration when the adopted datadir already holds the key.
_PLAIN_INSERT = re.compile(
    r"^INSERT\s+INTO\s+`?(?P<table>[A-Za-z0-9_]+)`?\s+VALUES\s*(?P<rows>.*?);\s*$",
    re.DOTALL | re.IGNORECASE | re.MULTILINE)

# (leading integer, immediately following quoted field) of each VALUES tuple.
_ROW_KEY_AND_CODE = re.compile(r"\(\s*(-?\d+)\s*,\s*'((?:[^'\\]|\\.)*)'")

_CONSTRAINT_PREFIXES = (
    "PRIMARY KEY", "UNIQUE KEY", "UNIQUE INDEX", "KEY", "INDEX", "CONSTRAINT",
    "FOREIGN KEY", "FULLTEXT KEY", "FULLTEXT INDEX", "SPATIAL KEY",
    "SPATIAL INDEX", "CHECK",
)

# The UNIQUE indexes the forward migrations add over legacy billing filenames,
# with the column the disambiguation suffix has to stay inside. Ordered by the
# migration that introduces them so the report reads in migration order.
BILLING_UNIQUE = (
    # table, column, order-by deciding which row keeps the original string, and
    # the suffix's human-readable part (already qualified with the UPDATE alias
    # `b`). The primary key is appended to whatever this yields, so the result
    # is unique even when two submissions share the filename AND the year.
    ("billing_on_diskname", "ohipfilename", "createdatetime", "YEAR(b.`createdatetime`)"),
    ("billing_on_filename", "htmlfilename", "timestamp", None),
)


class TableDef:
    """One `CREATE TABLE` out of the genesis DDL.

    `columns` is ordered as the genesis declares them; `statement` is the
    original text, reused verbatim (bar an injected IF NOT EXISTS) when the
    table is missing outright."""

    def __init__(self, name, columns, statement):
        self.name = name
        self.columns = columns          # [(column_name, definition), ...]
        self.statement = statement


def _warn(message: str) -> None:
    """`util.warn`, with stdout flushed first.

    `log` writes to stdout and `warn` to stderr, and Python BLOCK-buffers
    stdout whenever it is a pipe or a file. An operator keeping a transcript of
    a clinical adoption -- `carlos-ctl db-baseline > adopt.log 2>&1` -- would
    otherwise get every warning hoisted above progress lines printed before it,
    which is exactly the wrong order for the one message that has to be acted
    on. Flushing first keeps the transcript honest."""
    sys.stdout.flush()
    warn(message)


def _log(message: str) -> None:
    """`util.log`, flushed.

    The same buffering trap as `_warn`, and here it inverts the one ordering
    the whole design rests on. This verb shells out to the Flyway runner, whose
    output goes straight to the inherited descriptor while Python's own stdout
    sits in a block buffer. Unflushed, an adoption transcript shows "stamped
    flyway_schema_history" ABOVE the reconciliation lines that in fact came
    first -- reading as though the schema was stamped before it was
    reconciled, which is precisely the bug this verb exists to prevent."""
    log(message)
    sys.stdout.flush()


def _is_constraint(line: str) -> bool:
    upper = line.upper()
    return any(upper.startswith(p) for p in _CONSTRAINT_PREFIXES)


def parse_create_tables(sql: str):
    """Parse mysqldump-shaped `CREATE TABLE` statements into `TableDef`s.

    The genesis files are mysqldump output, so one column per line and the
    closing paren on its own line -- which is what makes a regex honest here
    rather than a half-written SQL parser."""
    tables = {}
    for match in _CREATE_TABLE.finditer(sql):
        name = match.group("name")
        if not _IDENT.match(name):
            continue
        columns = []
        for raw in match.group("body").splitlines():
            line = raw.strip()
            if not line or not line.startswith("`") or _is_constraint(line):
                continue
            # Only a TRAILING separator comma goes; a comma inside enum(...)
            # or a DEFAULT literal is part of the definition.
            line = line.rstrip().rstrip(",")
            column = re.match(r"`([^`]+)`\s+(.*)", line, re.DOTALL)
            if not column or not _IDENT.match(column.group(1)):
                continue
            columns.append((column.group(1), column.group(2).strip()))
        tables[name] = TableDef(name, columns, match.group(0))
    return tables


def parse_temporary_tables(sql: str):
    """Names created as TEMPORARY, which never collide with adopted data."""
    return {m.group(1) for m in _CREATE_TEMPORARY.finditer(sql)}


def parse_plain_seed_inserts(sql: str):
    """`table -> [(leading integer, next quoted field), ...]` for unguarded
    seed INSERTs.

    The leading integer is only a CANDIDATE primary key; the caller confirms
    against the live schema that the table's primary key really is that single
    first integer column before deleting anything on the strength of it. The
    quoted field that follows is carried so the caller can check that the rows
    it is about to clear really do correspond to the canonical ones."""
    temporary = parse_temporary_tables(sql)
    found = {}
    for match in _PLAIN_INSERT.finditer(sql):
        table = match.group("table")
        if table in temporary or not _IDENT.match(table):
            continue
        rows = [(int(m.group(1)), m.group(2))
                for m in _ROW_KEY_AND_CODE.finditer(match.group("rows"))]
        if rows:
            found.setdefault(table, []).extend(rows)
    return found


def genesis_files(schema_province: str, root: str = None):
    """The genesis a `baseline` stamp asserts: common `V1` and the province
    `V1.0.1` schema.

    `V1.0.2` is province REFERENCE DATA, not structure. It is deliberately not
    reconciled: the adopted datadir brings its own reference rows and its own
    provider records, and replaying the seed over them is a data decision, not
    a schema one."""
    root = root or MIGRATION_ROOT
    files = sorted(glob.glob(os.path.join(root, "common", "V1__*.sql")))
    files += sorted(glob.glob(os.path.join(root, schema_province, "V1.0.1__*.sql")))
    return files


def forward_migration_files(schema_province: str, root: str = None):
    """Every migration Flyway will actually RUN after a 1.0.2 baseline stamp."""
    root = root or MIGRATION_ROOT
    files = []
    for area in ("common", schema_province):
        for path in glob.glob(os.path.join(root, area, "V1.0.*__*.sql")):
            version = re.search(r"V(\d+(?:\.\d+)*)__", os.path.basename(path))
            if version and _version_tuple(version.group(1)) > (1, 0, 2):
                files.append(path)
    return sorted(files, key=lambda p: _version_tuple(
        re.search(r"V(\d+(?:\.\d+)*)__", os.path.basename(p)).group(1)))


def _version_tuple(text: str):
    return tuple(int(part) for part in text.split("."))


def reconciliation_statements(tables):
    """The DDL that makes a live schema satisfy `tables`.

    Every statement is guarded, so the pass is a no-op against a schema that is
    already complete and safe to repeat after a partial run.

    Two deliberate omissions:

    * No `AFTER` clause. An added column lands at the end of the table rather
      than in its genesis position. Column ORDER is not part of any contract
      CARLOS relies on -- Hibernate binds by name -- and threading `AFTER`
      through would break the moment a preceding column is one of the
      AUTO_INCREMENT columns skipped below.
    * AUTO_INCREMENT columns are never added. `ADD COLUMN ... AUTO_INCREMENT`
      requires the column to become a key in the same breath, and a table that
      has lost its auto-increment primary key is not a table this pass should
      quietly rebuild. They are reported instead."""
    statements = []
    skipped = []
    for name in sorted(tables):
        table = tables[name]
        statements.append(_with_if_not_exists(table.statement))
        for column, definition in table.columns:
            if re.search(r"\bAUTO_INCREMENT\b", definition, re.IGNORECASE):
                skipped.append((name, column))
                continue
            statements.append(
                "ALTER TABLE `{0}` ADD COLUMN IF NOT EXISTS `{1}` {2};".format(
                    name, column, definition))
    return statements, skipped


def _with_if_not_exists(statement: str) -> str:
    if re.search(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS", statement, re.IGNORECASE):
        return statement
    return re.sub(r"CREATE\s+TABLE\s+", "CREATE TABLE IF NOT EXISTS ",
                  statement, count=1, flags=re.IGNORECASE)


def _read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


# --- the database side -----------------------------------------------------

def _client(dbops, db_name, args, **kw):
    return dbops.db_root(["--database", db_name] + args, **kw)


def _scalar(dbops, db_name, sql, default=None):
    cp = _client(dbops, db_name, ["-N", "-B", "-e", sql], capture_output=True)
    if cp.returncode != 0:
        return default
    text = (cp.stdout or "").strip()
    return text.splitlines()[-1] if text else default


def _count(dbops, db_name, sql) -> int:
    """A count, treating an unanswerable query as zero.

    Only for probes where "cannot tell" and "none" lead to the same safe
    action -- planning work that simply will not be scheduled. Anything that
    GATES a destructive step uses `_count_or_die`."""
    value = _scalar(dbops, db_name, sql, "0")
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _count_or_die(dbops, db_name, sql, what) -> int:
    """A count where silence is not the same answer as zero.

    `_count` cannot distinguish "no rows" from "the query never ran", and the
    duplicate re-check is the ONLY thing standing between a truncation
    collision and the CREATE UNIQUE INDEX in V1.0.11/V1.0.12. A dropped
    connection there would have let the stamp proceed and surfaced the failure
    inside db-migrate, against a database already marked adopted."""
    cp = _client(dbops, db_name, ["-N", "-B", "-e", sql], capture_output=True)
    if cp.returncode != 0:
        tail = (cp.stderr or "").strip().splitlines()
        die("could not {0}: mariadb exited {1}{2}".format(
            what, cp.returncode, " (" + tail[-1] + ")" if tail else ""))
    text = (cp.stdout or "").strip()
    try:
        return int(text.splitlines()[-1])
    except (IndexError, ValueError):
        die("could not {0}: mariadb answered {1!r}".format(what, text[:80]))


def _run_script(dbops, db_name, script: str, what: str) -> None:
    script = "\n".join(list(SESSION_PRAGMAS) + [script])
    cp = _client(dbops, db_name, ["-B"], input=script, capture_output=True)
    if cp.returncode != 0:
        # stderr was CAPTURED, so nothing reached the operator's terminal on its
        # own; print it rather than reducing a multi-line server error -- and a
        # 10,000-statement script that dies in the middle needs the line number
        # MariaDB reports, not a summary.
        detail = (cp.stderr or "").strip()
        if detail:
            sys.stderr.write(detail + "\n")
        die("{0} failed (mariadb exited {1}); the database is partially "
            "changed -- re-running db-baseline is safe and resumes from where "
            "this stopped".format(what, cp.returncode))


def _table_exists(dbops, db_name, table) -> bool:
    return _count_or_die(
        dbops, db_name,
        "SELECT COUNT(*) FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{0}'".format(table),
        "check whether `{0}` exists".format(table)) > 0


def _single_integer_pk(dbops, db_name, table):
    """The table's primary key when it is ONE integer column, else None.

    This is the gate on the seed-collision clearing below: a compound or
    non-integer key means the leading integer parsed out of the seed row is
    not the key, and nothing is deleted."""
    cp = _client(dbops, db_name, [
        "-N", "-B", "-e",
        "SELECT c.COLUMN_NAME, c.DATA_TYPE, s.SEQ_IN_INDEX, c.ORDINAL_POSITION "
        "FROM information_schema.COLUMNS c "
        "JOIN information_schema.STATISTICS s ON s.TABLE_SCHEMA = c.TABLE_SCHEMA "
        " AND s.TABLE_NAME = c.TABLE_NAME AND s.COLUMN_NAME = c.COLUMN_NAME "
        "WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = '{0}' "
        "  AND s.INDEX_NAME = 'PRIMARY' "
        # The parsed value is the FIRST field of each seed tuple, so it is the
        # key only if the primary key is also the first column. Without this a
        # future seed whose leading field is, say, demographic_no would produce
        # DELETE ... WHERE id IN (<demographic numbers>) -- deleting live rows
        # that were never backed up.
        "ORDER BY s.SEQ_IN_INDEX".format(table),
    ], capture_output=True)
    if cp.returncode != 0:
        die("could not inspect the primary key of `{0}`".format(table))
    rows = [line.split("\t") for line in (cp.stdout or "").strip().splitlines() if line]
    if len(rows) != 1 or len(rows[0]) != 4:
        return None
    column, data_type, sequence, position = rows[0]
    if sequence != "1" or position != "1" or data_type.lower() not in (
            "int", "bigint", "smallint", "mediumint", "tinyint"):
        return None
    return column


def _backup_table(table: str) -> str:
    return BACKUP_PREFIX + table


# --- plan steps ------------------------------------------------------------

def _live_column_list(dbops, db_name, table):
    """The table's columns in ordinal order, as the live database has them."""
    cp = _client(dbops, db_name, [
        "-N", "-B", "-e",
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{0}' "
        "ORDER BY ORDINAL_POSITION".format(table),
    ], capture_output=True)
    if cp.returncode != 0:
        die("could not inspect the columns of `{0}`".format(table))
    return [line.strip() for line in (cp.stdout or "").splitlines() if line.strip()]


def applied_versions(dbops, db_name):
    """Versions `flyway_schema_history` records as successfully applied.

    Empty when there is no history yet -- an un-adopted legacy datadir -- which
    is the same answer as "everything above the baseline is pending"."""
    if not _table_exists(dbops, db_name, "flyway_schema_history"):
        return set()
    cp = _client(dbops, db_name, [
        "-N", "-B", "-e",
        "SELECT `version` FROM `flyway_schema_history` "
        "WHERE `success` = 1 AND `version` IS NOT NULL",
    ], capture_output=True)
    if cp.returncode != 0:
        die("could not read flyway_schema_history")
    return {line.strip() for line in (cp.stdout or "").splitlines() if line.strip()}


def plan_seed_collisions(dbops, db_name, schema_province, applied, root=None):
    """Rows an unguarded seed INSERT in a PENDING migration would collide with.

    `applied` is the set of versions Flyway already records as successful; a
    migration in it will not run again, so its seed rows are the canonical ones
    that are SUPPOSED to be there. Clearing those is not preparation, it is
    deletion: re-running db-baseline against an already-adopted database
    removed 1070 icd10 rows that the following db-migrate -- with nothing
    pending -- never put back, and db-validate still passed because it compares
    the history against the WAR, not the data.

    Returns `[(table, pk_column, [keys], present, source, columns, diverging),
    ...]` for the collisions actually present in this database."""
    collisions = []
    for path in forward_migration_files(schema_province, root):
        source = os.path.basename(path)
        version = re.search(r"V(\d+(?:\.\d+)*)__", source)
        if version and version.group(1) in applied:
            continue
        for table, rows in sorted(parse_plain_seed_inserts(_read(path)).items()):
            if not _table_exists(dbops, db_name, table):
                continue
            pk = _single_integer_pk(dbops, db_name, table)
            if pk is None:
                _warn("{0}: {1} carries an unguarded seed INSERT but the live "
                     "table's primary key is not a single leading integer "
                     "column; leaving it alone -- if the migration fails on a "
                     "duplicate key here, it needs a human".format(source, table))
                continue
            canonical = dict(rows)
            keys = sorted(canonical)
            key_list = ",".join(str(k) for k in keys)
            present = _count_or_die(
                dbops, db_name,
                "SELECT COUNT(*) FROM `{0}` WHERE `{1}` IN ({2})".format(
                    table, pk, key_list),
                "check `{0}` for seed-collision keys".format(table))
            if present == 0:
                continue

            columns = _live_column_list(dbops, db_name, table)
            if not columns:
                die("could not read the columns of `{0}`".format(table))
            backup = _backup_table(table)
            if _table_exists(dbops, db_name, backup):
                # CREATE TABLE IF NOT EXISTS is a no-op over a backup left by an
                # earlier run, and a different legacy dump loaded since then may
                # have changed the table's shape. Refuse rather than write the
                # only copy of the rows being deleted into a mismatched table.
                existing = _live_column_list(dbops, db_name, backup)
                if set(existing) != set(columns):
                    die("`{0}` already exists with a different shape than "
                        "`{1}`; move it aside before adopting again (it holds "
                        "rows cleared by an earlier run)".format(backup, table))

            diverging = _check_seed_rows(dbops, db_name, table, pk, canonical,
                                         present, columns, source)
            collisions.append((table, pk, keys, present, source, columns,
                               diverging))
    return collisions


def _check_seed_rows(dbops, db_name, table, pk, canonical, present, columns,
                     source):
    """Report rows whose code differs from the replacement, and return how many.

    The whole premise of clearing these keys is that the migration will lay
    down the SAME reference rows. Where it does not, a code the legacy database
    carried at that id disappears -- and `Icd10DaoImpl` looks this table up by
    CODE, never by id, so a clinical record still referencing it stops
    resolving.

    This REPORTS rather than refuses, deliberately. Refusing blocks the whole
    adoption on a reference-table discrepancy and leaves the operator
    hand-writing SQL against a clinical database at go-live, which is the more
    dangerous of the two. The rows are preserved in the backup table, so
    nothing is destroyed, and the count is repeated once more when adoption
    finishes so it does not scroll past.

    Everything else here still fails closed: a missing code column, an
    unanswerable comparison, an unparseable row, or a collision count that
    moved under us all stop adoption, because those mean the comparison itself
    cannot be trusted."""
    if len(columns) < 2:
        die("`{0}` has no code column to compare with {1}".format(table, source))
    code_column = columns[1]
    cp = _client(dbops, db_name, [
        "-N", "-B", "-e",
        "SELECT `{0}`, `{1}` FROM `{2}` WHERE `{0}` IN ({3})".format(
            pk, code_column, table, ",".join(str(k) for k in canonical)),
    ], capture_output=True)
    if cp.returncode != 0:
        die("could not compare live `{0}` codes with {1}".format(table, source))
    diverging = 0
    checked = 0
    for line in (cp.stdout or "").splitlines():
        parts = line.rstrip("\n").split("\t")
        if len(parts) != 2:
            die("could not parse a live `{0}` code while checking {1}".format(
                table, source))
        try:
            key = int(parts[0])
        except ValueError:
            die("could not parse a live `{0}` key while checking {1}".format(
                table, source))
        checked += 1
        if canonical.get(key) != parts[1]:
            diverging += 1
    if checked != present:
        die("`{0}` changed while checking seed codes: counted {1} collision "
            "row(s) but read {2}; retry adoption".format(
                table, present, checked))
    if diverging:
        _warn("{0}: {1} row(s) hold a different `{2}` than the seed in {3} will "
             "replace them with. The originals are preserved in `{4}`, but any "
             "clinical record referencing one of those values will stop "
             "resolving -- reconcile them before go-live".format(
                 table, diverging, code_column, source, _backup_table(table)))
    return diverging


def seed_collision_script(table, pk, keys, columns) -> str:
    """Copy the colliding rows aside, then clear exactly those keys.

    Only the keys the migration is about to insert are touched: a legacy row
    the canonical seed does not cover keeps its place.

    `columns` is the LIVE column list, and it is spelled out on both sides of
    the copy. `SELECT *` into a column-less INSERT binds by POSITION, and
    `CREATE TABLE IF NOT EXISTS` is a silent no-op when a backup from an
    earlier run is already there -- so a backup left behind before a different
    legacy dump was loaded would take the new rows into the wrong columns, and
    the only copy of what is about to be DELETEd would be quietly wrong. The
    caller checks the shapes agree; this makes the statement itself immune to
    column ORDER."""
    backup = _backup_table(table)
    key_list = ",".join(str(k) for k in keys)
    column_list = ", ".join("`{0}`".format(c) for c in columns)
    # An old backup can contain the same primary key from another restored
    # datadir. Only delete a live row when the backup still has every byte of
    # that row. The post-copy collision re-check then prevents a false stamp.
    copied = " AND ".join("BINARY b.`{0}` <=> BINARY t.`{0}`".format(c)
                          for c in columns)
    return "\n".join([
        "CREATE TABLE IF NOT EXISTS `{0}` LIKE `{1}`;".format(backup, table),
        "INSERT IGNORE INTO `{0}` ({1}) SELECT {1} FROM `{2}` "
        "WHERE `{3}` IN ({4});".format(backup, column_list, table, pk, key_list),
        "DELETE t FROM `{0}` t JOIN `{1}` b ON b.`{2}` = t.`{2}` "
        "WHERE t.`{2}` IN ({3}) AND {4};".format(
            table, backup, pk, key_list, copied),
    ])


def _column_width(dbops, db_name, table, column) -> int:
    """The live CHARACTER_MAXIMUM_LENGTH, so the suffix is kept inside the
    column this database actually has rather than the one the genesis
    declared."""
    cp = _client(dbops, db_name, [
        "-N", "-B", "-e",
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{0}' "
        "  AND COLUMN_NAME = '{1}'".format(table, column),
    ], capture_output=True)
    if cp.returncode != 0:
        die("could not inspect the width of `{0}`.`{1}`".format(table, column))
    try:
        width = int((cp.stdout or "").strip())
    except (TypeError, ValueError):
        die("could not read the width of `{0}`.`{1}`".format(table, column))
    if width <= 0:
        die("invalid width for `{0}`.`{1}`: {2}".format(table, column, width))
    return width


def plan_billing_duplicates(dbops, db_name):
    """Legacy billing filenames that violate the UNIQUE indexes V1.0.11/V1.0.12
    add. Absent tables (a non-Ontario install) simply yield nothing."""
    found = []
    for table, column, order_by, suffix in BILLING_UNIQUE:
        if not _table_exists(dbops, db_name, table):
            continue
        extra = _count_or_die(
            dbops, db_name,
            ("SELECT COALESCE(SUM(n - 1), 0) FROM (SELECT COUNT(*) AS n "
             "FROM `{0}` WHERE `{1}` IS NOT NULL GROUP BY `{1}` "
             "HAVING n > 1) d").format(table, column),
            "count duplicates in {0}.{1}".format(table, column))
        if extra:
            found.append((table, column, order_by, suffix, extra,
                          _column_width(dbops, db_name, table, column)))
    return found


def billing_disambiguation_script(table, column, order_by, suffix, width=50) -> str:
    """Make every value in `column` unique WITHOUT losing a single row.

    The row that submitted first keeps the filename verbatim; every later row
    gains a suffix. The suffix always ends in the primary key, so the result is
    unique by construction even when two submissions share both the filename
    and the year -- the shape the field-expedient `-YEAR` fix got wrong.

    The original string is copied into a backup table first. For
    `ohipfilename` that string is the clinic's record of the filename actually
    sent to the Ministry, and this rewrite is the one part of adoption that
    changes a value a human may later have to reconcile against an MOH
    remittance."""
    backup = _backup_table(table)
    tag = "CONCAT('-', {0}, '-', b.`id`)".format(
        "COALESCE({0}, 'x')".format(suffix) if suffix else "'dup'")
    return "\n".join([
        "CREATE TABLE IF NOT EXISTS `{0}` ("
        "  `row_id` bigint NOT NULL,"
        "  `column_name` varchar(64) NOT NULL,"
        "  `original_value` varchar(255) DEFAULT NULL,"
        "  PRIMARY KEY (`row_id`, `column_name`)"
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;".format(backup),
        # Ranked once, used twice: the same window decides who is backed up and
        # who is rewritten, so the backup can never disagree with the change.
        "CREATE TEMPORARY TABLE `_carlos_adopt_rank` AS "
        "SELECT `id` AS row_id, ROW_NUMBER() OVER ("
        "  PARTITION BY `{1}` ORDER BY `{2}`, `id`) AS rn "
        "FROM `{0}` WHERE `{1}` IS NOT NULL;".format(table, column, order_by),
        "INSERT IGNORE INTO `{0}` (`row_id`, `column_name`, `original_value`) "
        "SELECT b.`id`, '{2}', b.`{2}` FROM `{1}` b "
        "JOIN `_carlos_adopt_rank` r ON r.row_id = b.`id` WHERE r.rn > 1;".format(
            backup, table, column),
        # LEFT() keeps the result inside the column, which matters for a long
        # legacy filename; the post-check below catches the truncation collision
        # that would imply.
        # `timestamp` is declared ON UPDATE current_timestamp() on both billing
        # tables, so an UPDATE that touches the row rewrites the clinic's record
        # of WHEN it submitted -- and on billing_on_filename that column is the
        # very ORDER BY this ranking depends on, so a second run would rank
        # differently. Assigning it to itself suppresses the auto-update; it is
        # not a no-op and must not be "tidied" away.
        "UPDATE `{0}` b JOIN `_carlos_adopt_rank` r ON r.row_id = b.`id` "
        "JOIN `{4}` prior ON prior.`row_id` = b.`id` "
        "  AND prior.`column_name` = '{1}' "
        "  AND BINARY prior.`original_value` <=> BINARY b.`{1}` "
        "SET b.`{1}` = CONCAT("
        "  LEFT(b.`{1}`, GREATEST(1, {2} - CHAR_LENGTH({3}))), {3}), "
        "b.`timestamp` = b.`timestamp` "
        "WHERE r.rn > 1;".format(table, column, width, tag, backup),
        "DROP TEMPORARY TABLE `_carlos_adopt_rank`;",
    ])


def _remaining_duplicates(dbops, db_name, table, column) -> int:
    return _count_or_die(
        dbops, db_name,
        "SELECT COALESCE(SUM(n - 1), 0) FROM (SELECT COUNT(*) AS n "
        "FROM `{0}` WHERE `{1}` IS NOT NULL GROUP BY `{1}` "
        "HAVING n > 1) d".format(table, column),
        "re-check {0}.{1} for duplicates".format(table, column))


# --- the verb --------------------------------------------------------------

_USAGE = """usage: carlos-ctl db-baseline [--dry-run] [--stamp-only]

Adopt an existing pre-Flyway (OSCAR 19 / OpenO) database: reconcile the live
schema up to the genesis this stamp asserts, prepare the adopted data for the
forward migrations, then stamp flyway_schema_history.

  --dry-run     print the whole plan and change nothing
  --stamp-only  the bare Flyway baseline stamp, reconciling nothing (this is
                what db-baseline did before; it leaves an adopted datadir
                missing every column added to the genesis since the fork)
"""


def cmd_db_baseline(argv) -> int:
    dry_run = stamp_only = False
    for arg in argv:
        if arg in ("-h", "--help", "help"):
            print(_USAGE, end="")
            return 0
        elif arg == "--dry-run":
            dry_run = True
        elif arg == "--stamp-only":
            stamp_only = True
        else:
            die("unknown option: {0}".format(arg))

    if dry_run and stamp_only:
        die("--dry-run and --stamp-only are mutually exclusive")

    need_root("db-baseline")
    dbops.require_db_root()
    settings = config.load()

    if stamp_only:
        _warn("--stamp-only: stamping without reconciling. An adopted OSCAR 19 "
             "datadir will be missing every column added to the genesis since "
             "it was forked, and the failure surfaces at login, not here.")
        return dbops.run_flyway("baseline")

    if not os.path.isdir(MIGRATION_ROOT):
        die("{0} does not hold the packaged migrations; is the CARLOS webapp "
            "deployed?".format(MIGRATION_ROOT))

    db_name = settings.db_name
    files = genesis_files(settings.schema_province)
    if not files:
        die("no genesis migration found under {0} for province '{1}'".format(
            MIGRATION_ROOT, settings.schema_province))

    tables = {}
    for path in files:
        tables.update(parse_create_tables(_read(path)))
    if not tables:
        die("parsed no CREATE TABLE out of {0}".format(", ".join(files)))

    statements, skipped = reconciliation_statements(tables)
    total_columns = sum(len(t.columns) for t in tables.values())
    _log("genesis: {0} table(s), {1} column(s) from {2}".format(
        len(tables), total_columns, ", ".join(os.path.basename(f) for f in files)))

    schema = live_schema(dbops, db_name)
    stale = _stale_history(dbops, db_name, tables, schema)
    # A stale history is renamed aside below, so nothing it records will be
    # honoured: every forward migration becomes pending again.
    applied = set() if stale else applied_versions(dbops, db_name)
    collisions = plan_seed_collisions(dbops, db_name, settings.schema_province,
                                      applied)
    duplicates = plan_billing_duplicates(dbops, db_name)

    for table, pk, keys, present, source, _columns, _div in collisions:
        _log("{0}: {1} row(s) in `{2}` collide with the unguarded seed in {3}; "
            "they will be copied to `{4}` and cleared so the migration can lay "
            "down its canonical rows".format(
                "PLAN" if dry_run else "preparing", present, table, source,
                _backup_table(table)))
    for table, column, _order, _suffix, extra, _width in duplicates:
        _log("{0}: {1} row(s) in `{2}`.`{3}` share a filename with an earlier "
            "row; they will be suffixed (originals kept in `{4}`) so the "
            "UNIQUE index can be created without discarding billing "
            "history".format("PLAN" if dry_run else "preparing", extra, table,
                             column, _backup_table(table)))
    if stale:
        _log("{0}: flyway_schema_history describes a schema this database no "
            "longer has -- the installer stamped it before the legacy dump "
            "replaced the tables. It will be renamed aside, not dropped."
            .format("PLAN" if dry_run else "preparing"))
    for table, column in skipped:
        live = schema.get(table.lower())
        if live is not None and column.lower() not in live:
            die("`{0}`.`{1}` is AUTO_INCREMENT and absent from an existing "
                "live table; refusing to stamp an incomplete genesis. A table "
                "that has lost its auto-increment key needs a human".format(
                    table, column))

    if dry_run:
        _log("PLAN: {0} reconciliation statement(s) would run ({1} genesis "
            "column(s) are missing today), then 'flyway baseline'. Nothing was "
            "changed.".format(len(statements),
                              len(missing_genesis_columns(tables, schema))))
        return 0

    before = _schema_size(dbops, db_name)

    if stale:
        parked = "flyway_schema_history_preadopt_{0}".format(int(time.time()))
        _run_script(dbops, db_name,
                    "RENAME TABLE `flyway_schema_history` TO `{0}`;".format(parked),
                    "parking the stale migration history")
        _log("stale history renamed to `{0}`".format(parked))

    # Structure first: the data preparation below reads columns the genesis
    # reconciliation may have just added. FOREIGN_KEY_CHECKS is off for the
    # duration because the genesis declares foreign keys and these statements
    # are emitted in NAME order, so a child table can be created before its
    # parent -- exactly why the genesis file itself opens the same way.
    _run_script(dbops, db_name,
                "\n".join(["SET FOREIGN_KEY_CHECKS=0;"] + statements
                          + ["SET FOREIGN_KEY_CHECKS=1;"]),
                "genesis reconciliation")
    after = _schema_size(dbops, db_name)
    _log("reconciled: {0} table(s) and {1} column(s) added; {2} table(s) "
        "checked".format(after[0] - before[0], after[1] - before[1], len(tables)))

    for table, pk, keys, _present, _source, columns, _div in collisions:
        _run_script(dbops, db_name,
                    seed_collision_script(table, pk, keys, columns),
                    "clearing seed collisions in {0}".format(table))
        left = _count_or_die(
            dbops, db_name,
            "SELECT COUNT(*) FROM `{0}` WHERE `{1}` IN ({2})".format(
                table, pk, ",".join(str(k) for k in keys)),
            "re-check `{0}` for seed-collision keys".format(table))
        if left:
            die("`{0}` still has {1} seed-collision row(s); the backup in "
                "`{2}` does not match the live rows. Refusing to stamp or "
                "delete an unpreserved row".format(table, left,
                                                   _backup_table(table)))

    for table, column, order_by, suffix, _extra, width in duplicates:
        _run_script(dbops, db_name,
                    billing_disambiguation_script(table, column, order_by,
                                                  suffix, width),
                    "disambiguating {0}.{1}".format(table, column))
        left = _remaining_duplicates(dbops, db_name, table, column)
        if left:
            die("{0}.{1} still has {2} duplicate value(s) after "
                "disambiguation; the UNIQUE index in V1.0.11/V1.0.12 would "
                "still fail. Originals are in `{3}`.".format(
                    table, column, left, _backup_table(table)))

    rc = dbops.run_flyway("baseline")
    if rc == 0:
        # Repeated here on purpose. The per-table warning was printed before
        # 10,000 reconciliation statements and a Flyway stamp went past it, and
        # this one is the operator's last chance to see that a reference code
        # their records point at is about to stop resolving.
        _log("adopted. Now run: carlos-ctl db-migrate")
        diverging = sum(c[6] for c in collisions)
        if diverging:
            _warn("{0} reference row(s) did not match the code the migration "
                  "will replace them with. They are preserved in `{1}*` "
                  "tables. Reconcile them before go-live: a clinical record "
                  "pointing at one of those codes will not resolve.".format(
                      diverging, BACKUP_PREFIX))
    return rc


def _schema_size(dbops, db_name):
    return (
        _count(dbops, db_name, "SELECT COUNT(*) FROM information_schema.TABLES "
                               "WHERE TABLE_SCHEMA = DATABASE()"),
        _count(dbops, db_name, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                               "WHERE TABLE_SCHEMA = DATABASE()"),
    )


def live_schema(dbops, db_name):
    """`{table: {column, ...}}` for the whole database, lowercased, in ONE
    query. Probing 410 tables one information_schema round trip at a time is
    the difference between an adoption that feels instant and one an operator
    interrupts."""
    cp = _client(dbops, db_name, [
        "-N", "-B", "-e",
        "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA = DATABASE()",
    ], capture_output=True)
    if cp.returncode != 0:
        die("could not inspect the live schema")
    schema = {}
    for line in (cp.stdout or "").splitlines():
        parts = line.rstrip("\n").split("\t")
        if len(parts) == 2:
            schema.setdefault(parts[0].lower(), set()).add(parts[1].lower())
    return schema


def missing_genesis_columns(tables, schema):
    """Genesis columns absent from a table the live schema DOES have.

    A table missing outright is not counted: that is an ordinary gap the
    reconciliation fills, whereas a table that exists with FEWER columns than
    the genesis declares is the fingerprint of a datadir forked before those
    columns were added."""
    missing = []
    for name in sorted(tables):
        live = schema.get(name.lower())
        if not live:
            continue
        for column, _definition in tables[name].columns:
            if column.lower() not in live:
                missing.append((name, column))
    return missing


def _stale_history(dbops, db_name, tables, schema) -> bool:
    """Whether `flyway_schema_history` is bookkeeping for a schema that is gone.

    The installer runs `db-migrate` on the fresh database it provisions, so the
    history is already populated by the time an operator loads a legacy dump
    over it -- and mysqldump's `DROP TABLE IF EXISTS` replaces the data tables
    but not this one, because an authentic OSCAR 19 dump never contained it.
    Flyway then refuses to baseline: "flyway_schema_history already contains
    migrations".

    Two signals together, because either alone is a false positive:

    * NO BASELINE MARKER. A history written by `migrate` against an empty
      database records `V1`/`V1.0.1`/`V1.0.2` as ordinary applied migrations.
      A history written by `baseline` carries a BASELINE row. That row is
      exactly what makes re-running `baseline` a harmless no-op, so a schema
      that already has one is a previously ADOPTED datadir whose history is
      correct and must be left alone -- even though it, too, can be short of
      genesis columns if it was adopted before this reconciliation existed.
    * GENESIS COLUMNS MISSING. Otherwise this is an ordinary healthy install
      and nothing here should touch its history at all."""
    if not _table_exists(dbops, db_name, "flyway_schema_history"):
        return False
    if _count_or_die(dbops, db_name,
                     "SELECT COUNT(*) FROM `flyway_schema_history`",
                     "inspect migration history") == 0:
        return False
    if _count_or_die(dbops, db_name,
                     "SELECT COUNT(*) FROM `flyway_schema_history` "
                     "WHERE `type` = 'BASELINE'",
                     "check the migration baseline marker") > 0:
        return False
    return bool(missing_genesis_columns(tables, schema))

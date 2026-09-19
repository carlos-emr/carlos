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
from .util import WEBAPP, die, log, need_root, sql_escape, warn

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

# Seed INSERTs without a column list or SELECT. Unguarded statements need
# collision clearing; guarded ones can also restore a displaced code.
_SEED_INSERT = re.compile(
    r"^INSERT\s+(?P<ignore>IGNORE\s+)?INTO\s+`?(?P<table>[A-Za-z0-9_]+)`?\s+VALUES\s*(?P<rows>.*?);\s*$",
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

# Both published Ontario migrations establish the same two index artifacts.
# Match the recorded version AND script, so a BASELINE at that version or an
# unrelated migration cannot be mistaken for a successful index migration.
BILLING_INDEX_MIGRATIONS = {
    "1.0.11": "V1.0.11__billing_filename_unique_indexes.sql",
    "1.0.12": "V1.0.12__portable_billing_filename_unique_indexes.sql",
}


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


def parse_plain_seed_inserts(sql: str, include_ignored=False):
    """`table -> [(leading integer, next quoted field), ...]` for unguarded
    seed INSERTs. Include guarded INSERT IGNORE seeds when requested so their
    codes can also be considered as survivors.

    The leading integer is only a CANDIDATE primary key; the caller confirms
    against the live schema that the table's primary key really is that single
    first integer column before deleting anything on the strength of it. The
    quoted field that follows is carried so the caller can check that the rows
    it is about to clear really do correspond to the canonical ones."""
    temporary = parse_temporary_tables(sql)
    found = {}
    for match in _SEED_INSERT.finditer(sql):
        if match.group("ignore") and not include_ignored:
            continue
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


def check_schema_province(schema, schema_province, root=None):
    """Reject an opposite or mixed province schema before adoption writes.

    Derive distinguishing tables from the deployed ON and BC genesis files,
    excluding tables they share. A schema with only common tables provides no
    province evidence; absent tables can still be reconciled for the configured
    province. Never infer a province from missing tables alone.
    """
    root = root or MIGRATION_ROOT
    provinces = ("on", "bc")
    if schema_province not in provinces:
        die("unsupported schema_province: {0}".format(schema_province))
    families = {}
    for province in provinces:
        if not glob.glob(os.path.join(root, province, "V1.0.1__*.sql")):
            die("cannot verify schema_province: missing {0} genesis under {1}"
                .format(province.upper(), root))
        tables = set()
        for path in genesis_files(province, root):
            tables.update(name.lower() for name in parse_create_tables(_read(path)))
        families[province] = tables
    observed = {}
    for province, other in (("on", "bc"), ("bc", "on")):
        markers = families[province] - families[other]
        if not markers:
            die("cannot verify schema_province: no distinguishing {0} genesis "
                "tables were parsed".format(province.upper()))
        observed[province] = sorted(markers.intersection(schema))
    other = "bc" if schema_province == "on" else "on"
    if observed[other]:
        if observed[schema_province]:
            die("live schema contains both ON and BC province tables; cannot "
                "verify schema_province={0}. Check CARLOS_PROVINCE and the "
                "imported database before adopting; nothing was changed"
                .format(schema_province))
        die("province mismatch: schema_province={0}, but the live schema has "
            "{1} province tables (including `{2}`). Correct CARLOS_PROVINCE "
            "or select the matching database before adopting; nothing was "
            "changed".format(schema_province, other.upper(), observed[other][0]))


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

    Returns `[(table, pk_column, [keys], present, source, columns, diverging,
    rehome), ...]` for the collisions actually present in this database."""
    collisions = []
    for path in forward_migration_files(schema_province, root):
        source = os.path.basename(path)
        version = re.search(r"V(\d+(?:\.\d+)*)__", source)
        if version and version.group(1) in applied:
            continue
        sql = _read(path)
        all_seeds = parse_plain_seed_inserts(sql, include_ignored=True)
        for table, rows in sorted(parse_plain_seed_inserts(sql).items()):
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

            diverging, rehome = _classify_seed_rows(
                dbops, db_name, table, pk, canonical, present, columns, source,
                all_seeds=dict(all_seeds[table]))
            collisions.append((table, pk, keys, present, source, columns,
                               diverging, rehome))
    return collisions


def _max_key(dbops, db_name, table, pk) -> int:
    """The table's current highest primary key, or 0 when it is empty."""
    value = _scalar(dbops, db_name,
                    "SELECT COALESCE(MAX(`{0}`), 0) FROM `{1}`".format(pk, table))
    try:
        return int(value)
    except (TypeError, ValueError):
        die("could not read the highest `{0}` in `{1}`".format(pk, table))


def _codes_at_vacant_seed_keys(dbops, db_name, table, pk, seeds, source):
    """Codes an INSERT IGNORE seed can restore at currently vacant keys."""
    if not seeds:
        return set()
    cp = _client(dbops, db_name, ["-N", "-B"], capture_output=True,
                 input="SELECT `{0}` FROM `{1}` WHERE `{0}` IN ({2});".format(
                     pk, table, ",".join(str(k) for k in sorted(seeds))))
    if cp.returncode != 0:
        die("could not check vacant `{0}` seed keys for {1}".format(table, source))
    try:
        occupied = {int(line) for line in (cp.stdout or "").splitlines()}
    except ValueError:
        die("could not read occupied `{0}` seed keys for {1}".format(table, source))
    if not occupied.issubset(seeds):
        die("unexpected occupied `{0}` seed keys for {1}".format(table, source))
    return {code for key, code in seeds.items() if key not in occupied}


def _seed_keys_with_surviving_codes(dbops, db_name, table, pk, code_column,
                                    canonical, source, pending_codes=()):
    """Collision keys whose code survives in the seed or an untouched row.

    Compare in MariaDB using the code column's collation, just as the
    application's lookups do. Python string equality would treat e.g. `y19`
    as a new code beside canonical `Y19` under a case-insensitive collation,
    leaving two lookup results after migration. Return integer keys so batch
    escaping of tabs, newlines and backslashes cannot change the comparison.
    """
    keys = ",".join(str(k) for k in sorted(canonical))
    codes = ",".join("'{0}'".format(sql_escape(c))
                     for c in sorted(set(canonical.values()) | set(pending_codes)))
    # Stream the full seed code set: it can exceed the OS limit for one argv
    # entry, and no clinical strings need to appear in process arguments.
    cp = _client(dbops, db_name, ["-N", "-B"], capture_output=True, input=
        "SET NAMES utf8mb4; SET SESSION sql_mode='';"
        "SELECT t.`{0}` FROM `{1}` t WHERE t.`{0}` IN ({2}) AND ("
        "t.`{3}` IN ({4}) OR EXISTS ("
        "SELECT 1 FROM `{1}` survivor WHERE survivor.`{0}` NOT IN ({2}) "
        "AND survivor.`{3}` = t.`{3}`))".format(
            pk, table, keys, code_column, codes) + ";")
    if cp.returncode != 0:
        die("could not check whether `{0}` codes survive {1}".format(table, source))
    try:
        surviving = {int(line) for line in (cp.stdout or "").splitlines()}
    except ValueError:
        die("could not read surviving `{0}` seed keys for {1}".format(table, source))
    if not surviving.issubset(canonical):
        die("unexpected surviving `{0}` seed keys for {1}".format(table, source))
    return surviving


def _classify_seed_rows(dbops, db_name, table, pk, canonical, present, columns,
                        source, all_seeds=None):
    """Split the colliding rows into ones safe to replace and ones to re-home.

    The premise of clearing these keys is that the migration lays down the SAME
    reference rows. Where a live row holds a DIFFERENT code, replacing it drops
    that code from the table -- and `Icd10DaoImpl` resolves this table by CODE,
    never by id, so a clinical record still naming it stops resolving.

    But the id carries no meaning of its own. Nothing in the schema references
    `icd10.id`: `dxresearch` stores the code string and has no foreign key
    here, and no query in the application selects by id. So a code that would
    otherwise be lost does not need deleting at all -- it needs MOVING. Each
    diverging row is therefore classified:

      * its code also appears on a row outside the collision range, or among
        the canonical replacements -> the code survives the migration anyway,
        so the row is replaced as before and nothing is lost;
      * its code appears nowhere else -> it is genuinely local to this clinic,
        and the row is RE-HOMED to a fresh id above everything, keeping the
        code resolvable while the migration lays its canonical row at the id.

    Returns `(diverging, {old_key: new_key})`.

    Everything here still fails closed: a missing code column, an unanswerable
    query, an unparseable row, or a collision count that moved under us all
    stop adoption, because each means the classification cannot be trusted."""
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
    diverged = {}
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
            diverged[key] = parts[1]
    if checked != present:
        die("`{0}` changed while checking seed codes: counted {1} collision "
            "row(s) but read {2}; retry adoption".format(
                table, present, checked))
    if not diverged:
        return 0, {}

    all_seeds = all_seeds or canonical
    pending_codes = _codes_at_vacant_seed_keys(
        dbops, db_name, table, pk,
        {key: code for key, code in all_seeds.items() if key not in canonical},
        source)
    surviving = _seed_keys_with_surviving_codes(
        dbops, db_name, table, pk, code_column, canonical, source, pending_codes)
    lost = sorted(k for k in diverged if k not in surviving)
    if not lost:
        return len(diverged), {}
    # Above the live maximum AND above every key the seed will write, so the
    # new ids collide with neither what is there now nor what arrives next.
    start = max(_max_key(dbops, db_name, table, pk), max(all_seeds)) + 1
    return len(diverged), {key: start + offset for offset, key in enumerate(lost)}


def seed_collision_script(table, pk, keys, columns, rehome=None) -> str:
    """Copy the colliding rows aside, re-home what would be lost, clear the rest.

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
    rehome = rehome or {}
    key_list = ",".join(str(k) for k in keys)
    column_list = ", ".join("`{0}`".format(c) for c in columns)
    # An old backup can contain the same primary key from another restored
    # datadir. Only delete a live row when the backup still has every byte of
    # that row. The post-copy collision re-check then prevents a false stamp.
    copied = " AND ".join("BINARY b.`{0}` <=> BINARY t.`{0}`".format(c)
                          for c in columns)
    statements = [
        "CREATE TABLE IF NOT EXISTS `{0}` LIKE `{1}`;".format(backup, table),
        # Every colliding row, re-homed or not. For a re-homed row this is the
        # record of the id it came from, which is the only audit trail of the
        # move.
        "INSERT IGNORE INTO `{0}` ({1}) SELECT {1} FROM `{2}` "
        "WHERE `{3}` IN ({4});".format(backup, column_list, table, pk, key_list),
    ]
    if rehome:
        # Reserve ids before moving any row. An interruption after a move must
        # not leave the next generated key inside the relocated range.
        statements.append("ALTER TABLE `{0}` AUTO_INCREMENT = {1};".format(
            table, max(rehome.values()) + 1))
    # Moving rows out of the collision range keeps the delete from reaching
    # them. Require the same complete backup as deletion: INSERT IGNORE can
    # leave a different row at the original key after another dump is loaded.
    for old in sorted(rehome):
        statements.append(
            "UPDATE `{0}` t JOIN `{1}` b ON b.`{2}` = t.`{2}` "
            "SET t.`{2}` = {3} WHERE t.`{2}` = {4} AND {5};".format(
                table, backup, pk, rehome[old], old, copied))
    statements.append(
        "DELETE t FROM `{0}` t JOIN `{1}` b ON b.`{2}` = t.`{2}` "
        "WHERE t.`{2}` IN ({3}) AND {4};".format(
            table, backup, pk, key_list, copied))
    return "\n".join(statements)


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


def _billing_tag(suffix):
    return "CONCAT('-', {0}, '-', b.`id`)".format(
        "COALESCE({0}, 'x')".format(suffix) if suffix else "'dup'")


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
            width = _column_width(dbops, db_name, table, column)
            oversized = _count_or_die(
                dbops, db_name,
                "SELECT COUNT(*) FROM `{0}` b JOIN ("
                "SELECT `id`, ROW_NUMBER() OVER (PARTITION BY `{1}` "
                "ORDER BY `{2}`, `id`) AS rn FROM `{0}` "
                "WHERE `{1}` IS NOT NULL) r ON r.`id` = b.`id` "
                "WHERE r.rn > 1 AND CHAR_LENGTH({3}) > {4}".format(
                    table, column, order_by, _billing_tag(suffix), width),
                "check billing suffix width for {0}.{1}".format(table, column))
            if oversized:
                die("`{0}`.`{1}` is too narrow ({2} characters) for {3} "
                    "billing suffix(es); refusing adoption before changes. "
                    "Review the column width before retrying".format(
                        table, column, width, oversized))
            found.append((table, column, order_by, suffix, extra, width))
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
    tag = _billing_tag(suffix)
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
        # Planning rejects tags wider than the column. Guard again here so a
        # changed row cannot cause silent truncation between plan and UPDATE.
        # An exactly fitting tag leaves no filename prefix. Never truncate the
        # tag itself: it carries the complete primary key.
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
        "  LEFT(b.`{1}`, GREATEST(0, {2} - CHAR_LENGTH({3}))), {3}), "
        "b.`timestamp` = b.`timestamp` "
        "WHERE r.rn > 1 AND CHAR_LENGTH({3}) <= {2};".format(
            table, column, width, tag, backup),
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
  --stamp-only  run only Flyway baseline after province validation; skip
                stale-history repair, schema reconciliation, and seed/billing
                data preparation (the previous bare-stamp behaviour)
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

    if not os.path.isdir(MIGRATION_ROOT):
        die("{0} does not hold the packaged migrations; is the CARLOS webapp "
            "deployed?".format(MIGRATION_ROOT))

    db_name = settings.db_name
    schema = live_schema(dbops, db_name)
    check_schema_province(schema, settings.schema_province)

    if stamp_only:
        # Deliberately the pre-adoption behaviour, refusal included. This flag
        # exists for compatibility, so it must NOT quietly acquire the stale
        # history parking below -- but say what that means, because an operator
        # reaching for it on a legacy import is the one Flyway is about to
        # refuse, and the way through is the plain verb.
        _warn("--stamp-only: stamping without reconciling. An adopted OSCAR 19 "
             "datadir will be missing every column added to the genesis since "
             "it was forked, and the failure surfaces at login, not here. "
             "This is the old behaviour in full, so a flyway_schema_history "
             "the installer already populated still makes Flyway refuse the "
             "stamp; run db-baseline without --stamp-only to park it.")
        return dbops.run_flyway("baseline")

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

    stale = _stale_history(dbops, db_name, tables, schema)
    # A stale history is renamed aside below, so nothing it records will be
    # honoured: every forward migration becomes pending again.
    applied = set() if stale else applied_versions(dbops, db_name)
    collisions = plan_seed_collisions(dbops, db_name, settings.schema_province,
                                      applied)
    duplicates = plan_billing_duplicates(dbops, db_name)

    for table, pk, keys, present, source, _columns, _div, rehome in collisions:
        _log("{0}: {1} row(s) in `{2}` collide with the unguarded seed in {3}; "
            "they will be copied to `{4}` and cleared so the migration can lay "
            "down its canonical rows".format(
                "PLAN" if dry_run else "preparing", present, table, source,
                _backup_table(table)))
        if rehome:
            # Not a warning. Nothing is lost and nothing is left for the
            # operator to reconcile -- but the ids DO move, and a clinic's own
            # report or eForm could conceivably have stored one, so every move
            # is named rather than summarised.
            _log("{0}: {1} of those hold a code that exists nowhere else; "
                 "re-homing to keep it resolvable ({2})".format(
                     "PLAN" if dry_run else "preparing", len(rehome),
                     ", ".join("{0}->{1}".format(old, rehome[old])
                               for old in sorted(rehome))))
    for table, column, _order, _suffix, extra, _width in duplicates:
        _log("{0}: {1} row(s) in `{2}`.`{3}` share a filename with an earlier "
            "row; they will be suffixed (originals kept in `{4}`) so the "
            "UNIQUE index can be created without discarding billing "
            "history".format("PLAN" if dry_run else "preparing", extra, table,
                             column, _backup_table(table)))
    if stale:
        _log("{0}: flyway_schema_history describes a schema this database no "
            "longer has. It will be renamed aside, not dropped, so forward "
            "migrations are prepared and run again."
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

    for table, pk, keys, _present, _source, columns, _div, rehome in collisions:
        _run_script(dbops, db_name,
                    seed_collision_script(table, pk, keys, columns, rehome),
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
        _log("adopted. Now run: carlos-ctl db-migrate")
        # Repeated here on purpose: the per-table line was printed before
        # 10,000 reconciliation statements and a Flyway stamp scrolled past it.
        # These are the ids that moved, and the only thing a human might still
        # want to check afterwards.
        moved = sum(len(c[7]) for c in collisions)
        if moved:
            _log("{0} reference row(s) kept a clinic-local code by moving to a "
                 "new id; the id they came from is recorded in `{1}*`. Nothing "
                 "to reconcile -- every code still resolves.".format(
                     moved, BACKUP_PREFIX))
    return rc


def _schema_size(dbops, db_name):
    """Live (table count, column count), or stop.

    These two are subtracted to produce the headline "N table(s) and M
    column(s) added" line of the adoption transcript. `_count` answers an
    unanswerable query with 0, which here does not read as "none" -- a failed
    BEFORE probe reports the whole schema as newly added, a failed AFTER probe
    reports a negative count, and either way the probe failure itself is
    invisible. A transcript of a clinical adoption should not be able to lie
    about what it changed, so this fails closed like every other probe."""
    return (
        _count_or_die(dbops, db_name,
                      "SELECT COUNT(*) FROM information_schema.TABLES "
                      "WHERE TABLE_SCHEMA = DATABASE()",
                      "count the live tables"),
        _count_or_die(dbops, db_name,
                      "SELECT COUNT(*) FROM information_schema.COLUMNS "
                      "WHERE TABLE_SCHEMA = DATABASE()",
                      "count the live columns"),
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
    reconciliation fills. Stale-history detection checks absent tables
    separately so these per-column counts remain meaningful."""
    missing = []
    for name in sorted(tables):
        live = schema.get(name.lower())
        if not live:
            continue
        for column, _definition in tables[name].columns:
            if column.lower() not in live:
                missing.append((name, column))
    return missing


def _missing_recorded_billing_indexes(dbops, db_name) -> bool:
    """Whether successful billing-index history contradicts the live schema.

    A legacy dump can replace data tables while leaving an earlier adoption's
    BASELINE and successful migrations behind. Missing genesis columns alone
    cannot distinguish that from adoption by an older package, but a missing
    artifact of a migration recorded as successful can.
    """
    recorded = " OR ".join(
        "(`version` = '{0}' AND `script` = '{1}')".format(version, script)
        for version, script in BILLING_INDEX_MIGRATIONS.items())
    if not _count_or_die(
            dbops, db_name,
            "SELECT COUNT(*) FROM `flyway_schema_history` "
            "WHERE `type` = 'SQL' AND `success` = 1 AND ({0})".format(recorded),
            "check recorded billing-index migrations"):
        return False
    for table, column, _order, _suffix in BILLING_UNIQUE:
        # Group all components before testing the shape. A composite,
        # non-unique, wrong-column or prefix index does not establish the
        # published constraint. Accept an equivalent index under another name:
        # a harmless rename is not evidence that the database was replaced.
        present = _count_or_die(
            dbops, db_name,
            "SELECT COUNT(*) FROM (SELECT INDEX_NAME "
            "FROM information_schema.STATISTICS "
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{0}' "
            "GROUP BY INDEX_NAME "
            "HAVING COUNT(*) = 1 AND MIN(COLUMN_NAME) = '{1}' "
            "AND MIN(NON_UNIQUE) = 0 AND MIN(SEQ_IN_INDEX) = 1 "
            "AND COUNT(SUB_PART) = 0) AS expected_index".format(table, column),
            "verify recorded billing uniqueness for `{0}`.`{1}`".format(table, column))
        if not present:
            _warn("migration history records successful billing-index creation, "
                  "but `{0}` lacks a full-column UNIQUE index on `{1}`; "
                  "treating that history as stale".format(table, column))
            return True
    return False


def _stale_history(dbops, db_name, tables, schema) -> bool:
    """Whether `flyway_schema_history` is bookkeeping for a schema that is gone.

    The installer runs `db-migrate` on the fresh database it provisions, so the
    history is already populated by the time an operator loads a legacy dump
    over it -- and mysqldump's `DROP TABLE IF EXISTS` replaces the data tables
    but not this one, because an authentic OSCAR 19 dump never contained it.
    Flyway then refuses to baseline: "flyway_schema_history already contains
    migrations".

    A BASELINE history is preserved unless a recorded successful billing-index
    migration is contradicted by the live indexes. This keeps older legitimate
    adoptions repairable while recognizing a later dump loaded over an adopted
    database. A metadata query failure aborts instead of guessing.

    Without a BASELINE, two signals together identify stale installer history:

    * NO BASELINE MARKER. A history written by `migrate` against an empty
      database records `V1`/`V1.0.1`/`V1.0.2` as ordinary applied migrations.
      A history written by `baseline` carries a BASELINE row and takes the
      forward-index check above instead; genesis gaps alone cannot invalidate
      that history because older adoptions may legitimately have those gaps.
    * GENESIS TABLES OR COLUMNS MISSING. The caller has already rejected a
      province mismatch, so missing whole tables also show that the recorded
      genesis is incomplete. Otherwise this is an ordinary healthy install
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
        return _missing_recorded_billing_indexes(dbops, db_name)
    return (any(name.lower() not in schema for name in tables)
            or bool(missing_genesis_columns(tables, schema)))

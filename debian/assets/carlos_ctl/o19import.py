# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""carlos-ctl import-o19 / o19-preflight — OSCAR 19 clinic import
(experimental).

Phase pipeline (docs/oscar19-to-carlos-migration-plan.md §9a; each phase
records its completion + input digests in state.json under
/var/lib/carlos-emr/o19-import/). A rerun over existing state REQUIRES
--resume (or --cleanup); nothing is silently continued. Once the ETL has
started, the target is mid-import by design, so a resumed run re-checks
the schema/replica/disk gates but not the emptiness sweep P0 already
passed. Execution order on a real import is P0, P3, P1, P2, P4..P7: the
rollback snapshot exists before any clinic-supplied SQL executes. --dry-run
and the o19-preflight verb run P0 (capacity checks only for the latter),
P1 and P2 without recording a verdict; --dry-run additionally emits the
P6 properties report, marked as a dry-run fragment:

  P0 check-pristine  stock-initial-deploy gate (manifest-driven emptiness
                     sweep; hard refusal, no --accept; --dev-target
                     downgrades it to a warning for dev databases)
  P1 stage           restore the dump verbatim into the o19_import schema
                     (as a throwaway account whose grants stop at that
                     schema; a dump carrying USE/CREATE DATABASE is refused)
  P2 preflight       §6.1 go/no-go over the staged schema (o19_preflight,
                     import mode with column-level checks)
  P3 backup          restic snapshot via the systemd backup unit (rollback
                     point)
  P4 etl             manifest-driven copy into the carlos schema, then
                     the roles/privileges post-step (o19roles)     (M4, M8)
  P5 documents       OscarDocument restore + reconciliation           (M5)
  P6 props           oscar.properties translation                     (M6)
  P7 verify          row parity + spot checks + role checks       (M4+, M8)

Every migration's output should receive a technical review — verification
report, spot checks, UI smoke — before clinical use.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from typing import (Callable, Dict, Iterator, List, Optional,
                    Sequence, Set, Tuple)

from . import (dbops, o19_preflight, o19bundle, o19digest, o19docs,
               o19etl, o19map_props, o19map_schema,
               o19report)
from .util import (BACKUP_ENV, ENV_FILE, STATE, die, genpw, genrandom, log,
                   run, warn)

STATE_DIR = os.path.join(STATE, "o19-import")
STAGING_SCHEMA = "o19_import"
ARCHIVE_SCHEMA = "o19_archive"

# every blocker class an operator may acknowledge (see o19_preflight and
# the phase drivers below); anything else passed to --accept is a typo.
ACCEPT_CLASSES = (
    "archived-forms", "unknown-as-archive", "dropped-columns",
    "charset-repair", "olis-gone", "no-documents", "no-pre-backup",
    "unverified-bundle", "carry-credentials",
    # M22 content integrity: a DISAGREEMENT and a GAP are separate
    # sign-offs. The first says the bytes differ from what the clinic
    # measured; the second says nobody measured. Accepting one must
    # never quietly accept the other.
    "content-transfer", "no-content-digests",
    # P7's counterpart: the preserved copies hold the right NUMBER of
    # rows but not the same values. Distinct from `content-transfer`,
    # which is about the dump and the restore -- this one is about what
    # the ETL did afterwards, and an operator who accepted one has not
    # seen the other.
    "content-migration",
)

#: the run's own copy of the clinic's content digests: P2 measures the
#: copy it took, never the operator's mutable file (see snapshot_digests)
DIGESTS_SNAPSHOT = "o19-digests.json"

#: P7 names the rows its value checks disagreed about here, at
#: 0600, because a primary key joins straight back to a patient,
#: an appointment or a bill. The report carries the counts and a
#: pointer; the keys themselves never leave this file.
CONTENT_DETAILS = "content-details.txt"

#: P7's verification problems in full, at 0600. The report and the
#: running log show the first `REPORT_PROBLEM_LINES` -- enough to see
#: the shape of a failure without turning a shareable document into a
#: table dump -- and point here for the rest. Without this file the
#: remainder existed in no artifact at all: re-running P7 prints only
#: the count, so 260 of 300 problem lines were simply unrecoverable.
VERIFY_PROBLEMS = "verify-problems.txt"

#: P0's own root-only record: the non-seed logins its pristine sweep
#: refused on. Separate from P7's `verify-details.txt` because both
#: writers open O_TRUNC and a `--dev-target` run reaches both, so P7
#: would erase what P0 wrote while report.txt still pointed at it.
PRISTINE_DETAILS = "pristine-details.txt"

#: How many problem lines the report body and report.txt carry before
#: they say how many more there are.
REPORT_PROBLEM_LINES = 40

#: Marks a content mismatch the operator signed off on. Its own
#: section in the report, because a reviewer must see what was
#: waved through before go-live -- not a line among the passes.
ACKNOWLEDGED_PREFIX = "ACKNOWLEDGED (--accept content-migration): "

#: How the report points at the private details without carrying
#: them. Both the failure finding and the accepted-mismatch
#: advisory use it: an accepted mismatch is the case a reviewer
#: is most likely to want the rows for.
#:
#: It promises no more than the file delivers. The copy and merge
#: checks pair ROWS, so they name primary keys; a preserved copy is
#: compared by whole-table digest, which has no per-row key to give
#: and is named by table. Promising keys for that case would send a
#: reviewer looking for something the check cannot produce.
CONTENT_DETAILS_NOTE = (
    "what differs is itemized in {0} (0600) — by primary key where "
    "the check pairs rows, by table for a preserved copy (compared "
    "by whole-table digest, so it has no per-row key). The contents "
    "are PHI-correlating and are not repeated here")

DUMP_COMPLETED_MARKER = b"-- Dump completed"
# statements a `mysqldump --databases/--all-databases` emits that would
# redirect the restore out of the staging schema, and a MySQL-only GTID
# directive the restricted staging account cannot execute anyway
#: a statement that would steer the restore out of the staging schema.
#: Matched case-insensitively and allowing leading blanks: mysqldump only
#: ever emits the upper-case forms, so the spellings this catches beyond
#: those are precisely the hand-crafted ones
DUMP_REDIRECT_RE = re.compile(rb"(?im)^[ \t]*(use\b|create\s+database\b)")
DUMP_GTID_MARKER = b"GTID_PURGED"
STAGING_USER = "o19_import"
SPOT_CHECK_PATIENTS = 10


# --------------------------------------------------------------------------
# state ledger
# --------------------------------------------------------------------------

def state_path(state_dir: str) -> str:
    """The run ledger's path. One name, in one place: `archive_state`
    retires it by suffix and several refusals name it."""
    return os.path.join(state_dir, "state.json")


def load_state(state_dir: str) -> Dict:
    """The run ledger; absent means a fresh run, unreadable or corrupt
    means STOP — treating it as fresh would re-sweep a mid-import target
    and send the operator to the wrong remedy."""
    try:
        with open(state_path(state_dir), encoding="utf-8") as fh:
            return json.load(fh)
    except FileNotFoundError:
        return {"phases": {}, "accepted": [], "inputs": {}}
    except (OSError, ValueError) as exc:
        die("cannot read {0} ({1}) — the run ledger is unreadable; if an "
            "import is in progress restore the pre-import snapshot, "
            "otherwise move the file aside".format(
                state_path(state_dir), exc))
        return {}  # unreachable


def durable_json(path: str, payload) -> None:
    """Write a JSON document so a power loss leaves either the old file
    or the new one. os.replace alone is atomic against a crash, not
    against a loss of power: without the fsyncs the rename can land
    before the data, leaving a ledger of zeroes describing writes that
    did happen."""
    tmp = path + ".tmp"
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)  # a stale .tmp keeps its old mode otherwise
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=1, sort_keys=True)
        fh.flush()
        os.fsync(fh.fileno())
    os.replace(tmp, path)
    dir_fd = os.open(os.path.dirname(path) or ".", os.O_RDONLY)
    try:
        os.fsync(dir_fd)
    finally:
        os.close(dir_fd)


def save_state(state_dir: str, state: Dict) -> None:
    """Persist the run ledger durably (0700 workspace, atomic write).

    Durable because a power loss between a write to the database and
    the ledger entry recording it is precisely the state a resume has
    to reason about."""
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    durable_json(state_path(state_dir), state)


def phase_done(state: Dict, phase: str) -> bool:
    """Whether `phase` COMPLETED. A phase that ran and failed is not
    done, and an in-progress one is not either -- both must be re-run,
    which is what makes `--resume` safe to offer."""
    return state.get("phases", {}).get(phase, {}).get("status") == "done"


def mark_done(state_dir: str, state: Dict, phase: str, **extra) -> None:
    """Record a phase as complete, with whatever it needs to prove it
    later (`extra`: the dump digest, the verdict, a skip reason).

    Called only on success -- see `mark_started` for the destructive
    phases that must also record having BEGUN."""
    entry = {"status": "done",
             "at": time.strftime("%Y-%m-%dT%H:%M:%S")}
    entry.update(extra)
    state.setdefault("phases", {})[phase] = entry
    save_state(state_dir, state)


def staging_holds_rows(query) -> bool:
    """Whether the staging schema currently holds any row.

    `information_schema.TABLES.TABLE_ROWS` is an ESTIMATE for InnoDB and
    can read 0 for a populated table, so it is not usable for a gate that
    decides whether data is about to be destroyed. This counts, stopping
    at the first table that has anything."""
    tables = [r[0] for r in query(
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE "
        "TABLE_SCHEMA = '{0}'".format(STAGING_SCHEMA))]
    for table in tables:
        if int(query("SELECT COUNT(*) FROM `{0}`.{1}".format(
                STAGING_SCHEMA, o19etl.ident(table)))[0][0]):
            return True
    return False


def mark_started(state_dir: str, state: Dict, phase: str, **extra) -> None:
    """Record that a phase has BEGUN, with what it is working on.

    `mark_done` records only success, which is right for a resume ("skip
    what finished") and wrong for a destructive step: P1 drops the
    staging schema before it restores into it, and a restore that dies
    half-way leaves rows behind with nothing in the ledger saying which
    dump they came from. The next attempt then cannot tell its own
    wreckage from another clinic's data. Recording the attempt first is
    what lets it tell the difference."""
    entry = dict(state.setdefault("phases", {}).get(phase) or {})
    entry.update({"status": "in-progress",
                  "at": time.strftime("%Y-%m-%dT%H:%M:%S")})
    entry.update(extra)
    state["phases"][phase] = entry
    save_state(state_dir, state)


def staging_drop_refusal(rows_present: bool, recorded_sha: Optional[str],
                         dump_sha: str, restage: bool) -> Optional[str]:
    """Why P1 may not drop the staging schema now, or None.

    P1's first act is `DROP DATABASE o19_import`, and staging is where a
    dump lives between the restore and the copy. Dropping it is safe
    while the dump file that made it is still there to restore again --
    which is the ordinary case, since P1 is about to restore exactly
    that file. It is NOT safe when the schema holds some OTHER dump's
    rows and this workspace has no record of staging it: a clinic whose
    bundle has since been deleted would lose the only copy, silently,
    to a command that reads as "start my import".

    Allowed when the schema is empty (nothing to lose), when the ledger
    records this same dump (the rows are this dump's own, from an
    interrupted restore), or when `--restage` says so explicitly."""
    if not rows_present or restage or recorded_sha == dump_sha:
        return None
    return ("the staging schema {0} already holds rows, and this "
            "workspace has no record of staging the dump offered now{1}. "
            "Dropping it would destroy the only copy of whatever is "
            "there. Export or drop {0} yourself if those rows are "
            "finished with, or pass --restage to say so."
            .format(STAGING_SCHEMA,
                    " (it staged {0}...)".format(recorded_sha[:12])
                    if recorded_sha else ""))


def report_append(state_dir: str, title: str, body: str) -> None:
    """Append one phase block to the running log.

    0600 like every other run artifact: report.txt was the only one left
    at the umask's 0644, and it carries table names, row counts and the
    roles findings."""
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    append_private(os.path.join(state_dir, "report.txt"),
                   "== {0} ==\n{1}\n\n".format(title, body.rstrip()))


def sha256_file(path: str) -> str:
    """Re-exported so this module's callers need not import o19bundle
    for one function; the digest is the same one the clinic sends."""
    return o19bundle.sha256_file(path)


# --------------------------------------------------------------------------
# database access (connection seam)
# --------------------------------------------------------------------------

def statement_timeout_prelude(seconds: int) -> str:
    """The session setting that bounds every statement's run time (MariaDB
    max_statement_time; 0 = unlimited). A crafted dump cannot make one
    statement run forever when the operator sets a bound."""
    return "SET SESSION max_statement_time={0}".format(int(seconds))


def make_query(mariadb_args: Optional[List[str]],
               statement_timeout: int = 0) -> Callable:
    """query(sql, db=None) -> rows. Default: root over the unix socket
    exactly like dbops.db_root; --mariadb-arg overrides the client argv
    tail for dev environments (and implies --dev-target)."""
    base = ["mariadb", "--protocol=socket", "--user=root"]
    if mariadb_args:
        base = ["mariadb"] + list(mariadb_args)

    def query(sql, db=None):
        argv = list(base) + list(CLIENT_COMMON_ARGS)
        if statement_timeout:
            argv.append("--init-command="
                        + statement_timeout_prelude(statement_timeout))
        if db:
            argv.append(db)
        # statements go through STDIN, never argv: /proc/<pid>/cmdline is
        # world-readable and some statements carry credentials
        cp = run(argv, input=sql, capture_output=True, errors="replace")
        if cp.returncode != 0:
            raise o19etl.QueryError("SQL failed ({0}): {1}".format(
                redact_statement(sql), cp.stderr.strip()), cp.stderr)
        return batch_rows(cp.stdout)

    query.base_argv = base  # type: ignore[attr-defined]
    return query


# batch mode escapes \0 \t \n \\ inside values, so a bare "\r" (a CRLF
# eForm) is DATA; only "\n" separates rows — never str.splitlines(). The
# escapes are decoded per value AFTER splitting, so a role name carrying a
# backslash or tab reaches the callers (and their _sql_str) as stored.
CLIENT_COMMON_ARGS = ("--default-character-set=utf8mb4", "-N", "-B")

# the ONE place batch escapes are decoded (o19docs.unescape_batch_field is
# the implementation); callers must not decode a second time — a literal
# backslash-n in an eForm would otherwise turn into a newline. SQL NULL
# arrives as the four letters NULL, exactly like a stored string 'NULL':
# a caller that must tell them apart selects an `IS NULL` flag next to the
# column (the archive CSV export does).
unescape_batch = o19docs.unescape_batch_field


#: statement fragments that carry a credential: the error text (stderr,
#: and whatever transcript the operator pastes) never shows what follows
CREDENTIAL_SQL_RE = re.compile(
    r"(IDENTIFIED\s+BY\s+(?:PASSWORD\s+)?|PASSWORD\s*\(\s*"
    r"|password\s*=\s*)'(?:[^'\\]|\\.)*'",
    re.IGNORECASE)


def redact_statement(sql: str, width: int = 80) -> str:
    """The statement prefix an error message may show: credential
    literals masked BEFORE truncation (a truncated `IDENTIFIED BY 'abc`
    would still leak the head of the password)."""
    masked = CREDENTIAL_SQL_RE.sub(lambda m: m.group(1) + "'<redacted>'",
                                   sql)
    return masked[:width]


def decode_batch_stream(chunks) -> Iterator[List[str]]:
    """Rows from a stream of byte chunks of the batch client's stdout.

    Split out of `make_row_stream` because this is the part that can be
    silently wrong: a chunk boundary falls wherever the pipe says, so a
    row -- or a multi-byte character, or a backslash escape -- can be cut
    in half between reads. Rows are assembled from BYTES and decoded only
    once complete.

    A trailing empty line is dropped, so an empty result set yields
    nothing rather than one empty row -- matching `batch_rows`."""
    # Chunks are HELD, not concatenated as they arrive: an archived
    # TEXT/BLOB row can span hundreds of reads, and `pending += chunk`
    # re-copies the whole unterminated row every time -- quadratic in the
    # row's size, on exactly the rows that are already the largest.
    held = []
    for chunk in chunks:
        if b"\n" not in chunk:
            held.append(chunk)
            continue
        held.append(chunk)
        parts = b"".join(held).split(b"\n")
        held = [parts.pop()]
        for line in parts:
            yield [unescape_batch(v) for v in
                   line.decode("utf-8", "replace").split("\t")]
    tail = b"".join(held)
    if tail:
        yield [unescape_batch(v) for v in
               tail.decode("utf-8", "replace").split("\t")]


def make_row_stream(mariadb_args: Optional[List[str]],
                    statement_timeout: int = 0) -> Callable:
    """stream(sql, db=None) -> iterator of decoded rows, one at a time.

    The buffered `query` reads a whole result set into memory and the
    caller then materialises a second copy. The archive export is the one
    place a CLINIC's own data decides the size -- a fork's table, or a
    merge table's full staging copy -- so it read in LIMIT/OFFSET windows
    instead. That is quadratic: the ORDER BY carries no index, so every
    window re-sorts the whole table and skips its way to the offset.
    Measured on 500k rows, ten windows: 14.6s against 3.5s for the same
    rows in one statement, and the ratio grows with the table (roughly
    n/2w), so a 20M-row archive costs about 200 scans instead of one.

    `--quick` makes the client hand rows over unbuffered, so one
    statement streams in constant memory and the windows are not needed
    at all.

    Rows are split on b"\n" over BYTES, never by text-mode line
    iteration: batch mode escapes \0 \t \n \\ inside values, so a bare
    "\r" (a CRLF eForm) is DATA, and universal-newline translation would
    split a row in half."""
    base = ["mariadb", "--protocol=socket", "--user=root"]
    if mariadb_args:
        base = ["mariadb"] + list(mariadb_args)

    def stream(sql, db=None):
        argv = list(base) + list(CLIENT_COMMON_ARGS) + ["--quick"]
        if statement_timeout:
            argv.append("--init-command="
                        + statement_timeout_prelude(statement_timeout))
        if db:
            argv.append(db)
        proc = subprocess.Popen(argv, stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE)
        try:
            proc.stdin.write(sql.encode("utf-8"))
            proc.stdin.close()
            for row in decode_batch_stream(
                    iter(lambda: proc.stdout.read(65536), b"")):
                yield row
        finally:
            proc.stdout.close()
            err = proc.stderr.read().decode("utf-8", "replace")
            proc.stderr.close()
            rc = proc.wait()
        if rc != 0:
            # raised AFTER the generator has drained: a partial export is
            # worse than none, and the caller must not keep the rows it
            # already wrote as if the table had ended there
            raise o19etl.QueryError("SQL failed ({0}): {1}".format(
                redact_statement(sql), err.strip()), err)

    stream.base_argv = base  # type: ignore[attr-defined]
    return stream


def batch_rows(stdout: str) -> List[List[str]]:
    """Rows from the batch client's tab-separated stdout, with its backslash
    escapes undone. A trailing empty line is dropped, so a result set of
    no rows is an empty list rather than one empty row."""
    lines = stdout.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return [[unescape_batch(v) for v in line.split("\t")] for line in lines]


# --------------------------------------------------------------------------
# P0 — check-pristine
# --------------------------------------------------------------------------

def pristine_violations(counts: Dict[str, int]) -> List[str]:
    """Pure gate logic: live row counts vs the manifest's expectations.

    counts covers every copy/merge-class table that exists on the target.
    Copy-class tables (where clinical and demo data live) must hold
    EXACTLY their counted Flyway seed rows, else be empty. Merge-class
    tables are CARLOS reference seeds (statuses, lookup lists, measurement
    groups) that migrations may also populate with INSERT ... SELECT — not
    statically countable — so they must hold AT LEAST the counted seeds.
    Returns human-readable violations.
    """
    violations = []
    seeds = o19map_schema.SEED_ROW_COUNTS
    tolerated = set(getattr(o19map_schema, "PRISTINE_TOLERATED_TABLES", ()))
    for table in sorted(counts):
        expected = seeds.get(table, 0)
        actual = counts[table]
        cls = o19map_schema.TABLES.get(table, {}).get("class")
        if table in tolerated:
            # the deploy's own audit rows: a sysadmin's verification
            # login writes one, and the copy deletes them before the
            # clinic's land (see PRISTINE_TOLERATED_TABLES). Reported by
            # the caller, never a refusal.
            continue
        if cls == "merge":
            if actual < expected:
                violations.append(
                    "{0}: {1} row(s), expected at least {2} (Flyway "
                    "reference seed)".format(table, actual, expected))
        elif actual != expected:
            violations.append(
                "{0}: {1} row(s), expected {2} (Flyway seed)"
                .format(table, actual, expected))
    return violations


def startup_row_counts(query, db: str) -> Dict[str, int]:
    """Rows the webapp created on its first start, per table (manifest
    STARTUP_CREATED_ROWS): a packaged host has booted before the import
    runs, so the sweep tolerates exactly these rows (the seed script
    deletes them before the copy)."""
    from . import o19roles
    out: Dict[str, int] = {}
    tables = {row[0] for row in query(
        "SELECT TABLE_NAME FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA = '{0}'".format(db))}
    for table, where in o19map_schema.STARTUP_CREATED_ROWS:
        if table not in tables:
            continue
        n = int(query(o19roles.startup_row_count_sql(table, where, db))
                [0][0])
        if n:
            out[table] = n
    return out


def tolerate_startup_rows(counts: Dict[str, int],
                          startup: Dict[str, int]) -> Dict[str, int]:
    """The sweep's counts with the startup-created rows subtracted (pure,
    for the tests): only copy-class tables the sweep knows are touched,
    and at most ONE row per table — the webapp creates each exactly once
    (it returns early when the program/site exists), so a second `OSCAR`
    program or `Main Clinic` site is not a first-start artifact and stays
    a violation."""
    adjusted = dict(counts)
    for table, n in startup.items():
        if table in adjusted:
            adjusted[table] = max(0, adjusted[table] - min(n, 1))
    return adjusted


def gather_copy_counts(query, db: str) -> Dict[str, int]:
    """Live row counts for every manifest `copy`/`merge` table PRESENT in
    `db`.

    Tables absent from the schema are omitted rather than counted as 0:
    the P0 pristine gate and the P7 parity check both distinguish "this
    patch level has no such table" from "the table is there and empty"."""
    tables = {row[0] for row in query(
        "SELECT TABLE_NAME FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA = '{0}'".format(db))}
    counts = {}
    for table, entry in o19map_schema.TABLES.items():
        if entry["class"] not in ("copy", "merge") or table not in tables:
            continue
        counts[table] = int(query(
            "SELECT COUNT(*) FROM `{0}`.`{1}`".format(db, table))[0][0])
    return counts


def _statvfs_nearest(path: str) -> os.statvfs_result:
    """statvfs of path or its nearest existing ancestor — a missing mount
    point must degrade to checking its parent filesystem, never to
    silently skipping the check."""
    p = path
    while True:
        try:
            return os.statvfs(p)
        except OSError:
            parent = os.path.dirname(p) or "/"
            if parent == p:
                return os.statvfs("/")
            p = parent


def uncompressed_size(path: str) -> int:
    """Exact byte size of a dump once decompressed (a streamed gzip pass
    for .gz — one read of the file; a gzip trailer only holds the size
    modulo 4 GiB, which is useless for exactly the dumps that matter)."""
    if not path.endswith(".gz"):
        return os.path.getsize(path)
    total = 0
    proc = subprocess.Popen(["gzip", "-dc", path],          # nosec B603
                            stdout=subprocess.PIPE)
    try:
        while True:
            chunk = proc.stdout.read(1 << 20)
            if not chunk:
                break
            total += len(chunk)
    finally:
        proc.stdout.close()
    if proc.wait() != 0:
        die("cannot decompress {0} (corrupt gzip?)".format(path))
    return total


DATADIR_FALLBACKS = ("/var/lib/mysql", "/var/lib/mariadb")


def server_datadir(query=None) -> str:
    """Where the server actually keeps its data. On Ubuntu 26.04 MariaDB
    moved to /var/lib/mariadb for MySQL co-installability, so the old
    constant names a path that does not exist — and _statvfs_nearest
    would then quietly measure /var/lib or / instead of the volume that
    fills. Ask the server; fall back to whichever default exists."""
    if query is not None:
        try:
            rows = query("SELECT @@datadir")
            if rows and rows[0] and rows[0][0]:
                return rows[0][0]
        except (RuntimeError, IndexError):
            pass
    for path in DATADIR_FALLBACKS:
        if os.path.isdir(path):
            return path
    return DATADIR_FALLBACKS[0]


def check_disk_headroom(dump_bytes: int, bundle_size: int,
                        documents_size: int = 0,
                        datadir: str = "",
                        db_factor: float = 2.5,
                        docs_factor: float = 2) -> Optional[str]:
    """None if fine, else a message. dump_bytes is the UNCOMPRESSED dump
    size: the database volume needs roughly 2.5x that (staging restore +
    the copy into the target + archive schema); the state volume needs
    the bundle expanded (x2) plus the documents tar extracted (x2).

    The two factors default to the FRESH-run budget and are lowered by
    `remaining_capacity_factors` on a resume, where part of that budget
    is already occupied by the run's own output. They are arguments
    rather than a branch inside here so this stays the pure "does this
    fit" question the state tests drive directly.

    Requirements on the SAME filesystem are summed before they are
    compared: on the single-root VM this normally runs on, checking each
    against the same free figure lets a host pass both and then fill up
    part-way through."""
    needs = (("database volume", datadir or server_datadir(),
              int(dump_bytes * db_factor)),
             ("state volume", STATE,
              bundle_size * 2 + int(documents_size * docs_factor)))
    by_device: Dict[int, List] = {}
    for label, path, needed in needs:
        if needed <= 0:
            continue
        st = _statvfs_nearest(path)
        try:
            dev = os.stat(path).st_dev
        except OSError:
            dev = -len(by_device) - 1     # unresolvable: keep it separate
        entry = by_device.setdefault(dev, [[], 0, st.f_bavail * st.f_frsize,
                                           path])
        entry[0].append(label)
        entry[1] += needed
    for labels, needed, free, path in by_device.values():
        if free < needed:
            return ("insufficient disk on {0} ({1}): {2} MB free, "
                    "~{3} MB needed".format(
                        " + ".join(labels), path, free // 1048576,
                        needed // 1048576))
    return None


def process_grant_state(rows) -> str:
    """Whether PROCESS is held: "held", "absent" or "unknown", read from
    `SHOW GRANTS` output.

    Token-aware on purpose. A substring search for "PROCESS" matches any
    identifier containing it — an account whose only grant is
    `SELECT ON \\`hl7_processing\\`.*` holds no PROCESS at all, yet made a
    substring test go quiet and restored the very blind spot this check
    closes. So only the privilege list is examined: the text between
    `GRANT` and the first ` ON `, split on commas.

    Returns "unknown" rather than "absent" whenever nothing parses as a
    grant line, so an unfamiliar server dialect can never turn this into
    a false refusal that blocks a clinic's migration. Only a positive
    "absent" refuses.
    """
    parsed = False
    for row in rows or []:
        if not row or not row[0]:
            continue
        upper = str(row[0]).upper().strip()
        if not upper.startswith("GRANT ") or " ON " not in upper:
            # role grants ("GRANT `r1`@`%` TO ...") and anything else we
            # do not recognise: not evidence either way
            continue
        parsed = True
        head, rest = upper.split(" ON ", 1)
        # PROCESS is a GLOBAL privilege: only a grant `ON *.*` can carry
        # it. `GRANT ALL PRIVILEGES ON `somedb`.*` is all privileges the
        # SCHEMA level has, which does not include PROCESS — reading it
        # as global was the same fail-open in a new place.
        scope = rest.split(" TO ", 1)[0].strip() if " TO " in rest \
            else rest.strip()
        if scope != "*.*":
            continue
        for privilege in head[len("GRANT "):].split(","):
            if privilege.strip() in ("PROCESS", "ALL PRIVILEGES"):
                return "held"
    return "absent" if parsed else "unknown"


def documents_expanded_size(tar_path: str) -> int:
    """Expanded footprint of the documents archive (sum of member sizes
    from the archive's own headers); the archive's own size is what a
    .tar.gz compresses PDFs to, not what the tree needs on disk. An
    archive whose headers cannot be read is REFUSED here rather than
    guessed at -- see the comment below for why a fallback was worse
    than useless."""
    try:
        entries = o19bundle.read_tar_entries(tar_path,
                                             tar_path.endswith(".gz"))
    except o19bundle.ARCHIVE_ERRORS as exc:
        # Refuse here rather than guess. Falling back to the COMPRESSED
        # size budgets a fraction of what a tree of PDFs needs, and the
        # guess buys nothing anyway: P5 reads the same headers through
        # the same function and dies outright on the same archive
        # (o19docs.run_docs, "cannot read documents tar"). Warning now
        # and refusing later spends the pre-import snapshot and the whole
        # staging restore before saying no -- the same reason the
        # charset-mojibake finding is a blocker and not an advisory.
        die("cannot read the documents archive ({0}). The documents "
            "phase reads the same headers and refuses the same file, so "
            "this import cannot finish: re-export the documents tar "
            "(GNU tar, no sparse members) and try again."
            .format(str(exc)[:200]))
    return max(o19bundle.entries_size(entries), os.path.getsize(tar_path))


def rollback_hint(state: Dict) -> str:
    """How to get back to a stock deploy, given what P3 actually did.

    Every refusal downstream of P3 used to name the pre-import snapshot.
    P3 can be told to skip it (`--accept no-pre-backup`, or a backup unit
    that failed), and it records that — so those refusals were handing
    the operator a remedy that does not exist, at the point where they
    have least time to discover it."""
    backup = state.get("phases", {}).get("backup", {})
    if backup.get("skipped"):
        return ("there is NO pre-import snapshot (this run recorded "
                "--accept no-pre-backup), so the target cannot be rolled "
                "back: the way to a stock deploy is `carlos-ctl "
                "destroy-data --confirm <server name>` plus `carlos-ctl "
                "db-users` and `carlos-ctl db-migrate`, and the clinic "
                "must re-export")
    return "restore the pre-import restic snapshot"


def resume_hint(state: Dict) -> str:
    """' --resume' when the workspace already records phases beyond the
    stage (a literal rerun of the hinted command is refused without it)."""
    phases = set(state.get("phases", {})) - {"stage"}
    return " --resume" if phases else ""


def etl_started(state_dir: str) -> bool:
    """True once the ETL has written anything to the target (the progress
    ledger exists with a seed step or a table entry)."""
    progress = o19etl.load_progress(state_dir)
    return bool(progress.get("admin_provider_no") or progress.get("tables"))


def remaining_capacity_factors(state: Dict,
                               restage: bool) -> Tuple[float, float]:
    """What a run still has to WRITE, as (multiple of the uncompressed
    dump owed to the database volume, multiple of the expanded documents
    tar owed to the state volume).

    P0 runs on every invocation, `--resume` included. The fresh-run
    budget is 2.5x the dump -- 1x the staging restore, 1x the copy into
    the target, 0.5x the archive schema -- and re-demanding all of it on
    a resume asked the host a second time for space the run itself had
    already spent on that same volume. A clinic host provisioned to the
    2.5x the documentation asks for was therefore REFUSED on the tool's
    own recovery path: the resume P4's abort message prescribes, the one
    P5's reconciliation failure prescribes, and the one carrying
    `--accept content-transfer`. There is no --accept class for a disk
    refusal, so the operator's only outs were growing the volume
    mid-cutover or hand-dropping o19_import (which makes P4/P7 row
    parity unrunnable).

    Judged per PHASE, never per byte: a phase merely in progress still
    budgets its whole share, because a half-written one may have written
    almost nothing. `--restage` drops and re-restores the staging
    schema, so its 1x is owed again.
    """
    db = 2.5
    if phase_done(state, "stage") and not restage:
        db -= 1.0                     # the staging restore is on disk
        if phase_done(state, "etl"):
            db -= 1.5                 # ... and so are the copy + archive
    docs = state.get("phases", {}).get("documents", {})
    # P5 leaves the phase in-progress with restored=True until
    # reconciliation passes, and its failure message prescribes
    # --resume -- so the tree is on disk for exactly the resume that
    # used to be charged for extracting it again.
    if docs.get("status") == "done" or docs.get("restored"):
        return db, 0
    return db, 2


def rewound_workspace_refusal(state: Dict,
                              state_dir: str) -> Optional[str]:
    """The message refusing a workspace whose two ledgers describe
    different runs, or None.

    `state.json` (phases) and `etl-progress.json` (per-table marks) are
    written at different moments and read by different guards --
    `etl_started` sees only the second, the phase dispatch only the
    first. The pre-import restic snapshot covers the workspace, but it
    is taken at P3, BEFORE P1 stages anything: its `state.json` records
    no `stage` phase and no ETL ledger exists yet. `restic restore` puts
    that `state.json` back and leaves the LATER `etl-progress.json`
    where it is, so the rollback the tool itself prescribes ends with
    one ledger saying the ETL never ran and another saying two hundred
    tables are done.

    Nothing noticed, and every documented next step then refused -- two
    of them naming the snapshot just restored: a rerun without --resume
    (recorded state), --resume (P1's staging-drop gate, which offers
    --restage), --resume --restage (refused because the stale ledger
    says the ETL already copied), and --cleanup (mid-import). The one
    instruction that works is the ETL's own rewind witness, and that
    sits behind P1, i.e. behind the gate this inconsistency blocks.

    The pair is proof rather than a heuristic: P1 records the stage
    phase (`mark_started`, before the DROP) and P4 runs only after P1,
    so an ETL ledger with writes and a `state.json` with no stage phase
    cannot both belong to one run. `--restage` clears the two together,
    and `--cleanup` retires them together, so neither leaves this
    shape behind."""
    if state.get("phases", {}).get("stage"):
        return None
    if not etl_started(state_dir):
        return None
    return ("the two ledgers in {0} describe different runs: state.json "
            "records no staged dump, while the ETL ledger records writes "
            "into the target. That is what restoring the pre-import "
            "snapshot leaves behind — it is taken before the dump is "
            "staged, so the restore rewinds state.json past the ETL "
            "while etl-progress.json, written afterwards, stays on disk. "
            "This run cannot be resumed or cleaned up. Move the "
            "workspace aside and start the import over against the "
            "restored database:\n  mv {0} {0}.rolled-back".format(
                state_dir))


def run_p0_capacity(ctx) -> None:
    """The server/disk half of P0 (replicas, headroom) — shared with the
    o19-preflight verb, which must not touch the target's pristine
    verdict."""
    query = ctx["query"]

    # Replicas would silently diverge: the whole ETL runs with
    # sql_log_bin=0, so a replica keeps the pristine seed while the
    # primary holds the clinic's chart, and nothing downstream compares
    # them. SHOW REPLICA HOSTS lists only replicas that registered a
    # report_host (unset by default), so a conventionally configured
    # replica is invisible to it — the dump threads are the reliable
    # signal and are checked as well.
    replicas = []
    for probe in ("SHOW REPLICA HOSTS", "SHOW SLAVE HOSTS"):
        try:
            replicas = query(probe)
            break
        except RuntimeError:
            continue
    # Without the PROCESS privilege the server does not ERROR on this
    # query — it silently restricts PROCESSLIST to the caller's own
    # threads, so the binlog-dump count comes back 0 and the gate passes
    # on a host that does have replicas attached. The connection is
    # root-on-localhost by contract, which holds PROCESS, so this is
    # belt-and-braces: it refuses only on a POSITIVE determination that
    # the privilege is absent, and stays quiet whenever the grant probe
    # is itself unavailable or unparseable (a false refusal here would
    # block an import for no reason).
    try:
        grant_rows = query("SHOW GRANTS")
    except RuntimeError:
        grant_rows = None
    if process_grant_state(grant_rows) == "absent":
        die("the database account this import runs as does not hold the "
            "PROCESS privilege, so information_schema.PROCESSLIST shows "
            "only its own threads and an attached replica would go "
            "unnoticed. The import's binlog-off bulk copy is not "
            "replica-safe: grant PROCESS, or detach the replicas and say "
            "so.")
    try:
        dump_threads = query(
            "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE "
            "COMMAND IN ('Binlog Dump', 'Binlog Dump GTID')")
    except RuntimeError as exc:
        # the more reliable half of the check — SHOW REPLICA HOSTS only
        # sees replicas that registered a report_host. Failing open here
        # would let the import silently diverge an attached replica,
        # which is unrecoverable without re-importing.
        die("cannot determine whether replicas are attached (the "
            "information_schema.PROCESSLIST probe failed: {0}). The "
            "import's binlog-off bulk copy is not replica-safe, so it "
            "will not proceed on an unknown answer: detach the replicas "
            "and say so, or fix the probe.".format(
                str(exc).strip()[:200]))
    connected = 0
    if dump_threads and dump_threads[0] and dump_threads[0][0]:
        try:
            connected = int(dump_threads[0][0])
        except ValueError:
            connected = 0
    if replicas or connected:
        die("this database server has replicas attached ({0} registered, "
            "{1} live binlog dump thread(s)) — the import's binlog-off "
            "bulk copy is not replica-safe and the replicas would keep "
            "the pristine seed. Detach them first.".format(
                len(replicas), connected))

    stage = ctx["state"].get("phases", {}).get("stage", {})
    restage = bool(ctx.get("restage"))
    # how much of the budget is still to be written; a resume that asked
    # for the whole fresh-run figure refused hosts sized as documented
    db_factor, docs_factor = remaining_capacity_factors(ctx["state"],
                                                        restage)
    # --restage drops the staged dump and restores this one, but P1 pops
    # the stage phase only later — so without the restage arm the gate
    # would size the NEW dump from the OLD one's recorded figure
    if restage or not phase_done(ctx["state"], "stage"):
        log("measuring the dump's uncompressed size for the disk check ...")
        dump_bytes = uncompressed_size(ctx["dump"])
        ctx["dump_uncompressed"] = dump_bytes
    else:
        # already restored: only the copy into the target is left, sized
        # by what the stage phase recorded (never re-measured)
        dump_bytes = int(stage.get("uncompressed_bytes") or ctx["dump_size"])
    docs_bytes = 0
    if ctx.get("documents") and docs_factor:
        log("measuring the documents archive's expanded size ...")
        docs_bytes = documents_expanded_size(ctx["documents"])
        ctx["documents_size"] = docs_bytes
    headroom = check_disk_headroom(dump_bytes, ctx.get("bundle_size", 0),
                                   docs_bytes, server_datadir(query),
                                   db_factor, docs_factor)
    if headroom:
        die(headroom)


def inherited_import_refusal(archive_present: bool,
                             kept_tables: Sequence[str],
                             target_db: str) -> Optional[str]:
    """Why this host may not import a second clinic, or None.

    Two leftovers of a previous import, both of which would silently mix
    two clinics' records:

    * the `o19_archive` schema, inherited whole (its tables are rebuilt
      per table, so a table this dump does not carry survives) and then
      exported into this clinic's document tree;
    * the `import_archived_` tables in the LIVE schema. The emptiness
      sweep cannot see these -- it iterates the manifest, and a
      preserved table is by definition not in it -- so without this they
      would take the second clinic's rows under the first's names, in
      the schema the application reads.

    Pure so the refusal can be tested without a database; the caller
    supplies what it found."""
    if archive_present:
        return ("the archive schema {0} of a previous import exists — run "
                "`import-o19 --cleanup` for that run, or drop the schema "
                "once the clinic holds its CSV export, before importing "
                "another clinic on this host".format(ARCHIVE_SCHEMA))
    if kept_tables:
        names = sorted(kept_tables)
        return ("the target schema {0} already holds {1} table(s) "
                "preserved by a previous import ({2}{3}) — this host has "
                "imported a clinic already. Export and drop them before "
                "importing another clinic, or --resume the run that made "
                "them.".format(target_db, len(names), ", ".join(names[:5]),
                               ", ..." if len(names) > 5 else ""))
    return None


def run_p0(ctx) -> None:
    """P0 -- refuse anything but a stock, province-matched, pristine target.

    Runs the shared capacity half, then the checks that only a real
    import may make: Flyway `validate` against the deployed WAR, no
    inherited `o19_archive`, and live row counts within the manifest's
    seed floors. Dies on the first refusal; a `--dev-target` run skips
    the checks that assume a packaged host."""
    query = ctx["query"]
    dev = ctx["dev_target"]
    # The manifest is curated FOR one province, and says which: every
    # ruling in it -- which table is copied, which column is dropped,
    # which rows the pristine sweep expects -- was decided against that
    # province's CARLOS schema. Running it against another host would not
    # fail loudly; it would classify silently and wrongly, and the seed
    # floors would refuse the host for the wrong reason.
    #
    # Asserted rather than string-tested against 'on': this is the check
    # that has to hold when a second profile ships, and a check written
    # only when its second case exists has never been run.
    profile = getattr(o19map_schema, "O19_PROFILE", None)
    if profile != ctx.get("province"):
        die("this package carries the {0!r} schema manifest and the host "
            "is configured for province {1!r}. Every table ruling in the "
            "manifest was curated against one province's CARLOS schema, "
            "so it cannot be run against another: install a carlos-ctl "
            "whose manifest profile matches, or correct the host's "
            "province."
            .format(profile, ctx.get("province")))
    # A profile can be CARRIED before it is supported. Curating a
    # province's rulings is what makes them reviewable and testable, but
    # only a full rehearsal -- a clinic database of that province taken
    # from P0 to a passing P7 -- earns a run against a real clinic. The
    # gap is deliberate and named, so an operator meets a refusal that
    # explains itself rather than an import that quietly assumes its
    # unrehearsed rulings hold.
    supported = getattr(o19map_schema, "SUPPORTED_PROVINCES", ("on",))
    if ctx.get("province") not in supported:
        die("this package carries a {0!r} schema manifest but the {0!r} "
            "profile has not completed an end-to-end migration rehearsal, "
            "so the import will not run against a clinic database. "
            "Supported provinces in this build: {1}."
            .format(ctx.get("province"), ", ".join(supported)))
    run_p0_capacity(ctx)

    if not dev:
        rc = dbops.run_flyway("validate")
        if rc != 0:
            die("flyway validate failed — the carlos schema does not match "
                "the deployed application (run carlos-ctl db-migrate first)")

    recorded = set(ctx["state"].get("phases", {})) - {"stage"}
    if not (ctx.get("resume") and (recorded or etl_started(ctx["state_dir"])))\
            and not dev:
        # a previous import's archive schema would be inherited whole
        # (its tables are per-table DROP+CREATE) and its rows exported
        # into this clinic's document tree; only a resume of a RECORDED
        # run may find its own archive here
        left = query("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
                     "WHERE SCHEMA_NAME = '{0}'".format(ARCHIVE_SCHEMA))
        kept = [r[0] for r in query(
            "SELECT TABLE_NAME FROM information_schema.TABLES WHERE "
            "TABLE_SCHEMA = '{0}' AND TABLE_NAME LIKE '{1}%'".format(
                ctx["target_db"], o19etl.ARCHIVED_PREFIX))]
        refusal = inherited_import_refusal(bool(left), kept,
                                           ctx["target_db"])
        if refusal:
            die(refusal)
    prior = ctx["state"].get("phases", {}).get("check-pristine", {})
    if ctx.get("resume") and prior.get("status") == "done" \
            and prior.get("pristine") is True \
            and etl_started(ctx["state_dir"]):
        # the sweep passed before the ETL began writing; the target is
        # mid-import by design now, so re-sweeping would refuse every
        # legitimate resume (the row-parity gate still verifies the result)
        log("resume: pristine gate passed at {0}; the target is mid-import "
            "— emptiness sweep not repeated".format(
                ctx["state"]["phases"]["check-pristine"].get("at")))
        report_append(ctx["state_dir"], "P0 check-pristine",
                      "resumed: sweep passed on the original run")
        return

    counts = gather_copy_counts(query, ctx["target_db"])
    startup = startup_row_counts(query, ctx["target_db"])
    # tables pristine_violations() waves through (the deploy's own audit
    # rows). Waved through is not invisible: their counts go in the
    # report, because P0 is the last place anyone looks before the copy
    # deletes them.
    tolerated = {
        t: counts[t] for t in
        getattr(o19map_schema, "PRISTINE_TOLERATED_TABLES", ())
        if counts.get(t)}
    violations = pristine_violations(tolerate_startup_rows(counts, startup))
    identity_rows = query(
        "SELECT user_name FROM `{0}`.security".format(ctx["target_db"]))
    users = sorted(r[0] for r in identity_rows if r)
    if users != [o19map_schema.SEED_USER_NAME]:
        # the logins themselves go to a root-only file: a login name is
        # a person, and report.txt is the shareable record.
        #
        # Its OWN file, not verify-details.txt: that one is P7's, both
        # writers truncate, and a --dev-target run continues past this
        # sweep, so P7 would erase the record of what P0 refused on —
        # and the sweep line in report.txt would point at a file
        # describing something else entirely.
        write_private(os.path.join(ctx["state_dir"], PRISTINE_DETAILS),
                      "P0 pristine sweep: security holds "
                      + ", ".join(repr(u) for u in users) + "\n")
        violations.append("security holds {0} login(s) where only the "
                          "'{1}' seed is expected (named in "
                          "{2})".format(
                              len(users), o19map_schema.SEED_USER_NAME,
                              PRISTINE_DETAILS))
    if violations:
        text = ("the import runs ONLY on a stock initial deploy; this "
                "database is not one:\n  " + "\n  ".join(violations[:25])
                + ("\n  ... and {0} more".format(len(violations) - 25)
                   if len(violations) > 25 else "")
                + "\nNo --accept flag clears this. To start from a stock "
                  "schema: `carlos-ctl destroy-data --confirm <server "
                  "name>`, then `carlos-ctl db-users` and `carlos-ctl "
                  "db-migrate` — and do not log in to the result, not "
                  "even once, before the import.")
        # written BEFORE the refusal: a hard P0 stop otherwise leaves no
        # trace anywhere but the terminal
        report_append(ctx["state_dir"], "P0 check-pristine",
                      "target {0}: REFUSED\n  ".format(ctx["target_db"])
                      + "\n  ".join(violations[:25]))
        if dev:
            warn("DEV TARGET: pristine sweep downgraded to a warning:\n"
                 + text)
        else:
            die(text)
    report_append(ctx["state_dir"], "P0 check-pristine",
                  "target {0}: {1} copy/merge-class tables checked (copy: "
                  "exact seed rows or empty; merge: at least the reference "
                  "seeds); pristine={2}{3}{4}{5}{6}".format(
                      ctx["target_db"], len(counts), not violations,
                      " (DEV TARGET — sweep advisory only)" if dev else "",
                      ("\n  startup-created rows tolerated (the webapp's "
                       "first start): " + ", ".join(
                           "{0} {1}".format(t, n)
                           for t, n in sorted(startup.items())))
                      if startup else "",
                      ("\n  pre-existing rows tolerated (this deploy's own; "
                       "the copy deletes them before the clinic's land): "
                       + ", ".join("{0} {1}".format(t, n)
                                   for t, n in sorted(tolerated.items())))
                      if tolerated else "",
                      ("\n  " + "\n  ".join(violations[:25]))
                      if violations else ""))
    if not ctx.get("dry_run"):
        mark_done(ctx["state_dir"], ctx["state"], "check-pristine",
                  pristine=not violations, dev_target=dev)


# --------------------------------------------------------------------------
# P1 — stage
# --------------------------------------------------------------------------

#: `COLLATE=x` in a CREATE TABLE, `COLLATE x` in a column definition.
COLLATION_RE = re.compile(rb"COLLATE[= ]([A-Za-z0-9_]+)")

#: Lines whose content is the clinic's DATA, not its schema. They are
#: skipped: a progress note, an eform template or one of OSCAR's saved
#: SQL report templates can contain the word COLLATE, and a name read
#: out of a text column would refuse a perfectly good dump — mid-cutover,
#: with no flag to clear it. mysqldump writes DDL and data as separate
#: statements, one statement per line for INSERTs, so the line is the
#: boundary.
DATA_LINE_RE = re.compile(rb"\s*(?:INSERT|REPLACE)\b", re.I)

#: enough to hold a collation name split across a chunk boundary
COLLATION_CARRY = 64

#: an unfinished non-data line is held whole up to this much before it is
#: scanned and trimmed. mysqldump writes DDL a few hundred bytes to a
#: line, so this exists to bound memory on a pathological input (a dump
#: with no newlines at all), not because it is ever reached.
MAX_UNFINISHED_LINE = 1 << 20


def ddl_collations(data: bytes) -> Set[str]:
    """The collation names `data` declares, ignoring its data lines.

    `data` is treated as whole lines; a trailing partial line is read
    like any other, which is safe here because both callers either hold
    the whole head or have already split the stream on newlines."""
    names = set()
    for line in data.split(b"\n"):
        if DATA_LINE_RE.match(line):
            continue
        for m in COLLATION_RE.finditer(line):
            names.add(m.group(1).decode("ascii", "replace"))
    return names


def head_collations(head: bytes) -> List[str]:
    """The distinct collation names in the first 64 KiB of a dump.

    The head alone is what can be read BEFORE the restore starts, so it
    is what turns an unavailable collation into a refusal that costs
    nothing. It is not the whole gate: `CollationScanner` below carries
    the same test through the stream, because a dump declares
    `COLLATE=` only where the collation is not the charset default, so
    on an ordinary all-latin1_swedish_ci clinic this finds nothing at
    all -- and a 580-table dump puts most of its DDL far past 64 KiB."""
    return sorted(ddl_collations(head[:65536]))


class CollationScanner:
    """The head check, carried through the whole stream.

    Measured: a dump declaring an unavailable collation at 1.95 MB gets
    past the head scan, and the restore then dies mid-stream with the
    client's own `ERROR 1273 ... Unknown collation` — after however much
    of a multi-hour restore had already run, and with the diagnosis in
    the client's output rather than in a CARLOS refusal. The stream is
    already being read chunk by chunk for the redirect scan, so testing
    each name here costs one regex per megabyte.

    It IS line-anchored, like the redirect scan: only complete lines are
    tested, INSERT/REPLACE lines are skipped as the clinic's own text
    (see DATA_LINE_RE), and an unfinished line is carried whole so a
    name split across a chunk boundary is neither missed nor reported
    cut in half."""

    def __init__(self, available):
        self.available = set(available or ())
        #: the unfinished last line of the stream so far
        self.carry = b""
        #: True while the rest of the current line is a data line
        self.skipping = False

    def feed(self, chunk: bytes) -> Optional[str]:
        if not self.available:
            return None
        buf = self.carry + chunk
        self.carry = b""
        names = set()
        pos = 0
        while pos < len(buf):
            nl = buf.find(b"\n", pos)
            if nl < 0:
                break
            if not self.skipping:
                names |= ddl_collations(buf[pos:nl])
            self.skipping = False
            pos = nl + 1
        tail = buf[pos:]
        if self.skipping:
            pass                       # the rest of a data line: drop it
        elif DATA_LINE_RE.match(tail):
            # decided even though the line is unfinished: an extended
            # INSERT runs to megabytes and must not be buffered
            self.skipping = True
        elif len(tail) > MAX_UNFINISHED_LINE:
            names |= ddl_collations(tail[:-COLLATION_CARRY])
            self.carry = tail[-COLLATION_CARRY:]
        else:
            self.carry = tail
        missing = sorted(names - self.available)
        if not missing:
            return None
        return ("the dump uses collation(s) unavailable on this server: "
                "{0}. The restore was stopped where they appear rather "
                "than left to fail part-way through: re-take the dump on "
                "a server whose collations this one has, or install them "
                "here, then --restage.".format(", ".join(missing)))


def dump_redirect_marker(data: bytes) -> Optional[str]:
    """The refusal message when a dump fragment carries a statement that
    would steer the restore out of the staging schema (or a MySQL GTID
    directive), else None. `data` must start at a line boundary."""
    hit = DUMP_REDIRECT_RE.search(data)
    if hit:
        return ("the dump carries a {0} statement — it was taken with "
                "--databases/--all-databases and would redirect the "
                "restore at the live schema. Re-take it as "
                "`mysqldump <o19-db> > o19.sql` (no --databases)"
                .format(hit.group(1).decode("ascii", "replace").upper()))
    if DUMP_GTID_MARKER in data:
        return ("the dump carries SET @@GLOBAL.GTID_PURGED (a MySQL 5.6+ "
                "GTID directive MariaDB rejects) — re-take it with "
                "mysqldump --set-gtid-purged=OFF")
    return None


_CLIENT_IDENTITY_PREFIXES = ("--user", "-u", "--password", "-p",
                             "--defaults-extra-file", "--defaults-file")
# options that take their value as the NEXT argv element (`--user root`);
# a bare -p / --password prompts instead, so only a following non-option
# token (which the client would read as a database name) is dropped
_VALUE_IN_NEXT_ARG = ("--user", "-u", "--defaults-extra-file",
                      "--defaults-file")


def strip_client_identity(args: List[str]) -> List[str]:
    """Remove every identity-bearing option from a client argv tail —
    attached (`--user=x`, `-px`), paired (`--user x`) and bare prompting
    forms — so a dev seam's credentials never reach the restore client's
    argv (where a stray value becomes a positional database name)."""
    out: List[str] = []
    skip_value = False
    drop_bare_value = False
    for a in args:
        if skip_value:
            skip_value = False
            continue
        if drop_bare_value:
            drop_bare_value = False
            if not a.startswith("-"):
                continue
        if a in _VALUE_IN_NEXT_ARG:
            skip_value = True
            continue
        if a in ("-p", "--password"):
            drop_bare_value = True
            continue
        if a.startswith(_CLIENT_IDENTITY_PREFIXES):
            continue
        out.append(a)
    return out


def staging_client_argv(base_argv: List[str], client_cnf: str,
                        statement_timeout: int = 0) -> List[str]:
    """The restore client's argv: the connection tail of the root argv
    (socket/host/port), identity replaced by the throwaway staging account
    read from a 0600 defaults file (never argv), and --one-database so a
    statement addressed at another schema is skipped rather than run.
    --statement-timeout bounds the dump's own statements too (one crafted
    INSERT could otherwise hold the restore forever).

    --user is repeated on the argv even though the defaults file already
    names it: --defaults-extra-file does NOT suppress the other option
    files, and ~/.my.cnf is read AFTER it. A root ~/.my.cnf carrying
    `[client] user=root` would otherwise silently connect the clinic's
    dump as root, and every backstop this function exists for — the
    schema-scoped grants, --one-database — would be gone. A command-line
    option outranks every option file. --local-infile=0 closes the same
    class for a system defaults file that enables it."""
    tail = strip_client_identity(list(base_argv)[1:])
    init = ("SET SESSION sql_log_bin=0, FOREIGN_KEY_CHECKS=0, "
            "UNIQUE_CHECKS=0, sql_mode=''")
    if statement_timeout:
        init += ", max_statement_time={0}".format(int(statement_timeout))
    return (["mariadb", "--defaults-extra-file=" + client_cnf,
             "--user=" + STAGING_USER, "--local-infile=0",
             "--max-allowed-packet=1G"] + tail
            + ["--one-database", "--init-command=" + init, STAGING_SCHEMA])


# the account is created for both host patterns: a socket connection
# matches 'localhost' first (an anonymous ''@'localhost' row would otherwise
# shadow a '%' entry), a dev seam over TCP matches '%'
STAGING_ACCOUNT_HOSTS = ("localhost", "%")


def staging_account_statements(password: str) -> List[str]:
    """DDL for the throwaway restore account: all privileges on the staging
    schema only. Nothing here reaches the live schema even if the dump
    tries to."""
    pw = dbops.sql_escape(password)
    out = []
    for host in STAGING_ACCOUNT_HOSTS:
        out.append("DROP USER IF EXISTS '{0}'@'{1}'".format(
            STAGING_USER, host))
        out.append("CREATE USER '{0}'@'{1}' IDENTIFIED BY '{2}'".format(
            STAGING_USER, host, pw))
        out.append("GRANT ALL PRIVILEGES ON `{0}`.* TO '{1}'@'{2}'".format(
            STAGING_SCHEMA, STAGING_USER, host))
    return out


def grant_staging_account(query, client_cnf: str) -> None:
    """Create the throwaway staging account and write its client defaults
    file at 0600.

    The restore runs as this account, not as root: it is scoped to the
    staging schema plus `BINLOG ADMIN` (MariaDB 10.5+), so the clinic's
    dump cannot reach the live schema. There is deliberately no SUPER
    fallback -- an older server is refused instead. Any failure revokes
    whatever was created before dying, so a half-created account never
    outlives this call."""
    password = genpw()
    try:
        for sql in staging_account_statements(password):
            query(sql)
    except RuntimeError as exc:
        # a half-created account (one host row in, the other refused)
        # must not outlive the failure
        revoke_staging_account(query, client_cnf)
        die("cannot create the staging account: {0}".format(
            str(exc).strip()[:300]))
    # SET SESSION sql_log_bin needs the scoped BINLOG ADMIN privilege
    # (MariaDB 10.5+). There is deliberately no SUPER fallback: SUPER would
    # widen the throwaway account far beyond the staging schema, so an
    # older server is refused instead
    for host in STAGING_ACCOUNT_HOSTS:
        try:
            query("GRANT BINLOG ADMIN ON *.* TO '{0}'@'{1}'".format(
                STAGING_USER, host))
        except RuntimeError as exc:
            revoke_staging_account(query, client_cnf)
            die("cannot grant BINLOG ADMIN to the staging account ({0}); "
                "the import needs MariaDB 10.5 or newer so the restore can "
                "run under a schema-scoped account".format(
                    str(exc).strip()[:200]))
    fd = os.open(client_cnf, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    # the mode argument applies to a NEW file only, and O_TRUNC does not
    # reset an existing one's -- the same reason write_private,
    # durable_json, stage_digests, write_fragment and the archive CSV
    # writer all fchmod. This file carries the live password of an
    # account holding ALL PRIVILEGES on a full copy of the clinic's EMR.
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write("[client]\nuser={0}\npassword={1}\n".format(
            STAGING_USER, password.replace("\\", "\\\\")))


def revoke_staging_account(query, client_cnf: str) -> None:
    """Drop the staging account on every host row and remove its defaults
    file.

    The unlink is in a `finally`: a `DROP USER` that fails must still
    take the password off disk."""
    try:
        for host in STAGING_ACCOUNT_HOSTS:
            query("DROP USER IF EXISTS '{0}'@'{1}'".format(
                STAGING_USER, host))
    finally:
        if os.path.exists(client_cnf):
            os.unlink(client_cnf)


# an incomplete line longer than this is carried as "mid-line" instead:
# its start was scanned in an earlier buffer, and an unbounded carry
# would hold a single-line dump entirely in memory
DUMP_CARRY_MAX = 1 << 16


class RedirectScanner:
    """Scans a dump chunk by chunk for a redirecting statement, tracking
    line starts across chunk boundaries.

    dump_redirect_marker anchors on `^`, so it must only ever see a
    buffer that begins a line. A fixed-size carry gets this wrong in both
    directions: it misses a marker split across the boundary (`CREATE`
    plus whitespace ending one chunk), and it matches `^` in the middle
    of a clinical note that happens to start the carry."""

    def __init__(self):
        self.carry = b""       # the trailing incomplete line, a line start
        self.mid_line = False  # the next buffer opens inside a line

    def feed(self, chunk: bytes) -> Optional[str]:
        buf = self.carry + chunk
        scan = buf
        if self.mid_line:
            nl = buf.find(b"\n")
            scan = buf[nl + 1:] if nl >= 0 else b""
        marker = dump_redirect_marker(scan)
        nl = buf.rfind(b"\n")
        partial = buf[nl + 1:] if nl >= 0 else buf
        if nl < 0 or len(partial) > DUMP_CARRY_MAX:
            # one line longer than the carry bound: its start was scanned
            # already, so the next buffer opens mid-line
            self.carry, self.mid_line = b"", True
        else:
            self.carry, self.mid_line = partial, False
        return marker


def _stream_dump(opener: List[str], restore_argv: List[str],
                 available_collations=()):
    """Pipe the dump through the restore client, scanning every chunk for
    redirecting statements and for a collation this server lacks.
    Returns (source_rc, client_rc, tail_bytes, refusal_or_None); the
    caller drops the staging schema and dies on a refusal, which is the
    same handling either scan needs."""
    src = subprocess.Popen(opener, stdout=subprocess.PIPE)  # nosec B603
    sink = subprocess.Popen(restore_argv,                    # nosec B603
                            stdin=subprocess.PIPE)
    tail = b""
    scanner = RedirectScanner()
    collations = CollationScanner(available_collations)
    broken = False
    redirect = None
    try:
        while True:
            chunk = src.stdout.read(1 << 20)
            if not chunk:
                break
            # the whole stream is scanned, not only its head: a USE /
            # CREATE DATABASE anywhere would steer the rest of the dump
            # (the account's grants and --one-database are the backstops)
            redirect = scanner.feed(chunk) or collations.feed(chunk)
            if redirect:
                broken = True
                break
            try:
                sink.stdin.write(chunk)
            except BrokenPipeError:
                # the client died on a bad statement: stop feeding it, let
                # both children be reaped and report through rc below
                broken = True
                break
            tail = (tail + chunk)[-8192:]
    finally:
        src.stdout.close()
        try:
            sink.stdin.close()
        except BrokenPipeError:
            broken = True
    if broken and src.poll() is None:
        src.terminate()
    src.wait()
    rc = sink.wait()
    if broken and rc == 0:
        rc = 1
    return src.returncode, rc, tail, redirect


def run_p1(ctx) -> None:
    """P1 stage -- restore the clinic's dump into the staging schema.

    Idempotent on the dump's sha256: a phase already recorded `done` for
    the same digest returns without touching the database, so a resume
    never replays a multi-hour restore. A DIFFERENT digest is refused
    unless `--restage` was passed."""
    query = ctx["query"]
    dump = ctx["dump"]
    dump_sha = sha256_file(dump)
    prev = ctx["state"].get("phases", {}).get("stage", {})
    if prev.get("status") == "done":
        if prev.get("dump_sha256") == dump_sha and not ctx["restage"]:
            log("stage: already restored this dump (sha256 match) — "
                "skipping")
            return
        if not ctx["restage"]:
            if etl_started(ctx["state_dir"]):
                # do not offer --restage here: the next gate refuses it,
                # and the operator would spend a round trip finding out
                die("a different dump was offered, but the ETL has "
                    "already copied from the one staged earlier — the "
                    "two cannot be mixed and --restage will not be "
                    "accepted. Restore the pre-import snapshot and start "
                    "over, or re-run with the dump this workspace "
                    "staged.")
            die("a different dump was already staged — pass --restage to "
                "drop {0} and restore this one".format(STAGING_SCHEMA))
    if ctx["restage"] and etl_started(ctx["state_dir"]):
        die("the ETL already copied from the previously staged dump into "
            "the target — restaging a different dump now would mix two "
            "sources. Restore the pre-import snapshot and start over.")
    if ctx["restage"]:
        progress = o19etl.progress_path(ctx["state_dir"])
        if os.path.exists(progress):
            os.unlink(progress)
        # the preflight verdict belongs to the dump it was run over
        for phase in ("stage", "preflight"):
            ctx["state"].get("phases", {}).pop(phase, None)
        save_state(ctx["state_dir"], ctx["state"])

    gz = dump.endswith(".gz")
    opener = ["gzip", "-dc", dump] if gz else ["cat", dump]

    # collation availability pre-check on the head of the stream
    head_cp = subprocess.Popen(opener, stdout=subprocess.PIPE)  # nosec B603
    head = head_cp.stdout.read(65536)
    head_cp.stdout.close()
    head_cp.terminate()
    head_cp.wait()
    available = {row[0] for row in query("SHOW COLLATION")}
    missing = [c for c in head_collations(head) if c not in available]
    if missing:
        die("the dump uses collation(s) unavailable on this server: {0}"
            .format(", ".join(missing)))
    redirect = dump_redirect_marker(b"\n" + head)
    if redirect:
        die(redirect)

    # the drop below is the one place this phase can destroy data that
    # exists nowhere else: rows of a dump this workspace never staged
    refusal = staging_drop_refusal(
        staging_holds_rows(query), prev.get("dump_sha256"), dump_sha,
        ctx["restage"])
    if refusal:
        die(refusal)
    log("staging dump into {0} (binlog off, throwaway schema, restricted "
        "account) ...".format(STAGING_SCHEMA))
    # recorded BEFORE the drop, so a restore that dies half-way leaves a
    # ledger saying which dump the leftover rows came from and the retry
    # is not refused by the gate above
    mark_started(ctx["state_dir"], ctx["state"], "stage",
                 dump_sha256=dump_sha)
    query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
    query("CREATE DATABASE `{0}`".format(STAGING_SCHEMA))
    client_cnf = os.path.join(ctx["state_dir"], ".stage-client.cnf")
    restore_argv = staging_client_argv(ctx["query"].base_argv, client_cnf,
                                       ctx.get("statement_timeout", 0))
    grant_staging_account(query, client_cnf)

    try:
        src_rc, rc, tail, redirect = _stream_dump(opener, restore_argv,
                                                  available)
    finally:
        # the account and its credential file never outlive the restore,
        # whatever the failure mode
        revoke_staging_account(query, client_cnf)
    if redirect:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die(redirect)
    # the CLIENT first: when it dies on a statement it may not run, the
    # write end breaks and the source is terminated, so src_rc is
    # non-zero for a perfectly good dump. Reporting "corrupt archive"
    # there sends the operator to re-fetch a file that is fine, and the
    # message naming the actual cause is never reached.
    if rc != 0:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        # Naming the three flags alone was a remedy that could not
        # work: all three are already true of the documented recipe,
        # and a VIEW's DEFINER is not stripped by any mysqldump flag.
        # A clinic view is the likeliest cause of ERROR 1227 here, and
        # the only fix is to leave the views out of the dump — which
        # costs nothing, the import migrating base tables only.
        die("restore into {0} failed — see the client error above. The "
            "restore runs as an account limited to that schema: a dump "
            "carrying DEFINER clauses, GRANTs or server-wide SET "
            "statements must be re-taken without them (mysqldump "
            "--skip-triggers --set-gtid-purged=OFF, no --databases). "
            "ERROR 1227 naming SUPER or SET USER means the schema holds "
            "a VIEW: no flag strips a view's DEFINER, so re-take the "
            "dump excluding each one "
            "(--ignore-table=<db>.<view>). The import needs none of "
            "them — it migrates base tables only. o19_preflight.py "
            "lists this schema's views and prints the exact flags."
            .format(STAGING_SCHEMA))
    if src_rc != 0:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("reading the dump failed (corrupt archive?)")
    if DUMP_COMPLETED_MARKER not in tail:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("the dump has no '-- Dump completed' trailer — it is truncated "
            "or was interrupted; take a fresh mysqldump on the O19 server")

    n_tables = query("SELECT COUNT(*) FROM information_schema.TABLES "
                     "WHERE TABLE_SCHEMA = '{0}'".format(STAGING_SCHEMA))
    if ctx.get("dump_uncompressed") is None:
        # never record None: every later resume reads this back through
        # `or ctx["dump_size"]`, which is the COMPRESSED size for a .gz —
        # an order of magnitude too lax on the one volume that must not
        # fill
        ctx["dump_uncompressed"] = uncompressed_size(ctx["dump"])
    mark_done(ctx["state_dir"], ctx["state"], "stage", dump_sha256=dump_sha,
              uncompressed_bytes=ctx["dump_uncompressed"])
    report_append(ctx["state_dir"], "P1 stage",
                  "restored {0} ({1} tables) from {2}\nsha256 {3}".format(
                      STAGING_SCHEMA, n_tables[0][0],
                      os.path.basename(dump), dump_sha))


# --------------------------------------------------------------------------
# P2 — preflight (import mode)
# --------------------------------------------------------------------------

def run_p2(ctx) -> Dict:
    """P2 preflight -- assess the staged dump and record the verdict.

    Returns a dict carrying `verdict`, `exit_code`, `acknowledged` and
    `required_accepts`; the `o19-preflight` verb turns that exit code
    into its own. A full assessment also carries `findings`, but the
    resume path below does NOT: that path re-runs no checks, so it has
    nothing to report, and returning an empty `findings` would assert
    "nothing found" where the truth is "not assessed". Read it only from
    a return you know came from a full run.

    On a resume whose copy has already started the recorded verdict
    stands and the checks are NOT re-run: staging has been normalised
    since, so re-assessing could only refuse a target that is mid-import
    by design."""
    query = ctx["query"]
    prior = ctx["state"].get("phases", {}).get("preflight", {})
    if ctx.get("resume") and prior.get("status") == "done" \
            and etl_started(ctx["state_dir"]):
        # the verdict was recorded before the copy began and the staging
        # schema has since been normalised (table case); re-running the
        # checks could only refuse a target that is mid-import by design
        log("resume: preflight verdict '{0}' recorded at {1}; the copy has "
            "started — not re-assessed".format(prior.get("verdict"),
                                               prior.get("at")))
        report_append(ctx["state_dir"], "P2 preflight",
                      "resumed: verdict recorded on the original run")
        return {"verdict": prior.get("verdict"), "exit_code": 0,
                "acknowledged": prior.get("acknowledged", []),
                "required_accepts": []}

    def pf_query(sql):
        return query(sql, db=STAGING_SCHEMA)

    props = None
    if ctx.get("properties"):
        # a malformed file raises ValueError; without this it would
        # escape as a traceback, and it does so at P2 — after the
        # pre-import snapshot and the full staging restore, although the
        # check needs nothing but the file (_make_ctx runs it first now)
        try:
            props = o19_preflight.parse_properties(ctx["properties"])
        except (ValueError, OSError) as exc:
            die("cannot parse {0} as a java.util.Properties file ({1}) — "
                "CARLOS would reject it too. Obtain a readable "
                "oscar.properties from the clinic and --resume."
                .format(ctx["properties"], exc))
    report = o19_preflight.run_checks(
        pf_query, properties=props, province=ctx["province"],
        accepted=ctx["accepted"], schema_map=o19map_schema,
        db_name=STAGING_SCHEMA)
    # 0600 like every other artifact naming the clinic's own objects:
    # these two carry the unknown-table inventory, the identifier-class
    # names, the credential tables found and the per-table counts. The
    # workspace lives on the CARLOS host beside admin-credentials.txt,
    # and a purpose-built private writer was already in this file.
    text = o19_preflight.render_text(report)
    write_private(os.path.join(ctx["state_dir"], "preflight.json"),
                  json.dumps(report, indent=1, sort_keys=True))
    write_private(os.path.join(ctx["state_dir"], "preflight.txt"), text)
    sys.stdout.write(text)
    report_append(ctx["state_dir"], "P2 preflight",
                  "verdict: {0}; acknowledged: {1}".format(
                      report["verdict"],
                      ", ".join(report["acknowledged"]) or "none"))
    if ctx.get("dry_run"):
        # a dry run IS the assessment: report the verdict, never error out,
        # and do not mark the phase done — a real run re-checks. The
        # content check runs here too, and reports rather than refuses:
        # telling an operator their transfer is intact BEFORE the cutover
        # window is most of what a dry run is for.
        content = content_transfer_check(ctx)
        report_content_transfer(ctx, content)
        log("dry run: content transfer — " + content["summary"])
        log("dry run: preflight verdict is '{0}'".format(report["verdict"]))
        return report
    if report["verdict"] == "no-go":
        die("preflight verdict: no-go — remediate the blockers above "
            "(full report: {0}/preflight.txt)".format(ctx["state_dir"]))
    if report["verdict"] == "go-with-acknowledgements":
        die("preflight requires explicit sign-off — rerun with: "
            "{0}{1}".format(
                " ".join("--accept " + a
                         for a in report["required_accepts"]),
                resume_hint(ctx["state"])))
    # after the verdict, so a no-go is not made to wait for a full scan of
    # the staged schema, and before mark_done, so a run that cannot show
    # the transfer was faithful does not record the phase as passed
    content = content_transfer_check(ctx)
    report_content_transfer(ctx, content)
    log("content transfer: " + content["summary"])
    refusal = content_transfer_refusal(content, ctx["accepted"])
    if refusal:
        die(refusal + resume_hint(ctx["state"]))
    mark_done(ctx["state_dir"], ctx["state"], "preflight",
              verdict=report["verdict"],
              acknowledged=report["acknowledged"])
    return report


# --------------------------------------------------------------------------
# P2 — content transfer check (M22)
# --------------------------------------------------------------------------
#
# The row-parity checks at P4 and P7 all COUNT. They prove no row was
# orphaned, which is a different claim from "every row arrived intact". This
# is the first of the two content comparisons: the clinic measured its live
# database before the dump was taken, and this asks whether the dump, the
# transfer and the restore carried the same VALUES into staging.
#
# Both sides are deliberately UNREPAIRED here. P2 runs before the ETL, so a
# genuinely double-encoded clinic hashes as double-encoded on both sides and
# still matches -- the charset repair is a declared transform of the COPY,
# and belongs to the P7 comparison. Repairing here would fail the transfer
# check for a fault that is not a transfer fault.

def staging_column_types(ctx) -> Dict[str, List[Tuple[str, str]]]:
    """`{table: [(column, DATA_TYPE)]}` for the staging schema, in
    ORDINAL_POSITION order, base tables only."""
    out: Dict[str, List[Tuple[str, str]]] = {}
    rows = ctx["query"](
        "SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE "
        "FROM information_schema.COLUMNS c "
        "JOIN information_schema.TABLES t "
        "  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA "
        " AND t.TABLE_NAME = c.TABLE_NAME "
        "WHERE c.TABLE_SCHEMA = '{0}' AND t.TABLE_TYPE = 'BASE TABLE' "
        "ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION".format(STAGING_SCHEMA),
        db=STAGING_SCHEMA)
    for row in rows:
        if len(row) >= 3 and row[0]:
            out.setdefault(row[0], []).append((row[1], row[2]))
    return out


def content_transfer_check(ctx) -> Dict:
    """Compare the clinic's digests against the restored staging schema.

    Returns `{"status": ..., "summary": str, ...}` where status is one of
    `absent` (no digest document was supplied), `unreadable` (one was, and
    could not be used) or `compared`. Never raises: an operator who cannot
    run the check must be told exactly that, not handed a traceback in the
    middle of a phase that has staged a clinic's whole database.
    """
    path = ctx.get("o19_digests")
    if not path:
        return {"status": "absent", "summary":
                "no clinic content digests were supplied, so the transfer "
                "could be checked by row count only"}
    try:
        document = o19digest.load_document(path)
    except ValueError as exc:
        return {"status": "unreadable", "summary": str(exc)}

    def run(sql: str) -> o19digest.Digest:
        return o19digest.Digest.from_row(
            ctx["query"](sql, db=STAGING_SCHEMA)[0])

    result = o19digest.compare_document(
        document, staging_column_types(ctx), run, schema=STAGING_SCHEMA)
    return {
        "status": "compared",
        "summary": result.summary(),
        "verified": result.verified,
        "failed": [[t, why] for t, why in result.failed],
        "unverified": [[t, why] for t, why in result.unverified],
        "clinic_generated_at": document.get("generated_at"),
    }


def content_transfer_refusal(result: Dict, accepted) -> Optional[str]:
    """Why the run must stop after the transfer check, or None.

    A DISAGREEMENT and a GAP are separate sign-offs on purpose: the first
    says the bytes that arrived differ from the bytes that were measured
    (a broken dump, a truncated transfer, a restore that silently
    substituted), and the second says nobody measured. An operator who
    accepts one has not accepted the other. Pure, so both are testable
    without a database."""
    accepted = set(accepted or ())
    if result.get("status") == "compared" and result.get("failed"):
        if "content-transfer" not in accepted:
            worst = result["failed"][:5]
            return ("the restored staging schema does not match what the "
                    "clinic measured before the dump: {0}.\n  {1}\n"
                    "This is a broken dump, transfer or restore, not a "
                    "migration decision — take the dump again before "
                    "signing this off with --accept content-transfer."
                    .format(result["summary"],
                            "\n  ".join("{0}: {1}".format(t, why)
                                        for t, why in worst)))
    if result.get("status") != "compared" or result.get("unverified"):
        if "no-content-digests" not in accepted:
            if result.get("status") != "compared":
                detail = result.get("summary", "")
            else:
                detail = "{0}; first: {1}".format(
                    result["summary"],
                    "; ".join("{0} ({1})".format(t, why)
                              for t, why in result["unverified"][:3]))
            return ("the content of the transfer could not be fully "
                    "verified: {0}.\nRe-run the clinic assessment with "
                    "--digests and ship the file, or proceed with "
                    "--accept no-content-digests.".format(detail))
    return None


def report_content_transfer(ctx, result: Dict) -> None:
    """Record the check in the run report and its own JSON artifact."""
    lines = [result.get("summary", "")]
    for label, key in (("disagreed", "failed"),
                       ("not compared", "unverified")):
        for table, why in (result.get(key) or [])[:20]:
            lines.append("  {0}: {1} — {2}".format(label, table, why))
        if len(result.get(key) or []) > 20:
            lines.append("  ... and {0} more {1}".format(
                len(result[key]) - 20, label))
    report_append(ctx["state_dir"], "P2 content transfer",
                  "\n".join(lines))
    with open(os.path.join(ctx["state_dir"], "content-transfer.json"),
              "w", encoding="utf-8") as fh:
        json.dump(result, fh, indent=1, sort_keys=True)
    os.chmod(os.path.join(ctx["state_dir"], "content-transfer.json"), 0o600)


# --------------------------------------------------------------------------
# P3 — backup
# --------------------------------------------------------------------------

def run_p3(ctx) -> None:
    """P3 backup -- take the pre-import snapshot the rollback depends on.

    The only phase whose failure the operator may sign off: with no
    backup configured, `--accept no-pre-backup` (or `--dev-target`)
    records the phase as skipped and says so in the report. Everything
    after this point assumes a restorable snapshot exists."""
    if phase_done(ctx["state"], "backup"):
        log("backup: pre-import snapshot already taken — skipping")
        return
    if not os.path.exists(BACKUP_ENV):
        if "no-pre-backup" in ctx["accepted"] or ctx["dev_target"]:
            warn("backups are not configured — proceeding WITHOUT a "
                 "pre-import snapshot (acknowledged)")
            mark_done(ctx["state_dir"], ctx["state"], "backup",
                      skipped="no-pre-backup")
            report_append(ctx["state_dir"], "P3 backup",
                          "SKIPPED (no backup configuration; acknowledged)")
            return
        die("backups are not configured ({0} missing) — the pre-import "
            "restic snapshot is the rollback point. Configure backups, or "
            "acknowledge with --accept no-pre-backup{1}".format(
                BACKUP_ENV, resume_hint(ctx["state"])))
    log("taking the pre-import backup (systemd unit; this is the rollback "
        "point) ...")
    cp = run(["systemctl", "start", "carlos-emr-backup.service"])
    if cp.returncode != 0:
        if "no-pre-backup" in ctx["accepted"]:
            warn("the pre-import backup FAILED (journalctl -u "
                 "carlos-emr-backup -n 50) — proceeding WITHOUT a rollback "
                 "point under --accept no-pre-backup")
            mark_done(ctx["state_dir"], ctx["state"], "backup",
                      skipped="no-pre-backup", unit_failed=True)
            report_append(ctx["state_dir"], "P3 backup",
                          "FAILED and acknowledged (--accept no-pre-backup)")
            return
        die("the pre-import backup FAILED — journalctl -u carlos-emr-backup "
            "-n 50. Not proceeding without a rollback point.")
    mark_done(ctx["state_dir"], ctx["state"], "backup")
    report_append(ctx["state_dir"], "P3 backup",
                  "pre-import restic snapshot taken (rollback point)")


# --------------------------------------------------------------------------
# P4 — etl (+ row-parity reporting)
# --------------------------------------------------------------------------

def _make_password_hash():
    """(password, {bcrypt} hash, 4-digit pin) — same contract as
    cmd_bootstrap_admin (Login2Action silently discards non-4-digit PINs)."""
    import bcrypt  # python3-bcrypt is a package dependency
    password = genpw()
    pin = genrandom(4, "0123456789")
    hashed = "{bcrypt}" + bcrypt.hashpw(password.encode(),
                                        bcrypt.gensalt(12)).decode()
    return password, hashed, pin


def make_etl_query(base_argv: List[str],
                   statement_timeout: int = 0) -> Callable:
    """Statement executor with the bulk-copy session prelude."""
    prelude = ("SET SESSION sql_log_bin=0, FOREIGN_KEY_CHECKS=0, "
               "UNIQUE_CHECKS=0, sql_mode=''")
    if statement_timeout:
        prelude += ", " + statement_timeout_prelude(
            statement_timeout).replace("SET SESSION ", "")

    def query(sql, db=None):
        argv = list(base_argv) + ["--init-command=" + prelude] \
            + list(CLIENT_COMMON_ARGS)
        if db:
            argv.append(db)
        cp = run(argv, input=sql, capture_output=True, errors="replace")
        if cp.returncode != 0:
            raise o19etl.QueryError(
                "ETL statement failed ({0} ...): {1}".format(
                    redact_statement(sql, 120),
                    CREDENTIAL_SQL_RE.sub(r"\1'<redacted>'",
                                          cp.stderr.strip())), cp.stderr)
        return batch_rows(cp.stdout)

    return query


def _row_parity(ctx):
    """Parity with the exact break-glass delta (the admin identity the ETL
    recorded in its ledger); merge tables are checked in reverse (every
    staging row has a target twin), which needs the target's columns.

    Two halves, and the second is what makes "nothing was orphaned" a
    measurement rather than a claim: `row_parity` covers the tables
    CARLOS has a home for, `preserved_parity` counts every other staging
    table against the copies it was preserved into. Their results are
    concatenated, so one mismatch anywhere fails the phase."""
    archive = ctx.get("archive_schema", ARCHIVE_SCHEMA)
    progress = o19etl.load_progress(ctx["state_dir"])
    ok, bad = o19etl.row_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"],
        admin_user=(progress.get("admin_user") or ctx.get("admin_user")),
        admin_provider_no=progress.get("admin_provider_no"),
        appended=progress.get("roles", {}).get("appended"),
        dst_info=o19etl.introspect_columns(ctx["query"], ctx["target_db"]),
        archive_schema=archive,
        pruned_property_prefixes=o19_preflight.DROPPED_PROP_PREFIXES,
        pruned_property_keys=o19_preflight.DROPPED_PROP_KEYS)
    kept_ok, kept_bad = o19etl.preserved_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"], archive)
    # the same tables again, by VALUE this time: the three parity checks
    # above all COUNT, and a copy that moved the right number of rows
    # with the wrong values passes every one of them
    content_ok, content_bad = o19etl.preserved_content_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"], archive)
    # and the copy class, where a declared transform sits between the two
    # sides: rebuilt from the copy's OWN expressions, so the check cannot
    # model the copy differently from the copy
    src_info = o19etl.introspect_columns(ctx["query"], STAGING_SCHEMA)
    dst_info = o19etl.introspect_columns(ctx["query"], ctx["target_db"])
    # the KEYS of every differing row, for the private details file. A
    # primary key joins straight back to a patient, an appointment or a
    # bill, so the report gets the count and this file gets the keys.
    details: List[str] = []
    if content_bad:
        # the preserved copies are compared by WHOLE-TABLE digest, which
        # has no per-row key to name. An operator who reads "N tables
        # differ" and finds no keys should be told why, not left to
        # wonder whether the file failed to write.
        details.append(
            "preserved copies ({0}): compared by whole-table content "
            "digest, which yields no per-row key — re-run the digest "
            "against o19_archive.<table> to narrow it".format(
                len(content_bad)))
    copy_ok, copy_bad = o19etl.copy_content_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"],
        src_info, dst_info,
        repairs=progress.get("repairs"), archive_schema=archive,
        details=details)
    # and the merge class, whose live rows come from two places: the
    # pre-merge snapshot is what lets the check say which is which, so
    # "the seed won" and "the clinic's row arrived" become separate
    # answers instead of one row count
    merge_ok, merge_bad = o19etl.merge_content_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"], archive,
        src_info, dst_info, repairs=progress.get("repairs"),
        details=details,
        pruned_property_prefixes=o19_preflight.DROPPED_PROP_PREFIXES,
        pruned_property_keys=o19_preflight.DROPPED_PROP_KEYS)
    preserved_bad = content_bad
    content_ok = content_ok + copy_ok + merge_ok
    content_bad = content_bad + copy_bad + merge_bad
    path = os.path.join(ctx["state_dir"], CONTENT_DETAILS)
    # written on EVERY pass, "clean" when there is nothing to itemize:
    # a failed attempt's keys used to survive the clean --resume that
    # followed it, so a PASSED report sat beside a 0600 file headed
    # "rows whose values disagree" with no run identifier to say it was
    # stale. verify-details.txt and documents-details.txt are truncated
    # per pass for the same reason.
    if details:
        body = ("rows whose values disagree, by primary key (at most "
                "{0} per check)\n".format(o19etl.DETAIL_ROWS)
                + "\n".join(details) + "\n")
    elif content_bad:
        # a check can FAIL without naming rows: the count query itself
        # errored, or the check is a whole-table digest with no keys to
        # give. Writing "clean" beside a FAILED verification would say
        # the opposite of what happened, so the file states that instead
        # and the report still points here.
        body = ("no row keys are available for this run's content "
                "findings: the failing check(s) either could not be "
                "queried or compare whole tables by digest rather than "
                "row by row. The finding lines are in the validation "
                "report and report.txt.\n")
    else:
        body = "clean\n"
    write_private(path, body)
    # read back by `import_report`, which must stay derivable from its
    # arguments rather than from the filesystem. Only a pass with nothing
    # to say carries no pointer: "clean" is nothing to send a reviewer to
    # open, but a failure with no keys still needs explaining.
    ctx.pop("content_details", None)
    if details or content_bad:
        ctx["content_details"] = path
    if content_bad and "content-migration" in (ctx.get("accepted") or ()):
        # a recorded sign-off: the operator was shown the mismatches and
        # accepted them, so they stay in the report as findings but no
        # longer fail the phase. Counted by CLASS: "preserved" is this
        # tool's word for the inert archive/drop/reference copies, and a
        # copy- or merge-class mismatch is the opposite -- a LIVE
        # clinical table -- so one "preserved" label for all three sent
        # the operator to the archive for a difference in patient data
        by_class = [(len(preserved_bad), "preserved copy table(s)"),
                    (len(copy_bad), "LIVE copy-class table(s)"),
                    (len(merge_bad), "LIVE merge-class table(s)")]
        warn("{0} content mismatch(es) acknowledged (--accept "
             "content-migration): {1} differ from staging".format(
                 len(content_bad),
                 ", ".join("{0} {1}".format(n, what)
                           for n, what in by_class if n)))
        content_ok = content_ok + [
            ACKNOWLEDGED_PREFIX + line
            for line in content_bad]
        content_bad = []
    col_ok, col_bad = o19etl.archived_column_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"],
        pruned_property_prefixes=o19_preflight.DROPPED_PROP_PREFIXES,
        pruned_property_keys=o19_preflight.DROPPED_PROP_KEYS,
        archive_schema=ctx["archive_schema"])
    return (ok + kept_ok + col_ok + content_ok,
            bad + kept_bad + col_bad + content_bad)


def run_p4(ctx) -> None:
    """P4 etl -- copy, merge and archive the clinic's rows.

    Builds the ETL's own query callable (a longer statement timeout than
    the orchestrator's) and hands off to `o19etl.run_etl`, which keeps
    its own per-table ledger and is resumable window by window."""
    if phase_done(ctx["state"], "etl"):
        log("etl: already complete — skipping")
        return
    ctx["query_etl"] = make_etl_query(ctx["query"].base_argv,
                                      ctx.get("statement_timeout", 0))
    ctx["src_schema"] = STAGING_SCHEMA
    ctx["archive_schema"] = ARCHIVE_SCHEMA
    ctx["dump_sha256"] = ctx["state"].get("phases", {}).get(
        "stage", {}).get("dump_sha256")
    ctx["report"] = lambda body: report_append(ctx["state_dir"], "P4 etl",
                                               body)
    log("etl: copying clinic data into '{0}' (manifest {1}) ..."
        .format(ctx["target_db"], o19map_schema.SCHEMA_MAP_VERSION))
    try:
        o19etl.run_etl(ctx, _make_password_hash)
    except RuntimeError as exc:
        die("ETL aborted: {0}\nFix the cause and re-run with --resume — "
            "chunked tables continue from their checkpoint.".format(exc))

    ok, bad = _row_parity(ctx)
    report_append(ctx["state_dir"], "P4 row parity",
                  "{0} table(s) match; {1} mismatch\n".format(
                      len(ok), len(bad))
                  + ("MISMATCHES:\n  " + "\n  ".join(bad) if bad else ""))
    if bad:
        # the copy is COMPLETE at this point: run_etl returned, so the
        # target holds the clinic's data and every table is marked done
        # in the ledger. A --resume re-enters, skips all of them, and
        # re-computes the identical mismatch — saying "until this is
        # explained" without saying that sent operators into a loop.
        die("row parity failed for {0} table(s) — see {1}/report.txt for "
            "the per-table mismatch. The copy is COMPLETE and the target "
            "holds clinic data; a --resume will only re-check and "
            "re-fail, and no flag overrides parity. {2}, and send "
            "report.txt with the mismatch list.".format(
                len(bad), ctx["state_dir"],
                rollback_hint(ctx["state"]).capitalize()))
    mark_done(ctx["state_dir"], ctx["state"], "etl")
    log("etl complete — row parity clean for {0} table(s)"
        .format(len(ok)))


# --------------------------------------------------------------------------
# P5 — documents
# --------------------------------------------------------------------------

def run_p5(ctx) -> None:
    """P5 documents -- restore the document tree and reconcile it against
    the rows that name its files (see `o19docs.run_docs`)."""
    if phase_done(ctx["state"], "documents"):
        log("documents: already restored and reconciled — skipping")
        return
    ctx.setdefault("archive_schema", ARCHIVE_SCHEMA)
    o19docs.run_docs(ctx)


# --------------------------------------------------------------------------
# P6 — props, P7 — verify
# --------------------------------------------------------------------------

def run_p6(ctx) -> None:
    """P6 props -- derive the CARLOS properties fragment from the clinic's
    `oscar.properties` (see `o19props.run_props`)."""
    if phase_done(ctx["state"], "props"):
        log("props: fragment already produced — skipping")
        return
    from . import o19props
    o19props.run_props(ctx)


def write_private(path: str, text: str) -> None:
    """Write `text` to `path` at 0600, creating or truncating.

    `fchmod` after the open because the `os.open` mode applies to a NEW
    file only: re-running a phase must not leave an existing details
    file at whatever mode it already had. These files carry the names
    the PHI-free `report.txt` deliberately omits."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)  # the mode argument applies to a NEW file only
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write(text)


def append_private(path: str, text: str) -> None:
    """Append to a 0600 file without a read-truncate-write window."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write(text)


def _ledger_lines(progress: Dict, key: str) -> List[str]:
    """One bucket of the ETL ledger's persisted report lines.

    The ETL records its per-table findings as it makes them, so a resumed
    run reports what the crashed one already did. P7 reads them back
    rather than re-deriving anything."""
    kept = progress.get("report_lines") or {}
    value = kept.get(key) or []
    return [ln for ln in value if isinstance(ln, str)]


def load_content_transfer(state_dir: str) -> Optional[Dict]:
    """The P2 content-transfer verdict this run recorded, or None.

    Read back from `content-transfer.json` rather than re-derived: by P7
    the staging schema has been read by the ETL for hours, and the
    question the report answers is what P2 found BEFORE the first write.
    Unreadable reads as absent -- the report then says the check has no
    record, which is the truth of the matter."""
    path = os.path.join(state_dir, "content-transfer.json")
    try:
        with open(path, encoding="utf-8") as fh:
            result = json.load(fh)
    except (OSError, ValueError):
        return None
    return result if isinstance(result, dict) else None


def phase_report_rows(state: Dict, content: Optional[Dict]
                      ) -> Tuple[List[str], List[str], List[Dict]]:
    """What the phases other than the ETL contributed, as (arrived,
    unchecked, findings) rows for `import_report`.

    Three phases make claims the row-count verification cannot: P2 says
    whether the bytes that reached staging are the bytes the clinic
    measured; P5 says whether the document tree is in place and
    reconciled; P6 says what the properties fragment awaiting review
    holds. A report that itemised every table and said nothing about any
    of these was the gap the review found in requirement A. Each row goes
    where its answer belongs: a verified transfer or a restored tree is
    something that ARRIVED; a check nobody could run, or a phase this
    run's state does not record as done, is NOT CHECKED; a disagreement
    the operator signed off on is a finding a reviewer must see."""
    arrived, unchecked, findings = [], [], []
    phases = state.get("phases", {}) if state else {}

    if content is None:
        unchecked.append("content transfer (P2): no record of the check "
                         "in this run's state directory")
    else:
        summary = content.get("summary") or "no summary recorded"
        if content.get("status") == "compared" and content.get("failed"):
            # P2 stops on a disagreement unless --accept content-transfer
            # was given, so one that reached P7 was signed off; the
            # reviewer sees it under the accepted sign-off, not as a pass
            lines = [summary] + [
                "{0}: {1}".format(t, why)
                for t, why in (content.get("failed") or [])[:20]]
            findings.append(o19report.finding(
                "advisory", "content transfer disagreement accepted with "
                            "--accept content-transfer", lines))
        # independently of the sign-off above: a run can BOTH have a
        # disagreement the operator accepted AND tables nobody could
        # compare, and chaining these as one elif hid the second behind
        # the first -- the reviewer then read "some tables disagreed" and
        # never learned that others were never measured at all
        if content.get("status") != "compared" \
                or content.get("unverified"):
            unchecked.append("content transfer (P2): {0}".format(summary))
        elif not content.get("failed"):
            arrived.append("content transfer (P2): {0}".format(summary))

    docs = phases.get("documents") or {}
    if docs.get("status") == "done" and docs.get("skipped"):
        unchecked.append(
            "documents (P5): SKIPPED ({0} acknowledged) — document rows "
            "reference files that are not there".format(docs["skipped"]))
    elif docs.get("status") == "done":
        arrived.append(
            "documents (P5): tree restored from tar {0} and reconciled "
            "clean".format(str(docs.get("tar_sha256") or "?")[:12]))
    else:
        unchecked.append("documents (P5): not recorded as completed in "
                         "this run's state")

    props = phases.get("props") or {}
    if props.get("status") == "done":
        findings.append(o19report.finding(
            "advisory", "properties fragment awaits operator review",
            ["{0}: {1} key(s) carried, {2} unknown key(s) needing "
             "classification — nothing is applied until the fragment is "
             "reviewed and appended by hand".format(
                 props.get("fragment") or "o19-derived-carlos.properties",
                 props.get("carried", "?"), props.get("unknown", "?"))]))
    else:
        unchecked.append("properties (P6): not recorded as completed in "
                         "this run's state")
    return arrived, unchecked, findings


def split_parity_lines(parity_ok: Sequence[str]
                       ) -> Tuple[List[str], List[str], List[str]]:
    """The three answers `_row_parity` returns as one `ok` list, apart:
    (passed, not checked, acknowledged), each stripped of its prefix.

    One split for both readers. `report.txt`'s count line once did
    `len(ok)` on the unsplit list, so every table nobody could compare
    and every mismatch the operator signed off on was reported as a
    check that passed -- a number that contradicted the sections the
    validation report built from the same list."""
    passed: List[str] = []
    unchecked: List[str] = []
    acknowledged: List[str] = []
    for line in parity_ok:
        if line.startswith(o19etl.UNCHECKED_PREFIX):
            unchecked.append(line[len(o19etl.UNCHECKED_PREFIX):])
        elif line.startswith(ACKNOWLEDGED_PREFIX):
            acknowledged.append(line[len(ACKNOWLEDGED_PREFIX):])
        else:
            passed.append(line)
    return passed, unchecked, acknowledged


def package_version() -> str:
    """The carlos-emr package version executing this run, for the report
    header.

    Provenance the manifest cannot give. `o19report.HEADER_ORDER`
    reserved the row from the start but nothing ever filled it, so two
    package builds shipping the SAME manifest produced reports a
    reviewer could not tell apart -- and diffing two imports is the
    stated purpose of the JSON twin. Read the way config.py reads it for
    the build stamp; "unknown" rather than None on an unpackaged
    development host, so the row prints the gap instead of silently
    vanishing (an absent header row reads as a report format without the
    field, not as a version nobody recorded)."""
    from .util import out
    return out(["dpkg-query", "-f", "${Version}", "-W",
                "carlos-emr"]) or "unknown"


def truncated_problems_note(total: int, path: Optional[str]) -> str:
    """The line that keeps a capped failure list honest.

    Both truncation sites ended the list at 40 with no marker, under a
    title carrying the TRUE count -- so the report read as "there were
    exactly these" rather than "these and more", against a tool that
    marks every other capped list (`cleanup_data_refusal`, the P0
    pristine sweep)."""
    return "... and {0} more (full list in {1})".format(
        total - REPORT_PROBLEM_LINES, path or VERIFY_PROBLEMS)


def import_report(ctx, progress: Dict, parity_ok: Sequence[str],
                  problems: Sequence[str], verify_lines: Sequence[str],
                  advisories: Sequence[str], finished: str,
                  content: Optional[Dict] = None) -> Dict:
    """Assemble the operator's validation report.

    Pure: every fact comes from the caller, the run state, the ETL
    ledger or `content` (the P2 verdict `write_import_report` reads back
    from `content-transfer.json`), so the whole document can be built
    and asserted in a test.

    The three questions it answers, in this order, are the ones a
    reviewer actually has: what arrived, what did not arrive and where it
    went instead, and what needs a human before go-live."""
    state = ctx.get("state") or {}
    phases = state.get("phases", {})
    header = {
        "target_db": ctx.get("target_db"),
        "province": ctx.get("province"),
        "manifest": o19map_schema.SCHEMA_MAP_VERSION,
        "o19_source_commit": getattr(o19map_schema, "O19_SOURCE_COMMIT",
                                     None),
        "dump_sha256": phases.get("stage", {}).get("dump_sha256"),
        "manifest_props": getattr(o19map_props, "PROPS_MAP_VERSION",
                                  None),
        # recorded by _make_ctx so this stays derivable from its
        # arguments; never None, or the row would not render at all
        "tool_version": ctx.get("tool_version") or "unknown",
        "started": phases.get("stage", {}).get("at"),
        "finished": finished,
    }
    # `parity_ok` is one list of three different answers, and a reviewer
    # who reads them as one has been misled: a table nobody could check
    # and a mismatch somebody signed off on are not "what arrived".
    # the phases before the ETL and after it speak first: whether the
    # bytes arrived at all (P2) comes before which rows did
    arrived, unchecked, findings = phase_report_rows(state, content)
    passed, not_checked, acknowledged = split_parity_lines(parity_ok)
    arrived.extend(passed)
    unchecked.extend(not_checked)
    # "merge" is the one bucket that names clinic rows deliberately NOT
    # made live -- the rows CARLOS's seed won on a shared key -- and
    # where they went. It was recorded by the ETL from the start and
    # never rendered; a report that itemises every other population but
    # that one is not the report requirement A asks for.
    elsewhere = (_ledger_lines(progress, "absent")
                 + _ledger_lines(progress, "reference")
                 + _ledger_lines(progress, "merge")
                 + _ledger_lines(progress, "drop")
                 + _ledger_lines(progress, "unknown")
                 + _ledger_lines(progress, "archived_cols"))
    if problems:
        body = list(problems)[:REPORT_PROBLEM_LINES]
        if len(problems) > REPORT_PROBLEM_LINES:
            body.append(truncated_problems_note(
                len(problems), ctx.get("problem_details")))
        if ctx.get("content_details"):
            body.append(CONTENT_DETAILS_NOTE.format(
                ctx["content_details"]))
        findings.append(o19report.finding(
            "failure", "{0} verification problem(s)".format(len(problems)),
            body))
    if acknowledged:
        # the operator was shown these and chose to proceed; a reviewer
        # has to see them before go-live, so they are a finding rather
        # than a line among the passes
        body = list(acknowledged)
        if ctx.get("content_details"):
            # an ACCEPTED mismatch is the one a reviewer is most likely
            # to want the rows for, and it is the case where nothing
            # else in the report is red enough to carry the pointer
            body.append(CONTENT_DETAILS_NOTE.format(
                ctx["content_details"]))
        findings.append(o19report.finding(
            "advisory", "content mismatch(es) accepted with --accept "
                        "content-migration", body))
    for title, key in (("surrogate ids reassigned on merge", "idmap"),
                       ("dangling foreign keys in the source", "fk"),
                       ("dropped-column capture notes", "shadow")):
        found = _ledger_lines(progress, key)
        if found:
            findings.append(o19report.finding("advisory", title, found))
    if advisories:
        findings.append(o19report.finding(
            "advisory", "roles and privileges", list(advisories)))
    if verify_lines:
        findings.append(o19report.finding(
            "info", "verification checks run", list(verify_lines)))
    # A bare "PASSED" over a signed-off content mismatch is the one line
    # a downstream reader acts on, and it would not say that rows were
    # accepted as differing. `_row_parity` moves an acknowledged
    # mismatch out of `problems` deliberately -- it is not a failure --
    # but the verdict must still carry it, the way the report's other
    # two states carry their counts. The advisory below keeps the
    # detail; this is the line that sends a reviewer to it.
    if problems:
        verdict = "FAILED ({0} problem(s))".format(len(problems))
    elif acknowledged:
        verdict = ("PASSED WITH ACKNOWLEDGED MISMATCH(ES) ({0})"
                   .format(len(acknowledged)))
    else:
        verdict = "PASSED"
    return o19report.build(
        header,
        verdict,
        [o19report.section(
            "WHAT ARRIVED", arrived,
            empty="nothing was compared — this is not a verified import"),
         o19report.section(
             "WHAT DID NOT ARRIVE, AND WHERE IT WENT INSTEAD", elsewhere,
             empty="every staging table had a home in CARLOS"),
         o19report.section(
             "WHAT WAS NOT CHECKED, AND WHY", unchecked,
             empty="every table in scope was checked")],
        findings,
        FAILED_NEXT_STEPS if problems else NEXT_STEPS)


def billing_totals_table(ctx) -> str:
    """The claim-header table P7 aggregates and spot-checks, for this
    host's province.

    Fails CLOSED. A province with no entry is a curation gap, and the
    quiet alternative -- falling back to Ontario's table -- would find
    it absent on both sides of a BC import, report "absent from both
    schemas", and pass: the money check silently not run on the one
    surface an operator's accountant will ask about. The province gate
    has already refused anything but a carried profile by the time this
    runs, so an empty lookup means the overlay, not the host."""
    province = ctx.get("province")
    table = getattr(o19map_schema, "BILLING_TOTALS_TABLE", {}).get(province)
    if not table:
        die("the manifest names no billing claim header for province "
            "{0!r}, so verification cannot check billing totals; add one "
            "to BILLING_TOTALS_TABLE in overrides_schema.py and "
            "regenerate".format(province))
    if not o19etl.IDENTIFIER_RE.match(table):
        # it is interpolated into SQL as a bare identifier
        die("the manifest's billing claim header for province {0!r} is "
            "not a plain identifier".format(province))
    return table


def write_import_report(ctx, parity_ok: Sequence[str],
                        problems: Sequence[str],
                        verify_lines: Sequence[str],
                        advisories: Sequence[str]) -> Dict:
    """Render the validation report to `import-report.txt` and its JSON
    twin, both 0600 like every other run artifact."""
    report = import_report(
        ctx, o19etl.load_progress(ctx["state_dir"]), parity_ok, problems,
        verify_lines, advisories, time.strftime("%Y-%m-%dT%H:%M:%S"),
        content=load_content_transfer(ctx["state_dir"]))
    write_private(os.path.join(ctx["state_dir"], "import-report.txt"),
                  o19report.render_text(report))
    write_private(os.path.join(ctx["state_dir"], "import-report.json"),
                  o19report.render_json(report))
    return report


def run_p7(ctx) -> None:
    """P7 verify -- compare the target against staging and pass or fail the
    import.

    Row parity per manifest table, referential spot checks across a
    patient's chart, billing totals and the roles/privilege gates.
    Records the phase only when every check passes: a failed or
    interrupted verification leaves a `verify` phase that is NOT `done`,
    which is what the packaging's upgrade gate reads."""
    if phase_done(ctx["state"], "verify"):
        log("verify: already passed — skipping")
        return
    query = ctx["query"]
    src, dst = STAGING_SCHEMA, ctx["target_db"]
    problems: List[str] = []
    lines: List[str] = []

    ok, bad = _row_parity(ctx)
    # three counts, never one: NOT CHECKED lines and acknowledged
    # mismatches travel in `ok` so that the phase does not fail on
    # them, and `len(ok)` told the operator "345 check(s) pass" when 31
    # of those were tables nobody looked at and 3 were mismatches
    # somebody signed off on. This line is all report.txt carries.
    passed, unchecked, acknowledged = split_parity_lines(ok)
    lines.append("row parity and preserved content: {0} check(s) passed, "
                 "{1} not checked, {2} mismatch(es) acknowledged"
                 .format(len(passed), len(unchecked), len(acknowledged)))
    problems.extend(bad)

    # referential spot checks: random patients joined across the chart.
    # Per-patient lines carry demographic_no (a PHI-correlating id), so
    # they go to a separate root-only file, not the shareable report.
    total = int(query("SELECT COUNT(*) FROM `{0}`.demographic"
                      .format(src))[0][0])
    # Deterministic, and derived from the dump: an unseeded ORDER BY
    # RAND() draws a NEW sample on every attempt, so a failed
    # verification could be re-run until a sample happened to miss the
    # affected patients — and verify-details.txt, which is truncated per
    # pass, would then say "clean" with nothing recording the mismatch.
    seed = int(ctx["state"].get("phases", {}).get("stage", {})
               .get("dump_sha256", "0")[:12] or "0", 16)
    sample = [r[0] for r in query(
        "SELECT demographic_no FROM `{0}`.demographic ORDER BY "
        "RAND({1}) LIMIT {2}".format(src, seed, SPOT_CHECK_PATIENTS))]
    # isascii() too: '٣'.isdigit() is True, and the value is
    # interpolated unquoted into the join predicates below
    sample = [demo for demo in sample
              if demo.isascii() and demo.isdigit()]
    # any patient a previous attempt found a mismatch for is re-checked
    # alongside the sample, so a fix is proved rather than out-drawn
    prev_verify = ctx["state"].get("phases", {}).get("verify", {})
    for demo in prev_verify.get("mismatched", []):
        if demo not in sample and demo.isascii() and demo.isdigit():
            sample.append(demo)
    if total and not sample:
        problems.append("spot check drew no patients from {0} demographic "
                        "row(s)".format(total))
    # the claim header is per province: Ontario's OHIP header, BC's
    # invoice table. Named from the manifest so the BC spot check
    # covers billing instead of skipping an absent Ontario table.
    billing_table = billing_totals_table(ctx)
    joins = (("appointment", "demographic_no"),
             ("casemgmt_note", "demographic_no"),
             ("drugs", "demographic_no"),
             ("preventions", "demographic_no"),
             ("measurements", "demographicNo"),
             ("eform_data", "demographic_no"),
             (billing_table, "demographic_no"))
    details: List[str] = []
    checked = 0
    for demo in sample:
        for table, col in joins:
            try:
                s = query("SELECT COUNT(*) FROM `{0}`.`{1}` WHERE `{2}` = "
                          "{3}".format(src, table, col, demo))[0][0]
                d = query("SELECT COUNT(*) FROM `{0}`.`{1}` WHERE `{2}` = "
                          "{3}".format(dst, table, col, demo))[0][0]
            except RuntimeError as exc:
                if o19etl.absent_object_error(exc):
                    continue  # table absent at this patch level
                # the statement carries a demographic_no: name the table
                # and the server's reason, never the statement itself
                die("verification query failed on the {0} spot check: "
                    "{1}".format(table,
                                 getattr(exc, "stderr", "").strip()[:300]
                                 or "see the server log"))
            checked += 1
            if s != d:
                details.append(
                    "patient {0}: {1} count differs (staging {2}, "
                    "target {3})".format(demo, table, s, d))
    write_private(os.path.join(ctx["state_dir"], "verify-details.txt"),
                  "spot checks on {0} of {1} patient(s), {2} join(s)\n"
                  .format(len(sample), total, checked)
                  + ("\n".join(details) + "\n" if details else "clean\n"))
    if details:
        problems.append("{0} patient spot-check mismatch(es) — see {1}/"
                        "verify-details.txt".format(len(details),
                                                    ctx["state_dir"]))
    lines.append("referential spot checks on {0} of {1} random patient(s) "
                 "({2} join checks)".format(len(sample), total, checked))

    # billing totals per fiscal year, to the cent. Both provinces'
    # claim headers carry `billing_date` (DATE) and `total`, so only the
    # table name varies.
    agg = ("SELECT IFNULL(YEAR(billing_date),0), COUNT(*), "
           "IFNULL(SUM(CAST(total AS DECIMAL(14,2))),0) FROM "
           "`{0}`.`" + billing_table + "` GROUP BY 1 ORDER BY 1")

    def billing_totals(schema):
        """The aggregate, or None when this schema has no such table —
        judged per SIDE. Clearing BOTH on one absence would let a dump
        without the table pass verification against a target that has
        rows in it."""
        try:
            return {r[0]: (r[1], r[2]) for r in query(agg.format(schema))}
        except RuntimeError as exc:
            if not o19etl.absent_object_error(exc):
                raise
            return None

    s_rows = billing_totals(src)
    d_rows = billing_totals(dst)
    if s_rows is None and d_rows is None:
        # absent at this patch level on both sides: tolerated the same
        # way the parity and spot-check loops tolerate it, rather than
        # ending a completed import on a raw "table doesn't exist"
        s_rows = d_rows = {}
        lines.append("billing totals: {0} absent from both schemas"
                     .format(billing_table))
    elif s_rows is None or d_rows is None:
        # one-sided: recorded as a failure AND reported as uncompared.
        # Zeroing both sides here would make the equality below hold and
        # print "billing totals match for 0 fiscal year(s)" under a
        # problem saying the totals could not be compared at all.
        present = dst if s_rows is None else src
        problems.append(
            "{0} exists in {1} but not in {2} — verification cannot "
            "compare billing totals".format(
                billing_table, present, src if s_rows is None else dst))
        lines.append("billing totals: NOT COMPARED ({0} is in {1} "
                     "only)".format(billing_table, present))
        s_rows = d_rows = None
    if s_rows is None:
        pass
    elif s_rows != d_rows:
        for year in sorted(set(s_rows) | set(d_rows)):
            if s_rows.get(year) != d_rows.get(year):
                problems.append(
                    "billing year {0}: staging {1} vs target {2}".format(
                        year, s_rows.get(year), d_rows.get(year)))
    else:
        lines.append("billing totals match for {0} fiscal year(s)"
                     .format(len(s_rows)))

    # roles, privileges and the rows CARLOS code requires (M8)
    from . import o19roles
    progress = o19etl.load_progress(ctx["state_dir"])
    r_ok, r_bad, r_adv, r_private = o19roles.verify_role_checks(
        query, dst, progress.get("admin_provider_no"),
        o19map_schema.SEED_ROW_COUNTS.get("secObjPrivilege", 0))
    lines.append("roles/privileges: {0} check(s) passed".format(len(r_ok)))
    problems.extend(r_bad)
    # the roles post-step already wrote this file (activated assignments);
    # the verify findings are appended as one block that a re-run replaces
    # — also when the re-run has nothing private to say
    path = os.path.join(ctx["state_dir"], "roles-details.txt")
    existing = ""
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as fh:
            existing = fh.read().split("P7 verify:\n")[0]
    if os.path.isfile(path) or r_private:
        write_private(path, existing + "P7 verify:\n"
                      + "\n".join(r_private) + "\n")
    if r_adv:
        lines.append("ADVISORIES (review before go-live):\n  "
                     + "\n  ".join(r_adv))

    if total and not checked:
        # every join table absent at this patch level: "all checks
        # passed" after zero referential checks is not a verification
        problems.append("no referential spot check could run ({0} "
                        "patient(s) in staging, every join table absent) "
                        "— verification cannot vouch for this import"
                        .format(total))
    # record the patients a mismatch was seen for so the next attempt
    # re-checks them instead of drawing past them
    mismatched = sorted({ln.split()[1].rstrip(":") for ln in details
                         if ln.startswith("patient ")})
    ctx["state"].setdefault("phases", {}).setdefault("verify", {})
    ctx["state"]["phases"]["verify"]["mismatched"] = mismatched
    save_state(ctx["state_dir"], ctx["state"])

    # the full list goes to a 0600 file so the ones the report and the
    # log cannot fit exist somewhere: re-running P7 prints only the
    # count, so they used to be recoverable from no artifact at all.
    # Written on EVERY pass, "clean" when there is nothing to itemize,
    # for the same reason content-details.txt is: a failed attempt's
    # list must not survive beside the clean resume that followed it.
    problems_path = os.path.join(ctx["state_dir"], VERIFY_PROBLEMS)
    write_private(problems_path,
                  "\n".join(problems) + "\n" if problems else "clean\n")
    ctx.pop("problem_details", None)
    shown = list(problems[:REPORT_PROBLEM_LINES])
    if len(problems) > REPORT_PROBLEM_LINES:
        ctx["problem_details"] = problems_path
        shown.append(truncated_problems_note(len(problems), problems_path))
    report_append(ctx["state_dir"], "P7 verify",
                  "\n".join(lines)
                  + ("\nFAILURES:\n  " + "\n  ".join(shown)
                     if problems else "\nall checks passed"))
    # the operator's validation report: one document, built from
    # structured data, carrying the per-table counts the running log
    # throws away on a clean import
    write_import_report(ctx, ok, problems, lines, r_adv)
    if problems:
        die("verification FAILED ({0} problem(s)) — see {1}/report.txt. "
            "State is left in place for diagnosis; to roll back, {2}."
            .format(len(problems), ctx["state_dir"],
                    rollback_hint(ctx["state"])))
    mark_done(ctx["state_dir"], ctx["state"], "verify")
    log("verification passed — complete the technical review before "
        "go-live")


# --------------------------------------------------------------------------
# cleanup
# --------------------------------------------------------------------------

def cleanup_refusal(state: Dict, state_dir: str,
                    dev_target: bool) -> Optional[str]:
    """Why --cleanup may not run now (None when it may). Allowed after a
    passed verification, or while nothing has been written to the target
    (a dry run or an aborted assessment); a mid-import workspace is never
    cleaned up — that would destroy the only resume ledger — except on a
    dev database. --dry-run grants nothing here."""
    if phase_done(state, "verify"):
        return None
    if not etl_started(state_dir) and not phase_done(state, "documents"):
        return None
    if dev_target:
        return None
    return ("--cleanup is allowed only after verify has passed, or before "
            "the data copy started; this workspace is mid-import. Continue "
            "it with --resume, or restore the pre-import snapshot.")


def cleanup_data_refusal(started: bool,
                         mismatches: Sequence[str]) -> Optional[str]:
    """Why the staging schema may not be dropped now, or None.

    `cleanup_refusal` asks whether the RUN is in a state to be retired;
    this asks whether the DATA is. They are different questions, and only
    the second one stands between a clinic and a lost table: --cleanup
    used to be permitted on `phase_done(state, "verify")` alone, while
    the verification behind it had never once counted an archived row.

    A run whose ETL never started is allowed through: nothing was copied,
    so staging holds only a restore of the operator's own dump and the
    parity below would flag every table for the wrong reason. Once the
    copy has started, every staging table holding rows must have a
    verified home -- in the target, in `o19_archive`, or in an
    `import_archived_` twin -- before staging goes. `--dev-target` waives
    the ledger question, never this one: a scratch database is a reason
    to skip bookkeeping, not a licence to drop rows that exist nowhere
    else."""
    if not started or not mismatches:
        return None
    shown = list(mismatches)[:10]
    return ("--cleanup would drop the staging schema, but {0} table(s) "
            "have no verified home outside it:\n  {1}{2}\nStaging is the "
            "clinic's only remaining copy of those rows. Resolve the "
            "mismatch (or --resume the import) before cleaning up."
            .format(len(mismatches), "\n  ".join(shown),
                    "\n  ..." if len(mismatches) > len(shown) else ""))


def cleanup_manifest_refusal(state: Dict,
                             current_map: str) -> Optional[str]:
    """Why the staging drop may not be gated on a parity computed now,
    or None.

    Every component of `_row_parity` iterates the INSTALLED manifest's
    TABLES; nothing re-derives them under the manifest the run was
    actually made with, even though the ledger records it. The packaging
    makes that mismatch ordinary rather than exotic: the postinst
    upgrade gate clears as soon as `verify` is done, and NEXT_STEPS puts
    the restart, the backup and the technical review BEFORE --cleanup,
    so an unattended upgrade in that window is expected. A table
    reclassified between the two package versions (copy -> archive,
    reference -> merge, a merge table that gained a column) then reads
    as staging rows with "no verified home", and `cleanup_data_refusal`
    refuses the drop for a difference in the MANIFEST rather than in the
    data -- while --resume is refused in turn by
    `manifest_change_refusal`, which used to answer "run --cleanup".

    Refusing here rather than trusting the recorded verdict is the
    fail-safe half of that choice: the drop destroys the clinic's only
    remaining copy of those rows, and the only measurement that ever
    covered them was made under the recorded manifest. The remedy is the
    one `manifest_change_refusal` already names for a half-finished
    import -- reinstall the package version that shipped the recorded
    manifest, retire the run, then upgrade -- which is why its
    finished-import branch no longer promises that --cleanup alone
    works."""
    recorded = state.get("inputs", {}).get("schema_map_version")
    if not recorded or recorded == current_map:
        return None
    return ("this import ran with manifest {0}; the installed carlos-ctl "
            "carries {1}. --cleanup drops the staging schema only after "
            "re-counting every staging table against the home it was "
            "copied into, and that classification comes from the "
            "INSTALLED manifest — under {1} it would describe a "
            "different import. Staging is the clinic's only remaining "
            "copy of those rows, so this is not waived: reinstall the "
            "carlos-emr package version that shipped manifest {0}, run "
            "--cleanup, then upgrade again.".format(recorded, current_map))


def run_cleanup(ctx) -> None:
    """Retire a finished run: drop the staging schema and its throwaway
    account, remove the extracted bundle, and suffix this run's ledgers,
    reports and private files with `.completed-<timestamp>`.

    The `o19_archive` schema and its CSV export are KEPT for the clinic.
    Refused on a mid-import workspace, whose only resume ledger this
    would destroy. Marked `cleanup: in-progress` before the first
    destructive step, so an interrupted cleanup reads as "run --cleanup
    again" rather than as a resumable import."""
    state = ctx["state"]
    refusal = cleanup_refusal(state, ctx["state_dir"], ctx["dev_target"])
    if refusal:
        die(refusal)
    # the data question, asked separately from the run question above and
    # never waived: staging is dropped a few lines below, and a table
    # holding rows with no verified home would go with it
    staging_left = ctx["query"](
        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE "
        "SCHEMA_NAME = '{0}'".format(STAGING_SCHEMA))
    if staging_left:
        started = etl_started(ctx["state_dir"])
        if started:
            # the parity below classifies under the INSTALLED manifest;
            # a package upgrade since the run makes that a measurement
            # of a different import, not of this one
            refusal = cleanup_manifest_refusal(
                state, o19map_schema.SCHEMA_MAP_VERSION)
            if refusal:
                die(refusal)
        _ok, bad = _row_parity(ctx) if started else ([], [])
        refusal = cleanup_data_refusal(started, bad)
        if refusal:
            die(refusal)
    # marked first: a cleanup interrupted half-way is "run --cleanup
    # again", never a workspace a later --resume misreads
    if os.path.exists(state_path(ctx["state_dir"])):
        state["cleanup"] = "in-progress"
        save_state(ctx["state_dir"], state)
    log("dropping staging schema {0} (archive schema {1} is kept) ..."
        .format(STAGING_SCHEMA, ARCHIVE_SCHEMA))
    ctx["query"]("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
    for host in STAGING_ACCOUNT_HOSTS:
        ctx["query"]("DROP USER IF EXISTS '{0}'@'{1}'".format(
            STAGING_USER, host))
    for name in ("bundle", "bundle-assess"):
        bundle_dir = os.path.join(ctx["state_dir"], name)
        if os.path.isdir(bundle_dir):
            shutil.rmtree(bundle_dir)
    for name in (".stage-client.cnf", "state.json.tmp",
                 "etl-progress.json.tmp"):
        path = os.path.join(ctx["state_dir"], name)
        if os.path.exists(path):
            os.unlink(path)
    report_append(ctx["state_dir"], "cleanup",
                  "staging schema, staging account and extracted bundle "
                  "removed; o19_archive kept; the run's ledgers, report "
                  "and private files retired with a .completed- suffix")
    archived = archive_state(ctx["state_dir"])
    if archived:
        log("run state archived as {0} — a later import starts from "
            "scratch (and meets the pristine gate)".format(archived))
    log("cleanup complete")


def archive_state(state_dir: str) -> Optional[str]:
    """Retire state.json so the finished run can never be --resume'd or
    mistaken for a fresh one. The run's report and private files are
    retired with the same suffix, so a later import in the same state
    directory starts its own (the next roles step would otherwise append
    to the old lists and P7 would rewrite the wrong block)."""
    path = state_path(state_dir)
    if not os.path.exists(path):
        return None
    suffix = ".completed-" + time.strftime("%Y%m%dT%H%M%S")
    for name in RUN_FILES:
        run_file = os.path.join(state_dir, name)
        if os.path.exists(run_file):
            os.replace(run_file, run_file + suffix)
    target = path + suffix
    os.replace(path, target)
    return os.path.basename(target)


#: What the operator still has to do, in order. Shared between the
#: console summary and the validation report on purpose: they lived only
#: on the console before, so the file the operator keeps did not carry
#: them.
NEXT_STEPS = (
    "review and apply the properties fragment "
    "(o19-derived-carlos.properties), then `carlos-ctl restart` — it "
    "carries imported credentials to rotate or verify first",
    "run `carlos-ctl backup full` (the post-import snapshot)",
    "TECHNICAL REVIEW before clinical use: import-report.txt, "
    "report.txt, manual spot checks and a UI smoke of the migrated "
    "charts",
    "roles: confirm each clinic-custom role's privileges in "
    "Administration > Security (the report names the template used), and "
    "deal with expired or role-less accounts — see privilege-diff.txt "
    "and roles-details.txt",
    "review the preserved tables and columns the report names under "
    "\"what did not arrive\": they hold what CARLOS has no home for, and "
    "nothing in the application reads them",
    "then `carlos-ctl import-o19 --cleanup`",
)

#: The report is written for a FAILED verification too — that is the run
#: whose record matters most — but the go-live list above is wrong for
#: it, and wrong in the direction that hurts: applying the properties
#: fragment and restarting brings a half-verified clinic online, and
#: `--cleanup` is refused by `cleanup_refusal` while verification has
#: not passed, so the last step reads as an instruction the tool then
#: rejects. What a failed run needs is the opposite order: read, fix,
#: re-verify, and roll back if it cannot be fixed.
FAILED_NEXT_STEPS = (
    # deliberately no pointer to content-details.txt here: it exists
    # only when a value check wrote keys, and the finding that owns it
    # adds the pointer itself. A step naming a file that is not there
    # sends a reviewer looking for evidence nobody produced.
    "read the problems above, and the full list in verify-problems.txt "
    "(root-only) when the report says there are more",
    "do NOT apply the properties fragment or restart into this target: "
    "the clinic is not verified, and a restart brings it online",
    # --resume re-runs the CHECKS, not the copy: P4 records `etl` done
    # and P4 skips a done phase, so a change made in the staging schema
    # is never re-copied — it would only move the comparison, and a
    # re-verify that passes on it proves nothing about the target
    "fix what the problems name IN THE TARGET schema, then `carlos-ctl "
    "import-o19 --resume` to re-verify — --resume re-runs the checks "
    "only: the etl phase is recorded done, so a change made in the "
    "staging schema is never copied across",
    "if the fix belongs in the clinic's source data, or cannot be made "
    "at all, roll back to the pre-import snapshot (`carlos-ctl backup "
    "restic restore latest --tag db --target ...`, see the guide's "
    "rollback section) and start the run over — the snapshot is the "
    "run's rollback point",
    "`--cleanup` is refused while verification has not passed, and is "
    "the LAST step after a clean re-verification, never a way to clear "
    "a failure",
)


#: per-run outputs retired alongside state.json (admin-credentials.txt is
#: deliberately not among them: the operator is told where it is)
RUN_FILES = ("report.txt", "import-report.txt", "import-report.json",
             "roles-details.txt", "privilege-diff.txt",
             "verify-details.txt", PRISTINE_DETAILS,
             "documents-details.txt",
             CONTENT_DETAILS, VERIFY_PROBLEMS, "preflight.txt",
             "preflight.json", "etl-progress.json",
             "content-transfer.json", "o19-digests.json",
             "o19-derived-carlos.properties",
             "o19-derived-carlos.properties.dry-run",
             # the CSV rendering of this run's archive schema: clinic
             # records. Retired with the run, or a second clinic's import
             # into the same workspace would leave the first clinic's
             # tables sitting beside its own.
             "o19-archive-export")


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def _parser(prog: str, import_mode: bool) -> argparse.ArgumentParser:
    """The argument parser for either verb.

    `import_mode` selects the flags that only a real import has (the
    break-glass admin, `--resume`, `--cleanup`, the acknowledgement
    classes); the assessment shares the input and bundle options."""
    ap = argparse.ArgumentParser(
        prog=prog,
        description=(
            "OSCAR 19 clinic import (experimental). Migration output "
            "should receive a technical review before clinical use."
            if import_mode else
            "OSCAR 19 migration feasibility check (experimental): the "
            "capacity gates, a staged restore and the go/no-go report. "
            "Writes nothing to the EMR schema and records neither a "
            "verdict nor a sign-off; the exit status is the verdict "
            "(0 go, 1 acknowledgements required, 2 no-go, 3 tool "
            "failure)."))
    ap.add_argument("--bundle", metavar="FILE",
                    help="single handoff archive (.tar/.tar.gz/.tar.enc/"
                         ".tar.gz.enc) holding dump + documents tar + "
                         "properties at its root")
    ap.add_argument("--bundle-pass", metavar="SPEC",
                    help="openssl -pass spec for an .enc bundle "
                         "(file:PATH, env:VAR, fd:N, stdin; 'pass:' works "
                         "but lands in argv/history)")
    ap.add_argument("--bundle-sha256", metavar="HEX",
                    help="the bundle's sha256 as conveyed by the clinic "
                         "through a channel separate from the file "
                         "(openssl enc has no integrity check of its own); "
                         "required for --bundle unless --accept "
                         "unverified-bundle is given")
    ap.add_argument("--bundle-cipher", metavar="NAME",
                    default=o19bundle.DEFAULT_CIPHER,
                    help="openssl cipher the .enc bundle was created with "
                         "(default: " + o19bundle.DEFAULT_CIPHER + ")")
    ap.add_argument("--bundle-openssl-opt", action="append", default=[],
                    metavar="OPT",
                    help="passthrough openssl option for legacy bundles "
                         "(repeatable), e.g. -md then md5")
    ap.add_argument("--dump", metavar="FILE",
                    help="mysqldump of the O19 database (.sql or .sql.gz)")
    ap.add_argument("--properties", metavar="FILE",
                    help="the clinic's deployed oscar.properties")
    ap.add_argument("--o19-digests", metavar="FILE",
                    help="the clinic-side content digests (o19_preflight "
                         "--digests). Normally travels inside --bundle; "
                         "pass it here when it was shipped separately. "
                         "Without it P2 can compare row COUNTS only, "
                         "never the values themselves")
    ap.add_argument("--province", choices=["on", "bc"],
                    help="restate the host's configured province; a value "
                         "that differs from it is refused (default: the "
                         "host's own). The import supports Ontario")
    # the assessment evaluates only the preflight blockers, and opens
    # bundles: the phase sign-offs (backup, documents, charset repair)
    # belong to phases it never runs. choices stay the full set so a
    # script may pass an import-side flag harmlessly.
    advertised = (ACCEPT_CLASSES if import_mode else
                  tuple(o19_preflight.ACCEPT_IDS) + ("unverified-bundle",))
    ap.add_argument("--accept", action="append", default=[],
                    metavar="CLASS", choices=list(ACCEPT_CLASSES),
                    help="acknowledge a blocker class (repeatable): "
                         + ", ".join(advertised)
                         + ("" if import_mode else
                            " (not recorded: this verb persists no "
                            "sign-off)"))
    ap.add_argument("--restage", action="store_true",
                    help="drop and re-restore the staging schema (also "
                         "clears the recorded preflight verdict)")
    ap.add_argument("--mariadb-arg", action="append", default=None,
                    metavar="ARG",
                    help="DEV ONLY: override the mariadb client argv "
                         "(repeatable); implies --dev-target. Refused on "
                         "a packaged host")
    ap.add_argument("--dev-target", action="store_true",
                    help="DEV ONLY (with --mariadb-arg): downgrade the "
                         "stock-deploy pristine gate to a warning. Refused "
                         "on a packaged host")
    if import_mode:
        ap.add_argument("--documents", metavar="FILE",
                        help="tar of the OscarDocument tree")
        ap.add_argument("--skip-documents", action="store_true",
                        help="import without documents (requires "
                             "--accept no-documents)")
        ap.add_argument("--admin-user", metavar="NAME",
                        help="break-glass admin account created before the "
                             "seeded clinician is removed (required for a "
                             "real import)")
        ap.add_argument("--role-template", action="append", default=[],
                        metavar="CUSTOM=STOCK",
                        help="grant a clinic-custom role the CARLOS-era "
                             "privileges of the named CARLOS stock role "
                             "(repeatable); default: the closest stock role "
                             "by privilege similarity, reported")
        ap.add_argument("--fixups-dir", metavar="DIR",
                        help="DEV ONLY (with --dev-target): directory "
                             "holding the packaged data-fixup scripts "
                             "(Rich Text Letter); default "
                             "/usr/share/carlos-emr/schema/o19-fixups")
        ap.add_argument("--statement-timeout", type=_nonnegative_seconds,
                        default=0,
                        metavar="SECONDS",
                        help="bound every SQL statement of the import to "
                             "this many seconds (MariaDB max_statement_time; "
                             "0 = no bound). A sparse or crafted dump "
                             "cannot then hold one statement forever")
        ap.add_argument("--dry-run", action="store_true",
                        help="run P0-P2 plus the properties report; no "
                             "writes beyond the throwaway staging schema. "
                             "--accept flags are NOT recorded, and a "
                             "workspace whose import is in progress is "
                             "refused")
        ap.add_argument("--resume", action="store_true",
                        help="continue a previous run from its recorded "
                             "state (required whenever state exists)")
        ap.add_argument("--cleanup", action="store_true",
                        help="after verify (or before the copy started): "
                             "drop staging, keep archive + reports")
    return ap


def _default_province() -> str:
    """The packaged host's configured province; a development database
    (no env file) defaults to Ontario. A malformed env file is an error,
    never silently Ontario."""
    from . import config
    if not os.path.exists(ENV_FILE):
        return "on"
    return config.load().province


def _province(args) -> str:
    """--province may only restate the packaged host's configured province:
    a BC host imported 'as Ontario' would pass the Ontario seed floors and
    run the Ontario manifest against a BC schema."""
    configured = _default_province()
    chosen = getattr(args, "province", None)
    if chosen and os.path.exists(ENV_FILE) and chosen != configured:
        die("--province {0!r} contradicts this host's configured province "
            "{1!r} ({2}); the import runs against the host as deployed"
            .format(chosen, configured, ENV_FILE))
    return chosen or configured


def manifest_change_refusal(state: Dict,
                            current_map: str) -> Optional[str]:
    """Why this workspace may not continue under the installed manifest,
    or None.

    A carlos-emr upgrade between two runs of the same import changes how
    tables are classified, so a resume would copy the rest of the clinic
    under different rules than the first half. The ETL ledger refuses
    that on its own; this covers the resume whose ETL is already marked
    done and would skip P4 entirely -- and separates the finished import,
    where there is nothing left to continue, from the half-finished one,
    which has to be resumed first. Both are retired under the package
    that made them: --cleanup re-counts the staging tables under the
    INSTALLED manifest (see `cleanup_manifest_refusal`), so naming it as
    the whole answer for a finished import sent the operator into a loop
    between the two refusals."""
    recorded = state.get("inputs", {}).get("schema_map_version")
    if not recorded or recorded == current_map:
        return None
    if not any(phase_done(state, ph)
               for ph in ("etl", "documents", "props", "verify")):
        return None
    if phase_done(state, "verify"):
        return ("this import completed under manifest {0} (the installed "
                "carlos-ctl carries {1}): nothing is left to resume, but "
                "--cleanup re-counts the staging tables under the "
                "INSTALLED manifest and would describe a different "
                "import. Retire it under the package that made it: "
                "reinstall the carlos-emr version that shipped manifest "
                "{0}, run --cleanup, then upgrade again."
                .format(recorded, current_map))
    return ("this import ran with manifest {0}; the installed carlos-ctl "
            "carries {1}. A finished ETL cannot be continued under a "
            "different manifest. Lossless path: reinstall the carlos-emr "
            "package version that shipped manifest {0}, --resume, "
            "--cleanup, then upgrade; otherwise restore the pre-import "
            "snapshot and start over.".format(recorded, current_map))


def documents_refusal(skip_documents: bool, accepted, documents,
                      import_mode: bool, cleanup: bool) -> Optional[str]:
    """Why the run may not proceed without a documents tree, or None.

    The documents tar is not optional by default: a chart whose scanned
    letters are missing looks complete in the UI, which is worse than a
    refusal. Skipping it is a recorded sign-off, and the sign-off has to
    be present in the MERGED set so it survives --resume."""
    if skip_documents and "no-documents" not in accepted:
        return ("--skip-documents requires --accept no-documents (the "
                "missing documents are a recorded sign-off, not a "
                "default)")
    if (documents is None and import_mode and not skip_documents
            and not cleanup and "no-documents" not in accepted):
        return ("no documents tar in the inputs — pass --documents, or "
                "--skip-documents with --accept no-documents")
    return None


def _resolve_inputs(args, state_dir: str, accepted=None,
                    recorded_digest: Optional[str] = None,
                    workdir_name: str = "bundle") -> Dict:
    """Bundle vs separate flags -> concrete file paths (+ digests).
    `accepted` is the merged sign-off set (this run's --accept plus the
    ledger's), so a recorded `unverified-bundle` survives --resume — but
    only for the file it was recorded for (`recorded_digest`, the
    ledger's bundle sha256): a replacement bundle is a new file that
    needs its own digest or a fresh sign-off."""
    if accepted is None:
        accepted = args.accept
    if args.bundle:
        for flag, val in (("--dump", args.dump),
                          ("--properties", args.properties),
                          ("--documents", getattr(args, "documents", None))):
            if val:
                die("--bundle and {0} are mutually exclusive".format(flag))
        try:
            o19bundle.validate_bundle_args(args.bundle, args.bundle_pass)
        except ValueError as exc:
            die(str(exc))
        # integrity first: openssl enc's CBC output carries no MAC, so a
        # ciphertext altered in transit can still decrypt to a valid-looking
        # archive. The digest travels with the password, not the file.
        actual = sha256_file(args.bundle)
        applicable = bundle_acknowledgements(args.accept, accepted,
                                             recorded_digest, actual)
        refusal = bundle_digest_refusal(args.bundle_sha256, actual,
                                        applicable)
        if refusal:
            if "unverified-bundle" in accepted \
                    and "unverified-bundle" not in applicable:
                refusal = ("the recorded unverified-bundle sign-off names "
                           "the bundle opened earlier (sha256 {0}...); "
                           "this file differs ({1}...) and is not covered "
                           "by it. ".format((recorded_digest or "")[:12],
                                            actual[:12]) + refusal)
            die(refusal)
        opened = o19bundle.open_bundle(
            args.bundle, os.path.join(state_dir, workdir_name),
            pass_spec=args.bundle_pass, cipher=args.bundle_cipher,
            openssl_opts=args.bundle_openssl_opt, expected_sha256=actual)
        if getattr(args, "skip_documents", False) and opened["documents"]:
            die("--skip-documents contradicts a bundle that CONTAINS a "
                "documents member — drop one of the two")
        supplied_digests = getattr(args, "o19_digests", None)
        if supplied_digests:
            # never guess which of two digest documents describes this
            # dump: one of them would silently become the thing P2
            # measures the clinic against
            if opened.get("digests"):
                die("--o19-digests contradicts a bundle that CONTAINS a "
                    "content-digest member — drop one of the two")
            if not os.path.isfile(supplied_digests):
                die("no such file: {0}".format(supplied_digests))
            opened["digests"] = supplied_digests
        # the run's own copy is taken by _make_ctx, AFTER the ledger
        # binding has vetted it: a candidate that is about to be refused
        # must not first overwrite what the run measured
        return opened
    if args.bundle_pass or args.bundle_openssl_opt or args.bundle_sha256:
        die("--bundle-pass/--bundle-openssl-opt/--bundle-sha256 need "
            "--bundle")
    if not args.dump or not args.properties:
        die("either --bundle, or all of --dump and --properties (and "
            "--documents unless --skip-documents), are required")
    docs = getattr(args, "documents", None)
    if not docs and not getattr(args, "skip_documents", True):
        die("--documents missing (or pass --skip-documents with "
            "--accept no-documents)")
    supplied_digests = getattr(args, "o19_digests", None)
    for p in (args.dump, args.properties, docs, supplied_digests):
        if p and not os.path.isfile(p):
            die("no such file: {0}".format(p))
    return {"dump": args.dump, "documents": docs,
            "properties": args.properties,
            "digests": supplied_digests,
            "bundle_sha256": None}


def bundle_acknowledgements(cli_accept, merged, recorded_digest: Optional[str],
                            actual_digest: str) -> List[str]:
    """The sign-offs that apply to THIS bundle file: the merged set (CLI +
    ledger) when the file is the one the ledger recorded, otherwise only
    what this invocation passed — a recorded `unverified-bundle` covers
    one file, never a replacement. Pure, for the state tests."""
    if recorded_digest and recorded_digest == actual_digest:
        return sorted(set(merged or ()))
    return sorted(set(cli_accept or ()))


def bundle_digest_refusal(expected: Optional[str], actual: str,
                          accepted) -> Optional[str]:
    """Why the bundle may not be opened (None when it may): the digest the
    clinic conveyed separately must match the file, and skipping the check
    is a recorded sign-off (--accept unverified-bundle), never a default.
    Pure, for the state tests."""
    if expected:
        want = expected.strip().lower()
        if not (len(want) == 64 and all(c in "0123456789abcdef"
                                        for c in want)):
            return "--bundle-sha256 must be 64 hex characters"
        if want != actual.lower():
            return ("bundle sha256 mismatch: the file on disk is {0}..., "
                    "the clinic conveyed {1}... — the bundle was altered or "
                    "truncated in transit; obtain it again. (Never bypass "
                    "this by re-typing the digest from the file.)".format(
                        actual[:12], want[:12]))
        return None
    if "unverified-bundle" in (accepted or ()):
        return None
    return ("--bundle-sha256 is required: openssl enc has no integrity "
            "check, so the digest the clinic sends separately from the file "
            "is what proves the bundle arrived intact. Pass the digest, or "
            "record the sign-off with --accept unverified-bundle.")


def stage_digests(path: str, state_dir: str) -> str:
    """Copy a candidate digest document into the run directory at 0600,
    beside the run's own copy rather than over it; return the candidate.

    The run measures the copy it took, not the operator's file: a
    `--o19-digests` path is read at P2, AFTER the dump has been staged,
    so a file replaced in between would be compared against data it does
    not describe, and across a `--resume` a different file would silently
    change what the transfer was ever measured against.

    Two steps rather than one because the candidate has to be HASHED
    before it may replace anything -- writing straight over the run's
    snapshot would destroy the only copy of what P2 measured in order to
    then refuse the file that destroyed it."""
    dest = os.path.join(state_dir, DIGESTS_SNAPSHOT + ".incoming")
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    with open(path, "rb") as src:
        fd = os.open(dest, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        # fchmod after the open: the mode above applies to a NEW file
        # only, so a rerun over an earlier copy would keep its mode
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "wb") as out:
            shutil.copyfileobj(src, out)
    return dest


def commit_digests(incoming: str, state_dir: str) -> str:
    """Install a vetted candidate as the run's digest document."""
    dest = os.path.join(state_dir, DIGESTS_SNAPSHOT)
    os.replace(incoming, dest)
    return dest


def resolve_digests(inputs: Dict, state_dir: str, restage: bool) -> Dict:
    """Settle which digest document THIS run measures against.

    Returns `{"path": ..., "sha256": ..., "incoming": ...}`; `incoming`
    is a staged candidate the caller must commit or discard once the
    ledger binding has been checked.

    A resumed invocation that names no document keeps the copy the run
    already took -- the transfer check must go on measuring the same
    bytes the earlier phases did, exactly as the run keeps its dump.
    `--restage` is the deliberate exception: a new dump makes the old
    clinic's numbers meaningless, so the stale copy is retired with it.
    """
    kept = os.path.join(state_dir, DIGESTS_SNAPSHOT)
    supplied = inputs.get("digests")
    if restage and not supplied:
        if os.path.isfile(kept):
            os.unlink(kept)
        return {"path": None, "sha256": None, "incoming": None}
    if not supplied:
        if not os.path.isfile(kept):
            return {"path": None, "sha256": None, "incoming": None}
        return {"path": kept, "sha256": sha256_file(kept), "incoming": None}
    incoming = stage_digests(supplied, state_dir)
    return {"path": None, "sha256": sha256_file(incoming),
            "incoming": incoming}


def digests_change_refusal(recorded: Optional[str], actual: Optional[str],
                           restage: bool = False) -> Optional[str]:
    """Why this run may not continue with the digest document it was
    given (None when it may).

    The ledger records the sha256 of the document the run measured
    against. A `--resume` handed a DIFFERENT one would compare the
    transfer against numbers the earlier phases never saw, and nothing
    would say so. `--restage` is the way out and must therefore not be
    refused by this: it drops the staged dump for another, which is
    exactly when a different set of clinic numbers is the RIGHT one.
    Pure, for the state tests."""
    if restage or not recorded or not actual or recorded == actual:
        return None
    return ("the content digests differ from the ones this run recorded "
            "(ledger {0}..., supplied {1}...). A resumed run must measure "
            "against the same document the earlier phases did; restore "
            "the original file, or start a fresh run with --restage."
            .format(recorded[:12], actual[:12]))


def dev_mode_refusal(dev_target: bool, mariadb_arg: Optional[List[str]],
                     packaged_host: bool) -> Optional[str]:
    """--dev-target/--mariadb-arg exist for development databases only:
    on a packaged host (carlos-emr.env present) they are refused outright
    — the pristine gate has no override there — and --dev-target without
    the connection seam is meaningless (the deb socket IS the packaged
    host). Pure, for the state tests."""
    if not (dev_target or mariadb_arg):
        return None
    if packaged_host:
        return ("--dev-target/--mariadb-arg are for development databases "
                "only; this is a packaged host ({0} exists) and its "
                "stock-deploy gate has no override".format(ENV_FILE))
    if dev_target and not mariadb_arg:
        return "--dev-target requires --mariadb-arg (the dev database)"
    return None


def _dev_mode(args) -> bool:
    """Whether this invocation targets a development database, dying on the
    combinations that are not allowed on a packaged host."""
    refusal = dev_mode_refusal(bool(args.dev_target), args.mariadb_arg,
                               os.path.exists(ENV_FILE))
    if refusal:
        die(refusal)
    return bool(args.dev_target or args.mariadb_arg)


def continues_recorded_run(args) -> bool:
    """Whether this invocation continues the run the ledger records --
    and so inherits the sign-offs that run was given.

    `--resume` obviously does. `--cleanup` does too, and it was the one
    that did not: it re-runs row parity before dropping staging, and an
    import verified with `--accept content-migration` re-checked with an
    empty accept set refuses -- leaving a passed import permanently
    un-cleanable. Cleanup is the epilogue of the recorded run, whose
    ledger already says verification passed; it is not the fresh
    invocation the inheritance rule guards against."""
    return bool(getattr(args, "resume", False)
                or getattr(args, "cleanup", False))


def merged_acknowledgements(cli_accept, state: Dict,
                            resume: bool) -> List[str]:
    """This run's --accept classes, plus — when continuing the recorded
    run (`--resume`, `--cleanup`) — the sign-offs the ledger already
    records, so continuing a run need not repeat them.

    A fresh run gets exactly what it passed. The ledger's `accepted` is
    written before the first phase runs, so an invocation that dies in a
    P0 gate leaves its sign-offs behind with no phase recorded; because
    that is not a resumable run, the operator's only legal next step is
    a fresh invocation, and inheriting there would let a sign-off as
    consequential as `no-pre-backup` (which skips the rollback snapshot)
    or `charset-repair` (given for a different dump) apply to a run
    nobody acknowledged. Pure, for the state tests."""
    if not resume:
        return sorted(set(cli_accept or ()))
    return sorted(set(cli_accept or ()) | set(state.get("accepted", [])))


def assessment_refusal(state: Dict, state_dir: str) -> Optional[str]:
    """Why a dry run or an assessment may not touch this workspace: a run
    in progress (its ledger, its extracted bundle, its recorded inputs)
    must be continued with --resume or retired with --cleanup, never
    overwritten by an experiment. Pure, for the state tests."""
    phases = set(state.get("phases", {})) - {"stage"}
    if state.get("cleanup") == "in-progress":
        return "a previous --cleanup was interrupted — run --cleanup again"
    if phases or etl_started(state_dir):
        return ("this workspace holds an import in progress ({0}): a dry "
                "run or assessment would overwrite its inputs; continue it "
                "with --resume or retire it with --cleanup".format(
                    ", ".join(sorted(phases)) or "etl ledger"))
    return None


#: the held workspace lock's descriptor, kept in a dict so the helper
#: needs no `global` (it is process-wide state, deliberately)
_WORKSPACE_LOCK: Dict[str, int] = {}


def take_workspace_lock(state_dir: str) -> None:
    """Hold an exclusive lock on the workspace for this process's life.

    Every gate in this module is a read of state.json or the ETL ledger,
    so two concurrent invocations pass all of them. Both would then enter
    the seed block, allocate the same break-glass provider_no, and write
    over each other's admin-credentials.txt and ledger; one INSERT wins on
    security.user_name and the loser's cleanup on a later resume deletes
    the surviving admin by provider_no — while the seeded clinician has
    already been deleted. That leaves the clinic with no working login and
    a credentials file matching no row.

    The fd is deliberately never closed: the kernel releases the lock when
    the process exits, however it exits."""
    if _WORKSPACE_LOCK:
        return
    import fcntl
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    path = os.path.join(state_dir, ".lock")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        holder = ""
        try:
            with open(path, encoding="utf-8") as fh:
                holder = fh.read().strip()
        except OSError:
            pass
        os.close(fd)
        die("another carlos-ctl import is working in {0}{1} — wait for it "
            "to finish, or check it with `systemctl status` / `ps`. Two "
            "runs over one workspace can leave the clinic with no working "
            "login.".format(state_dir,
                            " (pid {0})".format(holder) if holder else ""))
    os.ftruncate(fd, 0)
    os.write(fd, "{0}\n".format(os.getpid()).encode("utf-8"))
    _WORKSPACE_LOCK["fd"] = fd


def _make_ctx(args, import_mode: bool, state_dir: str = STATE_DIR) -> Dict:
    """Build the phase context and take the workspace lock.

    `import_mode` false (and a dry run) means an ASSESSMENT: it refuses a
    workspace whose import has begun, extracts to `bundle-assess/`, and
    does not rewrite the ledger's recorded inputs -- an assessment must
    never disturb a run in progress."""
    dev_target = _dev_mode(args)
    # BEFORE anything reads the manifest. One package serves every
    # province (debconf picks one at install time from the same .deb),
    # so the module-level names carry one profile's rulings until they
    # are pointed at the host's -- and the first thing recorded below is
    # `schema_map_version`, which differs per profile. A province the
    # package does not carry rebinds nothing and is then refused by
    # run_p0's assertion, so binding here does not weaken that gate.
    province = _province(args)
    o19map_schema.bind(province)
    take_workspace_lock(state_dir)
    state = load_state(state_dir)
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    real_run = import_mode and not getattr(args, "dry_run", False)
    if not real_run:
        refusal = assessment_refusal(state, state_dir)
        if refusal:
            die(refusal)
    elif state.get("cleanup") == "in-progress":
        die("a previous --cleanup was interrupted — run --cleanup again")

    from . import o19roles
    try:
        role_templates = o19roles.parse_role_templates(
            getattr(args, "role_template", None))
    except ValueError as exc:
        die(str(exc))
    if getattr(args, "fixups_dir", None) and not dev_target:
        die("--fixups-dir is a development seam and needs --dev-target")
    accepted = merged_acknowledgements(args.accept, state,
                                       continues_recorded_run(args))
    inputs = _resolve_inputs(
        args, state_dir, accepted,
        recorded_digest=state.get("inputs", {}).get("bundle_sha256"),
        # an assessment extracts into its own workdir: the real run's
        # members (what --resume continues from) are never replaced
        workdir_name="bundle" if real_run else "bundle-assess")
    # An assessment records nothing and persists no sign-off, so it also
    # takes no copy and binds nothing: it reads the operator's file as
    # given, and cannot overwrite a real run's snapshot.
    digests_sha = None
    if real_run:
        settled = resolve_digests(inputs, state_dir,
                                  bool(getattr(args, "restage", False)))
        digests_sha = settled["sha256"]
        refusal = digests_change_refusal(
            state.get("inputs", {}).get("digests_sha256"), digests_sha,
            bool(getattr(args, "restage", False)))
        if refusal:
            # the run's own copy stays where it is: the candidate that
            # was refused must not be what destroyed the evidence
            if settled["incoming"]:
                os.unlink(settled["incoming"])
            die(refusal)
        if settled["incoming"]:
            inputs["digests"] = commit_digests(settled["incoming"],
                                               state_dir)
        else:
            inputs["digests"] = settled["path"]
    if inputs.get("properties"):
        # needs nothing but the file, so it runs before P0 rather than at
        # P2 — a typo in the clinic's oscar.properties should not cost a
        # restic snapshot and an hour of restore first
        try:
            o19_preflight.parse_properties(inputs["properties"])
        except (ValueError, OSError) as exc:
            die("cannot parse {0} as a java.util.Properties file ({1}) — "
                "CARLOS would reject it too. Obtain a readable "
                "oscar.properties from the clinic; nothing has been "
                "staged or written.".format(inputs["properties"], exc))
    # the merged set on purpose: a recorded no-documents sign-off
    # survives --resume
    refusal = documents_refusal(
        bool(getattr(args, "skip_documents", False)), accepted,
        inputs["documents"], import_mode,
        bool(getattr(args, "cleanup", False)))
    if refusal:
        die(refusal)

    if not getattr(args, "dry_run", False) and import_mode:
        # sign-offs persist only from a real run: a dry run's --accept is
        # an experiment, not a recorded acknowledgement
        state["accepted"] = accepted
    refusal = manifest_change_refusal(state,
                                      o19map_schema.SCHEMA_MAP_VERSION)
    if refusal:
        die(refusal)
    if real_run:
        # recorded by real runs only (an assessment must not re-point the
        # bundle sign-off or the manifest version); the manifest version
        # is the one the first phases ran under, never overwritten
        recorded = state.setdefault("inputs", {})
        recorded.update({
            "dump": os.path.basename(inputs["dump"]) if inputs["dump"]
            else None,
            "bundle_sha256": inputs.get("bundle_sha256"),
            # what P2 measured the transfer against; a resume handed a
            # different document is refused above rather than quietly
            # comparing against numbers the earlier phases never saw
            "digests_sha256": digests_sha,
            "dev_target": dev_target,
        })
        recorded.setdefault("schema_map_version",
                            o19map_schema.SCHEMA_MAP_VERSION)
        save_state(state_dir, state)

    return {
        "state_dir": state_dir,
        "state": state,
        "query": make_query(args.mariadb_arg,
                            getattr(args, "statement_timeout", 0)),
        # the unbuffered reader, for the one read whose size a CLINIC's
        # data decides: the archive CSV export (o19docs)
        "row_stream": make_row_stream(
            args.mariadb_arg, getattr(args, "statement_timeout", 0)),
        "statement_timeout": getattr(args, "statement_timeout", 0),
        "province": province,
        "tool_version": package_version(),
        "accepted": accepted,
        "dev_target": dev_target,
        "dump": inputs["dump"],
        "documents": inputs["documents"],
        "properties": inputs["properties"],
        "o19_digests": inputs.get("digests"),
        "dump_size": os.path.getsize(inputs["dump"]) if inputs["dump"] else 0,
        "documents_size": (os.path.getsize(inputs["documents"])
                           if inputs.get("documents") else 0),
        "bundle_size": (os.path.getsize(args.bundle)
                        if getattr(args, "bundle", None) else 0),
        "target_db": _target_db(dev_target),
        "restage": getattr(args, "restage", False),
        "resume": getattr(args, "resume", False),
        "dry_run": getattr(args, "dry_run", False),
        "admin_user": getattr(args, "admin_user", None),
        "role_templates": role_templates,
        "fixups_dir": getattr(args, "fixups_dir", None),
    }


def _nonnegative_seconds(text: str) -> int:
    """argparse type for --statement-timeout: 0 (no bound) or a positive
    whole number of seconds; a negative value would only fail at the
    first SQL statement, as an invalid session setting."""
    try:
        seconds = int(text)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            "expected a whole number of seconds, not {0!r}"
            .format(text)) from exc
    if seconds < 0:
        raise argparse.ArgumentTypeError(
            "must be 0 (no bound) or a positive number of seconds")
    return seconds


def require_resume_for_existing_state(state: Dict, resume: bool,
                                      dry_run: bool) -> Optional[str]:
    """The message refusing a rerun over recorded state without --resume
    (None when the run may proceed). Pure, for the state tests."""
    # a staged dump alone is reusable (a dry run or assessment leaves it);
    # anything else recorded is a run in progress
    phases = set(state.get("phases", {})) - {"stage"}
    if not phases or resume:
        return None
    done = ", ".join(sorted(phases))
    if dry_run:
        return ("a previous import left state behind ({0}); a dry run "
                "cannot inspect a run in progress — --resume it or "
                "--cleanup it first".format(done))
    return ("a previous import left state behind ({0}). Pass --resume to "
            "continue it, or --cleanup after a verified import; state is "
            "never continued implicitly.".format(done))


def nothing_to_resume_refusal(state: Dict, resume: bool,
                              etl_begun: bool) -> Optional[str]:
    """The message refusing --resume when no run is recorded (None when
    the flag matches one). A staged dump alone is what a dry run or an
    assessment leaves behind, not a run: the flag must never start a
    fresh import silently. Pure, for the state tests."""
    if not resume:
        return None
    if (set(state.get("phases", {})) - {"stage"}) or etl_begun:
        return None
    return ("--resume: no import is recorded under {0} (a staged dump "
            "alone is not a run) — start the import without --resume"
            .format(STATE_DIR))


def _target_db(dev_target: bool) -> str:
    """CARLOS_DB_NAME from the packaged host's env file; a dev database
    (no env file, --mariadb-arg seam) uses the deployment default."""
    from . import config
    if os.path.exists(ENV_FILE):
        return config.load().db_name
    if dev_target:
        return "oscar"  # the deb deployment's CARLOS_DB_NAME default
    die("{0} missing — this is not a packaged CARLOS host (a development "
        "database needs --mariadb-arg)".format(ENV_FILE))
    return ""  # unreachable


def webapp_running_refusal() -> Optional[str]:
    """CARLOS must not run against the target while it is being written:
    its startup listener creates rows (program, site, memberships) that
    would fail row parity, and a live session could read a half-copied
    chart. Checked on every real run and resume of a packaged host."""
    if not os.path.exists(ENV_FILE):
        return None  # a development database, no service unit
    # NOT `is-active --quiet`: a unit still starting reports
    # ActiveState=activating, for which is-active exits 3 — and Tomcat
    # takes tens of seconds to reach `active`, so the guard would be
    # inert for exactly the window in which the startup listener writes
    # its rows. dbops.py makes the same correction for the backup units.
    state = run(["systemctl", "show", "-p", "ActiveState", "--value",
                 "carlos-emr"], capture_output=True).stdout.strip()
    if state in ("active", "activating", "reloading", "deactivating"):
        return ("carlos-emr is {0} — stop it for the duration of the "
                "import (`carlos-ctl stop`, or `systemctl stop carlos-emr`) "
                "and re-run; start it again only after the verified import "
                "and the properties fragment have been applied".format(
                    state))
    return None


def _guarded(fn, code: int = 1):
    """Run a verb body; a failed client statement (server unreachable, a
    refused privilege) ends in one clear error line, never a traceback.
    `code` is the exit status of that failure: the preflight verb reserves
    the low codes for verdicts, so it fails with its tool-error code."""
    try:
        return fn()
    except o19etl.QueryError as exc:
        die(str(exc), code)


def cmd_o19_preflight(argv) -> int:
    # exit 1 is "go with acknowledgements" and 2 "no-go" for this verb:
    # a refusal of any kind (unreachable server, bad flags, missing file,
    # disk, replicas, a refused dump) must not read as a verdict, so
    # every early exit becomes the tool-error code — except --help
    """`carlos-ctl o19-preflight` entrypoint.

    Exit 0/1/2 are this verb's VERDICT (go, go with acknowledgements,
    no-go), so any other failure -- bad flags, an unreachable server, a
    refused dump -- is remapped to the tool-error code rather than being
    read as a migration verdict."""
    try:
        return _guarded(lambda: _cmd_o19_preflight(argv),
                        o19_preflight.EXIT_TOOL_ERROR)
    except SystemExit as exc:
        if exc.code in (0, None):
            raise
        raise SystemExit(o19_preflight.EXIT_TOOL_ERROR) from exc


def cmd_import_o19(argv) -> int:
    """`carlos-ctl import-o19` entrypoint: run the phases, or `--cleanup`."""
    return _guarded(lambda: _cmd_import_o19(argv))


def _cmd_o19_preflight(argv) -> int:
    """The assessment body: capacity gates, stage, report. Records no
    verdict and persists no sign-off; the return value IS the verdict."""
    args = _parser("carlos-ctl o19-preflight", import_mode=False).parse_args(
        list(argv))
    if os.geteuid() != 0 and not args.mariadb_arg:
        die("this command needs root (or --mariadb-arg for a dev database)")
    # an assessment: capacity gates, stage, report — never a recorded
    # verdict or a persisted sign-off; the exit code IS the verdict
    # (_make_ctx refuses a mid-import workspace before touching it)
    ctx = _make_ctx(args, import_mode=False)
    ctx["dry_run"] = True
    run_p0_capacity(ctx)
    run_p1(ctx)
    report = run_p2(ctx)
    log("preflight verdict: {0} — report in {1}/preflight.txt".format(
        report["verdict"], ctx["state_dir"]))
    return int(report["exit_code"])


def _cmd_import_o19(argv) -> int:
    """The import body: argument gates, then P0..P7 in order (or
    `--cleanup`)."""
    args = _parser("carlos-ctl import-o19", import_mode=True).parse_args(
        list(argv))
    if os.geteuid() != 0 and not args.mariadb_arg:
        die("this command needs root (or --mariadb-arg for a dev database)")

    # before any other gate, including --cleanup's: a workspace rewound
    # by a restored snapshot makes every one of them refuse, and two of
    # them point back at the snapshot the operator just restored
    refusal = rewound_workspace_refusal(load_state(STATE_DIR), STATE_DIR)
    if refusal:
        die(refusal)

    if args.cleanup:
        if args.dry_run:
            die("--cleanup has no dry-run mode: it drops the staging "
                "schema and retires this run's ledgers and reports. Run "
                "it without --dry-run when the import is verified.")
        ctx = _make_ctx_for_cleanup(args)
        run_cleanup(ctx)
        return 0

    if not args.dry_run and not args.admin_user:
        die("--admin-user is required for a real import (the break-glass "
            "administrator created before the seeded clinician is removed)")
    if args.admin_user:
        try:
            o19etl.validate_admin_user(args.admin_user)
        except ValueError as exc:
            die(str(exc))
    state = load_state(STATE_DIR)
    refusal = require_resume_for_existing_state(
        state, args.resume, args.dry_run)
    if refusal:
        die(refusal)
    refusal = nothing_to_resume_refusal(
        state, args.resume, etl_started(STATE_DIR))
    if refusal:
        die(refusal)
    if not args.dry_run:
        refusal = webapp_running_refusal()
        if refusal:
            die(refusal)

    ctx = _make_ctx(args, import_mode=True)
    log("import-o19 (experimental) — manifest {0}, province {1}{2}".format(
        o19map_schema.SCHEMA_MAP_VERSION, ctx["province"],
        ", DEV TARGET" if ctx["dev_target"] else ""))

    run_p0(ctx)
    if not args.dry_run:
        run_p3(ctx)  # the rollback point exists before any clinic SQL runs
    run_p1(ctx)
    run_p2(ctx)
    if args.dry_run:
        from . import o19props
        o19props.run_props(ctx)  # report-only in dry-run (fragment flagged)
        log("dry run complete — reports in {0}; nothing was written beyond "
            "the throwaway staging schema".format(ctx["state_dir"]))
        return 0
    run_p4(ctx)
    run_p5(ctx)
    run_p6(ctx)
    run_p7(ctx)
    log("import complete (experimental). Remaining operator steps:\n  "
        + "\n  ".join("{0}. {1}".format(i, step)
                      for i, step in enumerate(NEXT_STEPS, 1))
        + "\n  (the reports are in {0})".format(ctx["state_dir"]))
    return 0


def _make_ctx_for_cleanup(args) -> Dict:
    """The context `run_cleanup` needs: no bundle is opened and no inputs
    are resolved, because cleanup acts on the workspace alone -- but the
    target and archive schemas ARE needed, because the drop is gated on a
    parity that counts staging rows against the homes they were preserved
    into."""
    state_dir = STATE_DIR
    dev_target = _dev_mode(args)
    # cleanup counts staging rows against the homes the manifest says
    # they were preserved into, so it reads the same per-province rulings
    # the import ran under and has to bind them the same way
    o19map_schema.bind(_province(args))
    take_workspace_lock(state_dir)
    return {
        "state_dir": state_dir,
        "state": load_state(state_dir),
        "query": make_query(args.mariadb_arg),
        "dev_target": dev_target,
        "target_db": _target_db(dev_target),
        "archive_schema": ARCHIVE_SCHEMA,
        "dry_run": getattr(args, "dry_run", False),
    }

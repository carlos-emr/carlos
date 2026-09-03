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
import tarfile
import sys
import time
from typing import Callable, Dict, List, Optional

from . import dbops, o19_preflight, o19bundle, o19docs, o19etl, o19map_schema
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
)

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
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    durable_json(state_path(state_dir), state)


def phase_done(state: Dict, phase: str) -> bool:
    return state.get("phases", {}).get(phase, {}).get("status") == "done"


def mark_done(state_dir: str, state: Dict, phase: str, **extra) -> None:
    entry = {"status": "done",
             "at": time.strftime("%Y-%m-%dT%H:%M:%S")}
    entry.update(extra)
    state.setdefault("phases", {})[phase] = entry
    save_state(state_dir, state)


def report_append(state_dir: str, title: str, body: str) -> None:
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    with open(os.path.join(state_dir, "report.txt"), "a",
              encoding="utf-8") as fh:
        fh.write("== {0} ==\n{1}\n\n".format(title, body.rstrip()))


def sha256_file(path: str) -> str:
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


def batch_rows(stdout: str) -> List[List[str]]:
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
                        datadir: str = "") -> Optional[str]:
    """None if fine, else a message. dump_bytes is the UNCOMPRESSED dump
    size: the database volume needs roughly 2.5x that (staging restore +
    the copy into the target + archive schema); the state volume needs
    the bundle expanded (x2) plus the documents tar extracted (x2).

    Requirements on the SAME filesystem are summed before they are
    compared: on the single-root VM this normally runs on, checking each
    against the same free figure lets a host pass both and then fill up
    part-way through."""
    needs = (("database volume", datadir or server_datadir(),
              int(dump_bytes * 2.5)),
             ("state volume", STATE, bundle_size * 2 + documents_size * 2))
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


def documents_expanded_size(tar_path: str) -> int:
    """Expanded footprint of the documents archive (sum of member sizes
    from the archive's own headers); the archive's own size is what a
    .tar.gz compresses PDFs to, not what the tree needs on disk. Falls
    back to the file size when the archive cannot be read (P5 reports
    why)."""
    try:
        entries = o19bundle.read_tar_entries(tar_path,
                                             tar_path.endswith(".gz"))
    except (tarfile.TarError, OSError, EOFError) as exc:
        warn("cannot read the documents archive ({0}); using its file size "
             "for the disk check".format(str(exc)[:200]))
        return os.path.getsize(tar_path)
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
    try:
        dump_threads = query(
            "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE "
            "COMMAND IN ('Binlog Dump', 'Binlog Dump GTID')")
    except RuntimeError as exc:
        # this is the RELIABLE half of the check — SHOW REPLICA HOSTS
        # only sees replicas that registered a report_host. Failing open
        # here would let the import silently diverge an attached replica,
        # which is unrecoverable without re-importing.
        die("cannot determine whether replicas are attached (the "
            "information_schema.PROCESSLIST probe failed: {0}). The "
            "import's binlog-off bulk copy is not replica-safe, so it "
            "will not proceed on an unknown answer: grant the import "
            "account PROCESS, or detach the replicas and say so."
            .format(str(exc).strip()[:200]))
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
    # --restage drops the staged dump and restores this one, but P1 pops
    # the stage phase only later — so without the restage arm the gate
    # would size the NEW dump from the OLD one's recorded figure
    if ctx.get("restage") or not phase_done(ctx["state"], "stage"):
        log("measuring the dump's uncompressed size for the disk check ...")
        dump_bytes = uncompressed_size(ctx["dump"])
        ctx["dump_uncompressed"] = dump_bytes
    else:
        # already restored: only the copy into the target is left, sized
        # by what the stage phase recorded (never re-measured)
        dump_bytes = int(stage.get("uncompressed_bytes") or ctx["dump_size"])
    docs_bytes = 0
    if ctx.get("documents") and not phase_done(ctx["state"], "documents"):
        log("measuring the documents archive's expanded size ...")
        docs_bytes = documents_expanded_size(ctx["documents"])
        ctx["documents_size"] = docs_bytes
    headroom = check_disk_headroom(dump_bytes, ctx.get("bundle_size", 0),
                                   docs_bytes, server_datadir(query))
    if headroom:
        die(headroom)


def run_p0(ctx) -> None:
    query = ctx["query"]
    dev = ctx["dev_target"]
    if ctx.get("province") != "on":
        # the seed floors are generated from the Ontario migration set;
        # sweeping a BC host against them would refuse it for the wrong
        # reason before the preflight's own province gate is reached
        die("province {0!r}: the OSCAR 19 import supports Ontario "
            "deployments only (the BC manifest pass is outstanding)"
            .format(ctx.get("province")))
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
        if left:
            die("the archive schema {0} of a previous import exists — run "
                "`import-o19 --cleanup` for that run, or drop the schema "
                "once the clinic holds its CSV export, before importing "
                "another clinic on this host".format(ARCHIVE_SCHEMA))
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
    violations = pristine_violations(tolerate_startup_rows(counts, startup))
    identity_rows = query(
        "SELECT user_name FROM `{0}`.security".format(ctx["target_db"]))
    users = sorted(r[0] for r in identity_rows if r)
    if users != [o19map_schema.SEED_USER_NAME]:
        # the logins themselves go to the root-only file: a login name is
        # a person, and report.txt is the shareable record
        write_private(os.path.join(ctx["state_dir"], "verify-details.txt"),
                      "P0 pristine sweep: security holds "
                      + ", ".join(repr(u) for u in users) + "\n")
        violations.append("security holds {0} login(s) where only the "
                          "'{1}' seed is expected (named in "
                          "verify-details.txt)".format(
                              len(users), o19map_schema.SEED_USER_NAME))
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
                  "seeds); pristine={2}{3}{4}{5}".format(
                      ctx["target_db"], len(counts), not violations,
                      " (DEV TARGET — sweep advisory only)" if dev else "",
                      ("\n  startup-created rows tolerated (the webapp's "
                       "first start): " + ", ".join(
                           "{0} {1}".format(t, n)
                           for t, n in sorted(startup.items())))
                      if startup else "",
                      ("\n  " + "\n  ".join(violations[:25]))
                      if violations else ""))
    if not ctx.get("dry_run"):
        mark_done(ctx["state_dir"], ctx["state"], "check-pristine",
                  pristine=not violations, dev_target=dev)


# --------------------------------------------------------------------------
# P1 — stage
# --------------------------------------------------------------------------

def head_collations(head: bytes) -> List[str]:
    import re
    return sorted(set(
        m.decode("ascii", "replace")
        for m in re.findall(rb"COLLATE[= ]([A-Za-z0-9_]+)", head[:65536])))


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
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write("[client]\nuser={0}\npassword={1}\n".format(
            STAGING_USER, password.replace("\\", "\\\\")))


def revoke_staging_account(query, client_cnf: str) -> None:
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


def _stream_dump(opener: List[str], restore_argv: List[str]):
    """Pipe the dump through the restore client, scanning every chunk for
    redirecting statements. Returns (source_rc, client_rc, tail_bytes,
    redirect_message_or_None)."""
    src = subprocess.Popen(opener, stdout=subprocess.PIPE)  # nosec B603
    sink = subprocess.Popen(restore_argv,                    # nosec B603
                            stdin=subprocess.PIPE)
    tail = b""
    scanner = RedirectScanner()
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
            redirect = scanner.feed(chunk)
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
        progress = o19etl._progress_path(ctx["state_dir"])
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

    log("staging dump into {0} (binlog off, throwaway schema, restricted "
        "account) ...".format(STAGING_SCHEMA))
    query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
    query("CREATE DATABASE `{0}`".format(STAGING_SCHEMA))
    client_cnf = os.path.join(ctx["state_dir"], ".stage-client.cnf")
    restore_argv = staging_client_argv(ctx["query"].base_argv, client_cnf,
                                       ctx.get("statement_timeout", 0))
    grant_staging_account(query, client_cnf)

    try:
        src_rc, rc, tail, redirect = _stream_dump(opener, restore_argv)
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
        die("restore into {0} failed — see the client error above. The "
            "restore runs as an account limited to that schema: a dump "
            "carrying DEFINER clauses, GRANTs or server-wide SET "
            "statements must be re-taken without them (mysqldump "
            "--skip-triggers --set-gtid-purged=OFF, no --databases)"
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
    with open(os.path.join(ctx["state_dir"], "preflight.json"), "w",
              encoding="utf-8") as fh:
        json.dump(report, fh, indent=1, sort_keys=True)
    text = o19_preflight.render_text(report)
    with open(os.path.join(ctx["state_dir"], "preflight.txt"), "w",
              encoding="utf-8") as fh:
        fh.write(text)
    sys.stdout.write(text)
    report_append(ctx["state_dir"], "P2 preflight",
                  "verdict: {0}; acknowledged: {1}".format(
                      report["verdict"],
                      ", ".join(report["acknowledged"]) or "none"))
    if ctx.get("dry_run"):
        # a dry run IS the assessment: report the verdict, never error out,
        # and do not mark the phase done — a real run re-checks.
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
    mark_done(ctx["state_dir"], ctx["state"], "preflight",
              verdict=report["verdict"],
              acknowledged=report["acknowledged"])
    return report


# --------------------------------------------------------------------------
# P3 — backup
# --------------------------------------------------------------------------

def run_p3(ctx) -> None:
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
    staging row has a target twin), which needs the target's columns."""
    progress = o19etl.load_progress(ctx["state_dir"])
    return o19etl.row_parity(
        ctx["query"], STAGING_SCHEMA, ctx["target_db"],
        admin_user=(progress.get("admin_user") or ctx.get("admin_user")),
        admin_provider_no=progress.get("admin_provider_no"),
        appended=progress.get("roles", {}).get("appended"),
        dst_info=o19etl.introspect_columns(ctx["query"], ctx["target_db"]),
        archive_schema=ctx.get("archive_schema", ARCHIVE_SCHEMA),
        pruned_property_prefixes=o19_preflight.DROPPED_PROP_PREFIXES,
        pruned_property_keys=o19_preflight.DROPPED_PROP_KEYS)


def run_p4(ctx) -> None:
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
    if phase_done(ctx["state"], "documents"):
        log("documents: already restored and reconciled — skipping")
        return
    from . import o19docs
    ctx.setdefault("archive_schema", ARCHIVE_SCHEMA)
    o19docs.run_docs(ctx)


# --------------------------------------------------------------------------
# P6 — props, P7 — verify
# --------------------------------------------------------------------------

def run_p6(ctx) -> None:
    if phase_done(ctx["state"], "props"):
        log("props: fragment already produced — skipping")
        return
    from . import o19props
    o19props.run_props(ctx)


def write_private(path: str, text: str) -> None:
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


def run_p7(ctx) -> None:
    if phase_done(ctx["state"], "verify"):
        log("verify: already passed — skipping")
        return
    query = ctx["query"]
    src, dst = STAGING_SCHEMA, ctx["target_db"]
    problems: List[str] = []
    lines: List[str] = []

    ok, bad = _row_parity(ctx)
    lines.append("row parity: {0} table(s) match".format(len(ok)))
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
    joins = (("appointment", "demographic_no"),
             ("casemgmt_note", "demographic_no"),
             ("drugs", "demographic_no"),
             ("preventions", "demographic_no"),
             ("measurements", "demographicNo"),
             ("eform_data", "demographic_no"),
             ("billing_on_cheader1", "demographic_no"))
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
                if o19etl._absent_object_error(exc):
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

    # billing totals per fiscal year, to the cent
    agg = ("SELECT IFNULL(YEAR(billing_date),0), COUNT(*), "
           "IFNULL(SUM(CAST(total AS DECIMAL(14,2))),0) FROM "
           "`{0}`.billing_on_cheader1 GROUP BY 1 ORDER BY 1")

    def billing_totals(schema):
        """The aggregate, or None when this schema has no such table —
        judged per SIDE. Clearing BOTH on one absence would let a dump
        without the table pass verification against a target that has
        rows in it."""
        try:
            return {r[0]: (r[1], r[2]) for r in query(agg.format(schema))}
        except RuntimeError as exc:
            if not o19etl._absent_object_error(exc):
                raise
            return None

    s_rows = billing_totals(src)
    d_rows = billing_totals(dst)
    if s_rows is None and d_rows is None:
        # absent at this patch level on both sides: tolerated the same
        # way the parity and spot-check loops tolerate it, rather than
        # ending a completed import on a raw "table doesn't exist"
        s_rows = d_rows = {}
        lines.append("billing totals: billing_on_cheader1 absent from "
                     "both schemas")
    elif s_rows is None or d_rows is None:
        problems.append(
            "billing_on_cheader1 exists in {0} but not in {1} — "
            "verification cannot compare billing totals".format(
                dst if s_rows is None else src,
                src if s_rows is None else dst))
        s_rows = d_rows = {}
    if s_rows != d_rows:
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

    report_append(ctx["state_dir"], "P7 verify",
                  "\n".join(lines)
                  + ("\nFAILURES:\n  " + "\n  ".join(problems[:40])
                     if problems else "\nall checks passed"))
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


def run_cleanup(ctx) -> None:
    state = ctx["state"]
    refusal = cleanup_refusal(state, ctx["state_dir"], ctx["dev_target"])
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


#: per-run outputs retired alongside state.json (admin-credentials.txt is
#: deliberately not among them: the operator is told where it is)
RUN_FILES = ("report.txt", "roles-details.txt", "privilege-diff.txt",
             "verify-details.txt", "documents-details.txt", "preflight.txt",
             "preflight.json", "etl-progress.json",
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
    for p in (args.dump, args.properties, docs):
        if p and not os.path.isfile(p):
            die("no such file: {0}".format(p))
    return {"dump": args.dump, "documents": docs,
            "properties": args.properties, "bundle_sha256": None,
            "members": {}}


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
    refusal = dev_mode_refusal(bool(args.dev_target), args.mariadb_arg,
                               os.path.exists(ENV_FILE))
    if refusal:
        die(refusal)
    return bool(args.dev_target or args.mariadb_arg)


def merged_acknowledgements(cli_accept, state: Dict,
                            resume: bool) -> List[str]:
    """This run's --accept classes, plus — on a resume only — the
    sign-offs the ledger already records, so continuing a run need not
    repeat them.

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


_WORKSPACE_LOCK_FD = None


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
    global _WORKSPACE_LOCK_FD
    if _WORKSPACE_LOCK_FD is not None:
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
    _WORKSPACE_LOCK_FD = fd


def _make_ctx(args, import_mode: bool, state_dir: str = STATE_DIR) -> Dict:
    dev_target = _dev_mode(args)
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
                                       getattr(args, "resume", False))
    inputs = _resolve_inputs(
        args, state_dir, accepted,
        recorded_digest=state.get("inputs", {}).get("bundle_sha256"),
        # an assessment extracts into its own workdir: the real run's
        # members (what --resume continues from) are never replaced
        workdir_name="bundle" if real_run else "bundle-assess")
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
    if getattr(args, "skip_documents", False) \
            and "no-documents" not in accepted:
        die("--skip-documents requires --accept no-documents (the missing "
            "documents are a recorded sign-off, not a default)")
    if inputs["documents"] is None and import_mode \
            and not getattr(args, "skip_documents", False) \
            and not getattr(args, "cleanup", False):
        # the merged set: a recorded no-documents sign-off survives --resume
        if "no-documents" not in accepted:
            die("no documents tar in the inputs — pass --documents, or "
                "--skip-documents with --accept no-documents")

    if not getattr(args, "dry_run", False) and import_mode:
        # sign-offs persist only from a real run: a dry run's --accept is
        # an experiment, not a recorded acknowledgement
        state["accepted"] = accepted
    recorded_map = state.get("inputs", {}).get("schema_map_version")
    if (recorded_map and recorded_map != o19map_schema.SCHEMA_MAP_VERSION
            and any(phase_done(state, ph) for ph in ("etl", "documents",
                                                     "props", "verify"))):
        # the ETL ledger refuses a manifest change on its own; this covers
        # a resume whose ETL is already marked done and would skip P4
        if phase_done(state, "verify"):
            die("this import completed under manifest {0} (the installed "
                "carlos-ctl carries {1}): nothing is left to resume — run "
                "--cleanup to retire it".format(
                    recorded_map, o19map_schema.SCHEMA_MAP_VERSION))
        die("this import ran with manifest {0}; the installed carlos-ctl "
            "carries {1}. A finished ETL cannot be continued under a "
            "different manifest. Lossless path: reinstall the carlos-emr "
            "package version that shipped manifest {0}, --resume, "
            "--cleanup, then upgrade; otherwise restore the pre-import "
            "snapshot and start over.".format(
                recorded_map, o19map_schema.SCHEMA_MAP_VERSION))
    if real_run:
        # recorded by real runs only (an assessment must not re-point the
        # bundle sign-off or the manifest version); the manifest version
        # is the one the first phases ran under, never overwritten
        recorded = state.setdefault("inputs", {})
        recorded.update({
            "dump": os.path.basename(inputs["dump"]) if inputs["dump"]
            else None,
            "bundle_sha256": inputs.get("bundle_sha256"),
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
        "statement_timeout": getattr(args, "statement_timeout", 0),
        "province": _province(args),
        "accepted": accepted,
        "dev_target": dev_target,
        "dump": inputs["dump"],
        "documents": inputs["documents"],
        "properties": inputs["properties"],
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
    except ValueError:
        raise argparse.ArgumentTypeError(
            "expected a whole number of seconds, not {0!r}".format(text))
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
    try:
        return _guarded(lambda: _cmd_o19_preflight(argv),
                        o19_preflight.EXIT_TOOL_ERROR)
    except SystemExit as exc:
        if exc.code in (0, None):
            raise
        raise SystemExit(o19_preflight.EXIT_TOOL_ERROR)


def cmd_import_o19(argv) -> int:
    return _guarded(lambda: _cmd_import_o19(argv))


def _cmd_o19_preflight(argv) -> int:
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
    args = _parser("carlos-ctl import-o19", import_mode=True).parse_args(
        list(argv))
    if os.geteuid() != 0 and not args.mariadb_arg:
        die("this command needs root (or --mariadb-arg for a dev database)")

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
    log("import complete (experimental). Remaining operator steps:\n"
        "  1. review + apply the properties fragment (see report), then "
        "`carlos-ctl restart`\n"
        "  2. run `carlos-ctl backup full` (post-import snapshot)\n"
        "  3. TECHNICAL REVIEW before clinical use: {0}/report.txt, spot "
        "checks, UI smoke\n"
        "     roles: the 'roles:' report lines, privilege-diff.txt and "
        "roles-details.txt — confirm each custom role's privileges in "
        "Administration > Security, and expired or role-less accounts\n"
        "  4. then `carlos-ctl import-o19 --cleanup`".format(
            ctx["state_dir"]))
    return 0


def _make_ctx_for_cleanup(args) -> Dict:
    state_dir = STATE_DIR
    take_workspace_lock(state_dir)
    return {
        "state_dir": state_dir,
        "state": load_state(state_dir),
        "query": make_query(args.mariadb_arg),
        "dev_target": _dev_mode(args),
        "dry_run": getattr(args, "dry_run", False),
    }

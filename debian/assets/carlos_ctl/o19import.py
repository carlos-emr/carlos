# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""carlos-ctl import-o19 / o19-preflight — OSCAR 19 clinic import (experimental).

Phase pipeline (docs/oscar19-to-carlos-migration-plan.md §9a; each phase
records its completion + input digests in state.json under
/var/lib/carlos-emr/o19-import/). A rerun over existing state REQUIRES
--resume (or --cleanup); nothing is silently continued. Once the ETL has
started, the target is mid-import by design, so a resumed run re-checks
the schema/replica/disk gates but not the emptiness sweep P0 already
passed. Execution order on a real import is P0, P3, P1, P2, P4..P7: the
rollback snapshot exists before any clinic-supplied SQL executes. --dry-run
and the o19-preflight verb run P0 (capacity checks only for the latter),
P1 and P2 without recording a verdict:

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
  P4 etl             manifest-driven copy into the carlos schema      (M4)
  P5 documents       OscarDocument restore + reconciliation           (M5)
  P6 props           oscar.properties translation                     (M6)
  P7 verify          row parity + spot checks                         (M4+)

Every migration's output should receive a technical review — verification
report, spot checks, UI smoke — before clinical use.
"""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from typing import Callable, Dict, List, Optional

from . import dbops, o19_preflight, o19bundle, o19etl, o19map_schema
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
)

DUMP_COMPLETED_MARKER = b"-- Dump completed"
# statements a `mysqldump --databases/--all-databases` emits that would
# redirect the restore out of the staging schema, and a MySQL-only GTID
# directive the restricted staging account cannot execute anyway
DUMP_REDIRECT_MARKERS = (b"\nUSE ", b"\nCREATE DATABASE", b"\nuse ",
                         b"\ncreate database")
DUMP_GTID_MARKER = b"GTID_PURGED"
STAGING_USER = "o19_import"
SPOT_CHECK_PATIENTS = 10


# --------------------------------------------------------------------------
# state ledger
# --------------------------------------------------------------------------

def state_path(state_dir: str) -> str:
    return os.path.join(state_dir, "state.json")


def load_state(state_dir: str) -> Dict:
    try:
        with open(state_path(state_dir), encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return {"phases": {}, "accepted": [], "inputs": {}}


def save_state(state_dir: str, state: Dict) -> None:
    os.makedirs(state_dir, mode=0o700, exist_ok=True)
    tmp = state_path(state_dir) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(state, fh, indent=1, sort_keys=True)
    os.replace(tmp, state_path(state_dir))


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

def make_query(mariadb_args: Optional[List[str]]) -> Callable:
    """query(sql, db=None) -> rows. Default: root over the unix socket
    exactly like dbops.db_root; --mariadb-arg overrides the client argv
    tail for dev environments (and implies --dev-target)."""
    base = ["mariadb", "--protocol=socket", "--user=root"]
    if mariadb_args:
        base = ["mariadb"] + list(mariadb_args)

    def query(sql, db=None):
        argv = list(base) + list(CLIENT_COMMON_ARGS)
        if db:
            argv.append(db)
        # statements go through STDIN, never argv: /proc/<pid>/cmdline is
        # world-readable and some statements carry credentials
        cp = run(argv, input=sql, capture_output=True, errors="replace")
        if cp.returncode != 0:
            raise RuntimeError("SQL failed ({0}): {1}".format(
                sql[:80], cp.stderr.strip()))
        return batch_rows(cp.stdout)

    query.base_argv = base  # type: ignore[attr-defined]
    return query


# batch mode escapes \0 \t \n \\ inside values, so a bare "\r" (a CRLF
# eForm) is DATA; only "\n" separates rows — never str.splitlines()
CLIENT_COMMON_ARGS = ("--default-character-set=utf8mb4", "-N", "-B")


def batch_rows(stdout: str) -> List[List[str]]:
    lines = stdout.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return [line.split("\t") for line in lines]


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
    for table in sorted(counts):
        expected = seeds.get(table, 0)
        actual = counts[table]
        cls = o19map_schema.TABLES.get(table, {}).get("class")
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


def check_disk_headroom(dump_bytes: int, bundle_size: int,
                        documents_size: int = 0) -> Optional[str]:
    """None if fine, else a message. dump_bytes is the UNCOMPRESSED dump
    size: the database volume needs roughly 2.5x that (staging restore +
    the copy into the target + archive schema); the state volume needs
    the bundle expanded (x2) plus the documents tar extracted (x2)."""
    needs = (("database volume", "/var/lib/mysql", int(dump_bytes * 2.5)),
             ("state volume", STATE, bundle_size * 2 + documents_size * 2))
    for label, path, needed in needs:
        if needed <= 0:
            continue
        st = _statvfs_nearest(path)
        free = st.f_bavail * st.f_frsize
        if free < needed:
            return ("insufficient disk on {0} ({1}): {2} MB free, "
                    "~{3} MB needed".format(
                        label, path, free // 1048576, needed // 1048576))
    return None


def documents_expanded_size(tar_path: str) -> int:
    """Expanded footprint of the documents archive (sum of member sizes
    from the tar listing); the archive's own size is what a .tar.gz
    compresses PDFs to, not what the tree needs on disk. Falls back to
    the file size when the listing cannot be read (P5 reports why)."""
    flags = "-tvzf" if tar_path.endswith(".gz") else "-tvf"
    cp = run(["tar", flags, tar_path], capture_output=True)
    if cp.returncode != 0:
        warn("cannot list the documents archive ({0}); using its file size "
             "for the disk check".format(cp.stderr.strip()[:200]))
        return os.path.getsize(tar_path)
    return max(o19bundle.listed_size(cp.stdout.splitlines()),
               os.path.getsize(tar_path))


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

    # replicas double every byte of the ETL — refuse rather than surprise.
    try:
        replicas = query("SHOW REPLICA HOSTS")
    except RuntimeError:
        try:
            replicas = query("SHOW SLAVE HOSTS")
        except RuntimeError:
            replicas = []
    if replicas:
        die("this database server has replicas attached — the import's "
            "binlog-off bulk copy is not replica-safe. Detach them first.")

    stage = ctx["state"].get("phases", {}).get("stage", {})
    if not phase_done(ctx["state"], "stage"):
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
                                   docs_bytes)
    if headroom:
        die(headroom)


def run_p0(ctx) -> None:
    query = ctx["query"]
    dev = ctx["dev_target"]
    run_p0_capacity(ctx)

    if not dev:
        rc = dbops.run_flyway("validate")
        if rc != 0:
            die("flyway validate failed — the carlos schema does not match "
                "the deployed application (run carlos-ctl db-migrate first)")

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
    violations = pristine_violations(counts)
    identity_rows = query(
        "SELECT user_name FROM `{0}`.security".format(ctx["target_db"]))
    users = sorted(r[0] for r in identity_rows if r)
    if users != [o19map_schema.SEED_USER_NAME]:
        violations.append("security holds {0!r}, expected only the "
                          "'{1}' seed".format(
                              users, o19map_schema.SEED_USER_NAME))
    if violations:
        text = ("the import runs ONLY on a stock initial deploy; this "
                "database is not one:\n  " + "\n  ".join(violations[:25])
                + ("\n  ... and {0} more".format(len(violations) - 25)
                   if len(violations) > 25 else "")
                + "\nNo --accept flag clears this: provision a fresh "
                  "Flyway schema instead.")
        if dev:
            warn("DEV TARGET: pristine sweep downgraded to a warning:\n"
                 + text)
        else:
            die(text)
    report_append(ctx["state_dir"], "P0 check-pristine",
                  "target {0}: {1} copy/merge-class tables checked (copy: "
                  "exact seed rows or empty; merge: at least the reference "
                  "seeds); pristine={2}{3}{4}".format(
                      ctx["target_db"], len(counts), not violations,
                      " (DEV TARGET — sweep advisory only)" if dev else "",
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
    for marker in DUMP_REDIRECT_MARKERS:
        if marker in data:
            return ("the dump carries a {0} statement — it was taken with "
                    "--databases/--all-databases and would redirect the "
                    "restore at the live schema. Re-take it as "
                    "`mysqldump <o19-db> > o19.sql` (no --databases)"
                    .format(marker.strip().decode("ascii").upper()))
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


def staging_client_argv(base_argv: List[str], client_cnf: str) -> List[str]:
    """The restore client's argv: the connection tail of the root argv
    (socket/host/port), identity replaced by the throwaway staging account
    read from a 0600 defaults file (never argv), and --one-database so a
    statement addressed at another schema is skipped rather than run."""
    tail = strip_client_identity(list(base_argv)[1:])
    return (["mariadb", "--defaults-extra-file=" + client_cnf] + tail
            + ["--one-database",
               "--init-command=SET SESSION sql_log_bin=0, "
               "FOREIGN_KEY_CHECKS=0, UNIQUE_CHECKS=0, sql_mode=''",
               STAGING_SCHEMA])


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
        out.append("DROP USER IF EXISTS '{0}'@'{1}'".format(STAGING_USER, host))
        out.append("CREATE USER '{0}'@'{1}' IDENTIFIED BY '{2}'".format(
            STAGING_USER, host, pw))
        out.append("GRANT ALL PRIVILEGES ON `{0}`.* TO '{1}'@'{2}'".format(
            STAGING_SCHEMA, STAGING_USER, host))
    return out


def grant_staging_account(query, client_cnf: str) -> None:
    password = genpw()
    for sql in staging_account_statements(password):
        query(sql)
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
            query("DROP USER IF EXISTS '{0}'@'{1}'".format(STAGING_USER,
                                                          host))
    finally:
        if os.path.exists(client_cnf):
            os.unlink(client_cnf)


def _stream_dump(opener: List[str], restore_argv: List[str]):
    """Pipe the dump through the restore client, scanning every chunk for
    redirecting statements. Returns (source_rc, client_rc, tail_bytes,
    redirect_message_or_None)."""
    src = subprocess.Popen(opener, stdout=subprocess.PIPE)  # nosec B603
    sink = subprocess.Popen(restore_argv,                    # nosec B603
                            stdin=subprocess.PIPE)
    tail = b""
    carry = b"\n"
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
            redirect = dump_redirect_marker(carry + chunk)
            if redirect:
                broken = True
                break
            carry = chunk[-32:]
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
            log("stage: already restored this dump (sha256 match) — skipping")
            return
        if not ctx["restage"]:
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
    restore_argv = staging_client_argv(ctx["query"].base_argv, client_cnf)
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
    if src_rc != 0:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("reading the dump failed (corrupt archive?)")
    if rc != 0:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("restore into {0} failed — see the client error above. The "
            "restore runs as an account limited to that schema: a dump "
            "carrying DEFINER clauses, GRANTs or server-wide SET "
            "statements must be re-taken without them (mysqldump "
            "--skip-triggers --set-gtid-purged=OFF, no --databases)"
            .format(STAGING_SCHEMA))
    if DUMP_COMPLETED_MARKER not in tail:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("the dump has no '-- Dump completed' trailer — it is truncated "
            "or was interrupted; take a fresh mysqldump on the O19 server")

    n_tables = query("SELECT COUNT(*) FROM information_schema.TABLES "
                     "WHERE TABLE_SCHEMA = '{0}'".format(STAGING_SCHEMA))
    mark_done(ctx["state_dir"], ctx["state"], "stage", dump_sha256=dump_sha,
              uncompressed_bytes=ctx.get("dump_uncompressed"))
    report_append(ctx["state_dir"], "P1 stage",
                  "restored {0} ({1} tables) from {2}\nsha256 {3}".format(
                      STAGING_SCHEMA, n_tables[0][0],
                      os.path.basename(dump), dump_sha))


# --------------------------------------------------------------------------
# P2 — preflight (import mode)
# --------------------------------------------------------------------------

def run_p2(ctx) -> Dict:
    query = ctx["query"]

    def pf_query(sql):
        return query(sql, db=STAGING_SCHEMA)

    props = None
    if ctx.get("properties"):
        props = o19_preflight.parse_properties(ctx["properties"])
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
        die("preflight requires explicit sign-off — rerun with: {0}".format(
            " ".join("--accept " + a for a in report["required_accepts"])))
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
            "acknowledge with --accept no-pre-backup".format(BACKUP_ENV))
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


def make_etl_query(base_argv: List[str]) -> Callable:
    """Statement executor with the bulk-copy session prelude."""
    prelude = ("SET SESSION sql_log_bin=0, FOREIGN_KEY_CHECKS=0, "
               "UNIQUE_CHECKS=0, sql_mode=''")

    def query(sql):
        argv = list(base_argv) + ["--init-command=" + prelude] \
            + list(CLIENT_COMMON_ARGS)
        cp = run(argv, input=sql, capture_output=True, errors="replace")
        if cp.returncode != 0:
            raise RuntimeError("ETL statement failed ({0} ...): {1}".format(
                sql[:120], cp.stderr.strip()))
        return batch_rows(cp.stdout)

    return query


def _row_parity(ctx):
    """Parity with the exact break-glass delta (the admin identity the ETL
    recorded in its ledger)."""
    progress = o19etl.load_progress(ctx["state_dir"])
    return o19etl.row_parity(ctx["query"], STAGING_SCHEMA, ctx["target_db"],
                             admin_user=(progress.get("admin_user")
                                         or ctx.get("admin_user")),
                             admin_provider_no=progress.get(
                                 "admin_provider_no"))


def run_p4(ctx) -> None:
    if phase_done(ctx["state"], "etl"):
        log("etl: already complete — skipping")
        return
    ctx["query_etl"] = make_etl_query(ctx["query"].base_argv)
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
        die("row parity failed for {0} table(s) — see {1}/report.txt; "
            "nothing further runs until this is explained".format(
                len(bad), ctx["state_dir"]))
    mark_done(ctx["state_dir"], ctx["state"], "etl")
    log("etl complete — row parity clean for {0} copy tables"
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
    sample = [r[0] for r in query(
        "SELECT demographic_no FROM `{0}`.demographic ORDER BY RAND() "
        "LIMIT {1}".format(src, SPOT_CHECK_PATIENTS))]
    sample = [demo for demo in sample if demo.isdigit()]
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
                die("verification query failed: {0}".format(
                    str(exc).strip()[:300]))
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
    s_rows = {r[0]: (r[1], r[2]) for r in query(agg.format(src))}
    d_rows = {r[0]: (r[1], r[2]) for r in query(agg.format(dst))}
    if s_rows != d_rows:
        for year in sorted(set(s_rows) | set(d_rows)):
            if s_rows.get(year) != d_rows.get(year):
                problems.append(
                    "billing year {0}: staging {1} vs target {2}".format(
                        year, s_rows.get(year), d_rows.get(year)))
    else:
        lines.append("billing totals match for {0} fiscal year(s)"
                     .format(len(s_rows)))

    report_append(ctx["state_dir"], "P7 verify",
                  "\n".join(lines)
                  + ("\nFAILURES:\n  " + "\n  ".join(problems[:40])
                     if problems else "\nall checks passed"))
    if problems:
        die("verification FAILED ({0} problem(s)) — see {1}/report.txt. "
            "State is left in place for diagnosis; rollback is the "
            "pre-import restic snapshot.".format(
                len(problems), ctx["state_dir"]))
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
    log("dropping staging schema {0} (archive schema {1} is kept) ..."
        .format(STAGING_SCHEMA, ARCHIVE_SCHEMA))
    ctx["query"]("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
    for host in STAGING_ACCOUNT_HOSTS:
        ctx["query"]("DROP USER IF EXISTS '{0}'@'{1}'".format(STAGING_USER,
                                                             host))
    bundle_dir = os.path.join(ctx["state_dir"], "bundle")
    if os.path.isdir(bundle_dir):
        shutil.rmtree(bundle_dir)
    for name in ("etl-progress.json", ".stage-client.cnf"):
        path = os.path.join(ctx["state_dir"], name)
        if os.path.exists(path):
            os.unlink(path)
    report_append(ctx["state_dir"], "cleanup",
                  "staging schema and extracted bundle removed; "
                  "o19_archive and reports kept")
    archived = archive_state(ctx["state_dir"])
    if archived:
        log("run state archived as {0} — a later import starts from "
            "scratch (and meets the pristine gate)".format(archived))
    log("cleanup complete")


def archive_state(state_dir: str) -> Optional[str]:
    """Retire state.json so the finished run can never be --resume'd or
    mistaken for a fresh one; reports stay where they are."""
    path = state_path(state_dir)
    if not os.path.exists(path):
        return None
    target = path + ".completed-" + time.strftime("%Y%m%dT%H%M%S")
    os.replace(path, target)
    return os.path.basename(target)


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def _parser(prog: str, import_mode: bool) -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(
        prog=prog,
        description="OSCAR 19 clinic import (experimental). Migration "
                    "output should receive a technical review before "
                    "clinical use.")
    ap.add_argument("--bundle", metavar="FILE",
                    help="single handoff archive (.tar/.tar.gz/.tar.enc/"
                         ".tar.gz.enc) holding dump + documents tar + "
                         "properties at its root")
    ap.add_argument("--bundle-pass", metavar="SPEC",
                    help="openssl -pass spec for an .enc bundle "
                         "(file:PATH, env:VAR, fd:N, stdin; 'pass:' works "
                         "but lands in argv/history)")
    ap.add_argument("--bundle-cipher", default=o19bundle.DEFAULT_CIPHER)
    ap.add_argument("--bundle-openssl-opt", action="append", default=[],
                    metavar="OPT",
                    help="passthrough openssl option for legacy bundles "
                         "(repeatable), e.g. -md then md5")
    ap.add_argument("--dump", metavar="FILE",
                    help="mysqldump of the O19 database (.sql or .sql.gz)")
    ap.add_argument("--properties", metavar="FILE",
                    help="the clinic's deployed oscar.properties")
    ap.add_argument("--province", choices=["on", "bc"])
    ap.add_argument("--accept", action="append", default=[],
                    metavar="CLASS", choices=list(ACCEPT_CLASSES),
                    help="acknowledge a blocker class (repeatable): "
                         + ", ".join(ACCEPT_CLASSES))
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
        ap.add_argument("--dry-run", action="store_true",
                        help="run P0-P2 + reports only; no writes beyond "
                             "the throwaway staging schema")
        ap.add_argument("--resume", action="store_true",
                        help="continue a previous run from its recorded "
                             "state (required whenever state exists)")
        ap.add_argument("--cleanup", action="store_true",
                        help="after verify (or before the copy started): "
                             "drop staging, keep archive + reports")
    return ap


def _default_province() -> str:
    from . import config
    try:
        return config.load().province
    except SystemExit:
        return "on"


def _resolve_inputs(args, state_dir: str) -> Dict:
    """Bundle vs separate flags -> concrete file paths (+ digests)."""
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
        opened = o19bundle.open_bundle(
            args.bundle, os.path.join(state_dir, "bundle"),
            pass_spec=args.bundle_pass, cipher=args.bundle_cipher,
            openssl_opts=args.bundle_openssl_opt)
        if getattr(args, "skip_documents", False) and opened["documents"]:
            die("--skip-documents contradicts a bundle that CONTAINS a "
                "documents member — drop one of the two")
        return opened
    if args.bundle_pass or args.bundle_openssl_opt:
        die("--bundle-pass/--bundle-openssl-opt need --bundle")
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


def _make_ctx(args, import_mode: bool, state_dir: str = STATE_DIR) -> Dict:
    dev_target = _dev_mode(args)
    state = load_state(state_dir)
    os.makedirs(state_dir, mode=0o700, exist_ok=True)

    inputs = _resolve_inputs(args, state_dir)
    if getattr(args, "skip_documents", False) \
            and "no-documents" not in args.accept:
        die("--skip-documents requires --accept no-documents (the missing "
            "documents are a recorded sign-off, not a default)")
    if inputs["documents"] is None and import_mode \
            and not getattr(args, "skip_documents", False) \
            and not getattr(args, "cleanup", False):
        if "no-documents" not in args.accept:
            die("no documents tar in the inputs — pass --documents, or "
                "--skip-documents with --accept no-documents")

    accepted = sorted(set(args.accept) | set(state.get("accepted", [])))
    if not getattr(args, "dry_run", False) and import_mode:
        # sign-offs persist only from a real run: a dry run's --accept is
        # an experiment, not a recorded acknowledgement
        state["accepted"] = accepted
    state.setdefault("inputs", {}).update({
        "dump": os.path.basename(inputs["dump"]) if inputs["dump"] else None,
        "bundle_sha256": inputs.get("bundle_sha256"),
        "dev_target": dev_target,
        "schema_map_version": o19map_schema.SCHEMA_MAP_VERSION,
    })
    save_state(state_dir, state)

    return {
        "state_dir": state_dir,
        "state": state,
        "query": make_query(args.mariadb_arg),
        "province": args.province or _default_province(),
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
    }


def require_resume_for_existing_state(state: Dict, resume: bool,
                                      dry_run: bool) -> Optional[str]:
    """The message refusing a rerun over recorded state without --resume
    (None when the run may proceed). Pure, for the state tests."""
    # a staged dump alone is reusable (a dry run or assessment leaves it);
    # anything else recorded is a run in progress
    phases = set(state.get("phases", {})) - {"stage"}
    if not phases or resume or dry_run:
        return None
    done = ", ".join(sorted(phases))
    return ("a previous import left state behind ({0}). Pass --resume to "
            "continue it, or --cleanup after a verified import; state is "
            "never continued implicitly.".format(done))


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


def cmd_o19_preflight(argv) -> int:
    args = _parser("carlos-ctl o19-preflight", import_mode=False).parse_args(
        list(argv))
    if os.geteuid() != 0 and not args.mariadb_arg:
        die("this command needs root (or --mariadb-arg for a dev database)")
    ctx = _make_ctx(args, import_mode=False)
    # an assessment: capacity gates, stage, report — never a recorded
    # verdict or a persisted sign-off; the exit code IS the verdict
    ctx["dry_run"] = True
    if etl_started(ctx["state_dir"]):
        die("the target is mid-import (resume or clean up that run first)")
    run_p0_capacity(ctx)
    run_p1(ctx)
    report = run_p2(ctx)
    log("preflight verdict: {0} — report in {1}/preflight.txt".format(
        report["verdict"], ctx["state_dir"]))
    return int(report["exit_code"])


def cmd_import_o19(argv) -> int:
    args = _parser("carlos-ctl import-o19", import_mode=True).parse_args(
        list(argv))
    if os.geteuid() != 0 and not args.mariadb_arg:
        die("this command needs root (or --mariadb-arg for a dev database)")

    if args.cleanup:
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
    refusal = require_resume_for_existing_state(
        load_state(STATE_DIR), args.resume, args.dry_run)
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
        "  4. then `carlos-ctl import-o19 --cleanup`".format(
            ctx["state_dir"]))
    return 0


def _make_ctx_for_cleanup(args) -> Dict:
    state_dir = STATE_DIR
    return {
        "state_dir": state_dir,
        "state": load_state(state_dir),
        "query": make_query(args.mariadb_arg),
        "dev_target": _dev_mode(args),
        "dry_run": getattr(args, "dry_run", False),
    }

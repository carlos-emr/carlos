# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""carlos-ctl import-o19 / o19-preflight — OSCAR 19 clinic import (experimental).

Phase pipeline (docs/oscar19-to-carlos-migration-plan.md §9a; each phase
records its completion + input digests in state.json under
/var/lib/carlos-emr/o19-import/ and is resumable with --resume):

  P0 check-pristine  stock-initial-deploy gate (manifest-driven emptiness
                     sweep; hard refusal, no --accept; --dev-target
                     downgrades it to a warning for dev databases)
  P1 stage           restore the dump verbatim into the o19_import schema
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
from .util import BACKUP_ENV, STATE, die, genpw, genrandom, log, run, warn

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
        argv = list(base) + ["-N", "-B", "-e", sql]
        if db:
            argv.append(db)
        cp = run(argv, capture_output=True)
        if cp.returncode != 0:
            raise RuntimeError("SQL failed ({0}): {1}".format(
                sql[:80], cp.stderr.strip()))
        return [line.split("\t") for line in cp.stdout.splitlines()]

    query.base_argv = base  # type: ignore[attr-defined]
    return query


# --------------------------------------------------------------------------
# P0 — check-pristine
# --------------------------------------------------------------------------

def pristine_violations(counts: Dict[str, int]) -> List[str]:
    """Pure gate logic: live row counts vs the manifest's expectations.

    counts covers every copy-class table that exists on the target. Seeded
    tables must hold exactly their Flyway seed count; every other
    copy-class table must be empty. Returns human-readable violations.
    """
    violations = []
    seeds = o19map_schema.SEED_ROW_COUNTS
    for table in sorted(counts):
        expected = seeds.get(table, 0)
        actual = counts[table]
        if actual != expected:
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


def check_disk_headroom(dump_size: int, bundle_size: int) -> Optional[str]:
    """None if fine, else a message. Staging needs roughly dump x 2.5
    (uncompressed restore + archive schema), plus bundle extraction x 2."""
    needed = int(dump_size * 2.5) + bundle_size * 2
    for label, path in (("database volume", "/var/lib/mysql"),
                        ("state volume", STATE)):
        st = _statvfs_nearest(path)
        free = st.f_bavail * st.f_frsize
        if free < needed:
            return ("insufficient disk on {0} ({1}): {2} MB free, "
                    "~{3} MB needed".format(
                        label, path, free // 1048576, needed // 1048576))
    return None


def run_p0(ctx) -> None:
    query = ctx["query"]
    dev = ctx["dev_target"]

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

    headroom = check_disk_headroom(ctx["dump_size"], ctx.get("bundle_size", 0))
    if headroom:
        die(headroom)

    if not dev:
        rc = dbops.run_flyway("validate")
        if rc != 0:
            die("flyway validate failed — the carlos schema does not match "
                "the deployed application (run carlos-ctl db-migrate first)")

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
                  "target {0}: {1} copy-class tables checked; "
                  "pristine={2}{3}".format(
                      ctx["target_db"], len(counts), not violations,
                      " (DEV TARGET — sweep advisory only)" if dev else ""))


# --------------------------------------------------------------------------
# P1 — stage
# --------------------------------------------------------------------------

def head_collations(head: bytes) -> List[str]:
    import re
    return sorted(set(
        m.decode("ascii", "replace")
        for m in re.findall(rb"COLLATE[= ]([A-Za-z0-9_]+)", head[:65536])))


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

    log("staging dump into {0} (binlog off, throwaway schema) ..."
        .format(STAGING_SCHEMA))
    query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
    query("CREATE DATABASE `{0}`".format(STAGING_SCHEMA))

    restore_argv = list(ctx["query"].base_argv) + [
        "--init-command=SET SESSION sql_log_bin=0, FOREIGN_KEY_CHECKS=0, "
        "UNIQUE_CHECKS=0, sql_mode=''",
        STAGING_SCHEMA,
    ]
    src = subprocess.Popen(opener, stdout=subprocess.PIPE)  # nosec B603
    sink = subprocess.Popen(restore_argv,                    # nosec B603
                            stdin=subprocess.PIPE)
    tail = b""
    try:
        while True:
            chunk = src.stdout.read(1 << 20)
            if not chunk:
                break
            sink.stdin.write(chunk)
            tail = (tail + chunk)[-8192:]
    finally:
        src.stdout.close()
        sink.stdin.close()
    src.wait()
    rc = sink.wait()
    if src.returncode != 0:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("reading the dump failed (corrupt archive?)")
    if rc != 0:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("restore into {0} failed — see the client error above"
            .format(STAGING_SCHEMA))
    if DUMP_COMPLETED_MARKER not in tail:
        query("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
        die("the dump has no '-- Dump completed' trailer — it is truncated "
            "or was interrupted; take a fresh mysqldump on the O19 server")

    n_tables = query("SELECT COUNT(*) FROM information_schema.TABLES "
                     "WHERE TABLE_SCHEMA = '{0}'".format(STAGING_SCHEMA))
    mark_done(ctx["state_dir"], ctx["state"], "stage", dump_sha256=dump_sha)
    report_append(ctx["state_dir"], "P1 stage",
                  "restored {0} ({1} tables) from {2}\nsha256 {3}".format(
                      STAGING_SCHEMA, n_tables[0][0],
                      os.path.basename(dump), dump_sha))


# --------------------------------------------------------------------------
# P2 — preflight (import mode)
# --------------------------------------------------------------------------

def run_p2(ctx) -> None:
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
        return
    if report["verdict"] == "no-go":
        die("preflight verdict: no-go — remediate the blockers above "
            "(full report: {0}/preflight.txt)".format(ctx["state_dir"]))
    if report["verdict"] == "go-with-acknowledgements":
        die("preflight requires explicit sign-off — rerun with: {0}".format(
            " ".join("--accept " + a for a in report["required_accepts"])))
    mark_done(ctx["state_dir"], ctx["state"], "preflight",
              verdict=report["verdict"],
              acknowledged=report["acknowledged"])


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
        argv = list(base_argv) + [
            "--init-command=" + prelude, "-N", "-B", "-e", sql]
        cp = run(argv, capture_output=True)
        if cp.returncode != 0:
            raise RuntimeError("ETL statement failed ({0} ...): {1}".format(
                sql[:120], cp.stderr.strip()))
        return [line.split("\t") for line in cp.stdout.splitlines()]

    return query


def run_p4(ctx) -> None:
    if phase_done(ctx["state"], "etl"):
        log("etl: already complete — skipping")
        return
    ctx["query_etl"] = make_etl_query(ctx["query"].base_argv)
    ctx["src_schema"] = STAGING_SCHEMA
    ctx["archive_schema"] = ARCHIVE_SCHEMA
    ctx["report"] = lambda body: report_append(ctx["state_dir"], "P4 etl",
                                               body)
    log("etl: copying clinic data into '{0}' (manifest {1}) ..."
        .format(ctx["target_db"], o19map_schema.SCHEMA_MAP_VERSION))
    try:
        o19etl.run_etl(ctx, _make_password_hash)
    except RuntimeError as exc:
        die("ETL aborted: {0}\nFix the cause and re-run with --resume — "
            "chunked tables continue from their checkpoint.".format(exc))

    ok, bad = o19etl.row_parity(ctx["query"], STAGING_SCHEMA,
                                ctx["target_db"])
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
# cleanup
# --------------------------------------------------------------------------

def run_cleanup(ctx) -> None:
    state = ctx["state"]
    if not (phase_done(state, "verify") or ctx["dry_run"]
            or ctx["dev_target"]):
        die("--cleanup is allowed only after verify has passed (or on a "
            "--dry-run/--dev-target workspace)")
    log("dropping staging schema {0} (archive schema {1} is kept) ..."
        .format(STAGING_SCHEMA, ARCHIVE_SCHEMA))
    ctx["query"]("DROP DATABASE IF EXISTS `{0}`".format(STAGING_SCHEMA))
    bundle_dir = os.path.join(ctx["state_dir"], "bundle")
    if os.path.isdir(bundle_dir):
        shutil.rmtree(bundle_dir)
    progress = os.path.join(ctx["state_dir"], "etl-progress.json")
    if os.path.exists(progress):
        os.unlink(progress)
    report_append(ctx["state_dir"], "cleanup",
                  "staging schema and extracted bundle removed; "
                  "o19_archive and reports kept")
    log("cleanup complete")


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
    ap.add_argument("--mariadb-arg", action="append", default=None,
                    metavar="ARG",
                    help="DEV ONLY: override the mariadb client argv "
                         "(repeatable); implies --dev-target")
    ap.add_argument("--dev-target", action="store_true",
                    help="DEV ONLY: downgrade the stock-deploy pristine "
                         "gate to a warning")
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
        ap.add_argument("--resume", action="store_true")
        ap.add_argument("--restage", action="store_true",
                        help="drop and re-restore the staging schema")
        ap.add_argument("--cleanup", action="store_true",
                        help="after verify: drop staging, keep archive + "
                             "reports")
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


def _make_ctx(args, import_mode: bool, state_dir: str = STATE_DIR) -> Dict:
    dev_target = bool(args.dev_target or args.mariadb_arg)
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
        "bundle_size": (os.path.getsize(args.bundle)
                        if getattr(args, "bundle", None) else 0),
        "target_db": _target_db(),
        "restage": getattr(args, "restage", False),
        "dry_run": getattr(args, "dry_run", False),
        "admin_user": getattr(args, "admin_user", None),
    }


def _target_db() -> str:
    from . import config
    try:
        return config.load().db_name
    except (SystemExit, AttributeError):
        return "oscar"  # the deb deployment's CARLOS_DB_NAME default


def cmd_o19_preflight(argv) -> int:
    args = _parser("carlos-ctl o19-preflight", import_mode=False).parse_args(
        list(argv))
    if os.geteuid() != 0 and not args.mariadb_arg:
        die("this command needs root (or --mariadb-arg for a dev database)")
    ctx = _make_ctx(args, import_mode=False)
    run_p1(ctx)
    run_p2(ctx)
    log("preflight passed — verdict recorded in {0}".format(ctx["state_dir"]))
    return 0


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

    ctx = _make_ctx(args, import_mode=True)
    log("import-o19 (experimental) — manifest {0}, province {1}{2}".format(
        o19map_schema.SCHEMA_MAP_VERSION, ctx["province"],
        ", DEV TARGET" if ctx["dev_target"] else ""))

    run_p0(ctx)
    run_p1(ctx)
    run_p2(ctx)
    if args.dry_run:
        log("dry run complete — reports in {0}; nothing was written beyond "
            "the throwaway staging schema".format(ctx["state_dir"]))
        return 0
    run_p3(ctx)
    run_p4(ctx)
    run_p5(ctx)
    # P6 (props) lands in the next milestone; stopping here leaves data
    # and documents fully imported, with properties translation to come.
    die("the props/verify phases are not built yet (milestone M6); "
        "staging, preflight, backup, the data copy and the documents "
        "restore completed — rerun with --resume once the next milestone "
        "lands", code=3)
    return 3


def _make_ctx_for_cleanup(args) -> Dict:
    state_dir = STATE_DIR
    return {
        "state_dir": state_dir,
        "state": load_state(state_dir),
        "query": make_query(args.mariadb_arg),
        "dev_target": bool(args.dev_target or args.mariadb_arg),
        "dry_run": getattr(args, "dry_run", False),
    }

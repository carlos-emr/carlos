# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Database operations: the raw client, least-privilege accounts, Flyway.

Counterpart of carlos-podman carlos_ctl/dbops.py, with the same verb
contracts where the concept exists in both deployments:

  db          raw mariadb client passthrough as root (interactive shell with
              no args; args/redirects pass through) — identical semantics to
              the podman tool, minus the container hop.
  db-users    create-or-update the least-privilege accounts. Same name; here
              the set is carlos/drugref/backup (no exporter — there is no
              obs pod to feed).
  db-migrate  apply pending Flyway migrations.
  db-dump     consistent dump to stdout.

Administrative access is root@localhost over the unix socket: on Ubuntu that
account authenticates by uid, so root on the host is already root in the
database and NO password is stored anywhere for it — which is why this
package never asks for or records a MariaDB root password.
"""

import glob
import os
import subprocess
import sys

from . import config, util
from .util import (
    BACKUP_ENV, CONF_DIR, DRUGREF_PROPERTIES, PROPERTIES, SHARE, STATE, WEBAPP,
    die, env_get, genpw, genrandom, log, need_root, prop_escape, prop_get,
    prop_set, prop_unescape, run, warn,
)

_MARIADB = ["mariadb", "--protocol=socket", "--user=root"]


def db_root(args, **kw) -> subprocess.CompletedProcess:
    return run(_MARIADB + args, **kw)


def db_root_ok() -> bool:
    return db_root(["-N", "-B", "-e", "SELECT 1"], capture_output=True).returncode == 0


def require_db_root() -> None:
    if not db_root_ok():
        die("cannot reach MariaDB as root over the unix socket. Is mariadb running "
            "(systemctl status mariadb), and are you root?")


def sql_escape(value: str) -> str:
    """SQL string-literal escaping for generated passwords interpolated into
    account DDL. Belt and braces: everything this tool generates is
    alphanumeric already."""
    return value.replace("\\", "\\\\").replace("'", "\\'")


# --- raw client passthrough (verb: db) --------------------------------------

def cmd_db(argv) -> int:
    """mariadb shell on the EMR database as root: interactive with no args, or
    pass client args/redirects through — `carlos-ctl db -e 'SELECT ...'`,
    `carlos-ctl db < file.sql`. Same contract as the podman carlos-ctl."""
    need_root("db")
    require_db_root()
    s = config.load()
    os.execvp("mariadb", _MARIADB + [s.db_name] + list(argv))
    raise AssertionError("unreachable: execvp replaces the process")


def cmd_db_dump(argv) -> int:
    """Consistent dump to STDOUT, never to a default path: a dump is the whole
    clinical record in one file and the operator must choose, and secure,
    where it lands."""
    need_root("db-dump")
    require_db_root()
    s = config.load()
    os.execvp("mariadb-dump", [
        "mariadb-dump", "--protocol=socket", "--user=root",
        "--single-transaction", "--hex-blob", "--routines", "--events",
        "--triggers", "--no-tablespaces", "--default-character-set=utf8mb4",
        s.db_name,
    ])
    raise AssertionError("unreachable: execvp replaces the process")


# Passwords that ship in the upstream source trees (carlos.properties in the
# WAR, drugref2's defaults). Treated as "not set" everywhere a password is
# re-used, so they can never become a live credential.
UPSTREAM_PLACEHOLDER_PASSWORDS = frozenset({"liyi", "yessum", "xxxx", "password", "changeme"})


# --- least-privilege accounts (verb: db-users) ------------------------------

def _refuse_while_backup_runs(what: str) -> None:
    """ALTER USER or a MariaDB restart under a live backup fails the run
    with an alert blaming the credential or the server — a misdiagnosis
    that costs the operator an evening. The window is minutes long and the
    answer is simply to wait."""
    for unit in ("carlos-emr-backup.service", "carlos-emr-backup-verify.service"):
        # NOT `is-active --quiet`: a RUNNING Type=oneshot unit reports
        # ActiveState=activating, for which is-active exits 3 — the guard
        # was inert during the exact window it exists for (live-verified
        # on systemd 259).
        state = run(["systemctl", "show", "-p", "ActiveState", "--value", unit],
                    capture_output=True).stdout.strip()
        if state in ("active", "activating"):
            die(f"{unit} is running right now; {what} would fail it mid-flight "
                "with a misleading alert. Wait for it to finish (systemctl status "
                f"{unit}) or stop it first, then re-run.")


def cmd_db_users(argv) -> int:
    need_root("db-users")
    require_db_root()
    force_new = "--new-passwords" in argv
    for a in argv:
        if a != "--new-passwords":
            die(f"unknown option: {a}")
    s = config.load()

    # Re-use existing passwords so a reconfigure or upgrade never invalidates
    # the credential a running service is holding. But NEVER adopt the
    # placeholder passwords published in the upstream source repositories:
    # the properties skeleton is taken from the built WAR, so on a fresh
    # install "existing" is upstream's world-known default, and reusing it
    # would provision the live database account with a password anyone can
    # read on GitHub. Review finding, confirmed on a live install.
    def _usable(pw: str) -> str:
        return "" if pw in UPSTREAM_PLACEHOLDER_PASSWORDS else pw

    def _keep(pw: str) -> str:
        return "" if force_new else _usable(pw)

    app_pw = _keep(prop_unescape(prop_get(PROPERTIES, "db_password") or "")) or genpw()
    drugref_pw = _keep(prop_unescape(prop_get(DRUGREF_PROPERTIES, "db_password") or "")) or genpw()
    backup_pw = _keep(env_get(BACKUP_ENV, "CARLOS_BACKUP_DB_PASSWORD") or "") or genpw()
    import re as _re
    verify_db = env_get(BACKUP_ENV, "CARLOS_BACKUP_VERIFY_DB") or "carlos_restore_drill"
    # Same rule as the settings loader applies to CARLOS_DB_NAME — one
    # identifier policy, not two subtly different ones.
    if not _re.fullmatch(r"[A-Za-z0-9_]+", verify_db):
        die(f"CARLOS_BACKUP_VERIFY_DB ('{verify_db}') must be a plain identifier (A-Za-z0-9_)")
    # The drill DROPs every table in this schema and reloads it from the
    # dump. The read-only contract on the live database holds ONLY because
    # the grant below is scoped to a throwaway schema — this check is the
    # enforcement of that. Without it, verify_db=oscar armed the weekly
    # drill to roll the live clinical record back to last night's backup,
    # with ALL PRIVILEGES granted here making it possible.
    if verify_db.lower() in (s.db_name.lower(), "drugref2", "mysql",
                             "information_schema", "performance_schema", "sys"):
        die(f"CARLOS_BACKUP_VERIFY_DB ('{verify_db}') must be a THROWAWAY schema — "
            "the restore drill drops and reloads it. It can never be the live "
            "database, drugref2, or a system schema.")

    # Preflight the credential files BEFORE any SQL runs: the ALTER USER
    # below invalidates the live password, so discovering only afterwards
    # that carlos.properties is missing or read-only (traceback, partial
    # write) would leave the application locked out with no record of the
    # new credential.
    if not os.path.isfile(PROPERTIES):
        die(f"{PROPERTIES} does not exist — reinstall carlos-emr or restore it from backup "
            "before provisioning accounts")
    for path in [PROPERTIES] + [q for q in (DRUGREF_PROPERTIES, BACKUP_ENV) if os.path.isfile(q)]:
        if not os.access(path, os.W_OK):
            die(f"{path} is not writable; refusing to rotate credentials it must record")

    prev_app_pw = prop_unescape(prop_get(PROPERTIES, "db_password") or "")

    log("provisioning databases and least-privilege accounts")
    sql = f"""
-- Account and grant DDL must NOT ride the binary log. The backup's dump and
-- replay legs exclude the mysql schema, so a point-in-time restore never
-- re-applies credentials — but that contract only holds if provisioning
-- stays out of the binlog too; otherwise a windowed restore would replay an
-- ALTER USER and rewind the application's password to a stale generation.
SET SESSION sql_log_bin = 0;

CREATE DATABASE IF NOT EXISTS `{s.db_name}`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `{verify_db}`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- The application account: ALL PRIVILEGES on its own schema and nothing
-- anywhere else — no view of other databases, no FILE, no PROCESS, no SUPER.
CREATE USER IF NOT EXISTS 'carlos'@'localhost' IDENTIFIED BY '{sql_escape(app_pw)}';
CREATE USER IF NOT EXISTS 'carlos'@'127.0.0.1' IDENTIFIED BY '{sql_escape(app_pw)}';
ALTER USER 'carlos'@'localhost' IDENTIFIED BY '{sql_escape(app_pw)}';
ALTER USER 'carlos'@'127.0.0.1' IDENTIFIED BY '{sql_escape(app_pw)}';
GRANT ALL PRIVILEGES ON `{s.db_name}`.* TO 'carlos'@'localhost';
GRANT ALL PRIVILEGES ON `{s.db_name}`.* TO 'carlos'@'127.0.0.1';

-- DrugRef gets rights on the drug reference schema only: on a host where one
-- MariaDB serves both, a drug-lookup service must not hold a credential that
-- can read the patient schema. (Scoped ALL, not read-only: the in-app DPD
-- refresh pipeline INSERTs/UPDATEs and rebuilds tables inside drugref2.)
CREATE USER IF NOT EXISTS 'drugref'@'localhost' IDENTIFIED BY '{sql_escape(drugref_pw)}';
CREATE USER IF NOT EXISTS 'drugref'@'127.0.0.1' IDENTIFIED BY '{sql_escape(drugref_pw)}';
ALTER USER 'drugref'@'localhost' IDENTIFIED BY '{sql_escape(drugref_pw)}';
ALTER USER 'drugref'@'127.0.0.1' IDENTIFIED BY '{sql_escape(drugref_pw)}';

-- The backup account is READ-ONLY on the clinical schema:
--   SELECT, SHOW VIEW, TRIGGER, EVENT   what mariadb-dump reads
--   RELOAD                              FLUSH BINARY LOGS (nightly rotation)
--   REPLICATION CLIENT                  the binlog coordinates the dump records
--   REPLICATION SLAVE                   stream closed binlogs off the server,
--                                       so the job never reads the 0700
--                                       mysql-owned datadir
-- Deliberately NOT PROCESS: a leak of backup.env must not let anyone watch
-- clinicians' live SQL (SHOW PROCESSLIST exposes bound PHI). mariadb-dump
-- runs with --no-tablespaces so it never needs it.
CREATE USER IF NOT EXISTS 'backup'@'localhost' IDENTIFIED BY '{sql_escape(backup_pw)}';
CREATE USER IF NOT EXISTS 'backup'@'127.0.0.1' IDENTIFIED BY '{sql_escape(backup_pw)}';
ALTER USER 'backup'@'localhost' IDENTIFIED BY '{sql_escape(backup_pw)}';
ALTER USER 'backup'@'127.0.0.1' IDENTIFIED BY '{sql_escape(backup_pw)}';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON `{s.db_name}`.* TO 'backup'@'localhost';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON `{s.db_name}`.* TO 'backup'@'127.0.0.1';
GRANT RELOAD, REPLICATION CLIENT, REPLICATION SLAVE ON *.* TO 'backup'@'localhost';
GRANT RELOAD, REPLICATION CLIENT, REPLICATION SLAVE ON *.* TO 'backup'@'127.0.0.1';
-- Write rights on the restore-drill scratch database ONLY: the drill creates
-- and drops tables there, and scoping that to a throwaway schema is what
-- stops a mistyped drill from touching the live one.
GRANT ALL PRIVILEGES ON `{verify_db}`.* TO 'backup'@'localhost';
GRANT ALL PRIVILEGES ON `{verify_db}`.* TO 'backup'@'127.0.0.1';

-- mysql_secure_installation-equivalent hygiene: drop anonymous accounts
-- explicitly rather than trusting a packaging default. The `test` database is
-- deliberately NOT dropped — provisioning stays additive, and a migrated
-- legacy install can arrive with real data in a schema of that name.
DELETE FROM mysql.global_priv WHERE User='';
FLUSH PRIVILEGES;
"""
    cp = db_root([], input=sql, capture_output=True)
    if cp.returncode != 0:
        die(f"account provisioning failed:\n{cp.stderr.strip()}")

    prop_set(PROPERTIES, "db_password", prop_escape(app_pw))
    prop_set(PROPERTIES, "db_username", "carlos")
    if app_pw != prev_app_pw and \
            run(["systemctl", "is-active", "--quiet", "carlos-emr.service"]).returncode == 0:
        # The running JVM read its properties at deploy time: the account
        # password just changed underneath it (e.g. a placeholder was
        # replaced), and new pool connections will start failing. Silence
        # here turned a security fix into a slow outage.
        warn("the application is RUNNING with the previous database credential —")
        warn("restart it now: carlos-ctl restart")
    if os.path.isfile(DRUGREF_PROPERTIES):
        prop_set(DRUGREF_PROPERTIES, "db_password", prop_escape(drugref_pw))
        prop_set(DRUGREF_PROPERTIES, "db_user", "drugref")
    if os.path.isfile(BACKUP_ENV):
        # env_set appends when an operator deleted the line: the ALTER USER
        # above has already rotated the account, so a substitute-only write
        # would leave backup.env without the one password that now works.
        util.env_set(BACKUP_ENV, "CARLOS_BACKUP_DB_PASSWORD", backup_pw)
        # And the database NAME stays in lockstep with CARLOS_DB_NAME: the
        # grants above target s.db_name, so a site running a non-default name
        # would otherwise have a backup account authorized for one schema
        # while the dump targeted the skeleton default.
        util.env_set(BACKUP_ENV, "CARLOS_BACKUP_DB_NAME", s.db_name)
    log(f"accounts provisioned: carlos (read/write on {s.db_name}), drugref, backup (read-only)")
    return 0


def cmd_rotate(argv) -> int:
    """Rotate every generated database password and restart the application.
    Same verb name as the podman tool's credential rotation."""
    need_root("rotate")
    require_db_root()
    _refuse_while_backup_runs("rotating the database credentials")
    log("rotating database passwords")
    # The passwords are forced fresh INSIDE db-users, not by blanking the
    # credential files first: a failure between the old blanking step and
    # the provisioning left all three files EMPTY while the database kept
    # the old passwords — the worst possible record. Now a failure at any
    # point leaves the files holding complete (at worst stale) credentials,
    # and the recovery for every partial state is the same: re-run rotate.
    cmd_db_users(["--new-passwords"])
    if run(["systemctl", "restart", "carlos-emr.service"]).returncode != 0:
        # The new credential is provisioned and written out, but the service
        # did not come back — saying "restarted" here would hide an outage.
        die("passwords rotated, but carlos-emr FAILED to restart — "
            "run 'journalctl -u carlos-emr -n 50'")
    log("rotated; carlos-emr restarted")
    return 0


# --- Flyway (verbs: db-migrate / db-info / db-validate / db-baseline / db-repair)

def _is_java_21(home: str) -> bool:
    """The JDK's own release file names the version without spawning a JVM."""
    try:
        with open(os.path.join(home, "release"), encoding="utf-8") as fh:
            return any(line.startswith('JAVA_VERSION="21') for line in fh)
    except OSError:
        return False


def _find_java() -> str:
    """Java 21, VERIFIED, not merely a java binary: the migration engine and
    JDBC driver come out of the deployed WAR (class file version 65) and a
    default-java pointing at 17 or 25 fails in class-loading shapes rather
    than with a clean message."""
    for d in sorted(glob.glob("/usr/lib/jvm/java-21-openjdk-*")) + \
             ["/usr/lib/jvm/java-21-openjdk", "/usr/lib/jvm/default-java"]:
        if _is_java_21(d) and os.access(os.path.join(d, "bin", "java"), os.X_OK):
            return os.path.join(d, "bin", "java")
    die("no Java 21 runtime found; install openjdk-21-jre-headless")


def run_flyway(command: str) -> int:
    """The engine, driver and migration files all come from the deployed WAR:
    nothing else guarantees the history this writes is the history the
    application's boot gate validates against."""
    s = config.load()
    if not os.path.isdir(os.path.join(WEBAPP, "WEB-INF", "lib")):
        die(f"{WEBAPP} is not an exploded CARLOS webapp")
    user = (prop_get(PROPERTIES, "db_username") or "").strip()
    if not user:
        die(f"db_username missing from {PROPERTIES} — run 'carlos-ctl db-users'")
    password = prop_unescape(prop_get(PROPERTIES, "db_password") or "")
    env = dict(os.environ)
    # Credentials through the environment, never argv: /proc/<pid>/cmdline is
    # world-readable and this connects to a database holding PHI.
    env.update(
        FLYWAY_URL=f"jdbc:mysql://{s.db_host}:{s.db_port}/{s.db_name}"
                   "?useSSL=false&allowPublicKeyRetrieval=true",
        FLYWAY_USER=user,
        FLYWAY_PASSWORD=password,
    )
    cp = run([
        _find_java(),
        "-cp",
        f"{SHARE}/lib/carlos-flyway-runner.jar:{WEBAPP}/WEB-INF/classes:{WEBAPP}/WEB-INF/lib/*",
        "io.github.carlos_emr.carlos.deb.FlywayRunner", command, s.flyway_locations,
    ], env=env)
    return cp.returncode


def cmd_db_migrate(argv) -> int:
    need_root("db-migrate")
    warn("apply migrations only AFTER a backup you have verified. DDL is not "
         "transactional in MariaDB; a failed migration can leave a partial schema.")
    return run_flyway("migrate")


def make_flyway_cmd(action: str):
    def _cmd(argv) -> int:
        need_root(f"db-{action}")
        return run_flyway(action)
    return _cmd


# --- runtime settings (verb: db-apply-settings) -----------------------------

def _global(var: str) -> str:
    cp = db_root(["-N", "-B", "-e", f"SELECT @@GLOBAL.{var}"], capture_output=True)
    return cp.stdout.strip() if cp.returncode == 0 else "?"


def cmd_db_apply_settings(argv) -> int:
    """Restart MariaDB if — and only if — its RUNNING values disagree with the
    packaged drop-in. The drop-in is read at server start, and mariadb-server
    is already running when this package configures, so without this the
    deployment silently runs on distribution defaults: STRICT sql_mode (which
    the OSCAR-lineage app does not expect) and no binary log (no PITR, and a
    failing nightly backup). Bouncing somebody's database is not done
    casually, hence the compare-first."""
    need_root("db-apply-settings")
    require_db_root()
    _refuse_while_backup_runs("restarting MariaDB")

    # --- timezone alignment (order is load-tables THEN drop-in: a named
    # default-time-zone with EMPTY tz tables fails server startup, so the
    # drop-in is only ever written after the tables provably exist) --------
    #
    # The JVM is pinned to CARLOS_TZ; without this, mariadbd stayed on the
    # host zone (usually UTC on cloud images) and NOW()/CURDATE() disagreed
    # with the application's clock — mixed-timezone clinical timestamps.
    tz = util.env_get(util.ENV_FILE, "CARLOS_TZ") or ""
    tz_dropin = "/etc/mysql/mariadb.conf.d/61-carlos-emr.cnf"

    def _tz_resolves(zone: str) -> bool:
        # The one test that matters: can the SERVER actually resolve this
        # zone? A row-count guard treated a KILLED half-loaded table set as
        # "loaded", then wrote a default-time-zone the server cannot start
        # with. CONVERT_TZ returns NULL for an unknown zone.
        cp = db_root(["-N", "-B", "-e",
                      f"SELECT CONVERT_TZ('2026-01-01 00:00:00','UTC','{sql_escape(zone)}') IS NOT NULL"],
                     capture_output=True)
        return cp.returncode == 0 and cp.stdout.strip() == "1"

    # binlog_ignore_db is ADDITIVE across option files: the packaged 60-
    # keeps the default drill schema out of the binlog, and a generated line
    # adds a renamed one. A custom CARLOS_BACKUP_VERIFY_DB otherwise made
    # every db-apply-settings restart MariaDB for a value no restart applies.
    verify_db_now = env_get(BACKUP_ENV, "CARLOS_BACKUP_VERIFY_DB") or "carlos_restore_drill"
    extra = "" if verify_db_now == "carlos_restore_drill" else f"binlog_ignore_db = {verify_db_now}\n"

    tz_ok = bool(tz) and os.path.isfile(f"/usr/share/zoneinfo/{tz}")
    if tz_ok and not _tz_resolves(tz):
        log("loading the timezone tables into MariaDB (a few seconds; also repairs a partial load)")
        tzsql = run(["mariadb-tzinfo-to-sql", "/usr/share/zoneinfo"], capture_output=True)
        if tzsql.returncode != 0:
            die("mariadb-tzinfo-to-sql failed; cannot align the database timezone")
        cp = db_root(["mysql"], input="SET SESSION sql_log_bin=0;" + chr(10) + tzsql.stdout,
                     capture_output=True)
        if cp.returncode != 0:
            die(f"loading the timezone tables failed:\n{cp.stderr.strip()}")
    if tz_ok and not _tz_resolves(tz):
        # Loading did not help (truly unknown zone). Fall through to the
        # NOT-aligned branch: a drop-in the server cannot start with must
        # never be written, AND a stale one from a previously-good CARLOS_TZ
        # must be removed so the server does not keep an outdated zone while
        # the JVM has moved on.
        warn(f"MariaDB still cannot resolve '{tz}' after loading the timezone tables; "
             "NOT aligning MariaDB to it")
        tz_ok = False

    if tz_ok:
        # default-time-zone is written ONLY on this branch, where tz provably
        # resolves — never with an empty value.
        want_dropin = (f"# Generated by carlos-ctl db-apply-settings — kept in step with\n"
                       f"# CARLOS_TZ in /etc/carlos-emr/carlos-emr.env (and the drill schema\n"
                       f"# in backup.env). Do not edit here.\n"
                       f"[mariadbd]\ndefault-time-zone = {tz}\n{extra}")
    elif extra:
        # No timezone alignment, but a custom drill schema still needs its
        # additive binlog_ignore_db line.
        want_dropin = (f"# Generated by carlos-ctl db-apply-settings — the drill schema\n"
                       f"# in backup.env. Do not edit here.\n"
                       f"[mariadbd]\n{extra}")
    else:
        # Nothing to generate: the drop-in must not exist.
        want_dropin = None
        if tz and not tz_ok:
            warn(f"CARLOS_TZ='{tz}' is not a known timezone; NOT aligning MariaDB to it")

    have = None
    try:
        with open(tz_dropin, encoding="utf-8") as fh:
            have = fh.read()
    except OSError:
        pass
    if want_dropin is None:
        if os.path.exists(tz_dropin):
            os.unlink(tz_dropin)
            log(f"removed {tz_dropin} (no MariaDB alignment needed)")
    elif have != want_dropin:
        # 0644: mariadbd (a distinct uid) must read it. A tightened operator
        # umask would otherwise leave it unreadable and the alignment inert.
        fd = os.open(tz_dropin, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o644)
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            fh.write(want_dropin)
        os.chmod(tz_dropin, 0o644)
        log(f"rendered {tz_dropin}")
    # short-lived earlier name for the same generated file
    if os.path.exists("/etc/mysql/mariadb.conf.d/61-carlos-emr-tz.cnf"):
        os.unlink("/etc/mysql/mariadb.conf.d/61-carlos-emr-tz.cnf")

    # SELECT @@GLOBAL renders booleans as 1/0; accept either spelling.
    checks = {"log_bin": ("1", "ON"), "sql_mode": ("",),
              "character_set_server": ("utf8mb4",), "bind_address": ("127.0.0.1",)}
    if tz and os.path.isfile(f"/usr/share/zoneinfo/{tz}"):
        checks["time_zone"] = (tz,)
    stale = [v for v, want in checks.items() if _global(v) not in want]
    # binlog_ignore_db is a server option, not a system variable: it shows
    # up in SHOW MASTER STATUS, so it gets its own probe. It keeps the
    # weekly drill's full-database load out of the binlogs.
    ign = db_root(["-N", "-B", "-e", "SHOW MASTER STATUS"], capture_output=True)
    cols = ign.stdout.rstrip("\n").split("\t") if ign.returncode == 0 else []
    ign_val = cols[3] if len(cols) > 3 else ""
    verify_db = env_get(BACKUP_ENV, "CARLOS_BACKUP_VERIFY_DB") or "carlos_restore_drill"
    if verify_db not in ign_val.split(","):
        stale.append("binlog_ignore_db")
        checks["binlog_ignore_db"] = (verify_db,)
    for v in stale:
        log(f"MariaDB {v} is '{_global(v)}', the CARLOS drop-in asks for '{'|'.join(checks[v])}'")
    if not stale:
        log("MariaDB is already running with the CARLOS settings")
        return 0
    if not os.path.isdir("/run/systemd/system"):
        warn("MariaDB needs a restart to pick up the drop-in, but systemd is not "
             "running here. Restart it yourself before using this system.")
        return 0
    log("restarting MariaDB to apply /etc/mysql/mariadb.conf.d/60-carlos-emr.cnf")
    if run(["systemctl", "restart", "mariadb.service"]).returncode != 0:
        die("MariaDB failed to restart; check 'systemctl status mariadb' and 'journalctl -u mariadb'")
    import time
    for _ in range(60):
        if db_root_ok():
            break
        time.sleep(1)
    else:
        die("MariaDB did not come back after the restart")
    still = [v for v, want in checks.items()
             if v != "binlog_ignore_db" and _global(v) not in want]
    ign = db_root(["-N", "-B", "-e", "SHOW MASTER STATUS"], capture_output=True)
    cols = ign.stdout.rstrip("\n").split("\t") if ign.returncode == 0 else []
    ign_val = cols[3] if len(cols) > 3 else ""
    if "binlog_ignore_db" in checks and \
            checks["binlog_ignore_db"][0] not in ign_val.split(","):
        still.append("binlog_ignore_db")
    if still:
        warn("MariaDB restarted but is STILL not running the CARLOS settings.")
        warn("Something later in /etc/mysql/ overrides the drop-in — option files are "
             "last-occurrence-wins, so look for a file sorting after 60-.")
        return 1
    log("MariaDB restarted with the CARLOS settings applied")
    return 0


# --- seeded administrator (verb: bootstrap-admin) ---------------------------

def cmd_bootstrap_admin(argv) -> int:
    """Replace the seeded carlosdoc credential. The province data migration
    seeds it with a bcrypt hash PUBLISHED in the source repository, so every
    installation would otherwise start with the same known password."""
    need_root("bootstrap-admin")
    require_db_root()
    s = config.load()
    user = "carlosdoc"
    # The hash the migrations seed the account with — published in the source
    # repository (database/mysql/migration/*/V1.0.2__*_data.sql, identical
    # across provinces). Comparing against it makes this verb idempotent:
    # without the comparison, EVERY upgrade and dpkg-reconfigure re-randomized
    # a credential the clinic may have long since made its own.
    # This is NOT a leaked secret: it is the already-public value this verb
    # exists to hunt down and replace.
    seeded_hash = "{bcrypt}$2a$10$RcoNeqhcLzkfBzAoTQ5C5.nnsOs15iOasQCp0/smjDAuTtkMQ.Uju"  # nosemgrep: generic.secrets.security.detected-bcrypt-hash.detected-bcrypt-hash
    cp = db_root(["-N", "-B", "-e",
                  f"SELECT COUNT(*) FROM `{s.db_name}`.security "
                  f"WHERE user_name='{user}' AND password='{sql_escape(seeded_hash)}'"],
                 capture_output=True)
    if cp.returncode != 0:
        # A failed query (security table missing because migrations have not
        # run, server gone away) is NOT the same as "account absent": exiting
        # 0 here left the credential PUBLISHED in the source repository
        # active while the tool reported there was nothing to do.
        die(f"could not check for the seeded '{user}' account "
            f"(is the schema migrated? run 'carlos-ctl db-migrate' first): "
            f"{cp.stderr.strip()}")
    if cp.stdout.strip() == "0":
        log(f"no '{user}' account still carrying the seeded credential; nothing to reset")
        return 0

    import bcrypt  # python3-bcrypt is a package dependency
    password = genpw()
    # EXACTLY FOUR DIGITS: Login2Action discards any submitted PIN that does
    # not match [0-9]{4} and then compares the resulting empty string — a
    # six-digit PIN made the fresh administrator account impossible to log
    # into, with a message blaming the password. Verified live.
    pin = genrandom(4, "0123456789")
    hashed = "{bcrypt}" + bcrypt.hashpw(password.encode(), bcrypt.gensalt(12)).decode()

    # FILE FIRST, database second — the same discipline db-users applies to
    # its credential files. The old order ran the UPDATE and only then wrote
    # initial-admin.txt: a failed write (full root filesystem, damaged
    # /etc/carlos-emr) lost the only copy of the new password, and because
    # the seeded hash was already replaced, a re-run said "nothing to
    # reset" — the account was recoverable only by hand-written SQL. If the
    # UPDATE below fails instead, the file merely holds an unused credential
    # and the next run regenerates it.
    outfile = os.path.join(CONF_DIR, "initial-admin.txt")
    if not os.path.isdir(CONF_DIR):
        die(f"{CONF_DIR} does not exist — reinstall carlos-emr before resetting the seeded credential")
    sql = f"""
SET SESSION sql_log_bin = 0;
UPDATE `{s.db_name}`.security
   SET password='{sql_escape(hashed)}',
       pin='{sql_escape(pin)}',
       passwordUpdateDate=NOW(),
       pinUpdateDate=NOW(),
       lastUpdateUser='carlos-ctl'
 WHERE user_name='{user}';
"""
    content = f"""CARLOS EMR initial administrator credentials
============================================
Generated by the carlos-emr package on {util.out(['date', '-Is'])}.

  URL:      https://{s.server_name}/carlos/
  user:     {user}
  password: {password}
  PIN:      {pin}

The database migrations seed this account with a password whose hash is
published in the CARLOS source repository. The package has replaced it with
the random values above so that no installation ships with a known credential.

The account is also flagged for a FORCED PASSWORD RESET, which is how the
migrations seed it and which this package deliberately leaves in place: the
password above exists in a file on disk, so it must not stay in use. Your
first login goes straight to the reset page and asks for this password as the
"old" one, plus a new one of your choosing.

The PIN is four digits because the application accepts no other length.

DO THIS NOW, IN THIS ORDER:
  1. Log in and complete the forced password reset.
  2. Create a real named account for each clinician and administrator.
  3. Disable this account (Administration > User Management).
  4. Delete this file:  shred -u {outfile}

This file is mode 0600 and readable only by root. It is NOT included in the
backup.
"""
    try:
        fd = os.open(outfile, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as fh:
            fh.write(content)
            fh.flush()
            os.fsync(fh.fileno())
        os.chown(outfile, 0, 0)
    except OSError as e:
        die(f"could not write {outfile} ({e}); the seeded credential was NOT touched — "
            "fix the cause and re-run 'carlos-ctl bootstrap-admin'")

    cp = db_root([], input=sql, capture_output=True)
    if cp.returncode != 0:
        # The file holds an unused credential; the next run regenerates it.
        die(f"could not reset the seeded credential:\n{cp.stderr.strip()}")
    log(f"reset the seeded '{user}' credential; the new password and PIN are in {outfile}")
    return 0


# --- deliberate decommissioning (verb: destroy-data) ------------------------

def cmd_destroy_data(argv) -> int:
    """The ONLY supported way this software destroys clinical data. It lives
    in a command an operator types, not in the package's removal path: `apt
    purge` must never destroy patient records — not with -y, not through a
    debconf prompt somebody tabs past. Decommissioning a clinical system
    should require naming what is being decommissioned, out loud."""
    need_root("destroy-data")
    s = config.load()
    confirm, include_backups = None, False
    it = iter(argv)
    for a in it:
        if a == "--confirm":
            confirm = next(it, None)
        elif a == "--including-backups":
            include_backups = True
        else:
            die(f"unknown option: {a}")

    if confirm != s.server_name:
        print(f"""carlos-ctl: this destroys the clinical record on this host and cannot be undone.

It will DROP the '{s.db_name}' and 'drugref2' databases, and delete every
patient document under {STATE}/OscarDocument, every JVM heap dump, and the
application logs.

To proceed you must name the host you are destroying:

    carlos-ctl destroy-data --confirm {s.server_name}

Add --including-backups to also delete /var/backups/carlos-emr. Without it the
backups survive, which is almost always what you want — a decommissioned
instance whose backups are gone is not decommissioned, it is lost.""", file=sys.stderr)
        return 2

    # Reachability is checked BEFORE anything is destroyed: continuing past
    # an unreachable MariaDB removed the documents and (with
    # --including-backups) shredded the key material while the clinical
    # DATABASES survived intact on disk — and still printed "done." with
    # exit 0. The report must be exact; a partial destruction that cannot
    # even start its most important leg refuses instead.
    if not db_root_ok():
        die("MariaDB is not reachable, so the clinical databases CANNOT be dropped — "
            "refusing to start a destruction that would be incomplete. Start MariaDB "
            "(systemctl start mariadb) and re-run.")

    warn(f"destroying the CARLOS clinical data on {s.server_name}")
    if os.path.isdir("/run/systemd/system"):
        run(["systemctl", "stop", "carlos-emr.service"], capture_output=True)

    if db_root_ok():
        log("dropping databases")
        cp = db_root([], input=f"""
SET SESSION sql_log_bin = 0;
DROP DATABASE IF EXISTS `{s.db_name}`;
DROP DATABASE IF EXISTS `drugref2`;
DROP USER IF EXISTS 'carlos'@'localhost';
DROP USER IF EXISTS 'carlos'@'127.0.0.1';
DROP USER IF EXISTS 'drugref'@'localhost';
DROP USER IF EXISTS 'drugref'@'127.0.0.1';
DROP USER IF EXISTS 'backup'@'localhost';
DROP USER IF EXISTS 'backup'@'127.0.0.1';
""")
        if cp.returncode != 0:
            # Batch mode aborts at the first failing statement, so a failure
            # here can leave databases AND every later DROP un-executed.
            # This verb's report must be exact — never claim destruction that
            # did not happen.
            die("DROP DATABASE batch FAILED — the clinical databases may "
                "still exist; nothing was reported destroyed")
    import shutil
    log("removing patient documents, heap dumps and logs")
    # Errors are collected and REPORTED, never ignored: this command's whole
    # value is that its report is exact — "destroyed" must not mean "mostly".
    rm_errors = []
    def _collect(_fn, path, exc):
        rm_errors.append(f"{path}: {exc[1]}")
    for p in (f"{STATE}/OscarDocument", f"{STATE}/heapdumps",
              "/var/log/carlos-emr/tomcat", "/var/log/carlos-emr/modsec"):
        if os.path.exists(p):
            shutil.rmtree(p, onerror=_collect)
    if rm_errors:
        for e in rm_errors[:10]:
            warn(f"could not remove: {e}")
        die("destruction is INCOMPLETE — the paths above still hold data")
    # Recreate the EMPTY directory skeleton: the rmtree above removed the
    # directories themselves, and the still-installed services need them —
    # live-tested: with modsec/ gone, the running nginx kept serving on its
    # open descriptors but every later `nginx -t` (and therefore every
    # reload and cert operation) failed until the tmpfiles skeleton was
    # restored. Data stays destroyed; the empty, correctly-owned directories
    # come back.
    if os.path.isdir("/run/systemd/system"):
        run(["systemd-tmpfiles", "--create", "/usr/lib/tmpfiles.d/carlos-emr.conf"],
            capture_output=True)

    if include_backups:
        log("removing backups")
        if os.path.isdir("/var/backups/carlos-emr"):
            shutil.rmtree("/var/backups/carlos-emr", onerror=_collect)
        # Shredded, not unlinked: these two files are the difference between a
        # retained backup being readable and being noise.
        shred_errors = []
        for f in (BACKUP_ENV, PROPERTIES):
            if not os.path.exists(f):
                continue
            cp = run(["shred", "-u", f], capture_output=True)
            if cp.returncode != 0:
                shred_errors.append(f"{f}: {cp.stderr.strip()}")
        # The root-filesystem twin of the repo marker must go with the
        # backups: left behind, it made every backup on a re-provisioned
        # host refuse to initialise the (genuinely fresh) repository.
        for f in ("/var/log/carlos-emr/backup-state/.repo-known",):
            if os.path.exists(f):
                try:
                    os.unlink(f)
                except OSError as e:
                    rm_errors.append(f"{f}: {e}")
        if rm_errors or shred_errors:
            for e in (rm_errors + shred_errors)[:10]:
                warn(f"could not destroy: {e}")
            die("key material may STILL BE READABLE — destruction is incomplete")
        log("backups and key material destroyed")
    else:
        warn("backups in /var/backups/carlos-emr were KEPT.")
        warn(f"{BACKUP_ENV} and {PROPERTIES} were kept with them — without "
             "RESTIC_PASSWORD and encryption.util.secret.key those backups are "
             "unreadable. Escrow both before you wipe this host.")
    log("done. 'apt purge carlos-emr carlos-emr-drugref' now removes the software.")
    return 0

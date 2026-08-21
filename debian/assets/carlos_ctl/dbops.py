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


# --- least-privilege accounts (verb: db-users) ------------------------------

def cmd_db_users(argv) -> int:
    need_root("db-users")
    require_db_root()
    s = config.load()

    # Re-use existing passwords so a reconfigure or upgrade never invalidates
    # the credential a running service is holding.
    app_pw = prop_unescape(prop_get(PROPERTIES, "db_password") or "") or genpw()
    drugref_pw = prop_unescape(prop_get(DRUGREF_PROPERTIES, "db_password") or "") or genpw()
    backup_pw = (env_get(BACKUP_ENV, "CARLOS_BACKUP_DB_PASSWORD") or "") or genpw()
    import re as _re
    verify_db = env_get(BACKUP_ENV, "CARLOS_BACKUP_VERIFY_DB") or "carlos_restore_drill"
    # Same rule as the settings loader applies to CARLOS_DB_NAME — one
    # identifier policy, not two subtly different ones.
    if not _re.fullmatch(r"[A-Za-z0-9_]+", verify_db):
        die(f"CARLOS_BACKUP_VERIFY_DB ('{verify_db}') must be a plain identifier (A-Za-z0-9_)")

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
    log("rotating database passwords")
    prop_set(PROPERTIES, "db_password", "")
    if os.path.isfile(DRUGREF_PROPERTIES):
        prop_set(DRUGREF_PROPERTIES, "db_password", "")
    if os.path.isfile(BACKUP_ENV):
        util.env_set(BACKUP_ENV, "CARLOS_BACKUP_DB_PASSWORD", "")
    cmd_db_users([])
    run(["systemctl", "restart", "carlos-emr.service"])
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

    # SELECT @@GLOBAL renders booleans as 1/0; accept either spelling.
    checks = {"log_bin": ("1", "ON"), "sql_mode": ("",),
              "character_set_server": ("utf8mb4",), "bind_address": ("127.0.0.1",)}
    stale = [v for v, want in checks.items() if _global(v) not in want]
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
    still = [v for v in ("log_bin", "sql_mode") if _global(v) not in checks[v]]
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
    cp = db_root(["-N", "-B", "-e",
                  f"SELECT COUNT(*) FROM `{s.db_name}`.security WHERE user_name='{user}'"],
                 capture_output=True)
    if cp.returncode != 0 or cp.stdout.strip() == "0":
        log(f"no seeded '{user}' account present; nothing to reset")
        return 0

    import bcrypt  # python3-bcrypt is a package dependency
    password = genpw()
    # EXACTLY FOUR DIGITS: Login2Action discards any submitted PIN that does
    # not match [0-9]{4} and then compares the resulting empty string — a
    # six-digit PIN made the fresh administrator account impossible to log
    # into, with a message blaming the password. Verified live.
    pin = genrandom(4, "0123456789")
    hashed = "{bcrypt}" + bcrypt.hashpw(password.encode(), bcrypt.gensalt(12)).decode()
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
    cp = db_root([], input=sql, capture_output=True)
    if cp.returncode != 0:
        die(f"could not reset the seeded credential:\n{cp.stderr.strip()}")

    outfile = os.path.join(CONF_DIR, "initial-admin.txt")
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
    fd = os.open(outfile, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as fh:
        fh.write(content)
    os.chown(outfile, 0, 0)
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

    warn(f"destroying the CARLOS clinical data on {s.server_name}")
    if os.path.isdir("/run/systemd/system"):
        run(["systemctl", "stop", "carlos-emr.service"], capture_output=True)

    if db_root_ok():
        log("dropping databases")
        db_root([], input=f"""
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
    else:
        warn("MariaDB is not reachable; the databases were NOT dropped")

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

    if include_backups:
        log("removing backups")
        shutil.rmtree("/var/backups/carlos-emr", ignore_errors=True)
        # Shredded, not unlinked: these two files are the difference between a
        # retained backup being readable and being noise.
        for f in (BACKUP_ENV, PROPERTIES):
            run(["shred", "-u", f], capture_output=True)
        log("backups and key material destroyed")
    else:
        warn("backups in /var/backups/carlos-emr were KEPT.")
        warn(f"{BACKUP_ENV} and {PROPERTIES} were kept with them — without "
             "RESTIC_PASSWORD and encryption.util.secret.key those backups are "
             "unreadable. Escrow both before you wipe this host.")
    log("done. 'apt purge carlos-emr carlos-emr-drugref' now removes the software.")
    return 0

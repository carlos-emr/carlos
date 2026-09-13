# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Finishing an installation whose database provisioning did not run.

carlos-emr.postinst provisions the database — settings drop-in, least-privilege
accounts, the Flyway schema, the seeded administrator credential, the optional
demonstration dataset — and every one of those steps is deliberately NON-FATAL.
A database that is unreachable, or a migration that fails, must not leave dpkg
with a half-configured package: that would block the other two CARLOS packages
and any unrelated package in the same apt transaction, and it would give the
operator no recovery instructions.

The cost of that choice is what an alpha tester hit on a fresh desktop install:
apt exits 0, the summary says nothing is wrong, and the host has no clinical
schema at all. The diagnosis was printed — on stderr, in the middle of a long
install, where a graphical or quiet front end never shows it — and nothing
retried it, so a reboot left the system exactly as broken. DrugRef "not
answering on loopback" is the same failure seen from the other end: it shares
the EMR's Tomcat, which is never started while the schema is missing.

This module closes that hole from both ends:

  * A durable marker (``/var/lib/carlos-emr/.install-incomplete``) records that
    provisioning is owed, and what the install asked for. The postinst writes
    it, ``carlos-ctl check`` reports it first, and the postinst raises a
    debconf note so the failure reaches an operator who never saw stderr.
  * ``carlos-ctl finish-install`` resumes provisioning from wherever it
    stopped, and carlos-emr-provision.service runs it once at boot, ordered
    before carlos-emr.service, so an install interrupted by a database that
    went away completes by itself.

Nothing here is a second implementation of provisioning: every step is the same
carlos-ctl verb the postinst calls, in the same order, with the same tolerance
for the same failures.
"""

import os
import time
from typing import Optional

from . import config, dbops, util
from .util import PROPERTIES, STATE, die, log, need_root, run, warn

# Written by carlos-emr.postinst when a provisioning step did not run or
# failed; removed by a successful configure and by finish-install. Plain
# KEY=value so both the shell that writes it and util.env_get can read it.
MARKER = os.path.join(STATE, ".install-incomplete")

# The postinst's own fail-closed sentinel: while it exists the seeded
# 'carlosdoc' credential published in the source repository is still live and
# the unit must stay disabled. Shared name, one meaning.
SEED_SENTINEL = os.path.join(STATE, ".seed-credential-live")

# Marker key -> the debconf question that answered it, for a hand-run repair on
# a host whose marker predates the key (or was cleared).
_DEBCONF_KEY = {
    "reset_admin": "carlos-emr/reset-seed-admin",
    "demo_data": "carlos-emr/install-demo-data",
}


def pending() -> bool:
    return os.path.exists(MARKER)


def reason() -> str:
    return (util.env_get(MARKER, "reason")
            or "the installer did not finish provisioning the database")


def clear() -> None:
    try:
        os.unlink(MARKER)
    except OSError:
        pass


def _answer(key: str, default: bool) -> bool:
    """What the install asked for. The marker is authoritative — it is what the
    postinst recorded at the moment it gave up — and debconf is the fallback
    for a repair run by hand with no marker present."""
    recorded = util.env_get(MARKER, key)
    if recorded is not None:
        return recorded.strip().lower() == "true"
    # which() first: util.out does not survive a missing binary, and debconf-show
    # is absent from a minimal chroot — where falling back to the defaults below
    # is the right answer, not a traceback in the middle of provisioning.
    if util.which("debconf-show"):
        for line in util.out(["debconf-show", "carlos-emr"]).splitlines():
            name, _, value = line.lstrip("*").strip().partition(":")
            if name.strip() == _DEBCONF_KEY[key]:
                return value.strip().lower() == "true"
    return default


def _wait_for_db(seconds: int) -> bool:
    """mariadb.service being "started" is not the same as accepting
    connections, and this runs immediately after it at boot."""
    deadline = time.monotonic() + seconds
    while True:
        if dbops.db_root_ok():
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(2)


def _table_count(db_name: str) -> Optional[int]:
    """Tables in the clinical schema, or None when the COUNT itself failed.
    'could not check' and 'nothing there' must stay different answers: the
    whole point of this verb is deciding whether to create a schema."""
    cp = dbops.db_root(
        ["-N", "-B", "-e",
         "SELECT COUNT(*) FROM information_schema.tables "
         f"WHERE table_schema='{db_name}'"],
        capture_output=True)
    if cp.returncode != 0:
        return None
    value = cp.stdout.strip()
    return int(value) if value.isdigit() else None


def _tolerate(what: str, fn, *args) -> bool:
    """Run a provisioning verb whose failure the postinst also tolerates.
    These verbs report through die(), which raises SystemExit — catching it
    here keeps one failing step from abandoning the ones after it."""
    try:
        return (fn(*args) or 0) == 0
    except SystemExit:
        warn(f"{what} did not succeed (see above); continuing")
        return False


def _fail_closed() -> None:
    """The postinst's fail-closed, repeated here because this verb can be the
    thing that creates the schema the seeded credential lives in. A live
    published administrator password over a VALID schema is not something a
    reboot may undo, so the unit is disabled as well as stopped."""
    try:
        with open(SEED_SENTINEL, "w", encoding="utf-8"):
            pass
        os.chmod(SEED_SENTINEL, 0o600)
    except OSError as e:
        warn(f"could not write {SEED_SENTINEL}: {e}")
    run(["systemctl", "disable", "--now", "carlos-emr.service"],
        capture_output=True)


def _report_drugref_seed() -> None:
    """The same unreachable database skips carlos-emr-drugref's own postinst,
    which creates and seeds the drugref2 schema. That work lives in that
    package's maintainer script, not in carlos-ctl, so this names the one
    command that re-runs it rather than reimplementing the seed load here."""
    if not os.path.isfile(util.DRUGREF_PROPERTIES):
        return  # carlos-emr-drugref is not installed; lookups are off by choice
    cp = dbops.db_root(
        ["-N", "-B", "-e", "SELECT COUNT(*) FROM information_schema.tables "
         "WHERE table_schema='drugref2'"], capture_output=True)
    if cp.returncode == 0 and cp.stdout.strip().isdigit() and int(cp.stdout.strip()) > 0:
        return
    warn("the drug reference database is empty or missing, so prescribing lookups will "
         "return nothing. Its dataset is loaded by the carlos-emr-drugref package, which "
         "was skipped for the same reason: sudo dpkg-reconfigure carlos-emr-drugref")


def cmd_finish_install(argv) -> int:
    """Resume an installation that did not finish provisioning its database.

    Idempotent, and safe to run at any time: every step below either detects
    that it has already been done or is itself idempotent. With --boot it is a
    no-op unless the postinst left the marker, and it will not migrate a
    schema that already has tables — an upgrade migration is an operator
    decision taken after a verified backup, never something a boot does."""
    boot = "--boot" in argv
    for a in argv:
        if a != "--boot":
            die(f"unknown option: {a} (usage: carlos-ctl finish-install [--boot])")
    need_root("finish-install")
    if boot and not pending():
        return 0
    if pending():
        log(f"resuming an unfinished installation: {reason()}")

    if not _wait_for_db(120 if boot else 0):
        die("MariaDB is not answering as root over the unix socket. Start it "
            "(systemctl status mariadb), then re-run 'carlos-ctl finish-install'.")
    s = config.load()

    # carlos.properties carries the account credentials db-users writes and the
    # migration reads; without it nothing below can run. Re-rendering it is
    # what init-config is for, and it is the step the postinst tolerates a
    # failure of (it also reloads nginx, which can fail for another vhost).
    if not os.path.isfile(PROPERTIES):
        log(f"{PROPERTIES} is missing; re-rendering the configuration")
        _tolerate("init-config", config.cmd_init_config, [])
        if not os.path.isfile(PROPERTIES):
            die(f"{PROPERTIES} is still missing — reinstall carlos-emr")

    # Same order, same tolerance as the postinst: the settings drop-in is worth
    # a warning, the accounts are not optional.
    _tolerate("db-apply-settings", dbops.cmd_db_apply_settings, [])
    dbops.cmd_db_users([])

    tables = _table_count(s.db_name)
    if tables is None:
        die(f"could not count the tables in `{s.db_name}` — the database answered "
            "root a moment ago, so investigate before provisioning further")
    if tables == 0:
        # run_flyway rather than the db-migrate verb: that verb opens with
        # "back up first", which is the right warning before an upgrade
        # migration and a misleading one over a schema that does not exist yet.
        log(f"`{s.db_name}` is empty; creating the schema (a few minutes)")
        if dbops.run_flyway("migrate") != 0:
            die("the schema migration FAILED (the Flyway message is above). "
                "'carlos-ctl db-info' shows the state; fix the cause, then re-run "
                "'carlos-ctl finish-install'.")
    elif dbops.run_flyway("validate") == 0:
        log(f"`{s.db_name}` is already migrated ({tables} tables)")
    elif boot:
        # Tables but not a schema this WAR accepts: a failed or partial
        # migration, or an upgrade whose migration has not been applied. Both
        # are operator decisions taken after a verified backup.
        die(f"`{s.db_name}` has {tables} tables but does not validate against the "
            "deployed application. NOT migrating unattended — back up, then run "
            "'carlos-ctl db-migrate' by hand ('carlos-ctl db-info' shows the state).")
    else:
        dbops.cmd_db_migrate([])

    # The schema seeds 'carlosdoc' with a password hash published in the CARLOS
    # source repository. Replacing it is the last thing that must happen before
    # anything starts serving.
    if _answer("reset_admin", True):
        try:
            dbops.cmd_bootstrap_admin([])
        except SystemExit:
            _fail_closed()
            die("the seeded 'carlosdoc' password could NOT be replaced, so the EMR has "
                "been stopped and disabled rather than served with a credential published "
                "in the CARLOS source repository. Fix the cause above, then re-run "
                "'carlos-ctl finish-install'.")
        if os.path.exists(SEED_SENTINEL):
            # Cleared here as well as in the postinst: this verb is the other
            # route out of that state, and leaving the unit disabled would mean
            # the EMR silently fails to come back at the next boot.
            try:
                os.unlink(SEED_SENTINEL)
            except OSError:
                pass
            run(["systemctl", "enable", "carlos-emr.service"], capture_output=True)
            log("the seeded administrator credential is replaced; the service unit is "
                "enabled again")

    if _answer("demo_data", False):
        _tolerate("the demonstration dataset", dbops.cmd_demo_data, [])

    _report_drugref_seed()

    clear()
    log("provisioning is complete")
    if os.path.exists(SEED_SENTINEL):
        warn("NOT starting the EMR: the seeded administrator credential is still live "
             "(carlos-ctl bootstrap-admin)")
        return 1
    if boot:
        # carlos-emr.service is ordered after this unit, so systemd starts the
        # EMR itself as soon as this returns.
        return 0
    util.reset_emr_start_limit()
    if run(["systemctl", "start", "carlos-emr.service"]).returncode != 0:
        warn("the application server did not start — journalctl -u carlos-emr -n 200")
        return 1
    log("the EMR is deploying; it answers in about two minutes. Then run "
        "'carlos-ctl check'.")
    return 0

# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Finishing an installation whose database provisioning did not run.

carlos-emr.postinst provisions the database — settings drop-in, least-privilege
accounts, the Flyway schema, the seeded administrator credential, the optional
demonstration dataset — with provisioning errors deliberately non-fatal to dpkg.
Failure to contain a potentially live seeded credential is the safety exception.
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
carlos-ctl verb the postinst calls, in the same order. Unlike dpkg configure, this repair command must return
nonzero when any requested step fails.
"""

import os
import time
import tempfile
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
    except FileNotFoundError:
        pass


def _record(reset_admin: bool, demo_data: bool, why: str) -> None:
    """Record manual repairs too, including repairs of packages predating markers."""
    directory = os.path.dirname(MARKER)
    os.makedirs(directory, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", dir=directory, delete=False,
                                     encoding="utf-8") as fh:
        temporary = fh.name
        try:
            fh.write(f"reason={why}\nreset_admin={str(reset_admin).lower()}\n"
                     f"demo_data={str(demo_data).lower()}\nat={int(time.time())}\n")
            fh.flush()
            os.fchmod(fh.fileno(), 0o644)
            os.replace(temporary, MARKER)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)


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


def _succeeded(fn, *args) -> bool:
    """CLI verbs use both integer return codes and SystemExit for failure."""
    try:
        return fn(*args) in (None, 0)
    except SystemExit as exc:
        return exc.code in (None, 0)


def _required(what: str, fn, *args) -> None:
    if not _succeeded(fn, *args):
        die(f"{what} failed; installation remains incomplete. Fix the cause above, "
            "then re-run 'carlos-ctl finish-install'.")


def _fail_closed() -> None:
    """Persist the credential guard and verify both runtime and boot containment."""
    persisted = True
    try:
        with open(SEED_SENTINEL, "w", encoding="utf-8"):
            pass
        os.chmod(SEED_SENTINEL, 0o600)
    except OSError as exc:
        persisted = False
        warn(f"could not write {SEED_SENTINEL}: {exc}")
    disabled = run(["systemctl", "disable", "carlos-emr.service"], capture_output=True)
    # Do not wait on an EMR start job ordered after this very provisioner at boot.
    stopped = run(["systemctl", "stop", "--no-block", "carlos-emr.service"],
                  capture_output=True)
    deadline = time.monotonic() + 130
    while True:
        state = run(["systemctl", "is-active", "carlos-emr.service"], capture_output=True)
        if state.stdout.strip() != "deactivating" or time.monotonic() >= deadline:
            break
        time.sleep(1)
    enabled = run(["systemctl", "is-enabled", "carlos-emr.service"], capture_output=True)
    if (not persisted or disabled.returncode != 0 or stopped.returncode != 0
            or state.stdout.strip() not in ("inactive", "failed")
            or enabled.stdout.strip() not in ("disabled", "masked")):
        die("COULD NOT verify that the EMR is stopped and protected at boot while "
            "the published administrator credential may still be live. Check "
            "'systemctl status carlos-emr' and 'systemctl disable --now carlos-emr' "
            "immediately; fix the errors above before retrying finish-install.")


def _report_drugref_seed() -> None:
    """Diagnose the companion package without overwriting an existing dataset."""
    if not os.path.isfile(util.DRUGREF_PROPERTIES):
        return
    cp = dbops.db_root(
        ["-N", "-B", "-e", "SELECT COUNT(*), "
         "COALESCE(SUM(table_name='_carlos_seed_complete'), 0) "
         "FROM information_schema.tables WHERE table_schema='drugref2'"],
        capture_output=True)
    values = cp.stdout.split()
    if cp.returncode != 0 or len(values) != 2 or not all(v.isdigit() for v in values):
        warn("could not verify the drug reference dataset. Check MariaDB and run "
             "'carlos-ctl check'; prescribing lookups have not been verified.")
    elif int(values[1]) == 1:
        return
    elif int(values[0]) == 0:
        warn("the drug reference database is empty or missing, so prescribing lookups "
             "will return nothing. Load it with: sudo dpkg-reconfigure carlos-emr-drugref")
    else:
        warn("drugref2 has tables but no _carlos_seed_complete marker: its seed may be "
             "incomplete, or it may be an older dataset. Existing data was left alone. "
             "Back it up and have an administrator verify it before any reload; "
             "dpkg-reconfigure carlos-emr-drugref does not overwrite populated databases.")


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
    reset_admin = _answer("reset_admin", True)
    demo_data = _answer("demo_data", False)
    if not pending():
        try:
            _record(reset_admin, demo_data, "manual install completion has not finished")
        except OSError as exc:
            die(f"could not record the unfinished install in {MARKER}: {exc}")
    if pending():
        log(f"resuming an unfinished installation: {reason()}")

    if not _wait_for_db(120 if boot else 0):
        die("MariaDB is not answering as root over the unix socket. Start it "
            "(systemctl status mariadb), then re-run 'carlos-ctl finish-install'.")
    s = config.load()

    # init-config requires this file too; only the package installs its skeleton.
    if not os.path.isfile(PROPERTIES):
        die(f"{PROPERTIES} is missing; reinstall carlos-emr to restore the configuration, "
            "then re-run 'carlos-ctl finish-install'.")

    _required("init-config", config.cmd_init_config, [])
    _required("db-apply-settings", dbops.cmd_db_apply_settings, [])
    _required("db-users", dbops.cmd_db_users, [])

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
        _required("the schema migration", dbops.cmd_db_migrate, [])

    # The schema seeds 'carlosdoc' with a password hash published in the CARLOS
    # source repository. Replacing it is the last thing that must happen before
    # anything starts serving.
    reenabled = False
    if reset_admin:
        if not _succeeded(dbops.cmd_bootstrap_admin, []):
            _fail_closed()
            die("the seeded 'carlosdoc' password could NOT be replaced, so the EMR has "
                "been stopped and disabled rather than served with a credential published "
                "in the CARLOS source repository. Fix the cause above, then re-run "
                "'carlos-ctl finish-install'.")
        if os.path.exists(SEED_SENTINEL):
            # Cleared here as well as in the postinst: this verb is the other
            # route out of that state, and leaving the unit disabled would mean
            # the EMR silently fails to come back at the next boot.
            enabled = run(["systemctl", "enable", "carlos-emr.service"], capture_output=True)
            state = run(["systemctl", "is-enabled", "carlos-emr.service"], capture_output=True)
            if enabled.returncode != 0 or state.stdout.strip() != "enabled":
                die("could not re-enable carlos-emr.service; the credential guard and "
                    "unfinished-install marker remain. Fix systemctl enable errors, "
                    "then re-run 'carlos-ctl finish-install'.")
            try:
                os.unlink(SEED_SENTINEL)
            except OSError as exc:
                die(f"could not remove the credential guard {SEED_SENTINEL}: {exc}; "
                    "installation remains incomplete")
            reenabled = True
            log("the seeded administrator credential is replaced; the service unit is "
                "enabled again")

    if demo_data:
        _required("the demonstration dataset", dbops.cmd_demo_data, [])

    _report_drugref_seed()

    if os.path.exists(SEED_SENTINEL):
        warn("NOT starting the EMR: the seeded credential guard remains, but password "
             "replacement was not requested. Run 'dpkg-reconfigure carlos-emr' and "
             "enable seeded-password replacement to recover.")
        return 1
    # Clear before a manual start, which also pulls in the provisioner. Otherwise
    # that dependency would repeat this repair. Restore the marker if start fails.
    try:
        clear()
    except OSError as exc:
        die(f"could not clear {MARKER}: {exc}; completion has not been recorded")
    if boot:
        # A disabled EMR had no start job queued at boot. Queue one now, without
        # waiting for a service that is ordered after this running provisioner.
        if reenabled and run(["systemctl", "start", "--no-block", "carlos-emr.service"]).returncode != 0:
            _record(reset_admin, demo_data, "the application server start could not be queued")
            die("could not queue the recovered EMR start; retry finish-install")
        log("database provisioning is complete")
        # carlos-emr.service is ordered after this unit, so systemd starts the
        # EMR itself as soon as this returns.
        return 0
    util.reset_emr_start_limit()
    if run(["systemctl", "start", "carlos-emr.service"]).returncode != 0:
        try:
            _record(reset_admin, demo_data, "the application server did not start")
        except OSError as exc:
            warn(f"could not restore {MARKER}: {exc}; retry finish-install manually")
        warn("the application server did not start — journalctl -u carlos-emr -n 200")
        return 1
    log("database provisioning is complete; the EMR is deploying. "
        "It answers in about two minutes. Then run "
        "'carlos-ctl check'.")
    return 0

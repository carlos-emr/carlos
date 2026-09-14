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

# One repair at a time. carlos-emr-provision.service runs this verb at boot and
# an operator can run it by hand, so two runs can overlap: both would pass the
# schema checks, both would call bootstrap-admin — and each writes
# initial-admin.txt before updating `security`, so the surviving file could name
# the other run's password — and they would race the marker clearing and the
# service start. Same discipline, and the same non-blocking lock, that the
# demonstration-data loader already applies to itself.
LOCK = os.path.join(STATE, ".finish-install.lock")

# The SHIPPED guard, and the same file carlos-emr.service runs as its
# ExecCondition= and cli._refuse_start_during_o19_import() consults before a
# start: one predicate, so none of them can disagree about what "an OSCAR 19
# import is in progress" means. Referenced by path rather than imported
# because cli imports this module, not the other way round.
O19_GUARD = os.path.join(util.LIB, "carlos-emr-o19-guard")


def pending() -> bool:
    return os.path.exists(MARKER)


def reason() -> str:
    return (util.env_get(MARKER, "reason")
            or "the installer did not finish provisioning the database")


def clear() -> None:
    """Record that nothing is owed. Only an absent marker is tolerated: it is
    what a completed repair looks like, and clearing twice is not an error.
    Every other failure — a read-only or full /var — must reach the caller,
    because a marker that outlives the work it describes would have this verb
    and the boot provisioner repeat a repair that is already done."""
    try:
        os.unlink(MARKER)
    except FileNotFoundError:
        pass  # already clear; nothing was owed


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
    count = ("SELECT COUNT(*) FROM information_schema.tables "
             f"WHERE table_schema='{db_name}'")
    cp = dbops.db_root(["-N", "-B", "-e", count], capture_output=True)
    if cp.returncode != 0:
        return None
    value = cp.stdout.strip()
    return int(value) if value.isdigit() else None


# The open descriptor IS the lock, so it has to outlive _acquire_lock(): a
# dropped local would be closed by the garbage collector and unlock mid-repair.
_LOCK_HANDLE = None


def _acquire_lock(boot: bool = False) -> bool:
    """Hold an exclusive, non-blocking lock for the whole repair.

    Three things provision this database with the same carlos-ctl verbs: this
    command by hand, carlos-emr-provision.service at boot, and
    carlos-emr.postinst (which takes this same file with flock(1)). Any two of
    them overlapping would clear the same schema checks and both call
    bootstrap-admin, which writes initial-admin.txt before it updates
    `security` — so the file left on disk could name the other run's password —
    and they would race the marker and the service state too.

    Returns False when the lock is held and this is a boot run: another
    provisioning run owns the work, so the boot has nothing to do and nothing
    to complain about. A hand-run repair is told instead. The lock is held for
    the life of the process (the descriptor stays open) and goes with it.
    """
    import fcntl

    global _LOCK_HANDLE

    try:
        os.makedirs(os.path.dirname(LOCK), exist_ok=True)
        handle = open(LOCK, "w", encoding="utf-8")
    except OSError as exc:
        die(f"could not open {LOCK}: {exc}; finish-install cannot serialize itself "
            "against the boot-time provisioner, so it will not start")
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        if boot:
            log("another CARLOS provisioning run holds the provisioning lock (an apt "
                "transaction, or a repair run by hand); leaving the work to it")
            return False
        die("another CARLOS provisioning run is already in progress — the boot-time "
            "carlos-emr-provision.service, or an apt transaction configuring the "
            "package. Wait for it to finish, then check the result with "
            "'carlos-ctl check'")
    _LOCK_HANDLE = handle
    return True


def _succeeded(fn, *args) -> bool:
    """CLI verbs use both integer return codes and SystemExit for failure."""
    try:
        return fn(*args) in (None, 0)
    except SystemExit as exc:
        return exc.code in (None, 0)


# Appended to any failure that stops this verb BEFORE bootstrap-admin has run,
# when the install asked for the seeded credential to be replaced. The guard is
# NOT armed from those paths on purpose: .seed-credential-live stops every start
# of carlos-emr.service, and arming it because a settings drop-in or a GRANT
# failed would take a clinic's already-serving EMR down over a transient
# database error. The case that can be proved — a bootstrap-admin that ran and
# failed — is contained, by _fail_closed() here and by the postinst. What is
# left is a host whose published password may not have been replaced yet, and
# the operator is the one who knows whether it is serving, so tell them.
_SEED_NOTE = (" The seeded 'carlosdoc' password has NOT been replaced yet: if this host "
              "is already serving, treat the credential published in the CARLOS source "
              "repository as LIVE until finish-install completes.")


def _required(what: str, fn, *args, note: str = "") -> None:
    if not _succeeded(fn, *args):
        die(f"{what} failed; installation remains incomplete. Fix the cause above, "
            f"then re-run 'carlos-ctl finish-install'.{note}")


def _unit_enablement() -> str:
    """systemd's own word for the unit's install state: enabled, disabled,
    masked, static, or "" when systemd is not running."""
    return run(["systemctl", "is-enabled", "carlos-emr.service"],
               capture_output=True).stdout.strip()


def _o19_import_running() -> Optional[str]:
    """Whether an OSCAR 19 import owns the clinical database right now.

    `carlos-ctl import-o19` copies a clinic's OSCAR 19 database into this
    schema, and the guard exists because even *starting* CARLOS during that
    window writes the startup listener's rows into a half-copied schema. This
    verb does considerably more than start it — db-apply-settings restarts
    MariaDB under the importer, db-users rewrites grants, and a migration on a
    schema mid-copy is unrecoverable — so it has to consult the same guard.

    Returns the guard's reason when an import is running, None when it is not.
    A missing guard is a broken unpack, not an absent import: it fails CLOSED,
    exactly as carlos-emr.postinst's o19_import_in_progress() does.
    """
    if not os.path.exists(O19_GUARD):
        return (f"{O19_GUARD} is missing, so whether an OSCAR 19 import is "
                "running cannot be established (reinstall carlos-emr)")
    verdict = run([O19_GUARD], capture_output=True)
    if verdict.returncode == 0:
        return None
    return (verdict.stderr or "").strip() or "an OSCAR 19 import is in progress"


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
    if not persisted:
        # The sentinel is what actually blocks a start: carlos-emr.service
        # carries ConditionPathExists=!.seed-credential-live precisely because
        # `disable` does not stop a queued or hand-typed `systemctl start`. With
        # /var refusing the sentinel, masking is the only guard left that lives
        # outside it, so take it — the recovery path below unmasks the unit once
        # the credential has actually been replaced.
        masked = run(["systemctl", "mask", "carlos-emr.service"], capture_output=True)
        if masked.returncode != 0:
            warn("could not mask carlos-emr.service either: "
                 f"{masked.stderr.strip() or 'systemctl mask failed'}")
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
            "immediately; fix the errors above before retrying finish-install. "
            "A unit reported as masked was masked HERE, because the credential "
            "guard could not be written: a successful finish-install unmasks it.")


def _report_drugref_seed() -> None:
    """Diagnose the companion package without overwriting an existing dataset."""
    if not os.path.isfile(util.DRUGREF_PROPERTIES):
        return
    # One literal, assembled before the call: adjacent strings inside the
    # argument list read as a missing comma to both a reviewer and CodeQL.
    seed_state = ("SELECT COUNT(*), "
                  "COALESCE(SUM(table_name='_carlos_seed_complete'), 0) "
                  "FROM information_schema.tables WHERE table_schema='drugref2'")
    cp = dbops.db_root(["-N", "-B", "-e", seed_state], capture_output=True)
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
    if not _acquire_lock(boot):
        return 0
    reset_admin = _answer("reset_admin", True)
    demo_data = _answer("demo_data", False)
    # Every failure before bootstrap-admin carries this; see _SEED_NOTE.
    note = _SEED_NOTE if reset_admin else ""

    def fail(message: str) -> None:
        die(message + note)

    if not pending():
        try:
            _record(reset_admin, demo_data, "manual install completion has not finished")
        except OSError as exc:
            die(f"could not record the unfinished install in {MARKER}: {exc}")
    if pending():
        log(f"resuming an unfinished installation: {reason()}")

    # Before the database is touched at all: settings, grants and a migration
    # would all land inside an import's half-copied schema.
    import_running = _o19_import_running()
    if import_running:
        if boot:
            # Not a failure of this boot: the import owns the database, and it
            # is resumed by an operator, not by a boot. The marker stays, so
            # the next boot after the import finishes completes the install.
            log("NOT provisioning: " + import_running
                + ". The unfinished install stays recorded; it is completed after "
                "the import finishes (carlos-ctl import-o19 --resume).")
            return 0
        die("finish-install refused: " + import_running
            + ". Finish or retire the import first ('carlos-ctl import-o19 --resume' "
            "or '--cleanup'), then re-run 'carlos-ctl finish-install'. The "
            "unfinished install stays recorded until then.")

    if not _wait_for_db(120 if boot else 0):
        fail("MariaDB is not answering as root over the unix socket. Start it "
             "(systemctl status mariadb), then re-run 'carlos-ctl finish-install'.")
    s = config.load()

    # init-config requires this file too; only the package installs its skeleton.
    if not os.path.isfile(PROPERTIES):
        fail(f"{PROPERTIES} is missing; reinstall carlos-emr to restore the configuration, "
             "then re-run 'carlos-ctl finish-install'.")

    _required("init-config", config.cmd_init_config, [], note=note)
    _required("db-apply-settings", dbops.cmd_db_apply_settings, [], note=note)
    _required("db-users", dbops.cmd_db_users, [], note=note)

    tables = _table_count(s.db_name)
    if tables is None:
        fail(f"could not count the tables in `{s.db_name}` — the database answered "
             "root a moment ago, so investigate before provisioning further")
    if tables == 0:
        # run_flyway rather than the db-migrate verb: that verb opens with
        # "back up first", which is the right warning before an upgrade
        # migration and a misleading one over a schema that does not exist yet.
        log(f"`{s.db_name}` is empty; creating the schema (a few minutes)")
        if dbops.run_flyway("migrate") != 0:
            fail("the schema migration FAILED (the Flyway message is above). "
                 "'carlos-ctl db-info' shows the state; fix the cause, then re-run "
                 "'carlos-ctl finish-install'.")
    elif dbops.run_flyway("validate") == 0:
        log(f"`{s.db_name}` is already migrated ({tables} tables)")
    elif boot:
        # Tables but not a schema this WAR accepts: a failed or partial
        # migration, or an upgrade whose migration has not been applied. Both
        # are operator decisions taken after a verified backup.
        fail(f"`{s.db_name}` has {tables} tables but does not validate against the "
             "deployed application. NOT migrating unattended — back up, then run "
             "'carlos-ctl db-migrate' by hand ('carlos-ctl db-info' shows the state).")
    else:
        _required("the schema migration", dbops.cmd_db_migrate, [], note=note)

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
        # Containment has three shapes, because the postinst writes the
        # sentinel best-effort and then VERIFIES rather than asserts: the
        # sentinel is present; the unit is masked (what _fail_closed() does
        # when the sentinel cannot be written); or the unit is merely disabled
        # (the sentinel write failed but the disable succeeded). All three have
        # to be lifted here. Recognizing only the sentinel left the third case
        # with a cleared marker and a unit that never starts again — nothing
        # for a later run to notice, and at boot no start queued either.
        # Re-enabling a unit an operator disabled by hand is within this verb's
        # contract: it ends by starting the EMR.
        enablement = _unit_enablement()
        if os.path.exists(SEED_SENTINEL) or enablement in ("masked", "disabled"):
            # Cleared here as well as in the postinst: this verb is the other
            # route out of that state, and leaving the unit disabled would mean
            # the EMR silently fails to come back at the next boot. `enable`
            # fails outright on a masked unit, so lift the mask first or the
            # EMR never comes back at all.
            if enablement == "masked" and run(["systemctl", "unmask", "carlos-emr.service"],
                                              capture_output=True).returncode != 0:
                die("could not unmask carlos-emr.service, so the EMR cannot be started "
                    "even though the seeded credential has been replaced. Fix the "
                    "systemctl errors, then re-run 'carlos-ctl finish-install'.")
            enabled = run(["systemctl", "enable", "carlos-emr.service"], capture_output=True)
            state = run(["systemctl", "is-enabled", "carlos-emr.service"], capture_output=True)
            if enabled.returncode != 0 or state.stdout.strip() != "enabled":
                die("could not re-enable carlos-emr.service; the credential guard and "
                    "unfinished-install marker remain. Fix systemctl enable errors, "
                    "then re-run 'carlos-ctl finish-install'.")
            try:
                os.unlink(SEED_SENTINEL)
            except FileNotFoundError:
                pass  # the mask was the only guard; it has just been lifted
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
        # Reset the start limiter first, exactly as the manual path does: the
        # EMR may well have burned its six failures against the schema this run
        # has just created, and systemd would refuse the queued start outright.
        util.reset_emr_start_limit()
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

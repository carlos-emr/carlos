# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The deployment substrate the OSCAR 19 import runs on.

Everything in the importer that is not about OSCAR 19 or CARLOS — where
the workspace lives, how a mariadb client is spawned, who takes the
pre-import snapshot, how the application is asked whether it is running —
is answered by ONE object, so a second deployment can answer differently
without a second orchestrator.

Why an object rather than a second copy of `o19import`: the ledger, the
workspace lock, the resume rules, the phase order, the refusals and the
validation report are the parts a clinic's data depends on, and they are
identical on every deployment. A carlos-podman port that re-implemented
them would be a second implementation of the same safety properties,
drifting from this one commit by commit. What genuinely differs is small
and named here.

`Host` IS the deb package's implementation — the default, and the only
one in this repository. carlos-podman subclasses it and overrides the
handful of methods whose answers differ (the client runs `podman exec`
into the database container, the workspace lives under `$EMR_HOME`, the
snapshot comes from restic through `carlos-ctl backup`, the app-running
question is asked of the pod). Nothing here knows that.
"""

import os
from typing import Dict, List, Optional, Sequence, Tuple

from . import dbops
from .util import BACKUP_ENV, ENV_FILE, STATE, log, run

#: the deb's workspace: ledger, reports, staged bundle, archive export
STATE_DIR = os.path.join(STATE, "o19-import")

#: the deb's patient document tree (o19docs/o19props default to the same
#: path; the import passes this one through the phase context so a
#: deployment that stores documents elsewhere is not a special case)
DOCUMENTS_ROOT = os.path.join(STATE, "OscarDocument")

#: the throwaway account the dump is restored as: all privileges on the
#: staging schema and nothing else, created for the restore and dropped
#: after it
STAGING_USER = "o19_import"

#: the identity the deb's webapp runs as, and so the owner of the
#: document tree it has to read
SERVICE_USER = "carlos"

#: the account is created for both host patterns: a socket connection
#: matches 'localhost' first (an anonymous ''@'localhost' row would
#: otherwise shadow a '%' entry), a dev seam over TCP matches '%'
STAGING_ACCOUNT_HOSTS = ("localhost", "%")


class Host(object):

    """The deb package's deployment, and the base every other implements.

    Methods are grouped by what they answer: where things live, who this
    host is, how to reach the database, and what the deployment can be
    asked to do (snapshot, validate, report whether the app is up)."""

    #: named in operator-facing refusals, so a message says which
    #: deployment's rules were applied
    label = "the carlos-emr deb package"

    # -- where things live -------------------------------------------------

    @property
    def state_dir(self) -> str:
        """The import workspace: ledger, reports, bundle, archive export."""
        return STATE_DIR

    @property
    def documents_root(self) -> str:
        """The patient document tree the documents phase restores into."""
        return DOCUMENTS_ROOT

    # -- who this host is --------------------------------------------------

    def is_packaged_host(self) -> bool:
        """Whether this is a real deployment rather than a development
        database reached through the `--mariadb-arg` seam.

        It gates the dev-only flags and the stock-deploy pristine sweep,
        so it must be a fact about the machine, never a flag."""
        return os.path.exists(ENV_FILE)

    def configured_province(self) -> str:
        """The province this host is deployed for, which selects the
        manifest profile every ruling was curated against. A development
        database (no env file) defaults to Ontario; a malformed env file
        is an error, never silently Ontario."""
        from . import config
        if not self.is_packaged_host():
            return "on"
        return config.load().province

    def configured_db_name(self) -> Optional[str]:
        """The CARLOS schema this host deploys, or None when this is not
        a packaged host and the caller must fall back to a dev default."""
        from . import config
        if not self.is_packaged_host():
            return None
        return config.load().db_name

    def identity_source(self) -> str:
        """What an operator should look at when the host's identity is
        the thing being refused."""
        return ENV_FILE

    # -- reaching the database ---------------------------------------------

    def client_base_argv(self,
                         mariadb_args: Optional[Sequence[str]]) -> List[str]:
        """argv[0..] that starts a mariadb client speaking to THIS host's
        database as root, with the statement still to be appended.

        The deb runs it directly over the unix socket, exactly like
        `dbops.db_root`. `--mariadb-arg` overrides the connection tail for
        development databases (and implies `--dev-target`)."""
        if mariadb_args:
            return ["mariadb"] + list(mariadb_args)
        return ["mariadb", "--protocol=socket", "--user=root"]

    def client_env(self) -> Dict[str, str]:
        """Environment entries every client invocation needs. Empty here:
        the deb connects as root over the socket, so there is no
        credential to pass. A deployment whose client must authenticate
        returns it HERE rather than on the argv — /proc/<pid>/cmdline is
        world-readable and these statements touch PHI."""
        return {}

    def stage_credential(self, password: str,
                         client_cnf: str) -> Dict[str, str]:
        """Make the throwaway staging account's password available to the
        restore client, and return the environment the client needs.

        The deb writes a 0600 defaults file: the client is a separate
        process on this machine, and a defaults file keeps the password
        off its argv. The file carries the live password of an account
        holding ALL PRIVILEGES on a full copy of the clinic's EMR, so the
        mode is set on the descriptor -- the mode argument of `open`
        applies to a NEW file only, and O_TRUNC does not reset an
        existing one's."""
        fd = os.open(client_cnf, os.O_WRONLY | os.O_CREAT | os.O_TRUNC,
                     0o600)
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            fh.write("[client]\nuser={0}\npassword={1}\n".format(
                STAGING_USER, password.replace("\\", "\\\\")))
        return {}

    def clear_stage_credential(self, client_cnf: str) -> None:
        """Take the staging password off disk. Called from a `finally`: a
        `DROP USER` that fails must still not leave it there."""
        if os.path.exists(client_cnf):
            os.unlink(client_cnf)

    def staging_client_argv(self, base_argv: Sequence[str],
                            client_cnf: str,
                            statement_timeout: int = 0) -> List[str]:
        """The restore client's argv: the connection tail of the root
        argv (socket/host/port), identity replaced by the throwaway
        staging account read from a 0600 defaults file (never argv), and
        --one-database so a statement addressed at another schema is
        skipped rather than run. --statement-timeout bounds the dump's
        own statements too (one crafted INSERT could otherwise hold the
        restore forever).

        --user is repeated on the argv even though the defaults file
        already names it: --defaults-extra-file does NOT suppress the
        other option files, and ~/.my.cnf is read AFTER it. A root
        ~/.my.cnf carrying `[client] user=root` would otherwise silently
        connect the clinic's dump as root, and every backstop this
        function exists for -- the schema-scoped grants, --one-database --
        would be gone. A command-line option outranks every option file.
        --local-infile=0 closes the same class for a system defaults file
        that enables it."""
        from .o19import import (STAGING_SCHEMA, staging_init_command,
                                strip_client_identity)
        tail = strip_client_identity(list(base_argv)[1:])
        return (["mariadb", "--defaults-extra-file=" + client_cnf,
                 "--user=" + STAGING_USER, "--local-infile=0",
                 "--max-allowed-packet=1G"] + tail
                + ["--one-database",
                   "--init-command=" + staging_init_command(
                       statement_timeout),
                   STAGING_SCHEMA])

    def document_ownership(self) -> Tuple[str, str, str]:
        """(owner, directory mode, file mode) for the restored document
        tree.

        The deb's webapp runs as `carlos` and its tmpfiles skeleton is
        2750/0640. A deployment whose application reads the tree as a
        different identity -- a rootless container's mapped uid, say --
        answers with that one, or the import leaves a tree the
        application cannot read and the reconciliation, run as root,
        would not notice."""
        return (SERVICE_USER, "2750", "0640")

    def sql_escape(self, value: str) -> str:
        """Escape a value for a single-quoted SQL literal."""
        return dbops.sql_escape(value)

    # -- what the deployment can be asked to do ----------------------------

    def flyway_validate(self) -> int:
        """Run Flyway's `validate` against the deployed application, and
        return its exit code. P0 refuses a target whose schema does not
        match the WAR that will read it."""
        return dbops.run_flyway("validate")

    def backup_configured(self) -> bool:
        """Whether a pre-import snapshot can be taken at all."""
        return os.path.exists(BACKUP_ENV)

    def backup_configuration_hint(self) -> str:
        """What an operator should configure when it is not."""
        return BACKUP_ENV

    def pre_import_backup(self) -> Tuple[bool, str]:
        """Take the pre-import snapshot -- the rollback point everything
        after P3 assumes exists.

        Returns (ok, diagnosis): the diagnosis is what an operator should
        read when it failed, and is shown verbatim."""
        log("taking the pre-import backup (systemd unit; this is the "
            "rollback point) ...")
        cp = run(["systemctl", "start", "carlos-emr-backup.service"])
        if cp.returncode != 0:
            return False, "journalctl -u carlos-emr-backup -n 50"
        return True, ""

    def app_running_refusal(self) -> Optional[str]:
        """Why the import may not run right now, or None.

        CARLOS must not run against the target while it is being written:
        its startup listener creates rows (program, site, memberships)
        that would fail row parity, and a live session could read a
        half-copied chart."""
        if not self.is_packaged_host():
            return None  # a development database, no service unit
        # NOT `is-active --quiet`: a unit still starting reports
        # ActiveState=activating, for which is-active exits 3 — and
        # Tomcat takes tens of seconds to reach `active`, so the guard
        # would be inert for exactly the window in which the startup
        # listener writes its rows. dbops.py makes the same correction
        # for the backup units.
        cp = run(["systemctl", "show", "-p", "ActiveState", "--value",
                  "carlos-emr"], capture_output=True)
        state = (cp.stdout or "").strip()
        # Fail CLOSED. A `systemctl show` that exits non-zero, or that
        # answers something this does not recognise, has not established
        # that the application is stopped -- and the whole point of the
        # gate is that a running CARLOS writes rows into the target
        # while the import copies into it. Reading an unknown answer as
        # "not running" would make the guard silently inert on exactly
        # the host where systemd is unwell.
        if cp.returncode != 0 or not state:
            return ("could not determine whether carlos-emr is running "
                    "(systemctl show exited {0}). The import must not run "
                    "against a live application: stop it (`carlos-ctl "
                    "stop`), confirm with `systemctl status carlos-emr`, "
                    "and re-run.".format(cp.returncode))
        if state in ("active", "activating", "reloading", "deactivating"):
            return ("carlos-emr is {0} — stop it for the duration of the "
                    "import (`carlos-ctl stop`, or `systemctl stop "
                    "carlos-emr`) and re-run; start it again only after "
                    "the verified import and the properties fragment have "
                    "been applied".format(state))
        if state not in ("inactive", "failed"):
            return ("carlos-emr reports an unrecognised state {0!r}; the "
                    "import will not assume it is stopped. Confirm with "
                    "`systemctl status carlos-emr` and re-run."
                    .format(state))
        return None

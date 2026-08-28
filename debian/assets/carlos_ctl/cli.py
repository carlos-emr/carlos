# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""carlos-ctl — administer a CARLOS EMR host installed from the carlos-emr
package.

The package's maintainer scripts call the same verbs an administrator does,
so provisioning logic exists in exactly one place: what dpkg-reconfigure runs
is what you can re-run by hand, and it behaves identically.

Verb names match carlos-podman's carlos-ctl wherever the two deployments
share a concept; see the package docstring in __init__.py.
"""

import os
import sys
from typing import List, Optional

from . import config, dbops, util, validate, waf
from .util import LIB, die, need_root

_USAGE = """carlos-ctl — administration for a CARLOS EMR host

  carlos-ctl check                run the full deployment check (start here)
  carlos-ctl status               systemd status of the EMR and its timers
  carlos-ctl restart              restart the EMR (applies config changes;
                                  takes ~2 minutes to redeploy)
  carlos-ctl start / stop         start or stop the EMR

  carlos-ctl db [args]            SQL shell on the EMR database as root
                                  (interactive with no args; -e/redirects
                                  pass through, e.g. carlos-ctl db < f.sql)
  carlos-ctl db-info              show the schema migration state
  carlos-ctl db-validate          verify the schema matches the deployed WAR
  carlos-ctl db-migrate           apply pending migrations (BACK UP FIRST)
  carlos-ctl db-baseline          adopt an existing pre-Flyway schema
  carlos-ctl db-repair            fix flyway_schema_history after a failure
  carlos-ctl db-apply-settings    restart MariaDB if it is not running the
                                  settings in the CARLOS drop-in
  carlos-ctl db-dump              consistent dump to stdout
  carlos-ctl db-users             (re)create the databases and accounts
  carlos-ctl demo-data            load the fictitious demonstration dataset
                                  into an EMPTY, freshly migrated database
                                  (refuses on any database with patients;
                                  NEVER for production systems)

  carlos-ctl cert status          what certificate is being served
  carlos-ctl cert selfsigned      (re)generate the self-signed certificate
  carlos-ctl cert acme <email>    switch to a Let's Encrypt certificate
  carlos-ctl cert manual          adopt a certificate you placed yourself
  carlos-ctl cert-renew           what the twice-daily timer runs

  carlos-ctl waf status           ModSecurity engine state and file locations
  carlos-ctl waf tail [lines]     show recent WAF blocks, readable
  carlos-ctl waf reload           apply edited exclusion/policy files
  carlos-ctl waf detect-only      stop blocking (triage only — see warning)
  carlos-ctl waf blocking         resume blocking

  carlos-ctl backup full          take a backup now
  carlos-ctl backup verify        restore the newest backup into a scratch db
  carlos-ctl backup status        when backups and drills last succeeded
  carlos-ctl backup snapshots     list what is in the repository
  carlos-ctl backup restic <args> raw restic against the configured repository

  carlos-ctl init-config          re-render + APPLY configuration from
                                  carlos-emr.env (nginx reload, cert refresh)
  carlos-ctl bootstrap-admin      reset the seeded administrator credential
  carlos-ctl rotate               rotate every generated database password
  carlos-ctl logs [args]          journalctl -u carlos-emr

Decommissioning:
  carlos-ctl destroy-data --confirm <server-name>
                                  DESTROY the clinical record on this host.
                                  Removing the package never does this; this
                                  is the only supported way, and it makes you
                                  type the host's own name back to it.

Configuration — the loop is: edit the file, then run the verb beside it:
  /etc/carlos-emr/carlos-emr.env       site settings -> carlos-ctl init-config
  /etc/carlos-emr/carlos.properties    app config    -> carlos-ctl restart
  /etc/carlos-emr/backup.env           backups       -> next timer run; prove
                                                        with carlos-ctl backup full
  /etc/carlos-emr/modsecurity/         WAF policy    -> carlos-ctl waf reload
  /etc/carlos-emr/tomcat/              Tomcat        -> carlos-ctl restart

Full documentation: /usr/share/doc/carlos-emr/README.Debian
"""


def _cmd_status(argv) -> int:
    # Propagate systemctl's own verdict: an inactive service must not read as
    # exit 0 to a script wrapping this verb.
    rc = util.run(["systemctl", "--no-pager", "--lines=0", "status",
                   "carlos-emr.service", "nginx.service", "mariadb.service"]).returncode
    print("\ntimers:")
    util.run(["systemctl", "--no-pager", "list-timers", "carlos-emr*"])
    return rc


def _cmd_lifecycle(verb: str, argv) -> int:
    # Thin passthroughs so day-two administration has one entry point.
    # `restart` is what applies carlos-emr.env and carlos.properties changes;
    # expect ~2 minutes for the webapp to redeploy.
    if argv:
        # Silently discarding arguments turned 'carlos-ctl restart nginx'
        # into a restart of the EMR — the opposite of what was asked.
        die(f"'{verb}' takes no arguments; it manages carlos-emr.service only "
            f"(for other units use systemctl directly)")
    need_root(verb)
    os.execvp("systemctl", ["systemctl", verb, "carlos-emr.service"])
    raise AssertionError("unreachable: execvp replaces the process")


def _cmd_cert(argv) -> int:
    need_root("cert")
    os.execv(os.path.join(LIB, "carlos-emr-cert"),
             [os.path.join(LIB, "carlos-emr-cert")] + list(argv))
    raise AssertionError("unreachable: execv replaces the process")


def _cmd_cert_renew(argv) -> int:
    # Aligned with the podman verb name; the timer calls the helper directly.
    need_root("cert-renew")
    os.execv(os.path.join(LIB, "carlos-emr-cert"),
             [os.path.join(LIB, "carlos-emr-cert"), "renew"])
    raise AssertionError("unreachable: execv replaces the process")


def _cmd_backup(argv) -> int:
    """`full` is the podman-aligned name; `run` stays as a compat spelling.

    full and verify go THROUGH THE SYSTEMD UNITS, not runuser: the units
    carry CAP_DAC_READ_SEARCH (the application creates some document
    directories 0700, and without the capability a manual run silently
    skipped them — restic exit 3 — while the nightly timer succeeded, which
    is exactly the sort of "works at night, fails by hand" split that erodes
    trust in the tool). Driving the same unit also means a manual run and a
    timer run are byte-for-byte the same code path. The read-only verbs
    (status/snapshots/restic) run as the backup user directly — they only
    touch the repository carlos-backup owns."""
    need_root("backup")
    sub = list(argv) or ["status"]
    if sub[0] in ("full", "run"):
        util.log("running the nightly backup unit (journalctl -u carlos-emr-backup -f to watch)")
        rc = util.run(["systemctl", "start", "carlos-emr-backup.service"]).returncode
        if rc == 0:
            util.log("backup complete")
        else:
            util.warn("the backup FAILED — journalctl -u carlos-emr-backup -n 50")
        return rc
    if sub[0] == "verify":
        util.log("running the restore-drill unit (journalctl -u carlos-emr-backup-verify -f to watch)")
        rc = util.run(["systemctl", "start", "carlos-emr-backup-verify.service"]).returncode
        if rc == 0:
            util.log("restore drill passed")
        else:
            util.warn("the restore drill FAILED — journalctl -u carlos-emr-backup-verify -n 50")
        return rc
    os.execvp("runuser", ["runuser", "-u", "carlos-backup", "--",
                          os.path.join(LIB, "carlos-emr-backup")] + sub)
    raise AssertionError("unreachable: execvp replaces the process")


def _cmd_logs(argv) -> int:
    os.execvp("journalctl", ["journalctl", "-u", "carlos-emr.service"] + list(argv))
    raise AssertionError("unreachable: execvp replaces the process")


_VERBS = {
    "check": validate.cmd_check,
    "status": _cmd_status,
    "db": dbops.cmd_db,
    "db-dump": dbops.cmd_db_dump,
    "db-users": dbops.cmd_db_users,
    "db-migrate": dbops.cmd_db_migrate,
    "db-info": dbops.make_flyway_cmd("info"),
    "db-validate": dbops.make_flyway_cmd("validate"),
    "db-baseline": dbops.make_flyway_cmd("baseline"),
    "db-repair": dbops.make_flyway_cmd("repair"),
    "db-apply-settings": dbops.cmd_db_apply_settings,
    "demo-data": dbops.cmd_demo_data,
    "cert": _cmd_cert,
    "cert-renew": _cmd_cert_renew,
    "waf": waf.cmd_waf,
    "backup": _cmd_backup,
    "init-config": config.cmd_init_config,
    "bootstrap-admin": dbops.cmd_bootstrap_admin,
    "rotate": dbops.cmd_rotate,
    "destroy-data": dbops.cmd_destroy_data,
    "logs": _cmd_logs,
    "restart": lambda argv: _cmd_lifecycle("restart", argv),
    "start": lambda argv: _cmd_lifecycle("start", argv),
    "stop": lambda argv: _cmd_lifecycle("stop", argv),
}


def main(argv: Optional[List[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if os.environ.get("CARLOS_CTL_INVOKED_AS") == "carlosctl":
        print("carlos-ctl: note: 'carlosctl' is the old name; use 'carlos-ctl' "
              "(matching the carlos-podman tool). This alias keeps working.",
              file=sys.stderr)
    if not args or args[0] in ("-h", "--help", "help"):
        print(_USAGE)
        return 0
    verb, rest = args[0], args[1:]
    handler = _VERBS.get(verb)
    if handler is None:
        die(f"unknown command: {verb} (try: carlos-ctl --help)")
    try:
        return int(handler(rest) or 0)
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())

# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The `check` verb — same name and same job as carlos-podman's carlos-ctl
check: prove the DEPLOYED system end to end, probing live behaviour rather
than trusting configuration files to describe it."""

import os
import re
import time

from . import config, dbops, util
from .util import (
    BACKUP_ENV, CONF_DIR, GREEN, LIB, RED, RESET, YELLOW, out, run,
)

_failures = 0


def _ok(msg):   print(f"  {GREEN}OK{RESET}      {msg}")
def _note(msg): print(f"  {YELLOW}NOTE{RESET}    {msg}")
def _bad(msg):
    global _failures
    _failures += 1
    print(f"  {RED}FAIL{RESET}    {msg}")


def _is_loopback(addr: str) -> bool:
    # Strip BOTH brackets: ss prints IPv6 listeners as [::1]:port, and after
    # the port strip a trailing ] remains — matching only the de-[ form
    # rejected a perfectly correct [::1] bind.
    a = addr.lstrip("[").rstrip("]")
    return a.startswith("127.") or a == "::1" or a.startswith("::ffff:127.")


def _listener(port: str):
    """First listener bound to exactly this port. Split on the LAST colon —
    a plain endswith(":443") also matched ":8443" and would have reported
    the front door up when only some other service was."""
    for line in out(["ss", "-ltnH"]).splitlines():
        cols = line.split()
        if len(cols) >= 4 and cols[3].rsplit(":", 1)[-1] == port:
            return cols[3]
    return None


def _curl(args, timeout=20):
    cp = run(["curl", "-sk", "--max-time", str(timeout)] + args, capture_output=True)
    return cp


def cmd_check(argv) -> int:
    global _failures
    _failures = 0
    s = config.load()
    print(f"\nCARLOS EMR deployment check ({s.server_name})\n")

    print("services")
    for unit in ("mariadb", "nginx", "carlos-emr"):
        if run(["systemctl", "is-active", "--quiet", unit]).returncode == 0:
            _ok(f"{unit} is running")
        else:
            _bad(f"{unit} is NOT running (systemctl status {unit})")
    for unit in ("carlos-emr-backup.timer", "carlos-emr-backup-verify.timer",
                 "carlos-emr-cert-renew.timer"):
        if run(["systemctl", "is-enabled", "--quiet", unit], capture_output=True).returncode == 0:
            _ok(f"{unit} is enabled")
        else:
            _bad(f"{unit} is NOT enabled")

    print("\nprocess ownership")
    # The whole point of the user split — prove it at runtime rather than
    # trusting that the unit files still say what they said at install time.
    owners = set(out(["ps", "-o", "user=", "-C", "java"]).split())
    if not owners:
        _bad("no java process found")
    elif "root" in owners:
        _bad(f"a java process is running as ROOT: {' '.join(owners)}")
    else:
        _ok(f"application JVM runs as: {' '.join(owners)}")

    print("\nnetwork exposure")
    # Tomcat must not be reachable except on loopback: anything else is a
    # path around the WAF — and therefore around TLS, the headers and the
    # rate limit in one step.
    addr = _listener("18080")
    if addr is None:
        _bad("nothing is listening on 18080 — is carlos-emr running?")
    elif _is_loopback(addr.rsplit(":", 1)[0]):
        _ok(f"Tomcat listens on loopback only ({addr})")
    else:
        _bad(f"Tomcat is listening on {addr} — requests can bypass the WAF")
    addr = _listener("3306")
    if addr is not None:
        if _is_loopback(addr.rsplit(":", 1)[0]):
            _ok(f"MariaDB listens on loopback only ({addr})")
        else:
            _bad(f"MariaDB is listening on {addr} — check bind-address in "
                 "/etc/mysql/mariadb.conf.d/60-carlos-emr.cnf")
    if _listener("443"):
        _ok("nginx is listening on 443")
    else:
        _bad("nothing is listening on 443")

    print("\nTLS")
    run([os.path.join(LIB, "carlos-emr-cert"), "status"])
    fullchain = os.path.join(CONF_DIR, "tls", "fullchain.pem")
    if os.path.exists(fullchain):
        if run(["openssl", "x509", "-checkend", str(21 * 86400), "-noout",
                "-in", fullchain], capture_output=True).returncode == 0:
            _ok("certificate is valid for at least 21 more days")
        else:
            _bad("certificate expires within 21 days")

    print("\nfront door")
    # Probe through the CONFIGURED server name resolved to loopback: it
    # exercises SNI and the certificate a real client sees, and avoids CRS
    # rule 920350 ("Host header is a numeric IP address") filling the audit
    # log with noise this check generated.
    resolve = ["--resolve", f"{s.server_name}:443:127.0.0.1"]
    url = f"https://{s.server_name}/carlos/"
    # Retry a while before calling it down: deploying this webapp takes about
    # two minutes from cold, and a false alarm here teaches operators to
    # ignore the tool.
    code = "000"
    for attempt in range(12):
        cp = _curl(resolve + ["-o", "/dev/null", "-w", "%{http_code}", url])
        code = cp.stdout.strip() or "000"
        if code in ("200", "302", "303"):
            break
        if attempt == 0:
            _note("front door not answering yet; waiting up to 2 minutes for the application to deploy")
        time.sleep(10)
    if code in ("200", "302", "303"):
        _ok(f"{url} returned {code}")
    elif code == "000":
        _bad(f"no HTTPS response from the front door at {url} after 2 minutes")
    else:
        _bad(f"{url} returned {code}")
    # nginx add_header does not inherit into a block that declares its own
    # add_header — the exact regression that silently strips headers from a
    # rate-limited login location — so probe the served header, never the
    # configuration.
    hdrs = _curl(resolve + ["-I", url], timeout=10).stdout.lower()
    if "strict-transport-security:" in hdrs:
        _ok("HSTS header is served")
    else:
        _bad("HSTS header is missing — check /etc/nginx/snippets/carlos-emr-headers.conf")
    # DrugRef speaks unauthenticated XML-RPC; the front door must have no
    # route to it. Anything but 404 means one of the two gates is gone.
    cp = _curl(resolve + ["-o", "/dev/null", "-w", "%{http_code}",
                          f"https://{s.server_name}/drugref2/DrugrefService"], timeout=10)
    if cp.stdout.strip() == "404":
        _ok("DrugRef is not reachable through the front door")
    else:
        _bad("the front door exposes /drugref2 — it is an unauthenticated service")

    print("\nWAF")
    engine = ""
    with open(os.path.join(CONF_DIR, "modsecurity", "main.conf"),
              encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = re.match(r"^SecRuleEngine\s+(\S+)", line)
            if m:
                engine = m.group(1)
                break
    if engine == "On":
        _ok("ModSecurity rule engine is On (blocking)")
    else:
        _bad(f"ModSecurity rule engine is {engine or '?'} — it is not blocking anything")
    # A live probe: a config that says "blocking" and a WAF that blocks are
    # different claims. A canonical CRS SQLi signature must never reach the
    # application.
    cp = _curl(resolve + ["-o", "/dev/null", "-w", "%{http_code}",
                          f"https://{s.server_name}/carlos/index.jsp?id=1%27%20OR%20%271%27=%271"],
               timeout=10)
    if cp.stdout.strip() == "403":
        _ok("a probe SQL-injection request was blocked (403)")
    else:
        _bad(f"a probe SQL-injection request returned {cp.stdout.strip() or '000'}, expected 403")

    print("\nDrugRef")
    # A live XML-RPC call, not a page probe: the context can deploy and still
    # have a dead pool behind it (seen in validation — a connection leak that
    # only surfaced on the second search). Only an actual lookup exercises
    # pool checkout, the query and the marshalling.
    if os.path.exists(os.path.join(CONF_DIR, "tomcat", "Catalina", "localhost", "drugref2.xml")):
        body = ('<?xml version="1.0"?><methodCall><methodName>list_search_element3'
                '</methodName><params><param><value><string>ASA</string></value>'
                '</param></params></methodCall>')
        cp = run(["curl", "-s", "--max-time", "30", "-X", "POST",
                  "-H", "Content-Type: text/xml", "-d", body,
                  "http://127.0.0.1:18080/drugref2/DrugrefService"], capture_output=True)
        resp = cp.stdout or ""
        if "<methodResponse>" in resp:
            if "<string>None found</string>" in resp:
                _bad("DrugRef answers XML-RPC but finds no drugs — pool exhaustion or an "
                     "empty dataset (journalctl -u carlos-emr | grep -i hikari)")
            else:
                _ok("DrugRef answers a live drug lookup over XML-RPC")
        else:
            _bad("DrugRef is not answering XML-RPC on loopback (is the /drugref2 context "
                 "deployed? carlos-ctl logs | grep drugref2)")
    else:
        _note("carlos-emr-drugref is not installed; prescription drug lookups will return nothing")

    print("\ndatabase")
    if dbops.db_root_ok():
        _ok("MariaDB reachable as root over the unix socket")
        n = out(["mariadb", "--protocol=socket", "--user=root", "-N", "-B", "-e",
                 f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{s.db_name}'"])
        if n.isdigit() and int(n) > 100:
            _ok(f"{s.db_name} has {n} tables")
        else:
            _bad(f"{s.db_name} has only {n or 0} tables — has the schema been migrated?")
        n = out(["mariadb", "--protocol=socket", "--user=root", "-N", "-B", "-e",
                 f"SELECT COUNT(*) FROM `{s.db_name}`.flyway_schema_history WHERE success=1"])
        if n.isdigit() and int(n) > 0:
            _ok(f"flyway_schema_history has {n} successful migration(s)")
        else:
            _bad("flyway_schema_history is empty or missing — the application's boot-time "
                 "schema gate will fail")
    else:
        _bad("cannot reach MariaDB as root over the unix socket")

    print("\nbackups")
    stamp = "/var/backups/carlos-emr/.last-success"
    if os.path.exists(stamp):
        with open(stamp) as fh:
            _ok(f"last successful backup: {fh.read().strip()}")
    else:
        _bad("no backup has ever succeeded (systemctl start carlos-emr-backup)")
    stamp = "/var/backups/carlos-emr/.last-verify"
    if os.path.exists(stamp):
        with open(stamp) as fh:
            _ok(f"last restore drill: {fh.read().strip()}")
    else:
        _note("no restore drill has run yet; the weekly timer will run one, or start "
              "carlos-emr-backup-verify now")
    repo = util.env_get(BACKUP_ENV, "RESTIC_REPOSITORY") or ""
    if repo.startswith("/var/backups"):
        _note("backups are stored on THIS HOST only. That is not disaster recovery — set an "
              f"offsite RESTIC_REPOSITORY in {BACKUP_ENV}.")

    print()
    if _failures == 0:
        print(f"{GREEN}All checks passed.{RESET}\n")
        return 0
    print(f"{RED}{_failures} check(s) failed.{RESET}\n")
    return 1

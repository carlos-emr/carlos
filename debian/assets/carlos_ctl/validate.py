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
    BACKUP_ENV, CONF_DIR, GREEN, LIB, PROPERTIES, RED, RESET, YELLOW, need_root, out, run,
)

_failures = 0


def _ok(msg):
    print(f"  {GREEN}OK{RESET}      {msg}")


def _note(msg):
    print(f"  {YELLOW}NOTE{RESET}    {msg}")


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


def _listeners(port: str):
    """EVERY listener bound to exactly this port — a service can bind
    loopback and a public address at once, and checking only the first
    line of ss output missed the second. Split on the LAST colon: a plain
    endswith(":443") also matched ":8443" and would have reported the
    front door up when only some other service was."""
    found = []
    for line in out(["ss", "-ltnH"]).splitlines():
        cols = line.split()
        if len(cols) >= 4 and cols[3].rsplit(":", 1)[-1] == port:
            found.append(cols[3])
    return found


def _listener(port: str):
    """First listener on the port, for is-anything-there checks."""
    ls = _listeners(port)
    return ls[0] if ls else None


def _curl(args, timeout=20):
    cp = run(["curl", "-sk", "--max-time", str(timeout)] + args, capture_output=True)
    return cp


def cmd_check(argv) -> int:
    global _failures
    _failures = 0
    # Root is required for what check READS (credential files, TLS pair,
    # the WAF policy): without it half the probes false-failed with
    # misleading diagnoses instead of one clear message.
    need_root("check")
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
    addrs = _listeners("18080")
    exposed = [a for a in addrs if not _is_loopback(a.rsplit(":", 1)[0])]
    if not addrs:
        _bad("nothing is listening on 18080 — is carlos-emr running?")
    elif exposed:
        _bad(f"Tomcat is listening on {', '.join(exposed)} — requests can bypass the WAF")
    else:
        _ok(f"Tomcat listens on loopback only ({', '.join(addrs)})")
    addrs = _listeners("3306")
    exposed = [a for a in addrs if not _is_loopback(a.rsplit(":", 1)[0])]
    if exposed:
        _bad(f"MariaDB is listening on {', '.join(exposed)} — check bind-address in "
             "/etc/mysql/mariadb.conf.d/60-carlos-emr.cnf")
    elif addrs:
        _ok(f"MariaDB listens on loopback only ({', '.join(addrs)})")
    if _listener("443"):
        _ok("nginx is listening on 443")
    else:
        _bad("nothing is listening on 443")
    # The MariaDB drop-in leans on AppArmor as the file-access control (it is
    # why secure_file_priv is not set there), so this check asserts the
    # profile is actually loaded and enforcing rather than assuming it.
    profiles = "/sys/kernel/security/apparmor/profiles"
    try:
        with open(profiles, encoding="utf-8", errors="replace") as fh:
            entries = fh.read()
    except OSError:
        _note("AppArmor is not available on this kernel — the MariaDB profile the "
              "drop-in relies on is not in effect (containers/VMs without AppArmor)")
    else:
        m = re.search(r"^(?:/usr/sbin/)?mariadbd \((\w+)\)$", entries, re.M)
        if m and m.group(1) == "enforce":
            _ok("AppArmor profile for mariadbd is loaded and enforcing")
        elif m:
            _bad(f"AppArmor profile for mariadbd is in {m.group(1)} mode, not enforce "
                 "(sudo aa-enforce /etc/apparmor.d/mariadbd — from the apparmor-utils package)")
        else:
            _bad("no AppArmor profile loaded for mariadbd — the file-access control the "
                 "MariaDB drop-in documents is missing")

    # The eForm render browser is optional (Recommends:), so probe it only when its
    # env file says it is installed. Every check here maps to a way it silently breaks:
    # the unit not running, the AppArmor userns grant missing on a kernel that enforces
    # apparmor_restrict_unprivileged_userns (Chromium aborts "No usable sandbox!" and
    # every eForm print/fax/archive fails closed), or carlos.properties pointing the
    # JVM at a different port/token than the driver actually serves.
    # Gate on the chromedriver BINARY, which a plain `apt remove` deletes — not on
    # render-browser.env, which survives until purge: keying on the env file made check
    # report a broken renderer on hosts where the operator deliberately removed the
    # package. Binary-present-but-env-missing IS a fault (postinst never completed).
    render_env = "/etc/carlos-emr/render-browser.env"
    render_driver = "/usr/lib/carlos-emr/chromium/chromedriver"
    if os.path.exists(render_driver) and not os.path.exists(render_env):
        print("\neForm render browser")
        _bad("the renderer package is installed but render-browser.env is missing — its "
             "postinst never completed (sudo apt install --reinstall carlos-emr-eform-renderer)")
    elif not os.path.exists(render_driver) and os.path.exists(render_env):
        print("\neForm render browser")
        _note("render-browser.env is left over from a removed carlos-emr-eform-renderer "
              "(it holds the url-base token and is deleted on purge); the renderer itself "
              "is not installed, so its checks are skipped")
    elif os.path.exists(render_driver):
        print("\neForm render browser")
        if run(["systemctl", "is-active", "--quiet", "carlos-emr-chromedriver"]).returncode == 0:
            _ok("carlos-emr-chromedriver is running")
        else:
            _bad("carlos-emr-chromedriver is NOT running "
                 "(systemctl status carlos-emr-chromedriver)")
        try:
            with open(profiles, encoding="utf-8", errors="replace") as fh:
                entries = fh.read()
        except OSError:
            entries = ""
        restricted = "0"
        try:
            with open("/proc/sys/kernel/apparmor_restrict_unprivileged_userns",
                      encoding="ascii") as fh:
                restricted = fh.read().strip()
        except OSError:
            pass
        if re.search(r"^carlos-emr-chromium ", entries, re.M):
            _ok("AppArmor profile carlos-emr-chromium is loaded (userns grant for the sandbox)")
        elif restricted == "1":
            _bad("AppArmor profile carlos-emr-chromium is NOT loaded and this kernel "
                 "restricts unprivileged user namespaces — the sandboxed browser cannot "
                 "start and eForm PDF rendering fails closed "
                 "(sudo apparmor_parser -r /etc/apparmor.d/carlos-emr-chromium)")
        else:
            _note("AppArmor profile carlos-emr-chromium is not loaded; the sandbox works "
                  "anyway because this kernel does not restrict unprivileged user namespaces")
        port, url_base = config._render_browser_endpoint()
        prop_url = None
        try:
            with open(PROPERTIES, encoding="utf-8", errors="replace") as fh:
                # prop_set writes "key = value"; tolerate any spacing around "=" and an
                # unspaced hand edit alike, or this check reports every healthy install
                # as misconfigured.
                m = re.search(r"^eform_pdf_browser_service_url\s*=\s*(\S+)", fh.read(), re.M)
                prop_url = m.group(1) if m else None
        except OSError:
            pass
        # Mirror config.py's composition exactly, including the empty-url-base shape it
        # deliberately writes mid-install: a base-less URL is then EXPECTED, and the broken
        # thing is the missing token — whose fix is the renderer postinst, not init-config.
        expected = None
        if port:
            expected = f"http://127.0.0.1:{port}/{url_base}" if url_base else f"http://127.0.0.1:{port}"
        if prop_url and expected and prop_url == expected:
            if url_base:
                _ok("eform_pdf_browser_service_url matches render-browser.env")
            else:
                _bad("CARLOS_RENDER_URL_BASE is empty in render-browser.env — the chromedriver "
                     "unit refuses to start without the token; reinstall the renderer package "
                     "(its postinst regenerates it): sudo apt install --reinstall "
                     "carlos-emr-eform-renderer")
        elif prop_url is None:
            _bad("carlos.properties has no eform_pdf_browser_service_url — the JVM cannot "
                 "reach the render browser (sudo carlos-ctl init-config)")
        else:
            _bad("eform_pdf_browser_service_url does not match render-browser.env — the JVM "
                 "and chromedriver disagree on port or url-base token "
                 "(sudo carlos-ctl init-config, then systemctl restart carlos-emr)")

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
    # Probe the address nginx actually listens on: with a non-default
    # CARLOS_BIND_IP nothing answers on loopback and every front-door check
    # would false-fail on a healthy install.
    probe_ip = s.bind_ip if s.bind_ip not in ("", "0.0.0.0", "::") else "127.0.0.1"
    resolve = ["--resolve", f"{s.server_name}:443:{probe_ip}"]
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

    # /ws/** (CXF SOAP/REST) is exempt from the login filter: every service is
    # expected to authenticate itself (OAuth 1.0a / WS-Security). Prove two
    # invariants at runtime rather than trusting the config — the service-list
    # catalog is not handed to an anonymous client, and a PHI data service
    # refuses an unauthenticated call.
    # CXF renders the service-list catalog at the SERVLET ROOT (/carlos/ws/),
    # not at a /services sub-path — a GET to /carlos/ws/services is pathInfo
    # "/services", matches no destination and 404s even with the catalog
    # enabled, so probing it would false-pass. Probe the real listing URL.
    ws_list = _curl(resolve + [f"https://{s.server_name}/carlos/ws/"], timeout=10).stdout
    if "Available SOAP services" in ws_list or "Available RESTful services" in ws_list:
        _bad("the CXF service-list catalog is served at /carlos/ws/ — set "
             "hide-service-list-page=true on the CXFServlet (WEB-INF/web.xml)")
    else:
        _ok("the CXF service-list catalog is not exposed")
    # An empty SOAP envelope carries no WS-Security header, so a gated data
    # service must reject it. Distinguish a genuine auth rejection (the WSS4J
    # interceptor maps a missing token to 400/401) from a 200 (auth bypassed)
    # and from a 403/404/redirect (a WAF block or moved path that MASKS the auth
    # check rather than proving it) — the latter is inconclusive, not a pass.
    ws_code = _curl(resolve + ["-o", "/dev/null", "-w", "%{http_code}", "-X", "POST",
                               "-H", "Content-Type: text/xml", "-d",
                               "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                               "<s:Body/></s:Envelope>",
                               f"https://{s.server_name}/carlos/ws/DemographicService"],
                    timeout=10).stdout.strip()
    if ws_code == "200":
        _bad("an unauthenticated SOAP call to /carlos/ws/DemographicService returned 200 — "
             "the WS-Security authentication gate is not enforcing")
    elif ws_code in ("400", "401"):
        _ok(f"an unauthenticated web-service call is rejected by the auth gate ({ws_code})")
    else:
        _note(f"could not confirm the /ws auth gate: /carlos/ws/DemographicService returned "
              f"{ws_code or '000'} (a WAF 403, a 404, or a redirect can mask the auth check)")

    print("\nWAF")
    engine = ""
    try:
        with open(os.path.join(CONF_DIR, "modsecurity", "main.conf"),
                  encoding="utf-8", errors="replace") as fh:
            for line in fh:
                m = re.match(r"^SecRuleEngine\s+(\S+)", line)
                if m:
                    engine = m.group(1)
                    break
    except OSError as e:
        _bad(f"cannot read the WAF policy: {e} — reinstall carlos-emr to restore it")
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
    # The docs promise FRESHNESS, not existence: a .last-success from three
    # weeks ago is a monitoring gap, not a passing check. The nightly timer
    # runs daily, so anything older than 2 days is a failure; a brand-new
    # install (state directory younger than 2 days) has legitimately not had
    # its first nightly yet and gets a note instead of a false alarm.
    import datetime as _dt
    def _stamp_age_days(path):
        try:
            with open(path) as fh:
                when = _dt.datetime.fromisoformat(fh.read().strip())
            return (_dt.datetime.now(when.tzinfo) - when).total_seconds() / 86400, when
        except (OSError, ValueError):
            return None, None
    state_dir = "/var/backups/carlos-emr"
    stamp = os.path.join(state_dir, ".last-success")
    if os.path.exists(stamp):
        age, when = _stamp_age_days(stamp)
        if age is None:
            _bad(f"{stamp} is unreadable or malformed")
        elif age > 2:
            _bad(f"last successful backup is {age:.1f} days old ({when.isoformat()}) — "
                 "check 'journalctl -u carlos-emr-backup'")
        else:
            _ok(f"last successful backup: {when.isoformat()}")
    else:
        import time as _time
        try:
            dir_age = (_time.time() - os.stat(state_dir).st_ctime) / 86400
        except OSError:
            dir_age = 99
        if dir_age < 2:
            _note("no backup yet (fresh install); the nightly timer runs at 01:30, or start "
                  "one now: systemctl start carlos-emr-backup")
        else:
            _bad("no backup has ever succeeded (systemctl start carlos-emr-backup)")
    stamp = os.path.join(state_dir, ".last-verify")
    if os.path.exists(stamp):
        age, when = _stamp_age_days(stamp)
        if age is None:
            _bad(f"{stamp} is unreadable or malformed")
        elif age > 9:
            _bad(f"last restore drill is {age:.1f} days old — the weekly drill is not running "
                 "(systemctl status carlos-emr-backup-verify.timer)")
        else:
            _ok(f"last restore drill: {when.isoformat()}")
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

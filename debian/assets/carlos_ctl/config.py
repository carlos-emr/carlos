# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Site settings (carlos-emr.env) and the init-config apply step.

Counterpart of carlos-podman carlos_ctl/config.py: there, configuration is
rendered by Ansible from host_vars; here, the single source of site truth is
/etc/carlos-emr/carlos-emr.env and this module derives everything else from it
— and APPLIES it, so the operator loop is "edit the file, run
carlos-ctl init-config" with nothing further to remember.
"""

import os
import re

from . import util
from .util import (
    CHROMIUM_DIR, CONF_DIR, ENV_FILE, LIB, PROPERTIES, RENDER_BROWSER_ENV, SHARE, STATE,
    die, env_get, log, prop_comment, prop_get, prop_set, run, warn,
)


class Settings:
    """The carlos-emr.env values every verb needs, validated once."""

    def __init__(self) -> None:
        self.server_name = env_get(ENV_FILE, "CARLOS_SERVER_NAME") or "localhost"
        # 0.0.0.0 is the documented default for a clinic server (see the
        # skeleton env file); the flag below tells scanners it is deliberate.
        self.bind_ip = env_get(ENV_FILE, "CARLOS_BIND_IP") or "0.0.0.0"  # nosec B104
        self.province = (env_get(ENV_FILE, "CARLOS_PROVINCE") or "on").lower()
        self.db_host = env_get(ENV_FILE, "CARLOS_DB_HOST") or "127.0.0.1"
        self.db_port = env_get(ENV_FILE, "CARLOS_DB_PORT") or "3306"
        self.db_name = env_get(ENV_FILE, "CARLOS_DB_NAME") or "carlos"
        # The database name is interpolated into backtick-quoted DDL run as
        # database root (db-users, destroy-data). The file it comes from is
        # root-owned, so this is hardening rather than a live injection path —
        # but the blast radius of a stray backtick there is a DROP on a PHI
        # host, so refuse anything that is not a plain identifier, once, for
        # every command.
        if not re.fullmatch(r"[A-Za-z0-9_]+", self.db_name):
            die(f"CARLOS_DB_NAME ('{self.db_name}') must be a plain identifier (A-Za-z0-9_)")
        if self.province not in ("on", "bc"):
            die(f"CARLOS_PROVINCE ('{self.province}') must be 'on' or 'bc'")

    @property
    def flyway_locations(self) -> str:
        return f"classpath:db/migration/common,classpath:db/migration/{self.province}"


def load() -> Settings:
    return Settings()


def cmd_init_config(argv) -> int:
    util.need_root("init-config")
    s = load()
    if not os.path.isfile(PROPERTIES):
        die(f"{PROPERTIES} does not exist; reinstall the package")

    doc = f"{STATE}/CarlosDocument/carlos"
    province_uc = s.province.upper()

    # JDBC parameters, and why each one is here:
    #   zeroDateTimeBehavior=round        the OSCAR-lineage schema contains
    #       0000-00-00 dates; the driver's default throws on read, which makes
    #       whole clinical screens fail. `round` returns 0001-01-01 — a
    #       FABRICATED date, not a null; it is upstream's long-standing choice
    #       and the application expects it.
    #   useOldAliasMetadataBehavior=true  legacy DAOs read columns by their
    #       pre-alias names.
    #   jdbcCompliantTruncation=false     matches the server's non-strict
    #       sql_mode; without it the driver rejects what the server accepts.
    #   characterEncoding/connectionCollation  keep the connection utf8mb4 so
    #       the server-side default is not silently downgraded.
    prop_set(PROPERTIES, "db_name",
             f"{s.db_name}?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true"
             f"&jdbcCompliantTruncation=false&characterEncoding=UTF-8"
             f"&connectionCollation=utf8mb4_general_ci")
    prop_set(PROPERTIES, "db_uri", f"jdbc:mysql://{s.db_host}:{s.db_port}/")
    prop_set(PROPERTIES, "db_type", "mysql")
    prop_set(PROPERTIES, "db_driver", "com.mysql.cj.jdbc.Driver")

    # Document storage. 2750 carlos:carlos with the backup user reading
    # through group membership; see debian/carlos-emr.tmpfiles.
    prop_set(PROPERTIES, "BASE_DOCUMENT_DIR", f"{STATE}/CarlosDocument/")
    prop_set(PROPERTIES, "DOCUMENT_DIR", f"{doc}/document/")
    prop_set(PROPERTIES, "INCOMINGDOCUMENT_DIR", f"{doc}/incomingdocs")
    prop_set(PROPERTIES, "INVOICE_DIR", f"{doc}/billing/invoices")
    prop_set(PROPERTIES, "FAX_INCOMING_DIR", f"{doc}/fax-incoming")
    prop_set(PROPERTIES, "tomcat_path", f"{STATE}/catalina/")

    prop_set(PROPERTIES, "billregion", province_uc)
    prop_set(PROPERTIES, "buildtag", "carlos-emr-deb")
    # project_home is a legacy OSCAR name used two ways: as the CarlosDocument
    # subdirectory, and as a fallback URL context prefix when the eForm PDF
    # composer and the MOH billing views cannot see a real context path. Both
    # are "carlos" in this layout; the upstream default of "oscar_mcmaster"
    # would send both down a path that does not exist here.
    prop_set(PROPERTIES, "project_home", "carlos")

    # Belt and braces for the build stamp: the skeleton comes from the built
    # WAR (already substituted), but if a future build ever ships the raw
    # ${...} placeholders the application renders them on the LOGIN page, to
    # every unauthenticated visitor.
    pkg_version = util.out(["dpkg-query", "-f", "${Version}", "-W", "carlos-emr"]) or "unknown"
    for key, fallback in (("buildDate", util.out(["date", "-I"])),
                          ("buildVersion", f"carlos-emr {pkg_version}")):
        cur = prop_get(PROPERTIES, key) or ""
        if "${" in cur:
            prop_set(PROPERTIES, key, fallback)

    # The schema gate. `validate` is the production posture: the application
    # refuses to start against a schema it was not built for, instead of
    # failing later with a column-not-found error mid-consultation.
    # Migrations are applied by the explicit `carlos-ctl db-migrate`.
    prop_set(PROPERTIES, "carlos.flyway.onBoot", "validate")
    prop_set(PROPERTIES, "carlos.flyway.locations", s.flyway_locations)

    # DrugRef is co-deployed in this Tomcat, loopback-only.
    prop_set(PROPERTIES, "drugref_url", "http://127.0.0.1:18080/drugref2/DrugrefService")

    # eForm-to-PDF renderer. carlos-emr-eform-renderer ships a pinned Chromium
    # and a chromedriver built from the same revision, run as the dedicated
    # carlos-emr-chromedriver service; the application CONNECTS to that service
    # (eform_pdf_browser_service_url) and never spawns or downloads a driver.
    #
    # The probe follows the browser rather than being hard-off: with no browser
    # installed it could only fail and log an error burst on every boot, but
    # once one IS installed a silent probe is worse than none — a broken
    # renderer then surfaces as a failed print mid-consultation instead of one
    # WARN at startup. "warn" is the application's own documented default; it
    # logs and continues, and never blocks deployment.
    chromium = f"{CHROMIUM_DIR}/chrome"
    chromedriver = f"{CHROMIUM_DIR}/chromedriver"
    if os.path.exists(chromium) and os.path.exists(chromedriver):
        prop_set(PROPERTIES, "eform_pdf_browser_chromium_path", chromium)
        # The application CONNECTS to chromedriver; it no longer spawns one. The
        # url-base is a bearer credential generated into render-browser.env at
        # install, and the two files are read by two accounts that deliberately
        # cannot read each other's — hence the value is composed here rather than
        # shared. A missing/empty url-base is tolerated HERE so init-config never
        # blocks, but the chromedriver unit itself refuses to start on an empty
        # CARLOS_RENDER_URL_BASE (its ExecStartPre guard): a bare-root endpoint
        # would silently drop the capability-token defence, and everything else in
        # this design fails closed. The renderer package's postinst generates the
        # token, so this branch only matters mid-install or after manual edits.
        port, url_base = _render_browser_endpoint()
        service_url = f"http://127.0.0.1:{port}"
        if url_base:
            service_url = f"{service_url}/{url_base}"
        prop_set(PROPERTIES, "eform_pdf_browser_service_url", service_url)
        # Retired with the spawning code path. Comment out rather than delete so
        # an operator can see it was deliberately retired, not silently dropped.
        prop_comment(PROPERTIES, "eform_pdf_browser_chromedriver_path")
        prop_set(PROPERTIES, "eform_pdf_browser_startup_check", "warn")
    else:
        # No browser installed. Comment the endpoint out rather than leaving it
        # pointing at a service that is no longer running — the renderer fails
        # closed, so a stale value would turn every eForm print into an error
        # naming a URL the operator just deliberately removed. The binary paths
        # are retracted for the same reason: they would otherwise keep naming
        # files the renderer package's removal just deleted.
        prop_comment(PROPERTIES, "eform_pdf_browser_service_url")
        prop_comment(PROPERTIES, "eform_pdf_browser_chromium_path")
        prop_comment(PROPERTIES, "eform_pdf_browser_chromedriver_path")
        prop_set(PROPERTIES, "eform_pdf_browser_startup_check", "off")

    # --- paths the upstream skeleton still aims at the OLD FHS location -----
    # The stock carlos.properties predates this packaging and carries several
    # path defaults under /var/lib/CarlosDocument, which does not exist here.
    # Each of the following is READ by live code (verified in the source), so
    # a stale value is a runtime failure in that feature, not cosmetics.
    prop_set(PROPERTIES, "log.purge.outputdir", f"{doc}/document/")
    prop_set(PROPERTIES, "ONEDT_INBOX", f"{doc}/onEDTDocs/inbox/")
    prop_set(PROPERTIES, "ONEDT_OUTBOX", f"{doc}/onEDTDocs/outbox/")
    prop_set(PROPERTIES, "ONEDT_SENT", f"{doc}/onEDTDocs/sent/")
    prop_set(PROPERTIES, "ONEDT_ARCHIVE", f"{doc}/onEDTDocs/archive/")

    # The two clinic-logo examples point at an image that exists on no system.
    # The code paths guard on the property being UNSET (ConsultationPDFCreator
    # checks != null before touching the file), so a present-but-bogus value
    # is strictly worse than no value. Guarded so a value an operator has
    # customised is never touched. Both prefixes stay matched: a properties
    # file written by a pre-rename package still carries the OscarDocument
    # spelling (the file is not a conffile and is never rewritten wholesale).
    for logo in ("clinicLetterheadLogo", "faxLogoInConsultation"):
        cur = prop_get(PROPERTIES, logo) or ""
        if cur.startswith(("/var/lib/CarlosDocument/", "/var/lib/OscarDocument/")):
            prop_comment(PROPERTIES, logo)

    # AES-256 key for credentials the app encrypts at rest (fax provider
    # passwords). Generated once and NEVER rotated automatically: rotating it
    # orphans everything already encrypted under the old key. It is inside
    # the backup; escrow it off-host too.
    if not (prop_get(PROPERTIES, "encryption.util.secret.key") or "").strip():
        prop_set(PROPERTIES, "encryption.util.secret.key",
                 util.out(["openssl", "rand", "-base64", "32"]))
        log("generated encryption.util.secret.key — it is in the backup; escrow it off-host too")

    os.chmod(PROPERTIES, 0o640)
    import grp
    os.chown(PROPERTIES, 0, grp.getgrnam("carlos").gr_gid)

    # nginx site fragments: generated, not conffiles, so changing the host
    # name or listen address is one edit plus this verb, with no conffile
    # prompt on the next upgrade.
    ngx = os.path.join(CONF_DIR, "nginx")
    os.makedirs(ngx, exist_ok=True)
    # World-readable on purpose: this is a DIRECTORY of nginx include
    # fragments holding listen addresses and a server_name — public facts
    # nginx serves — and the www-data worker must traverse it; nothing secret
    # ever lands here (the 0644-file advice the scanners give does not apply
    # to a directory, where 0644 would break traversal outright).
    # nosemgrep: python.lang.security.audit.insecure-file-permissions.insecure-file-permissions
    os.chmod(ngx, 0o755)  # nosec B103
    listen6_http = "listen [::]:80;" if s.bind_ip == "0.0.0.0" else ""  # nosec B104
    listen6_https = "listen [::]:443 ssl;" if s.bind_ip == "0.0.0.0" else ""  # nosec B104
    _write(os.path.join(ngx, "server-name.conf"),
           f"# Generated by carlos-ctl from CARLOS_SERVER_NAME in {ENV_FILE}. Do not edit.\n"
           f"server_name {s.server_name};\n")
    _write(os.path.join(ngx, "listen-http.conf"),
           f"# Generated by carlos-ctl from CARLOS_BIND_IP in {ENV_FILE}. Do not edit.\n"
           "# Plain HTTP exists only to redirect to HTTPS and to answer ACME challenges.\n"
           f"listen {s.bind_ip}:80;\n{listen6_http}\n")
    _write(os.path.join(ngx, "listen-https.conf"),
           f"# Generated by carlos-ctl from CARLOS_BIND_IP in {ENV_FILE}. Do not edit.\n"
           f"listen {s.bind_ip}:443 ssl;\n{listen6_https}\nhttp2 on;\n")
    if not os.path.exists(os.path.join(ngx, "proxy-params.conf")):
        import shutil
        shutil.copy(os.path.join(SHARE, "skel", "proxy-params.conf"),
                    os.path.join(ngx, "proxy-params.conf"))
        os.chmod(os.path.join(ngx, "proxy-params.conf"), 0o644)
    if not os.path.exists(os.path.join(ngx, "stapling.conf")):
        _write(os.path.join(ngx, "stapling.conf"), "# Managed by carlos-emr-cert.\n")
    log(f"configuration rendered for {s.server_name} (province {province_uc})")

    # RENDERING IS NOT APPLYING — finish the job so the operator loop is
    # simply "edit carlos-emr.env, run carlos-ctl init-config":
    #  * selfsigned mode regenerates the certificate when the host name
    #    changed (carlos-emr-cert's own guards keep operator-placed and ACME
    #    certificates untouched; no-op when nothing changed);
    #  * nginx is config-tested and reloaded so front-door changes serve now;
    #  * the one thing that needs a restart — application-side settings — is
    #    called out explicitly instead of left for the operator to discover.
    cert = os.path.join(LIB, "carlos-emr-cert")
    mode = ""
    st = run([cert, "status"], capture_output=True)
    for line in st.stdout.splitlines():
        if line.startswith("mode:"):
            mode = line.split(":", 1)[1].strip()
    if mode == "selfsigned":
        if run([cert, "selfsigned"]).returncode != 0:
            warn("certificate refresh failed; run 'carlos-ctl cert status'")
    if os.path.isdir("/run/systemd/system") and \
            run(["systemctl", "is-active", "--quiet", "nginx.service"]).returncode == 0:
        if run(["nginx", "-t"], capture_output=True).returncode == 0:
            if run(["systemctl", "reload", "nginx.service"]).returncode == 0:
                log("nginx reloaded — front-door changes are live")
            else:
                # The config passed its test but the reload job failed (nginx
                # died in between, ExecReload error). Silence here meant the
                # operator's front-door change never served, with exit 0.
                die("nginx reload FAILED — front-door changes are NOT live; "
                    "run 'systemctl status nginx'")
        else:
            warn("the rendered nginx configuration FAILS its test; nginx was NOT reloaded")
            warn("(the running config keeps serving). Details:")
            run(["nginx", "-t"])
            return 1
    if run(["systemctl", "is-active", "--quiet", "carlos-emr.service"]).returncode == 0:
        log("application-side settings (heap, timezone, database) need: carlos-ctl restart")
    return 0



def _render_browser_endpoint() -> tuple:
    """Port and url-base the render browser service is configured with.

    Read from /etc/carlos-emr/render-browser.env, which the renderer package's
    postinst generates. Returns the documented default port and an empty prefix
    when the file is absent, so a partially-installed system still produces a
    usable URL rather than a crash.
    """
    port, url_base = "9515", ""
    try:
        with open(RENDER_BROWSER_ENV, encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line.startswith("CARLOS_RENDER_PORT="):
                    port = line.split("=", 1)[1].strip() or port
                elif line.startswith("CARLOS_RENDER_URL_BASE="):
                    url_base = line.split("=", 1)[1].strip()
    except OSError:
        pass
    return port, url_base
def _write(path: str, content: str) -> None:
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    os.chmod(path, 0o644)

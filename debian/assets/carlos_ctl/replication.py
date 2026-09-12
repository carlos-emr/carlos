# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Optional MariaDB replication — the PRIMARY side (verb: replica).

Design: docs/deb-mariadb-replication-plan.md in the source tree. This module
implements phase 1 of that plan: turning the single-host MariaDB into a
replication primary that a second machine can stream from.

  replica add <replica-ip> [--listen <ip>] [--app-host <ip>]... [--reissue]
                            [--allow-public] [--no-restart]
  replica remove <replica-ip> [--no-restart]
  replica status

What "add" does, in order, every step idempotent:

  1. records the ONE extra address MariaDB will bind in
     /etc/carlos-emr/replication.env (role=primary);
  2. generates the MariaDB replication CA and server certificate
     (carlos-emr-cert db-tls) — per primary host, the CA key never leaves;
  3. renders /etc/mysql/mariadb.conf.d/62-carlos-emr-replication.cnf and the
     ip_nonlocal_bind sysctl drop-in, then runs the same compare-then-restart
     path as db-apply-settings (one restart, only when needed);
  4. creates a host-restricted, TLS-only replication account whose grant set
     is the backup account's — the replica reads every row anyway through
     the binlog, so SELECT on the two schemas widens nothing;
  5. writes a single-use join token (0600 root) for the operator to carry to
     the replica.

Everything that consumes the token — `replica join`, the replica package,
promotion — is phase 2 and lives in carlos-emr-db-replica; this module
refuses those verbs with a pointer rather than pretending.

Security posture, restated because it is the point:
  * MariaDB keeps listening on loopback; the listen address is the only
    addition, wildcards are refused, and a globally routable address needs
    --allow-public and prints the VPN recommendation.
  * Account DDL stays OUT of the binary log (sql_log_bin = 0), the same
    contract db-users keeps for point-in-time recovery.
  * The token carries the application passwords (the replica must provision
    the same accounts locally, since accounts never replicate) and therefore
    is as sensitive as backup.env: 0600 root, shredded by join, revoked by
    `replica remove` or `replica add --reissue`.
"""

import datetime as _dt
import ipaddress
import json
import os
import re
import shutil
from typing import Dict, List, Optional

from . import config, util
from .util import (
    BACKUP_ENV, CONF_DIR, DRUGREF_PROPERTIES, LIB, PROPERTIES, SHARE, STATE,
    die, env_get, genpw, log, need_root, prop_get, prop_unescape, run, warn,
)

REPL_ENV = os.path.join(CONF_DIR, "replication.env")
REPLICAS_DIR = os.path.join(STATE, "replicas")
DROPIN = "/etc/mysql/mariadb.conf.d/62-carlos-emr-replication.cnf"
SYSCTL_DROPIN = "/etc/sysctl.d/60-carlos-emr-replication.conf"
DB_TLS_DIR = "/etc/mysql/carlos-emr-tls"
DB_TLS_CA = os.path.join(DB_TLS_DIR, "ca.pem")
DB_TLS_CERT = os.path.join(DB_TLS_DIR, "server.pem")
DB_TLS_KEY = os.path.join(DB_TLS_DIR, "server.key")

TOKEN_SCHEMA = 1
TOKEN_TTL_HOURS = 24
ROLES = ("standalone", "primary", "replica")

# The replication account's grant set is the backup account's (dbops.py,
# cmd_db_users): what mariadb-dump --single-transaction --master-data reads,
# plus the binlog-streaming rights. Deliberately NOT PROCESS, for the same
# reason: a leaked credential must not let anyone watch clinicians' live SQL.
_SCHEMA_GRANTS = "SELECT, SHOW VIEW, TRIGGER, EVENT"
_GLOBAL_GRANTS = "RELOAD, REPLICATION CLIENT, REPLICATION SLAVE"


# --- settings ----------------------------------------------------------------

class ReplSettings:
    """replication.env, validated once. Absent file == standalone."""

    def __init__(self, path: str = REPL_ENV) -> None:
        self.path = path
        self.role = (env_get(path, "CARLOS_DB_ROLE") or "standalone").lower()
        if self.role not in ROLES:
            die(f"CARLOS_DB_ROLE ('{self.role}') in {path} must be one of {', '.join(ROLES)}")
        self.listen_ip = env_get(path, "CARLOS_DB_REPL_LISTEN_IP") or ""
        self.primary = env_get(path, "CARLOS_DB_REPL_PRIMARY") or ""
        self.alert_webhook = env_get(path, "CARLOS_DB_REPL_ALERT_WEBHOOK") or ""
        self.alert_email = env_get(path, "CARLOS_DB_REPL_ALERT_EMAIL") or ""
        try:
            self.max_lag = int(env_get(path, "CARLOS_DB_REPL_MAX_LAG_SECONDS") or "300")
        except ValueError:
            die(f"CARLOS_DB_REPL_MAX_LAG_SECONDS in {path} must be a whole number of seconds")
        if self.role == "primary" and not self.listen_ip:
            die(f"CARLOS_DB_ROLE=primary but CARLOS_DB_REPL_LISTEN_IP is empty in {path}; "
                "run 'carlos-ctl replica add <replica-ip> --listen <ip>' or set the role back to standalone")


def load() -> ReplSettings:
    return ReplSettings()


def role() -> str:
    """Cheap role probe for callers that must not die on a broken file."""
    return (env_get(REPL_ENV, "CARLOS_DB_ROLE") or "standalone").lower()


_SKEL_REPL_ENV = """# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
#
# MariaDB replication role for this host. WRITTEN BY carlos-ctl replica
# add/remove; the two alert keys and the lag threshold are yours to edit.
# Holds no secrets (mode 0644). Absent or standalone means: no replication.
#
# CARLOS_DB_ROLE            standalone | primary | replica
# CARLOS_DB_REPL_LISTEN_IP  primary: the ONE address, besides loopback, that
#                           MariaDB binds for replicas. Never a wildcard.
# CARLOS_DB_REPL_PRIMARY    replica: host:port it streams from (phase 2).
# CARLOS_DB_REPL_ALERT_*    where replication health alerts go. The backup's
#                           webhook/email in backup.env are used when these
#                           are empty. Neither set = silent; carlos-ctl check
#                           says so.
# CARLOS_DB_REPL_MAX_LAG_SECONDS  replica lag that raises an alert (phase 2).
CARLOS_DB_ROLE=standalone
CARLOS_DB_REPL_LISTEN_IP=
CARLOS_DB_REPL_PRIMARY=
CARLOS_DB_REPL_ALERT_WEBHOOK=
CARLOS_DB_REPL_ALERT_EMAIL=
CARLOS_DB_REPL_MAX_LAG_SECONDS=300
"""


def skeleton_repl_env() -> str:
    """The shipped skeleton (usr/share/carlos-emr/skel/replication.env) when
    installed, else the built-in copy — identical text."""
    try:
        with open(os.path.join(SHARE, "skel", "replication.env"), encoding="utf-8") as fh:
            return fh.read()
    except OSError:
        return _SKEL_REPL_ENV


def write_repl_env(role_value: str, listen_ip: str, path: str = REPL_ENV) -> None:
    """Set the two tool-owned keys, preserving operator edits (alerts, lag).
    Creates the file from the skeleton when absent. 0644 root: no secrets."""
    if role_value not in ROLES:
        raise ValueError(role_value)
    if not os.path.exists(path):
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o644)
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            fh.write(skeleton_repl_env())
    util.env_set(path, "CARLOS_DB_ROLE", role_value)
    util.env_set(path, "CARLOS_DB_REPL_LISTEN_IP", listen_ip)
    os.chmod(path, 0o644)


# --- pure helpers (unit-tested) ------------------------------------------------

def parse_ip(text: str):
    """A single, routable, non-wildcard IP address or die. Returns the
    ipaddress object so callers can ask is_global/version."""
    try:
        ip = ipaddress.ip_address(text.strip())
    except ValueError:
        die(f"'{text}' is not an IP address (host names are not accepted: the "
            "address becomes part of a MariaDB account name and a bind address)")
    if ip.is_unspecified:
        die(f"'{text}' is a wildcard; MariaDB must bind ONE named address, never every interface")
    if ip.is_loopback:
        die(f"'{text}' is a loopback address; a replica on another machine cannot reach it")
    if ip.is_multicast or ip.is_reserved or ip.is_link_local:
        die(f"'{text}' is not a usable unicast address")
    return ip


def is_public(ip) -> bool:
    return bool(ip.is_global)


def local_addresses(ip_json: Optional[str] = None) -> List[str]:
    """Every address currently assigned to a local interface, from
    `ip -j addr` (iproute2 is a package dependency). Injectable for tests."""
    if ip_json is None:
        ip_json = util.out(["ip", "-j", "addr"])
    found: List[str] = []
    try:
        for iface in json.loads(ip_json or "[]"):
            for a in iface.get("addr_info", []):
                if a.get("local"):
                    found.append(a["local"])
    except (ValueError, AttributeError, TypeError):
        return []
    return found


def account_name(replica_ip: str) -> str:
    """repl_<ip with separators folded>. MariaDB user names may be up to 80
    characters; the longest IPv6 form is 39, so this always fits."""
    return "repl_" + re.sub(r"[^0-9a-zA-Z]", "_", replica_ip)


def render_primary_dropin(listen_ip: str) -> str:
    return (
        "# Generated by carlos-ctl replica add — MariaDB as a replication PRIMARY.\n"
        "# Do not edit: re-run 'carlos-ctl replica add' or 'replica remove'.\n"
        "# Kept separate from 60-carlos-emr.cnf so a standalone host never\n"
        "# carries a listen address it did not ask for.\n"
        "[mariadbd]\n"
        "# Loopback stays; the ONE extra address is the operator's choice.\n"
        "# MariaDB >= 10.11 accepts a comma-separated list here.\n"
        f"bind-address        = 127.0.0.1,{listen_ip}\n"
        "# Refuse to serve a replica whose GTID is not in this binlog, instead\n"
        "# of silently applying from a wrong position after a restore.\n"
        "gtid_strict_mode    = ON\n"
        "gtid_domain_id      = 0\n"
        "# Server-side TLS for the replication port. Required per ACCOUNT\n"
        "# (REQUIRE SSL), not globally: require_secure_transport would force\n"
        "# TLS onto the application's loopback connection too.\n"
        f"ssl_ca              = {DB_TLS_CA}\n"
        f"ssl_cert            = {DB_TLS_CERT}\n"
        f"ssl_key             = {DB_TLS_KEY}\n"
    )


def render_sysctl(listen_ip: str) -> str:
    """mariadbd refuses to start when a listed bind address is not yet on an
    interface — a VPN address early in boot. Non-local bind lets the socket
    bind first and the interface arrive later."""
    ip = ipaddress.ip_address(listen_ip)
    key = "net.ipv6.ip_nonlocal_bind" if ip.version == 6 else "net.ipv4.ip_nonlocal_bind"
    return (
        "# Generated by carlos-ctl replica add. MariaDB binds the replication\n"
        f"# address {listen_ip}; without this a reboot on which that interface\n"
        "# (typically a VPN) comes up after mariadb.service leaves the database\n"
        "# down with 'Bind on TCP/IP port'. Removed by 'replica remove' of the\n"
        "# last replica.\n"
        f"{key} = 1\n"
    )


def account_sql(user: str, host: str, password: str, schemas: List[str]) -> str:
    """Create-or-update the replication account. Out of the binlog, like every
    other credential statement in this tool."""
    pw = password.replace("\\", "\\\\").replace("'", "\\'")
    lines = [
        "SET SESSION sql_log_bin = 0;",
        f"CREATE USER IF NOT EXISTS '{user}'@'{host}' IDENTIFIED BY '{pw}' REQUIRE SSL;",
        f"ALTER USER '{user}'@'{host}' IDENTIFIED BY '{pw}' REQUIRE SSL;",
    ]
    for schema in schemas:
        lines.append(f"GRANT {_SCHEMA_GRANTS} ON `{schema}`.* TO '{user}'@'{host}';")
    lines.append(f"GRANT {_GLOBAL_GRANTS} ON *.* TO '{user}'@'{host}';")
    lines.append("FLUSH PRIVILEGES;")
    return "\n".join(lines) + "\n"


def drop_account_sql(user: str, host: str) -> str:
    return f"SET SESSION sql_log_bin = 0;\nDROP USER IF EXISTS '{user}'@'{host}';\n"


def firewall_hint(listen_ip: str, replica_ips: List[str]) -> str:
    """The tool does not manage the firewall (nftables is only Suggested);
    it prints exactly what to add so nothing is left to guess."""
    v6 = ipaddress.ip_address(listen_ip).version == 6
    fam = "ip6" if v6 else "ip"
    lines = ["Allow the replication port from the replica(s) ONLY. Examples:"]
    for r in replica_ips:
        lines.append(f"  nft add rule inet filter input {fam} saddr {r} {fam} daddr {listen_ip} tcp dport 3306 accept")
    for r in replica_ips:
        lines.append(f"  ufw allow from {r} to {listen_ip} port 3306 proto tcp")
    lines.append("Every other source must NOT reach 3306 on that address; "
                 "carlos-ctl check can only see what is listening, not who can reach it.")
    return "\n".join(lines)


def norm_bind_list(value: str) -> List[str]:
    """`SELECT @@GLOBAL.bind_address` echoes the configured list; compare it
    as a set so ordering and whitespace never fake a 'stale' verdict."""
    return sorted(a.strip() for a in (value or "").split(",") if a.strip())


def build_token(*, primary_host: str, port: int, mariadb_version: str, ca_pem: str,
                repl_user: str, repl_password: str, accounts: Dict[str, str],
                app_hosts: List[str], db_name: str, province: str, schemas: List[str],
                size_bytes: int, tz: str, server_name: str, generation: int,
                now: Optional[_dt.datetime] = None) -> Dict:
    now = now or _dt.datetime.now(_dt.timezone.utc)
    return {
        "schema": TOKEN_SCHEMA,
        "issued_at": now.isoformat(timespec="seconds"),
        "expires_at": (now + _dt.timedelta(hours=TOKEN_TTL_HOURS)).isoformat(timespec="seconds"),
        "primary": {
            "host": primary_host,
            "port": port,
            "mariadb_version": mariadb_version,
            "ca_pem": ca_pem,
            "tz": tz,
            "server_name": server_name,
        },
        "repl": {"user": repl_user, "password": repl_password},
        "accounts": {
            "carlos": accounts["carlos"],
            "drugref": accounts["drugref"],
            "backup": accounts["backup"],
            "app_hosts": app_hosts,
            "generation": generation,
        },
        "db": {"name": db_name, "province": province, "schemas": schemas,
               "size_bytes": size_bytes},
    }


def token_expired(token: Dict, now: Optional[_dt.datetime] = None) -> bool:
    now = now or _dt.datetime.now(_dt.timezone.utc)
    try:
        return _dt.datetime.fromisoformat(token["expires_at"]) <= now
    except (KeyError, ValueError, TypeError):
        return True


# --- per-replica state ----------------------------------------------------------

def replica_dir(replica_ip: str) -> str:
    return os.path.join(REPLICAS_DIR, replica_ip)


def list_replicas(base: str = REPLICAS_DIR) -> List[Dict]:
    """Every recorded replica's state.json, oldest first; never dies on a
    damaged entry — status must still print the rest."""
    found = []
    try:
        names = sorted(os.listdir(base))
    except OSError:
        return found
    for name in names:
        st = os.path.join(base, name, "state.json")
        try:
            with open(st, encoding="utf-8") as fh:
                data = json.load(fh)
            data.setdefault("ip", name)
            found.append(data)
        except (OSError, ValueError):
            found.append({"ip": name, "damaged": True})
    return found


def _write_state(replica_ip: str, data: Dict) -> None:
    d = replica_dir(replica_ip)
    os.makedirs(d, mode=0o700, exist_ok=True)
    os.chmod(d, 0o700)
    tmp = os.path.join(d, ".state.json.tmp")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, sort_keys=True)
        fh.write("\n")
    os.replace(tmp, os.path.join(d, "state.json"))


def _read_state(replica_ip: str) -> Optional[Dict]:
    try:
        with open(os.path.join(replica_dir(replica_ip), "state.json"), encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return None


def _write_token(replica_ip: str, token: Dict) -> str:
    d = replica_dir(replica_ip)
    os.makedirs(d, mode=0o700, exist_ok=True)
    path = os.path.join(d, "join.token")
    tmp = path + ".tmp"
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(token, fh, indent=2, sort_keys=True)
        fh.write("\n")
    os.chown(tmp, 0, 0)
    os.replace(tmp, path)
    return path


def _read_token(replica_ip: str) -> Optional[Dict]:
    try:
        with open(os.path.join(replica_dir(replica_ip), "join.token"), encoding="utf-8") as fh:
            return json.load(fh)
    except (OSError, ValueError):
        return None


# --- database probes ---------------------------------------------------------------

def _db(sql: str) -> str:
    from . import dbops
    cp = dbops.db_root(["-N", "-B", "-e", sql], capture_output=True)
    return cp.stdout.strip() if cp.returncode == 0 else ""


def connected_replica_users(processlist: Optional[str] = None) -> Dict[str, str]:
    """{account: source-host} for every live binlog-dump connection. Root over
    the socket holds PROCESS; the replication account deliberately does not."""
    if processlist is None:
        processlist = _db("SELECT USER, HOST, COMMAND FROM information_schema.PROCESSLIST")
    live: Dict[str, str] = {}
    for line in (processlist or "").splitlines():
        cols = line.split("\t")
        if len(cols) >= 3 and cols[2].strip().lower().startswith("binlog dump"):
            live[cols[0].strip()] = cols[1].strip().rsplit(":", 1)[0]
    return live


def _mark_consumed(state: Dict, ip: str) -> None:
    if not state.get("consumed_at"):
        state["consumed_at"] = _dt.datetime.now(_dt.timezone.utc).isoformat(timespec="seconds")
        _write_state(ip, state)


def _account_exists(user: str, host: str) -> bool:
    return _db(f"SELECT COUNT(*) FROM mysql.global_priv WHERE User='{user}' AND Host='{host}'") == "1"


def _schema_exists(name: str) -> bool:
    return bool(_db(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{name}'"))


def _data_size(schemas: List[str]) -> int:
    quoted = ",".join(f"'{s}'" for s in schemas)
    v = _db("SELECT COALESCE(SUM(DATA_LENGTH+INDEX_LENGTH),0) FROM information_schema.TABLES "
            f"WHERE TABLE_SCHEMA IN ({quoted})")
    return int(v) if v.isdigit() else 0


# --- the verbs -----------------------------------------------------------------------

_USAGE = """usage:
  carlos-ctl replica add <replica-ip> [--listen <primary-ip>] [--app-host <ip>]...
                         [--reissue] [--allow-public] [--no-restart]
  carlos-ctl replica remove <replica-ip> [--no-restart]
  carlos-ctl replica status
  (replica join / promote arrive with the carlos-emr-db-replica package)"""


def cmd_replica(argv) -> int:
    args = list(argv)
    if not args or args[0] in ("-h", "--help", "help"):
        print(_USAGE)
        return 0
    sub, rest = args[0], args[1:]
    if sub == "add":
        return _cmd_add(rest)
    if sub == "remove":
        return _cmd_remove(rest)
    if sub == "status":
        return _cmd_status(rest)
    if sub in ("join", "promote"):
        die(f"'replica {sub}' runs on the REPLICA host and ships with the carlos-emr-db-replica "
            "package (phase 2 of docs/deb-mariadb-replication-plan.md); this host only issues tokens")
    die(f"unknown replica subcommand: {sub}\n{_USAGE}")


def _parse_add(rest: List[str]) -> Dict:
    opts: Dict = {"ip": None, "listen": None, "app_hosts": [], "reissue": False,
                  "allow_public": False, "no_restart": False}
    it = iter(rest)
    for a in it:
        if a == "--listen":
            opts["listen"] = next(it, None) or die("--listen needs an address")
        elif a == "--app-host":
            opts["app_hosts"].append(next(it, None) or die("--app-host needs an address"))
        elif a == "--reissue":
            opts["reissue"] = True
        elif a == "--allow-public":
            opts["allow_public"] = True
        elif a == "--no-restart":
            opts["no_restart"] = True
        elif a.startswith("-"):
            die(f"unknown option: {a}\n{_USAGE}")
        elif opts["ip"] is None:
            opts["ip"] = a
        else:
            die(f"unexpected argument: {a}\n{_USAGE}")
    if not opts["ip"]:
        die(_USAGE)
    return opts


def _apply_settings(no_restart: bool) -> None:
    from . import dbops
    rc = dbops.cmd_db_apply_settings(["--no-restart"] if no_restart else [])
    if rc != 0:
        die("MariaDB is not running the replication settings; fix the cause above and re-run "
            "'carlos-ctl db-apply-settings'")


def _cmd_add(rest: List[str]) -> int:
    need_root("replica add")
    o = _parse_add(rest)
    from . import dbops
    dbops.require_db_root()
    s = config.load()
    replica_ip = str(parse_ip(o["ip"]))

    # --- the listen address ----------------------------------------------
    rs = load()
    existing = list_replicas()
    listen = o["listen"] or rs.listen_ip
    if not listen:
        die("no listen address: pass --listen <primary-ip> (the address, besides loopback, "
            "that MariaDB will bind for replicas — a VPN address for an offsite replica)")
    listen_ip = parse_ip(listen)
    listen = str(listen_ip)
    if rs.listen_ip and listen != rs.listen_ip:
        others = [r["ip"] for r in existing if r["ip"] != replica_ip]
        if others:
            die(f"the listen address is already {rs.listen_ip} and {len(others)} replica(s) "
                "stream from it; remove them first ('carlos-ctl replica remove <ip>') before "
                "changing it")
        log(f"changing the listen address {rs.listen_ip} -> {listen}")
    if listen not in local_addresses():
        die(f"{listen} is not assigned to any interface on this host right now. Bring the "
            "interface (VPN?) up first; the sysctl this tool sets only covers later reboots")
    if is_public(listen_ip) and not o["allow_public"]:
        die(f"{listen} is a globally routable address. Exposing the clinical database's "
            "replication port on the internet is not something this tool does quietly: put "
            "the replica behind a VPN (WireGuard) and use that address, or re-run with "
            "--allow-public if the firewall in front of this host is your considered answer")
    if is_public(listen_ip):
        warn(f"{listen} is a PUBLIC address; the firewall rule printed at the end is not optional")
    if replica_ip == listen:
        die("the replica address and the listen address are the same host")
    for h in o["app_hosts"]:
        parse_ip(h)

    # --- credentials the token will carry: refuse placeholders ----------------
    app_pw = prop_unescape(prop_get(PROPERTIES, "db_password") or "")
    drugref_pw = prop_unescape(prop_get(DRUGREF_PROPERTIES, "db_password") or "")
    backup_pw = env_get(BACKUP_ENV, "CARLOS_BACKUP_DB_PASSWORD") or ""
    for label, pw in (("carlos.properties db_password", app_pw),
                      ("backup.env CARLOS_BACKUP_DB_PASSWORD", backup_pw)):
        if not pw or pw in dbops.UPSTREAM_PLACEHOLDER_PASSWORDS:
            die(f"{label} is empty or an upstream placeholder — run 'carlos-ctl db-users' first")
    if drugref_pw in dbops.UPSTREAM_PLACEHOLDER_PASSWORDS:
        drugref_pw = ""

    if not o["no_restart"]:
        dbops._refuse_while_backup_runs("restarting MariaDB for the replication settings")

    # --- render, certificate, apply ------------------------------------------
    write_repl_env("primary", listen)
    cert = os.path.join(LIB, "carlos-emr-cert")
    if run([cert, "db-tls"]).returncode != 0:
        die("could not generate the MariaDB replication certificate (carlos-emr-cert db-tls)")
    _write_file(DROPIN, render_primary_dropin(listen), 0o644)
    _write_file(SYSCTL_DROPIN, render_sysctl(listen), 0o644)
    if run(["sysctl", "-q", "-p", SYSCTL_DROPIN], capture_output=True).returncode != 0:
        warn(f"could not apply {SYSCTL_DROPIN} now (sysctl -p); it applies at the next boot")
    _apply_settings(o["no_restart"])

    # --- account and token ---------------------------------------------------
    user = account_name(replica_ip)
    state = _read_state(replica_ip) or {"ip": replica_ip, "account": user, "generation": 0}
    live = connected_replica_users()
    if user in live:
        _mark_consumed(state, replica_ip)
    token = _read_token(replica_ip)
    schemas = [s.db_name] + (["drugref2"] if _schema_exists("drugref2") else [])

    if state.get("consumed_at") and not o["reissue"]:
        # A joined replica: never rotate its password behind its back.
        if not _account_exists(user, replica_ip):
            die(f"{replica_ip} joined earlier but its account {user}@{replica_ip} is gone; "
                "re-run with --reissue and re-join the replica")
        log(f"{replica_ip} already streams from this host (joined "
            f"{state['consumed_at']}); re-asserting grants only, no new credential")
        # Grants only: no password, no token. An unknown current password is
        # left untouched by omitting IDENTIFIED BY.
        sql = "SET SESSION sql_log_bin = 0;\n" + "\n".join(
            f"GRANT {_SCHEMA_GRANTS} ON `{sc}`.* TO '{user}'@'{replica_ip}';" for sc in schemas
        ) + f"\nGRANT {_GLOBAL_GRANTS} ON *.* TO '{user}'@'{replica_ip}';\nFLUSH PRIVILEGES;\n"
        cp = dbops.db_root([], input=sql, capture_output=True)
        if cp.returncode != 0:
            die(f"could not re-assert the replication grants:\n{cp.stderr.strip()}")
        _write_state(replica_ip, state)
        _print_next_steps(listen, [r["ip"] for r in list_replicas()], None, replica_ip)
        return 0

    if token and not token_expired(token) and not o["reissue"] and not state.get("consumed_at"):
        log(f"a join token for {replica_ip} was issued {token['issued_at']} and is valid until "
            f"{token['expires_at']}; not replacing it (use --reissue to rotate)")
        _print_next_steps(listen, [r["ip"] for r in list_replicas()],
                          os.path.join(replica_dir(replica_ip), "join.token"), replica_ip)
        return 0

    if token and token_expired(token) and not o["reissue"]:
        log(f"the previous token for {replica_ip} expired {token['expires_at']}; issuing a new one")
    if o["reissue"] and state.get("consumed_at"):
        warn(f"{replica_ip} is a JOINED replica: its IO thread will fail at the next reconnect "
             "until it runs 'carlos-ctl replica join --credentials-only <new token>'")

    password = genpw()
    cp = dbops.db_root([], input=account_sql(user, replica_ip, password, schemas), capture_output=True)
    if cp.returncode != 0:
        die(f"could not provision the replication account:\n{cp.stderr.strip()}")
    try:
        with open(DB_TLS_CA, encoding="utf-8") as fh:
            ca_pem = fh.read()
    except OSError as e:
        die(f"cannot read {DB_TLS_CA}: {e}")
    generation = int(state.get("generation", 0)) + 1
    app_hosts = [listen] + [h for h in o["app_hosts"] if h != listen]
    tok = build_token(
        primary_host=listen, port=int(s.db_port), mariadb_version=_db("SELECT VERSION()"),
        ca_pem=ca_pem, repl_user=user, repl_password=password,
        accounts={"carlos": app_pw, "drugref": drugref_pw, "backup": backup_pw},
        app_hosts=app_hosts, db_name=s.db_name, province=s.schema_province, schemas=schemas,
        size_bytes=_data_size(schemas), tz=env_get(util.ENV_FILE, "CARLOS_TZ") or "",
        server_name=s.server_name, generation=generation)
    path = _write_token(replica_ip, tok)
    state.update({"ip": replica_ip, "account": user, "generation": generation,
                  "issued_at": tok["issued_at"], "expires_at": tok["expires_at"]})
    if o["reissue"]:
        # A reissue re-arms consumption tracking: the new credential is unproven.
        state.pop("consumed_at", None)
    _write_state(replica_ip, state)
    log(f"replication account {user}@{replica_ip} provisioned (TLS required, generation {generation})")
    _print_next_steps(listen, [r["ip"] for r in list_replicas()], path, replica_ip)
    return 0


def _print_next_steps(listen: str, replica_ips: List[str], token_path: Optional[str],
                      replica_ip: str) -> None:
    print()
    print(firewall_hint(listen, replica_ips))
    print()
    if token_path:
        print(f"Join token: {token_path}  (0600 root; expires in {TOKEN_TTL_HOURS} h; "
              "it carries credentials as sensitive as backup.env)")
        print(f"  sudo scp {token_path} <admin>@{replica_ip}:")
        print("On the replica: apt install carlos-emr-db-replica && "
              "sudo carlos-ctl replica join ./join.token")
        print("Then shred the copy here:  shred -u " + token_path)
    print("Check this side any time:   sudo carlos-ctl replica status")


def _cmd_remove(rest: List[str]) -> int:
    need_root("replica remove")
    no_restart = "--no-restart" in rest
    ips = [a for a in rest if a != "--no-restart"]
    if len(ips) != 1:
        die("usage: carlos-ctl replica remove <replica-ip> [--no-restart]")
    from . import dbops
    dbops.require_db_root()
    replica_ip = str(parse_ip(ips[0]))
    user = account_name(replica_ip)
    if not no_restart:
        dbops._refuse_while_backup_runs("restarting MariaDB after removing a replica")
    live = connected_replica_users()
    if user in live:
        warn(f"{replica_ip} is connected and streaming RIGHT NOW; dropping its account stops it")
    cp = dbops.db_root([], input=drop_account_sql(user, replica_ip), capture_output=True)
    if cp.returncode != 0:
        die(f"could not drop {user}@{replica_ip}:\n{cp.stderr.strip()}")
    d = replica_dir(replica_ip)
    if os.path.isdir(d):
        for name in ("join.token", "join.token.tmp"):
            p = os.path.join(d, name)
            if os.path.exists(p):
                run(["shred", "-u", p], capture_output=True)
        shutil.rmtree(d, ignore_errors=True)
    log(f"removed replica {replica_ip} (account {user} dropped, token shredded)")
    remaining = list_replicas()
    if remaining:
        log(f"{len(remaining)} replica(s) remain; the listen address stays")
        return 0
    # Last one: back to a loopback-only primary. TLS material stays for a
    # future re-enable (it is regenerable, but replicas that re-join keep
    # working with the same CA).
    rs = load()
    for p in (DROPIN, SYSCTL_DROPIN):
        if os.path.exists(p):
            os.unlink(p)
    write_repl_env("standalone", "")
    log(f"no replicas left: MariaDB returns to loopback only (was also on {rs.listen_ip or '?'}); "
        "ip_nonlocal_bind stays set until the next boot")
    _apply_settings(no_restart)
    return 0


def _cmd_status(rest: List[str]) -> int:
    need_root("replica status")
    if rest:
        die("replica status takes no arguments")
    rs = load()
    problems = 0
    print(f"role: {rs.role}")
    if rs.role == "standalone":
        print("no replication configured (carlos-ctl replica add <replica-ip> --listen <ip>)")
        return 0
    if rs.role == "replica":
        print(f"primary: {rs.primary or '?'}")
        print("replica-side status arrives with the carlos-emr-db-replica package (phase 2)")
        return 0
    from . import dbops
    dbops.require_db_root()
    print(f"listen: 127.0.0.1,{rs.listen_ip}")
    running = norm_bind_list(_db("SELECT @@GLOBAL.bind_address"))
    if rs.listen_ip not in running:
        problems += 1
        print(f"  MariaDB is NOT bound to {rs.listen_ip} (running: {','.join(running) or '?'}); "
              "run: carlos-ctl db-apply-settings")
    print(f"gtid_binlog_pos: {_db('SELECT @@GLOBAL.gtid_binlog_pos') or '?'}")
    expire = _db("SELECT @@GLOBAL.binlog_expire_logs_seconds")
    print(f"binlog retention: {expire or '?'} s")
    # TLS
    if os.path.exists(DB_TLS_CERT):
        cp = run(["openssl", "x509", "-noout", "-enddate", "-in", DB_TLS_CERT], capture_output=True)
        print(f"tls: {DB_TLS_CERT} ({cp.stdout.strip() or 'unreadable'})")
        if run(["openssl", "x509", "-checkend", str(21 * 86400), "-noout", "-in", DB_TLS_CERT],
               capture_output=True).returncode != 0:
            problems += 1
            print("  the MariaDB server certificate expires within 21 days; run: carlos-ctl cert-renew")
    else:
        problems += 1
        print(f"tls: {DB_TLS_CERT} MISSING — run: /usr/lib/carlos-emr/carlos-emr-cert db-tls")
    live = connected_replica_users()
    hosts = _db("SHOW SLAVE HOSTS")
    print("replicas:")
    replicas = list_replicas()
    if not replicas:
        print("  (none recorded)")
    for r in replicas:
        ip = r["ip"]
        if r.get("damaged"):
            problems += 1
            print(f"  {ip}: state.json is damaged — 'replica remove' then 'replica add' again")
            continue
        user = r.get("account", account_name(ip))
        connected = user in live
        if connected:
            _mark_consumed(r, ip)
        tok = _read_token(ip)
        if connected:
            state_txt = f"CONNECTED from {live[user]} (generation {r.get('generation', '?')})"
        elif r.get("consumed_at"):
            problems += 1
            state_txt = (f"NOT CONNECTED — it joined {r['consumed_at']} and is not streaming now "
                         "(host down? VPN? check 'carlos-ctl replica status' on the replica)")
        elif tok and token_expired(tok):
            state_txt = (f"token EXPIRED {tok['expires_at']} without a join — re-run "
                         f"'carlos-ctl replica add {ip}' to issue a new one")
        elif tok:
            state_txt = f"waiting for join (token valid until {tok['expires_at']})"
        else:
            problems += 1
            state_txt = "no token and never joined — re-run 'carlos-ctl replica add'"
        print(f"  {ip} ({user}): {state_txt}")
    if hosts:
        print("SHOW SLAVE HOSTS:")
        for line in hosts.splitlines():
            print(f"  {line}")
    # binlog age vs retention: a replica that is behind more than the window
    # cannot resume. The datadir is 0700 mysql; root reads it.
    oldest = _oldest_binlog_age_seconds()
    if oldest is not None and expire.isdigit():
        pct = 100 * oldest / max(int(expire), 1)
        print(f"oldest binlog: {oldest // 3600} h old ({pct:.0f}% of retention)")
    alert = (rs.alert_webhook or rs.alert_email or
             env_get(BACKUP_ENV, "CARLOS_BACKUP_ALERT_WEBHOOK") or
             env_get(BACKUP_ENV, "CARLOS_BACKUP_ALERT_EMAIL"))
    if not alert:
        print("alerts: NONE configured (replication.env or backup.env) — replica failures are silent "
              "until someone runs this command")
    return 1 if problems else 0


def _oldest_binlog_age_seconds() -> Optional[int]:
    logs = _db("SHOW BINARY LOGS")
    datadir = _db("SELECT @@GLOBAL.datadir")
    if not logs or not datadir:
        return None
    first = logs.splitlines()[0].split("\t")[0]
    try:
        st = os.stat(os.path.join(datadir, first))
    except OSError:
        return None
    return int(_dt.datetime.now().timestamp() - st.st_mtime)


def _write_file(path: str, content: str, mode: int) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, mode)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write(content)
    os.chmod(path, mode)


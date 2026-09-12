# MariaDB replication for the Debian deployment — design and implementation plan

**Status: phase 1 (asynchronous primary side) implemented in this branch.
The maintainer has chosen Path B — a Galera single-writer cluster — as the
next piece; it is specified in section 11 and re-orders the phases in
section 6. The asynchronous replica package (Path A) stays as an optional
later phase for an offsite copy. All fourteen decisions in sections 9 and
11.10 were taken by the maintainer on 2026-09-12 (the recommendations
recorded there), and the maintainer authorized the Flyway schema change
phase B0 needs.** Implemented: `carlos-ctl replica add|remove|status`,
`carlos-emr-cert db-tls`, the 62- drop-in and sysctl renderers,
`db-apply-settings --no-restart` and its primary-role compare set, the
`check` replication section, the preseed questions, the `demo-data` /
`destroy-data` / `rotate` guards, and stdlib unit tests under
`debian/tests/unit/`. Deliberately NOT yet done from phase 0: the
`carlos-emr-ctl` package split and `role.py` — nothing in phase 1 needs
them, and open question 1 is still the maintainer's. This document is the
design the `carlos-emr` packaging follows to add an *optional*, turnkey MariaDB
replica to a single-host install — either right after the first install or
added to an established site later. Nothing here changes what a site that
never asks for a replica gets from `apt install carlos-emr`.

Scope of the first deliverable, in the maintainer's words: **database side
only, CARLOS keeps talking to the primary.** A second machine holds a live,
continuously updated copy of the clinical database. Failover is an operator
decision with a scripted, fenced promotion — not automatic.

## 0. Two properties that hold in every phase

**Optional.** A site that never asks for a replica gets exactly what it gets
today:

- no new debconf question is shown at normal priority, and the two
  replication questions added for preseeding default to empty (= off);
- MariaDB keeps listening on loopback only, no certificate material is
  generated, no account is created, no drop-in is rendered, no timer runs;
- `carlos-emr-db-replica` is never pulled in by `Depends` or `Recommends`;
  the only visible change from the `carlos-emr-ctl` split is a second
  package in `apt list --installed`, which apt installs automatically as a
  dependency;
- `carlos-ctl check`, backups, PITR and the restore drill behave and report
  as before.

**Three independent, optional layers.** Local high availability (Path B,
a Galera cluster), an offsite copy (Path A, an asynchronous replica), and
neither. Any combination is supported: standalone; cluster only; offsite
replica only; cluster *plus* offsite replica (section 13). None is pulled in
by `Depends` or `Recommends`, each is enabled by one verb and removed by one
verb, and removing one never touches the other or the data.

**Easy to add at the start or later.** Enabling is the same one verb in both
cases, `carlos-ctl replica add`, and it is idempotent and reversible
(`replica remove`). On a fresh install it can be preseeded so `apt install`
finishes with the primary side ready and the join token written. On an
established site it changes nothing in the schema or the application; the
only interruption is one MariaDB restart to bind the extra address, which is
done through the existing compare-then-restart path, refused while a backup
is running, and can be deferred with `--no-restart` to a maintenance window.
Section 5 walks both paths.

Companion documents: [`install-deb.md`](install-deb.md) (what exists today),
[`carlos-ctl.md`](carlos-ctl.md) (the verb set this extends), and the
installed `README.Debian` (sections 6 "Database" and 7 "Backups", which this
design must stay consistent with).

---

## 1. Decisions up front

| Question | Decision | Why (short) |
|---|---|---|
| Replication style, local HA (**Path B, chosen**) | **Galera synchronous cluster, single writer**: two database nodes plus an arbitrator, the application pinned to one node with a driver-level failover list | Zero data loss; automatic database failover with no application code; quorum built in; MariaDB's own packages. Section 11 |
| Replication style, offsite copy (Path A, optional later) | Asynchronous GTID replica with manual fenced promotion (phase 1 implemented) | Works over a WAN; the only option for a distant copy |
| Multi-writer at the application | **No.** All Galera nodes are writable, but CARLOS writes to one at a time | The application has no retry for certification conflicts; with one writer they never happen. The failover list gives automatic switching without it |
| "Judge" / arbiter node | **Yes, for Path B**: `garbd` on a third small host (`carlos-emr-db-arbiter`) | Two voters cannot survive losing one; the arbitrator holds no data and needs no MariaDB |
| Automatic failover | **Path B: yes, at the driver** (`failOverReadOnly=false` over the node list). **Path A: no**, manual fenced promotion | On a Galera cluster every surviving node in the primary component is writable, so a driver switch is safe; on an async pair it is not |
| Packaging | **Split the admin tool into its own package (`carlos-emr-ctl`), add one new role package (`carlos-emr-db-replica`)**; `carlos-ctl` becomes role-aware | One tool, one code path, on every host. A db-only host cannot depend on `carlos-emr` (it would pull Tomcat, nginx, the WAR) |
| Role model | Derived, not declared twice: *app present?* (webapp installed) × *database role* (`standalone` / `primary` / `replica`) recorded in `/etc/carlos-emr/replication.env` | Leaves room for "app host with a remote database" and "warm-standby app host whose local MariaDB is a replica" without new packages |
| Network exposure | MariaDB keeps loopback; a primary or cluster node additionally binds **one** operator-named LAN/VPN address; accounts are host-restricted and `REQUIRE SSL`; certificates pinned. **Cluster verbs refuse a public address outright; only the asynchronous `replica add` accepts one, behind `--allow-public`** | The loopback posture is preserved: the only new listener is the one the operator explicitly asked for, and a Galera cluster is a LAN design by nature |
| Seeding a new replica | **Logical dump pulled by the replica over the replication port** (TLS, `--single-transaction --gtid`) in phase 1; physical (`mariadb-backup` over ssh) and restore-from-restic as later options | No ssh trust between hosts, no second port, works over a VPN; the replica pulls exactly what the nightly backup already dumps |
| Credentials | Join token carries the replication credential **and** the three local account passwords (`carlos`, `drugref`, `backup`) | Account DDL is deliberately *not* binlogged (PITR contract), so the replica must provision the same passwords itself or promotion would strand the app's `carlos.properties` |

### 1.1 Why not multi-master, in more detail

The maintainer asked whether multi-master is an option. It is technically
possible with Galera Cluster (in Ubuntu's `galera-4` package, recommended by
`mariadb-server`), and it would give synchronous, zero-data-loss copies with
automatic quorum. It is the wrong first step for this deployment:

- **The application has one writer.** CARLOS on one host connects to one
  JDBC URL. Multi-master only pays when several application hosts write
  concurrently, and then only if the application tolerates certification
  failures (Galera aborts one side of a conflicting commit with a deadlock
  error). The OSCAR-lineage code base does not retry transactions.
- **Schema constraints.** Galera requires InnoDB (satisfied: all 389 baseline
  tables are InnoDB) and a primary key on every table (not satisfied: 22
  baseline tables have none — DELETEs on those are unsupported and can
  diverge nodes). An audit and migration adding surrogate keys would come
  first.
- **Three voters.** Split-brain avoidance needs 3 nodes or 2 + `garbd`. That
  is the "judge node" role. For a clinic with one server room it is a third
  machine whose only job is to vote.
- **Commit latency** becomes the slowest node's round trip. For an offsite
  copy that is the WAN.
- **Operational surface.** SST/IST state transfers, `wsrep_*` tuning, and a
  failure vocabulary the current `carlos-ctl check` and README do not cover.

The asynchronous replica gives most of the operational value — a live copy on
separate hardware, the ability to take backups off the primary, a fast manual
failover with seconds of data loss at worst (zero with semi-sync) — with none
of the above. Galera stays a documented future option (section 6, phase 4) for a site
that reaches "two application hosts" and has done the primary-key audit.

Proxies (MaxScale, ProxySQL) are also out of scope for phase 1: MaxScale
25.01 and later are under a proprietary commercial licence (earlier releases
were BSL 1.1 and convert to GPL only on their change dates), it is not in the
Ubuntu archive, and no proxy is needed while the application talks to
exactly one database host.

---

## 2. What exists today (the foundation)

Facts the design builds on, with where they live:

- **MariaDB is already a replication-ready primary in everything but the
  network.** `debian/assets/mariadb/60-carlos-emr.cnf` sets `server_id = 1`,
  `log_bin = binlog`, `binlog_format = ROW`, `sync_binlog = 1`,
  `innodb_flush_log_at_trx_commit = 1`, `binlog_expire_logs_seconds = 864000`
  (10 days), `binlog_ignore_db = carlos_restore_drill`, and
  `bind-address = 127.0.0.1`.
- **A least-privilege account with replication rights already exists.**
  `carlos-ctl db-users` (`debian/assets/carlos_ctl/dbops.py`) grants the
  `backup` account `SELECT, SHOW VIEW, TRIGGER, EVENT` on the EMR schema plus
  `RELOAD, REPLICATION CLIENT, REPLICATION SLAVE` globally — exactly the grant
  set a replica needs to seed itself and stream. The replication account is
  a host-restricted copy of it.
- **Credential DDL is kept out of the binary log on purpose**
  (`SET SESSION sql_log_bin = 0` in `db-users`, `bootstrap-admin`,
  `destroy-data`, the drugref postinst, the demo load). PITR replays binlogs,
  so a windowed restore must never re-apply an `ALTER USER`. A replica sees
  the same stream, so **accounts do not replicate** — the join step has to
  provision them locally (section 4.4).
- **The nightly backup ships the same binlogs** a replica consumes
  (`debian/assets/bin/carlos-emr-backup`, `mariadb-binlog
  --read-from-remote-server` as the `backup` account). The dump anchors PITR
  at file+position (`--master-data=2`). GTID on the replica and file+position
  in the backup coexist; the backup format does not change.
- **The application's database endpoint is already a setting.**
  `CARLOS_DB_HOST/PORT/NAME` in `/etc/carlos-emr/carlos-emr.env`; `carlos-ctl
  init-config` (`config.py`) renders `db_uri = jdbc:mysql://host:port/` into
  `carlos.properties`; `spring_jpa.xml` builds the DBCP2 URL as
  `${db_uri}${db_name}`. Repointing the app at a promoted replica is an env
  edit + `init-config` + `restart`. (The connector could take a
  `host1,host2` failover list, but connector-j's failover makes the
  connection read-only and a replica *is* read-only, so that is not a
  substitute for promotion.)
- **`db-apply-settings` is the model for "render a drop-in, restart only if
  the running values disagree"** (`dbops.py`), including the additive
  `binlog_ignore_db` handling and the "something after 60- overrides us"
  diagnosis. Replication settings go through the same path.
- **`carlos-ctl check` asserts MariaDB listens on loopback only**
  (`validate.py`). It has to learn the one permitted extra address.
- **The certificate helper** (`debian/assets/bin/carlos-emr-cert`) already
  owns key generation and a mode file; it gets a MariaDB-facing sub-command
  rather than a second key-handling tool.
- **`carlos-podman`'s `carlos_ctl`** is the sibling tool with the same verb
  vocabulary and a pytest suite (`tests/unit/`). The deb tool has no unit
  tests yet; phase 0 adds them in the same shape.
- **Single-host assumptions that a remote database breaks** (must be handled
  before a promoted replica is used as the app's database, section 7):
  `carlos-emr-backup` dumps and drills over the unix socket only;
  `carlos-ctl db`, `db-users`, `bootstrap-admin`, `demo-data`, `destroy-data`
  connect as `root@localhost` over the socket; `check` expects a local
  `mariadb.service`.

---

## 3. Topologies

Phase 1 delivers **T1**. The role model is designed so T2 and T3 are additive.

```
T1  (phase 1)         T2  (phase 3)                  T3  (future)
┌──────────────┐      ┌──────────────┐               ┌──────────────┐
│ A: full      │      │ A: app only  │               │ A: full      │
│ app + db     │      │ CARLOS_DB_   │               │ app + db     │
│ primary      │──┐   │ HOST=B       │──┐            │ primary      │──┐
└──────────────┘  │   └──────────────┘  │            └──────────────┘  │ repl
   repl (TLS)     │        JDBC         │                              │
                  ▼                     ▼                              ▼
┌──────────────┐      ┌──────────────┐               ┌──────────────┐
│ B: db-only   │      │ B: db-only   │               │ B: full,     │
│ replica      │      │ PROMOTED     │               │ app idle,    │
│ read_only    │      │ primary      │               │ db replica   │
└──────────────┘      └──────────────┘               └──────────────┘
```

- **T1** — A is what `apt install carlos-emr` produces today plus
  `carlos-ctl replica add`. B is `carlos-emr-db-replica` + `carlos-ctl
  replica join`. If A dies entirely, B holds the data; recovery is a new full
  install pointed at B (T2) or a rebuild of A from B + the document backup.
- **T2** — the state after promotion when A's application survives but its
  database does not, or when the operator chooses to run the database on a
  separate host permanently. Requires the "remote database" work in
  section 7.
- **T3** — warm standby: a second full server whose MariaDB is a replica and
  whose application is stopped. Promotion becomes "promote B, start B's
  app, move DNS". This is why the tool must not assume "replica ⇒ no app".
- **T4 (Path B, chosen)** — Galera cluster: A is the full server whose
  MariaDB is cluster node 1 and the application's first-choice node; B is a
  db-only cluster node 2; C is the arbitrator (`garbd`, no data). The
  application's JDBC URL lists A then B. Section 11.

---

## 4. Design

### 4.1 Packages and roles

**New source-package layout** (same `debian/`, same build):

| Package | Depends (delta) | Ships |
|---|---|---|
| `carlos-emr-ctl` (new) | python3, mariadb-client, openssl, curl, iproute2, procps, adduser (the sysusers file is applied by systemd on a systemd host, with the same `adduser` fallback `carlos-emr.postinst` already carries for chroots) | `carlos_ctl/` Python package, `/usr/sbin/carlos-ctl` shim, `carlos-emr-cert`, the shared MariaDB drop-in `60-carlos-emr.cnf`, `carlos-ctl.8`, the **sysusers declaration for `carlos` and `carlos-backup`** (today in `carlos-emr.sysusers`; `db-users` and `init-config` chown credential files to the `carlos` group, so a db-only host needs the accounts too), the **`carlos.properties` skeleton** (today staged from the built WAR into `carlos-emr`'s `skel/`; `db-users` refuses to run without the file, so the ctl package stages its own copy from the same WAR at build time), the **`carlos-emr.env` skeleton** (see the hazard below), `backup.env` and `replication.env` skeletons, and the `carlos-emr-db-tls-renew.timer` |
| `carlos-emr` | `carlos-emr-ctl (= ${binary:Version})` | Everything it ships today minus what moved. `Breaks`/`Replaces: carlos-emr (<< <first split version>)` on `carlos-emr-ctl` so the file move is clean on upgrade |
| `carlos-emr-db-replica` (new, `Architecture: all`) | `carlos-emr-ctl (= ${binary:Version})`, mariadb-server (>= 1:11.4), mariadb-client, `Conflicts: carlos-emr` **in phase 2 only** (lifted in T3 work) | The `carlos-emr-replica-watch.service/.timer`, `replica-watch` README section. No WAR, no Tomcat, no nginx |
| `carlos-emr-drugref`, `carlos-emr-eform-renderer` | unchanged | unchanged |

**Silent-default hazard, and why the replica gets a `carlos-emr.env`.**
`util.env_get()` returns `None` for a file that does not exist, and
`config.Settings` then falls back to `db_name = "carlos"`, `province =
"on"`, `server_name = "localhost"` without a word. On a db-only host with
no `carlos-emr.env`, every verb would therefore act on a schema named
`carlos` — wrong, silently, for any site running a custom
`CARLOS_DB_NAME`, and wrong for the province. So `replica join` writes a
`carlos-emr.env` on the replica from the token (`CARLOS_DB_NAME`,
`CARLOS_PROVINCE`, `CARLOS_TZ`, and `CARLOS_SERVER_NAME` set to the
replica's own host name, which `destroy-data --confirm` will ask for), and
`role.py` refuses to run any database verb on a host whose role is not
`standalone` while that file is absent, instead of defaulting.

Moving `60-carlos-emr.cnf` to `carlos-emr-ctl` is what guarantees a replica
runs with the *same* character set, collation, `sql_mode`, row format and
durability settings as the primary — a replica on distribution defaults
(STRICT mode) would fail to apply rows the primary accepted.

**Role detection** (`carlos_ctl/role.py`, new):

```
has_app        = os.path.isdir("/usr/share/carlos-emr/webapp/carlos")
db_role        = env_get("/etc/carlos-emr/replication.env", "CARLOS_DB_ROLE") or "standalone"
                 # standalone | primary | replica
db_is_local    = CARLOS_DB_HOST in ("127.0.0.1", "localhost", "::1")   # from carlos-emr.env when has_app
```

`carlos-ctl --help` prints the detected role on its first line; verbs that
make no sense for the role refuse with a one-line reason (`restart` on a
db-only host: "no application on this host"). Existing verbs keep working
unchanged on a standalone full server, which is the whole installed base.

`/etc/carlos-emr/replication.env` (operator-readable, root-owned 0644, holds
**no secrets**):

```
CARLOS_DB_ROLE=primary            # standalone | primary | replica
CARLOS_DB_REPL_LISTEN_IP=10.0.0.5 # primary: the ONE extra address MariaDB binds
CARLOS_DB_REPL_PRIMARY=10.0.0.5:3306   # replica: where it streams from
CARLOS_DB_REPL_ALERT_WEBHOOK=     # replica: where lag/broken-thread alerts go
CARLOS_DB_REPL_ALERT_EMAIL=
CARLOS_DB_REPL_MAX_LAG_SECONDS=300
```

Per-replica state on the primary lives under `/var/lib/carlos-emr/replicas/`
(one directory per replica IP: the issued token, the account name, the
timestamp).

### 4.2 Replication mechanics

Generated by `carlos-ctl`, never hand-edited, always through the
`db-apply-settings` compare-then-restart path:

**Primary**, `/etc/mysql/mariadb.conf.d/62-carlos-emr-replication.cnf`:

```
[mariadbd]
bind-address        = 127.0.0.1,<CARLOS_DB_REPL_LISTEN_IP>   # MariaDB ≥ 10.11 accepts a list
gtid_strict_mode    = ON
gtid_domain_id      = 0
ssl_ca              = /etc/mysql/carlos-emr-tls/ca.pem
ssl_cert            = /etc/mysql/carlos-emr-tls/server.pem
ssl_key             = /etc/mysql/carlos-emr-tls/server.key
# semi-sync (phase 3, opt-in): rpl_semi_sync_master_enabled = ON, rpl_semi_sync_master_timeout = 10000
```

`server_id` stays 1 (already in the 60- file). `require_secure_transport` is
deliberately **not** set: it would force TLS on the application's loopback
connection too. TLS is required per account instead (`REQUIRE SSL` on the
replication account).

**Replica**, same filename, different content:

```
[mariadbd]
server_id           = <random 32-bit, generated at join, never 1>
read_only           = ON        # MariaDB has no super_read_only; root over the socket can still write, by design
log_slave_updates   = ON        # a promoted replica must already own a complete binlog chain
relay_log           = relay     # relative, same datadir-following reason as log_bin
gtid_strict_mode    = ON
report_host         = <this host's replication IP>   # so the primary's SHOW SLAVE HOSTS lists it
slave_net_timeout   = 60
slave_parallel_threads = 4
slave_parallel_mode = conservative                   # the default is optimistic since 10.5.1; its retries are avoidable risk here
bind-address        = 127.0.0.1                      # until promoted; promotion adds the listen IP
```

**Boot-order hazard of binding a specific address.** `mariadbd` refuses to
start when an address in `bind-address` is not present on any interface.
A listen IP on a VPN interface (WireGuard, the recommended way to reach an
offsite replica) is exactly such an address early in boot, and the failure
mode is the EMR down after a reboot with "Can't start server: Bind on TCP/IP
port" in the journal. `replica add` therefore: refuses an address that is
not currently assigned; writes `/etc/sysctl.d/60-carlos-emr-replication.conf`
with `net.ipv4.ip_nonlocal_bind = 1` (and the IPv6 twin for an IPv6 listen
IP) so the bind succeeds before the interface exists; and `check` asserts
the sysctl is in effect while a listen IP is configured. `replica remove`
of the last replica removes the sysctl file. The alternative — a systemd
ordering drop-in on `mariadb.service` after the VPN unit — is fragile
(there is no single unit name to order after) and is not used. Open
question 7 records the trade-off.

**Replication protocol:** GTID (`MASTER_USE_GTID = slave_pos`), ROW events,
TLS with the pinned CA and `MASTER_SSL_VERIFY_SERVER_CERT = 1`. Both schemas
(`carlos`, `drugref2`) replicate; nothing else on the server is worth
replicating, and `carlos_restore_drill` is already filtered at the binlog.
No `replicate_do_db` filters: they are a well-known source of silent
divergence with ROW events and cross-schema statements.

MariaDB (10.5.1+) accepts `REPLICA` synonyms for `START`/`STOP`/`RESET`
`SLAVE` and `SHOW SLAVE STATUS`/`HOSTS`; the connection statement remains
`CHANGE MASTER TO` (MariaDB did not adopt MySQL's `CHANGE REPLICATION
SOURCE TO`). The tool uses the legacy spellings the server documentation
and `mariadb-dump` output use, confirms every statement it issues in the
11.4 smoke test (section 8), and parses `SHOW SLAVE STATUS` by column
name, never by position.

**Schema changes arrive through replication, never through `db-migrate`
on the replica.** A package upgrade on the primary runs Flyway there; the
DDL and the `flyway_schema_history` rows are binlogged and the replica
applies them. `db-migrate`, `db-baseline`, `db-repair` and `db-validate`
are refused on the replica role (there is no WAR to validate against, and
a migration run there would diverge the copy). Consequently the
`carlos-emr-ctl` versions on the two hosts do not need to match — the
token carries its own schema version and both sides refuse a token they
do not understand — but the **MariaDB** rule is directional, and documented: the replica
should be the same or a later version than the primary, a constraint that
also applies to minor/patch releases (replicating from newer to older is
not supported). `join` refuses an older replica and
merely notes a newer one; the earlier "same major.minor" wording was too
strict and would have blocked the normal rolling-upgrade path.

**Replica disk.** `log_slave_updates` means the replica keeps its own
10 days of binlogs (the shared drop-in's `binlog_expire_logs_seconds`)
on top of the relay logs and the data, which is why the join preflight
demands 2× the primary's reported data size.

**Binlog retention vs. a disconnected replica.** The primary keeps 10 days
of binlogs (`binlog_expire_logs_seconds`, shared with the backup's PITR
window). A replica that is down longer than that cannot resume and must be
re-seeded. `carlos-ctl replica status` on the primary warns when a known
replica's last-seen GTID is older than half the window, and says "re-seed"
outright past it. The retention value is not changed by this work.

### 4.3 Network and TLS

- The primary binds loopback **plus exactly one** address the operator names
  (`carlos-ctl replica add <replica-ip> --listen <primary-ip>`).
  `<replica-ip>` is the address the replica *connects from* as the primary
  sees it — its VPN address when tunnelled, not its LAN address — because
  it becomes the host part of the replication account; `join` reports the
  source address the primary actually saw so a mismatch is diagnosed in
  one line rather than as a generic access-denied. `0.0.0.0`
  and `::` are refused. An address that is not on a local interface is
  refused. A globally routable address prints a warning recommending a VPN
  (WireGuard) and requires `--allow-public`.
- The tool does **not** manage the host firewall (the package only Suggests
  `nftables`). It prints the exact rule to add (nft and ufw forms) for
  "3306 from `<replica-ip>` only". `check` can only assert what is
  *listening*; it says plainly that the firewall rule is the operator's,
  the way it already says the backup repository being local is.
- A private **replication CA** and a server certificate for the primary are
  generated once by a new `carlos-emr-cert db-tls` sub-command into
  `/etc/mysql/carlos-emr-tls/` (root:mysql, 0640 keys), 10-year CA, 2-year
  server certificate with the listen IP and host name as SANs (MariaDB
  verifies IP-address SANs since 10.4.5, MDEV-18131, so `MASTER_HOST=<ip>`
  with `MASTER_SSL_VERIFY_SERVER_CERT=1` validates). **The CA is
  per primary host and its private key never leaves that host.** Only the
  CA's public certificate travels in the join token; the replica pins it.
  A promoted replica generates its *own* CA and server certificate during
  `promote` (it never received the old key), and every host re-joining it
  gets a fresh token carrying the new CA — which is the correct trust reset
  after a failover, not a limitation.
- Renewal must work on a host that has no application and therefore none
  of `carlos-emr`'s timers: `carlos-emr-ctl` ships
  `carlos-emr-db-tls-renew.timer` (weekly), enabled by `db-tls` when
  material exists, renewing the server certificate under 30 days from
  expiry and reloading MariaDB's TLS context with `FLUSH SSL` (no restart).
  Replicas verify the server certificate on every reconnect, so `check` on
  the replica reports the pinned certificate's remaining validity.
- Client certificates (mutual TLS, `REQUIRE SUBJECT`) are a phase-3
  hardening option, not phase 1.
- **AppArmor (verified against the Debian profile that Ubuntu 26.04
  ships):** the `mariadbd` profile (`/etc/apparmor.d/mariadbd`, from the
  Debian packaging's `debian/apparmor/mariadbd`, enforcing since
  1:11.8.6-4) grants `/etc/mysql/** r`, so
  `/etc/mysql/carlos-emr-tls/` is readable without any local override.
  It grants **nothing else** under `/etc/ssl` except `openssl.cnf`, and
  nothing under `/etc/carlos-emr/`, which is why the MariaDB material
  cannot share the nginx certificate directory. Site overrides go in
  `/etc/apparmor.d/local/mariadbd` (`include if exists`), which the plan
  does not need. `check` already asserts the profile is loaded and
  enforcing; the smoke test keeps one line for it.

### 4.4 Credentials and the join token

Created on the primary by `replica add`, written 0600 root to
`/var/lib/carlos-emr/replicas/<replica-ip>/join.token`, moved to the replica
by the operator (`scp`), consumed and shredded by `replica join`. JSON, one
schema version field, and:

| Field | Purpose |
|---|---|
| `primary.host`, `primary.port` | where to stream from (the listen IP) |
| `primary.mariadb_version` | refuse a replica *older* than the primary's series; note a newer one |
| `primary.ca_pem` | server certificate pinning |
| `repl.user`, `repl.password` | `repl_<replica-ip-mangled>@<replica-ip>`, `REQUIRE SSL`, grants = the backup account's set, host-restricted |
| `accounts.carlos`, `accounts.drugref`, `accounts.backup` | the passwords the primary's own `carlos.properties`, `drugref2.properties`, `backup.env` hold |
| `accounts.app_hosts` | IPs the `carlos`/`drugref` accounts must accept **after promotion**, so promotion needs no account work. Defaults to the primary's listen IP (the address the app host will present when it later connects over the same path); extendable with `replica add --app-host <ip>` |
| `db.size_bytes` | the primary's `information_schema` data+index size for the two schemas, for the replica's free-space preflight |
| `db.name`, `db.province`, `db.schemas` | `CARLOS_DB_NAME`, the schema province, and the list of schemas to seed (`carlos`, plus `drugref2` only when it exists on the primary — `mariadb-dump --databases` fails on a missing one) — recorded on the replica so a later full install on it inherits the right migrations |
| `primary.tz` | `CARLOS_TZ`, so `db-apply-settings` aligns the replica's `default-time-zone` the same way (it matters the day the replica is promoted and `NOW()` meets the JVM's clock) |
| `issued_at`, `expires_at` | 24 h, **checked by `join` only** (the `repl` password is not server-expired — a `PASSWORD EXPIRE INTERVAL` would cut off the running replica's reconnects). Revocation is `replica remove` or `replica add --reissue`; an unconsumed token past its expiry is reported by `replica status` on the primary |

**Re-running `replica add` is a no-op for a joined replica.** The
preseeded postinst path calls `replica add` on *every* configure (upgrades
included), and an operator will re-run it by hand. If a re-run regenerated
the `repl` password and wrote a fresh token, the live replica's IO thread
would fail at its next reconnect with a credential error — a silent break
caused by an upgrade. So: when the per-replica state directory records a
consumed token (the primary has seen that account open a `Binlog Dump`
connection, which `replica status`/the watch timer record), `replica add`
re-asserts the grants and the drop-in, changes no password, writes no
token, and says so. Only `--reissue` rotates the credential, and it prints
that the replica must `join --credentials-only` before its next reconnect.

Why the token carries the application passwords: account DDL does not
replicate (deliberately — the PITR contract). Without them, a promoted
replica would have no `carlos` account the app's existing properties file
can authenticate against, and promotion would become "rotate every
credential on two hosts during an outage". The token is already a secret
that grants the whole binlog stream (all PHI); the passwords do not widen
what it unlocks. It is single-use and short-lived.

Consequence for **`carlos-ctl rotate`** on a primary with replicas: the
verb keeps working, and prints that the replicas now hold stale application
passwords; the follow-up is `replica add <ip> --reissue` on the primary and
`replica join --credentials-only <token>` on each replica. `replica status`
on the primary shows the rotation generation it last handed each replica so
the drift is visible, not remembered. (Automatic propagation would mean
binlogging account DDL, which is the invariant this design keeps.)

**The replica's own accounts** are provisioned by the same `db-users` code
path with the passwords injected instead of generated, still under
`sql_log_bin = 0`, with the extra `@<app-host-ip>` host entries. Two
grants live *outside* `db-users` today and must be applied by `join` as
well, or promotion fails in the drug-lookup path: the `drugref` account's
`ALL PRIVILEGES ON drugref2.*` and the `backup` account's read grants on
`drugref2`, both currently issued by `carlos-emr-drugref.postinst`. The
replica gets its own `/etc/carlos-emr/carlos.properties` (from the shared
skeleton) and `backup.env` so that a future full install on it (T2/T3) and
`carlos-ctl db`, `db-info`, `db-validate` work there unchanged.

### 4.5 Seeding

Phase 1 seeds with a logical dump the **replica pulls** over the replication
port, as the `repl` account, over TLS:

```
mariadb-dump --host=<primary> --port=3306 --ssl-ca=<pinned> --ssl-verify-server-cert \
  --user=repl_… --single-transaction --gtid --master-data=2 \
  --hex-blob --routines --events --triggers --no-tablespaces \
  --default-character-set=utf8mb4 --databases <db.schemas from the token> \
| <carlos-ctl's seed filter: prepends SET SESSION sql_log_bin=0, SET NAMES utf8mb4 COLLATE utf8mb4_general_ci,
   SET SESSION sql_mode=''; passes every line through unchanged; records the commented
   CHANGE MASTER / SET GLOBAL gtid_slave_pos lines wherever they occur> \
| mariadb --protocol=socket --user=root
```

(Shown as a pipeline for clarity; `join` runs it as one Python process
owning both ends so a failure in any stage fails the join, which a shell
pipeline without `pipefail` would not.)

The same flags the nightly backup uses (`--single-transaction --hex-blob
--routines --events --triggers --no-tablespaces`), so the seed is a copy the
project already trusts to restore. `max_allowed_packet = 1G` is in the
shared drop-in on both sides. The dump streams straight into the local
server; nothing lands on disk.

Two mechanics that are easy to get silently wrong, both checked against
the 11.4 client source (`client/mysqldump.cc`: `check_consistent_binlog_pos`,
`do_show_master_status`, `do_print_set_gtid_slave_pos`):

- **No global read lock on a MariaDB primary.** With `--single-transaction`
  and `--master-data`, `mariadb-dump` first asks the server for the
  `Binlog_snapshot_file` / `Binlog_snapshot_position` status variables; when
  they exist (every MariaDB with binary logging on, i.e. every CARLOS
  primary) it opens `START TRANSACTION WITH CONSISTENT SNAPSHOT`, reads the
  position from inside that snapshot and converts it with
  `BINLOG_GTID_POS()` — no `FLUSH TABLES WITH READ LOCK` is issued. The lock
  *is* taken if `--flush-logs` is passed or binary logging is off, so
  `join` never passes `--flush-logs` and refuses a primary whose
  `SHOW STATUS LIKE 'binlog_snapshot_%'` comes back empty. (MySQL's
  `mysqldump` does lock here; the difference is why this is stated
  explicitly.) The dump's `REPEATABLE READ` snapshot holds open a read view
  for the duration: DDL on the primary must not run while a seed is in
  progress (`join` says so), and "seed outside clinic hours" is advice
  about network and I/O load, not locking.
- **Capturing the position.** With `--master-data=2 --gtid` the dump
  carries three commented lines: near the head,
  `-- CHANGE MASTER TO MASTER_USE_GTID=slave_pos;` and
  `-- CHANGE MASTER TO MASTER_LOG_FILE='…', MASTER_LOG_POS=…;`, and **at
  the very end of the dump** — deferred until after the `mysql.gtid_slave_pos`
  placeholder table has been dumped — `-- SET GLOBAL gtid_slave_pos='<gtid>';`.
  A head-only tee would therefore never see the GTID. `join` streams the
  dump through a line filter that passes everything to the local `mariadb`
  client unchanged and records those comment lines wherever they occur;
  after the load completes it validates that a GTID was captured, then
  issues its own `SET GLOBAL gtid_slave_pos = '<gtid>'` and
  `CHANGE MASTER TO … MASTER_USE_GTID = slave_pos` with host, port,
  credentials and TLS. It never uses `--master-data=1` (which would execute
  a `CHANGE MASTER` lacking host and credentials, and a `SET GLOBAL
  gtid_slave_pos` at the tail outside the tool's control). A missing or
  unparsable position aborts the join before `START SLAVE`; it is never
  defaulted.

Refusals before any byte moves: a replica MariaDB older than the primary's,
a primary without the `Binlog_snapshot_*` status variables, the `carlos`
schema already holds tables (unless `--reseed`, which requires the
`--confirm <server-name>` idiom `destroy-data` uses), free space under
2 × the primary's reported data size, the primary unreachable over TLS with
the pinned CA, token expired.

Later seed methods (section 6, phase 4): `--seed physical` (`mariadb-backup` streamed
over ssh, for very large sites) and `--seed restic` (restore the newest
backup snapshot, useful when the primary's WAN link is the constraint).

### 4.6 Health, alerting, and `check`

- **`carlos-emr-replica-watch.timer`** (every 5 min, on **both** roles).
  On the primary: every replica recorded under `/var/lib/carlos-emr/replicas/`
  has a live `Binlog Dump` thread (`SHOW SLAVE HOSTS` / processlist) and its
  last-seen GTID is inside the binlog retention window — this is what
  catches a replica host that has died or lost its VPN, which the replica's
  own timer can never report. Alerts go to the primary's existing
  `backup.env` webhook/email when set, else `replication.env`'s. On the
  replica: IO and SQL threads running, `Seconds_Behind_Master` under
  `CARLOS_DB_REPL_MAX_LAG_SECONDS`, `Last_IO_Error`/`Last_SQL_Error` empty,
  GTID position advancing when the primary's is, `read_only` still ON, disk
  above 10 %. Any failure alerts once (webhook / email, same payload shape
  as the backup's `alert()`, extracted into `carlos_ctl/alert.py` so the
  two share one implementation) and writes
  `/var/lib/carlos-emr/.replica-last-ok`; recovery clears it.
- A watch timer with no alert channel configured is a silent one:
  `check` prints the same NOTE for an empty webhook/email pair that it
  already prints for a local-only backup repository, and `join` prints
  it at the end of a successful join.
- **`carlos-ctl check`** grows a `replication` section on both roles and
  changes one existing assertion: the "MariaDB listens on loopback only"
  check accepts the configured `CARLOS_DB_REPL_LISTEN_IP` and still fails on
  any other address. On a replica it also proves `read_only`, the freshness
  stamp, and that the primary's certificate still verifies against the
  pinned CA (expiry visibility).
- **`carlos-ctl replica status`**: primary — connected replicas (`SHOW
  SLAVE HOSTS`), current `gtid_binlog_pos`, oldest binlog age vs. the
  retention window, tokens issued and whether each was consumed, credential
  generation per replica. Replica — the watch-timer view plus the primary
  endpoint and the last successful sync time.

### 4.7 Promotion (fenced, manual)

`carlos-ctl replica promote --confirm <server-name>` on the replica:

1. **Fence.** If the IO thread is connected, or the primary answers on the
   replication port, refuse: "the primary is alive; stop `mariadb` there (or
   isolate it) first". `--force` exists for a primary that is reachable but
   known-corrupt, and prints what it is bypassing.
2. **Drain.** `STOP SLAVE IO_THREAD`, wait until the SQL thread has applied
   every received event (`Exec_Master_Log_Pos` = `Read_Master_Log_Pos`, GTID
   `gtid_slave_pos` = `gtid_io_pos`), bounded with a visible countdown.
3. **Promote.** `RESET SLAVE ALL`; rewrite the 62- drop-in to the primary
   shape (`read_only` off, keep the new server_id, and bind the address the
   replica recorded as its own replication address at join — `--listen`
   overrides it), run the compare-then-restart path; set
   `CARLOS_DB_ROLE=primary` and `CARLOS_DB_REPL_LISTEN_IP`.
4. **TLS.** Generate this host's own replication CA and server certificate
   (`carlos-emr-cert db-tls`), enable the renewal timer.
5. **Print the runbook** for the application host, exactly:
   `CARLOS_DB_HOST=<this ip>` in `carlos-emr.env`, `carlos-ctl init-config`,
   `carlos-ctl restart`, then `carlos-ctl check` — plus the DrugRef endpoint,
   which `init-config` does **not** render today: `db_url` in
   `/etc/carlos-emr/drugref2.properties` points at `127.0.0.1:3306` and must
   follow the database (phase 3 teaches `init-config` to render it from
   `CARLOS_DB_HOST`; until then the runbook names the edit). And the
   reminder that the nightly backup on the app host must be moved or
   re-targeted (section 7).
6. **Offer the reverse** once the old primary is repaired: on the new
   primary `replica add <old-ip>`, on the old primary `replica join --reseed`.

`replica demote` is not a verb: "turn a primary back into a replica" is
`replica join --reseed` with the destructive confirmation, because the only
safe way to re-join a host that has taken writes is to re-seed it.

### 4.8 Interaction with backups and PITR

- Backups **stay on the primary** in phase 1; nothing about
  `carlos-emr-backup`, the restic layout, or the drill changes. The replica
  is **not** a backup: a `DELETE` replicates in milliseconds.
- Binlog retention is shared; the replica adds a consumer but no new
  retention rule.
- `binlog_ignore_db = carlos_restore_drill` on the primary keeps the drill
  off the replica. The replica must never run the drill against the
  replicated schema; the watch timer has no drill.
- The backup's `--master-data=2` file+position anchor is untouched. A
  restore onto a *replica* is not a supported path (restore onto the
  primary, re-seed the replica).
- **A point-in-time restore on the primary invalidates every replica.**
  The restored server's binlog and GTID history no longer match what the
  replicas applied; their IO threads stop with error 1236 (or, worse, in
  a non-strict setup silently apply from a wrong position). `gtid_strict_mode`
  makes it stop; the watch timer alerts on it; and the README's restore
  runbook ends with "re-seed each replica: `replica join --reseed`". The
  backup script itself prints that line when it restores on a host whose
  `replication.env` says `primary`.
- **Offloading the nightly dump to the replica** (section 6, phase 4) is the natural
  phase-4 feature; it needs `--dump-slave` semantics so the PITR anchor
  still refers to the primary's binlog.

---

## 5. Operator experience (the turnkey flow)

Two machines, A (existing or fresh full install) and B (new, Ubuntu with
the CARLOS apt source). Six commands, all of which are idempotent and safe to
re-run:

```
# On A — the full server (right after install, or years later)
sudo carlos-ctl replica add 10.0.0.6 --listen 10.0.0.5
#   → generates the MariaDB TLS CA + server cert (once), renders the
#     replication drop-in, restarts MariaDB once, creates the host-restricted
#     replication account, prints the firewall rule, writes the token:
#     /var/lib/carlos-emr/replicas/10.0.0.6/join.token   (0600 root, expires in 24 h)
sudo scp /var/lib/carlos-emr/replicas/10.0.0.6/join.token admin@10.0.0.6:

# On B — the replica
sudo apt install carlos-emr-db-replica
sudo carlos-ctl replica join ./join.token
#   → preflight, renders the replica drop-in, restarts MariaDB once,
#     provisions the same accounts, pulls the seed over TLS, CHANGE MASTER,
#     START SLAVE, waits for lag 0, enables the watch timer, shreds the token
sudo carlos-ctl check          # replication section: IO/SQL running, lag 0, read_only ON

# Day two, either side
sudo carlos-ctl replica status
```

### 5.1 At first install (fresh server)

Two low-priority debconf questions on `carlos-emr`, never shown at the
default priority, both defaulting to empty (off), for unattended or scripted
installs:

```
carlos-emr/replica-listen-ip     string   10.0.0.5        # bind this ONE extra address
carlos-emr/replica-allow-from    string   10.0.0.6        # comma-separated replica IPs
```

When both are preseeded (`debconf-set-selections`), the postinst runs
`carlos-ctl replica add <ip> --listen <listen-ip>` for each replica after
provisioning, so the fresh install ends with the primary side ready and the
tokens written under `/var/lib/carlos-emr/replicas/`. The interactive
installer does not ask; a fresh interactive install simply runs `replica
add` right after `apt install`. On a fresh install the second MariaDB
restart this causes is harmless: the postinst provisions and migrates
before it starts the application, and on the preseeded path `replica add`
runs before that start, so nothing is connected yet. Like every other
provisioning step in the postinst it is non-fatal: a failure (listen IP
not present, MariaDB unreachable) prints the exact `replica add` command
to re-run and never blocks the apt transaction.

The replica side mirrors it: `carlos-emr-db-replica` accepts a preseeded
`carlos-emr-db-replica/join-token` path (a file placed on the host before
`apt install`) and its postinst runs `replica join` against it, so a fully
unattended two-host bring-up is `apt install` on each machine plus one
`scp` between them. Interactive installs run `replica join` by hand as
above.

### 5.2 On an established site

The same `replica add`, on a server that has been in production for any
length of time:

- no schema change, no Flyway migration, no application restart;
- one MariaDB restart to bind the listen address and load the TLS
  material, through `db-apply-settings`'s compare-then-restart path — so a
  re-run never bounces the server again — refused while a backup or drill
  is running (the existing `_refuse_while_backup_runs` guard), and
  deferrable: `replica add --no-restart` renders everything and prints the
  one command to run in the maintenance window. The application's pool
  (`testOnBorrow`) reconnects on its own after the restart;
- the seed dump runs as `--single-transaction` against a binlogging
  MariaDB primary, which takes **no global read lock** (section 4.5); it
  does hold a long read view, so no schema migration may run on the primary
  during the seed, and it moves the whole database over the wire — run
  `replica join` outside clinic hours for the load, not for locking;
- `replica remove <ip>` undoes all of it (account, token, listen address
  once the last replica is gone), leaving only the certificate material
  behind for a future re-enable.

`dpkg-reconfigure carlos-emr` shows the two replication questions only at
low priority; existing answers are seeded from `replication.env` so an
upgrade never resets a configured listen address.

Promotion, when needed, is section 4.7's single command on B followed by
the three-line runbook on A.

---

## 6. Implementation phases and work items

Each phase is a separately reviewable PR (or small set), each leaves the
installed base working unchanged.

### Phase 0 — foundations (no behaviour change for existing installs)

1. **`carlos-emr-ctl` package split.** `debian/control`: new binary; move
   `carlos_ctl/`, `bin/carlos-ctl`, `bin/carlos-emr-cert`, `carlos-ctl.8`,
   `60-carlos-emr.cnf`, the sysusers file and the `carlos.properties` /
   `backup.env` skeletons into it in `debian/rules`; `Breaks`/`Replaces`
   against pre-split `carlos-emr` (the `60-carlos-emr.cnf` conffile changes
   owner package — dpkg transfers it under `Replaces` keeping a locally
   modified copy, which the upgrade test asserts explicitly); `carlos-emr` `Depends: carlos-emr-ctl (=
   ${binary:Version})`. Adjust `deb-packages.yml` expectations for the new
   artifact name (workflow edits are a maintainer task; the plan lists the
   exact asset-name checks that change: the `startswith("carlos-emr_")`
   assertions and the upload loop).
2. **`carlos_ctl/role.py`** with the detection above; `cli.py` gates verbs by
   role and prints the role in `--help` (`start`/`stop`/`restart`/`logs`/
   `cert selfsigned|acme|manual`/`waf`/`init-config` need the app;
   `db-migrate`/`db-baseline`/`db-repair`/`db-validate`/`demo-data`/
   `bootstrap-admin` are refused on the replica role); a non-standalone
   role with no `carlos-emr.env` is an error, never a default;
   `validate.py` skips app-only sections on a db-only host.
3. **`carlos_ctl/alert.py`**: the webhook/email `alert()` from
   `carlos-emr-backup`, in Python, used by the watch timer (the bash backup
   script keeps its own copy until a later cleanup).
4. **Unit tests for the deb `carlos_ctl`** under `debian/tests/unit/`
   (pytest, mirroring `carlos-podman/tests/unit/` — `conftest.py` stubs for
   `run()`, filesystem fixtures for the env/properties writers). Cover
   `role.py`, `config.Settings`, `env_get/env_set/prop_*`, and the
   drop-in renderers added below. Wire into the existing lint job that
   already runs on PRs (maintainer task in `.github/`).
5. **`carlos-emr-cert db-tls`**: CA + server certificate generation under
   `/etc/mysql/carlos-emr-tls/`, `status` reports it, `renew` renews it and
   issues `FLUSH SSL`; `carlos-emr-db-tls-renew.timer` in `carlos-emr-ctl`.
   Verify the path against the enforcing `mariadbd` AppArmor profile on
   Ubuntu 26.04 and record the outcome in the 60- drop-in's comments.

### Phase 1 — primary side

6. **`carlos_ctl/replication.py`** (new module; verbs `replica add`,
   `replica remove`, `replica status`, `replica add --reissue`):
   - listen-IP validation (local interface, not wildcard, public needs
     `--allow-public`);
   - `62-carlos-emr-replication.cnf` renderer (primary shape) +
     `db-apply-settings` extended so its compare set includes
     `bind_address`, `ssl_cert` (the configured path, rather than the
     legacy `have_ssl` flag), `gtid_strict_mode`;
   - `repl_<ip>@<ip>` account with the backup grant set, `REQUIRE SSL`,
     `sql_log_bin = 0`;
   - token writer (schema v1), per-replica state dir, expiry, `--reissue`;
   - `replica remove <ip>`: drop account, delete state; if it was the last
     replica, drop the listen address from the drop-in (TLS material
     stays);
   - firewall hint printer;
   - the `ip_nonlocal_bind` sysctl drop-in and its removal on the last
     `replica remove`;
   - the primary-side `carlos-emr-replica-watch` timer (connected replicas,
     retention-window check, unconsumed tokens);
   - `--no-restart` (render and print the deferred restart command).
7. **`check`**: loopback assertion accepts the configured listen IP;
   `replication` section for the primary role.
8. **Preseed path**: the two low-priority debconf questions in
   `carlos-emr.templates` / `carlos-emr.config`, seeded from
   `replication.env` on reconfigure; postinst calls `replica add` for each
   preseeded replica after provisioning (non-fatal, like the other
   provisioning steps).
9. **Docs**: `README.Debian` section "8. Replication (optional)";
   `docs/carlos-ctl.md` verb reference; `docs/install-deb.md` pointer;
   `carlos-ctl.8`.

### Phase B (Path B, chosen — see section 11 for the design)

- **B0** primary-key migration for the 24 key-less tables (Flyway
  migration, authorized; see 11.2), `innodb_autoinc_lock_mode = 2` in the shared
  drop-in, DrugRef seed created as InnoDB up front.
- **B1** `carlos-emr-ctl` split and `role.py` (phase 0 items 1–4), now
  required because two of the three cluster hosts carry no application.
- **B2** cluster verbs in `carlos_ctl/cluster.py`: `cluster init`, `cluster
  add`, `cluster join`, `cluster status`, `cluster remove`, `cluster
  bootstrap`; the 63- Galera drop-in; wsrep TLS from the existing
  `carlos-emr-cert db-tls` CA; `carlos-emr-db-node` and
  `carlos-emr-db-arbiter` packages; the cluster watch timer; `check`.
- **B3** application side: `CARLOS_DB_HOSTS` failover list rendered into
  `carlos.properties` and `drugref2.properties`, the start wrapper's
  any-of database wait, the restore drill's cluster-aware load, `db-migrate`
  pinned to one node.
- **B4** two-node-plus-arbiter integration test (11.9), then the README's
  cluster runbook.

### Phase 2 (Path A, optional, before or after Path B) — replica package and join

Must also deliver, from this review: per-host `server_id` in the replica
drop-in (4.2, 11.3); `replica leave`; candidate-primary handling when the
primary is a cluster (section 13); `--explain`; the mandatory alert
channel; the ufw handling of 12.4.

10. **`carlos-emr-db-replica`** binary package: depends, `Conflicts:
   carlos-emr` (phase 2 only), postinst that creates
   `/etc/carlos-emr/` from the shared skeletons, applies the shared 60-
   drop-in via `db-apply-settings`, installs the watch timer disabled until
   `join` succeeds. `postrm` never drops the replicated schema (same
   contract as `carlos-emr`: only `destroy-data` destroys data).
11. **`replica join <token> [--reseed --confirm <name>] [--credentials-only]`**:
    preflight → replica drop-in → local accounts with injected passwords,
    app-host entries and the `drugref2` grants → streamed seed with the
    header tee → explicit `gtid_slave_pos` → `CHANGE MASTER` (TLS, pinned
    CA, GTID) → `START SLAVE` → wait for lag 0 → write `replication.env`
    and the replica's `carlos-emr.env` (db name, province, tz, own host
    name) → enable timer → print the alert-channel note → shred token. Plus the primary-side guards: `demo-data`
    refuses and `destroy-data` warns when replicas are recorded.
12. **`carlos-emr-replica-watch`** service + timer, `replica status`
    (replica role), `check` replication section (replica role).
13. **`replica promote --confirm <name> [--force]`** as in 4.7.
14. **Integration test** (section 8) exercised end to end.

### Phase 3 — remote database on the app host, hardening

15. `carlos-emr-backup`: dump and drill legs accept a TCP+TLS endpoint when
    `CARLOS_DB_HOST` is not local (today socket-only by design); the `backup`
    account on the promoted replica already exists from the token.
16. `carlos-ctl` root-socket verbs (`db`, `db-users`, `bootstrap-admin`,
    `destroy-data`) either refuse clearly on an app-only host ("run this on
    the database host") or take `--db-host`; `check` tolerates no local
    `mariadb.service` when the database is remote.
17. Semi-synchronous replication as `replica add --semi-sync` (primary
    `rpl_semi_sync_master_enabled`, timeout fallback to async; replica
    `rpl_semi_sync_slave_enabled`), off by default, recommended for a LAN
    replica, discouraged over a WAN.
18. Optional mutual TLS for the replication account.

### Phase 4 — later (each its own decision)

19. Backup offload to the replica (`--dump-slave` anchoring).
20. Lift `Conflicts: carlos-emr` for the warm-standby topology (T3); the
    app on such a host must refuse to start while `CARLOS_DB_ROLE=replica`
    and `CARLOS_DB_HOST` is local (it would only get read-only errors).
21. Physical and restic seed methods.
22. Galera evaluation (primary-key audit for the 22 PK-less baseline
    tables first), with `carlos-emr-db-arbiter` as the `garbd` role package
    if that path is ever chosen.

---

## 7. Known single-host assumptions this touches

Listed so no phase forgets one:

| Assumption | Where | Phase that handles it |
|---|---|---|
| MariaDB listens on loopback only | `60-carlos-emr.cnf`, `validate.py` | 1 (one extra explicit address) |
| Backup dump/drill use the unix socket only | `carlos-emr-backup`, `backup.env` comments | 3 |
| `db-users` targets `@localhost`/`@127.0.0.1` only | `dbops.py` | 2 (app-host entries from the token) |
| `check` requires local `mariadb.service` and a `java` process | `validate.py` | 0 (role gating), 3 |
| Binlog retention sized for the backup shipping interval only | `60-carlos-emr.cnf` | 1 (status warns; value unchanged) |
| `rotate` assumes one host holds every credential file | `dbops.py` | 2 (documented `--reissue` / `--credentials-only` loop) |
| `carlos-emr.service` `Wants=mariadb.service` | unit file | harmless with a remote DB; the start wrapper's DB wait already uses `CARLOS_DB_HOST` (verified: `carlos-emr-tomcat` probes `/dev/tcp/$CARLOS_DB_HOST/$CARLOS_DB_PORT`) |
| DrugRef's database endpoint is a separate, un-rendered file | `drugref2.properties` `db_url=jdbc:mysql://127.0.0.1:3306/drugref2`; `init-config` never touches it | 2 (runbook names the edit), 3 (`init-config` renders it) |
| `destroy-data` drops the schemas under `sql_log_bin = 0` | `dbops.py` | 2: on a primary with replicas it prints that every replica still holds the clinical record and must be destroyed there too (`destroy-data` on each) — a decommission that forgets a replica is a PHI retention failure |
| `demo-data` loads under `sql_log_bin = 0` | `dbops.py` | 2: refused on a primary that has replicas (they would silently diverge); the operator re-seeds after loading, or loads before adding replicas |
| `bootstrap-admin` resets `carlosdoc` under `sql_log_bin = 0` | `dbops.py` | 2: documented — a reset that runs *after* a replica joined does not reach it; the seed dump carries whatever the table held at join time. `replica status` on the replica compares the `carlosdoc` hash against the published seed and warns if it is live |
| DrugRef seed load is binlogged, its Aria→InnoDB conversion and grants are not | `carlos-emr-drugref.postinst` | 2: `join` dumps `drugref2` when it exists; installing `carlos-emr-drugref` on the primary *after* a replica joined replicates the seed rows — the smoke test (section 8) confirms whether the engine conversion also replicates, else `join --reseed` is the documented answer |
| `carlos.properties` skeleton and the `carlos`/`carlos-backup` accounts are shipped only by `carlos-emr` | `debian/rules`, `carlos-emr.sysusers` | 0 (moved to `carlos-emr-ctl`) |
| `config.Settings` silently defaults `db_name`/`province`/`server_name` when `carlos-emr.env` is missing | `util.env_get` returns `None` on `OSError`; `config.py` | 0 (`role.py` refuses non-standalone roles without the file), 2 (`join` writes it from the token) |
| Flyway verbs assume the WAR and a writable schema are local | `dbops.run_flyway` | 0 (refused on the replica role; DDL arrives by replication) |
| The PITR restore runbook assumes no downstream consumer of the binlog | `carlos-emr-backup`, README section 7 | 2 (restore prints the re-seed instruction on a primary) |

---

## 8. Test plan

- **Unit** (phase 0 onward, pytest): drop-in renderers are byte-exact;
  token round-trips and rejects expired/foreign-version tokens; listen-IP
  validation; `SHOW SLAVE STATUS` parsing by column name for both spellings;
  role detection matrix; fence logic in `promote` with a stubbed
  reachability probe.
- **Already verified from primary sources** (recorded in section 10 so
  nobody re-derives them): the dump's three comment lines and their
  positions, the no-lock `Binlog_snapshot_*` path, `bind_address` lists
  (10.11.1, MDEV-24377), the `REPLICA` synonyms (10.5.1) and the absence of
  `CHANGE REPLICATION SOURCE TO`, IP-SAN verification (10.4.5), the
  semi-sync variable names and 10 s default timeout, `FLUSH SSL`, the
  row-format `binlog_ignore_db` semantics, `slave_parallel_mode`'s
  optimistic default, the version-direction rule, the `/etc/mysql/** r`
  AppArmor grant, and the Galera primary-key and quorum limitations.
- **MariaDB 11.4 smoke** (once, on a live server, recorded in the module
  docstring — these are behaviours no document states precisely):
  - **what happens when one listed `bind_address` is absent** (expected:
    startup failure — this is what the `ip_nonlocal_bind` sysctl exists
    for; confirm the sysctl makes it start);
  - the enforcing `mariadbd` profile really is in enforce mode on the
    installed release (the packaging file itself says `flags=(complain)`
    and the switch to enforce is a later packaging revision), and
    `FLUSH SSL` picks up a renewed pair;
  - `REQUIRE SSL` refuses a plaintext login for the `repl` account;
  - `read_only = ON` does not block the replication SQL thread and does
    block the `carlos` account, while root over the socket still writes;
  - `binlog_ignore_db` with `binlog_format = ROW` keeps the drill schema's
    row events out of the stream (the drill is the primary's, never the
    replica's);
  - the DrugRef seed's Aria→InnoDB `ALTER TABLE` conversion either
    replicates or the replica ends with Aria tables in `drugref2`, so the
    documentation can say which;
  - `slave_parallel_mode = conservative` with 4 threads applies the demo
    dataset's write pattern without retries.
- **Integration** (phase 2), scripted like `carlos-podman/tests/db-migrate-integration.sh`,
  on two systemd containers or VMs from the built `.deb`s:
  1. install A with demo data **and a non-default `CARLOS_DB_NAME`** (the
     one configuration that exposes a silent schema-name default on B);
     `replica add`; verify MariaDB listens on loopback + listen IP only,
     `repl` account exists with `REQUIRE SSL`;
  2. install B, `replica join`; assert equal `gtid_binlog_pos`, equal
     `COUNT(*)` on `demographic`, `casemgmt_note`, `document`, and an
     `information_schema` checksum of table lists for both schemas; assert
     B holds `carlos`@`<A's listen IP>`, the `drugref2` grants, and the
     same password hashes as A for the three accounts; re-run `replica
     add` on A and assert the `repl` password hash is unchanged and B's
     IO thread survives a forced reconnect;
  3. write on A (an INSERT through the app account), observe on B within
     the lag bound; attempt a write on B as the app account, expect the
     read-only error;
  4. stop A's MariaDB, `replica promote` on B, re-point A's app
     (`CARLOS_DB_HOST`, and `db_url` in `drugref2.properties`), log in
     through the front door and run a drug search — the app serves against
     B. (`carlos-ctl check` on A passing with a remote database is the
     phase 3 assertion; in phase 2 the test expects and documents its
     local-`mariadb.service` and backup-socket failures);
  5. repair: `replica add` on B, `replica join --reseed` on A, assert
     equality again;
  6. upgrade A to a package carrying a pending Flyway migration; assert B's
     `flyway_schema_history` gains the same row and the new column exists
     on B without any verb run there;
  7. failure drills: expired token refused; join against a populated
     schema refused; `promote` refused while A is up; watch timer alerts
     when B's SQL thread is stopped by hand; `db-migrate` on B refused;
     `check` on B notes the missing alert channel until one is set.
- **Removal paths** (both paths): `replica remove` + `replica leave`, and
  `cluster disband` + `cluster leave`, each asserting the host is back to
  standalone with `carlos-ctl check` passing and the data intact; `apt
  purge` of every new package asserting the retained-data list.
- **Combined A+B** (section 13): an offsite replica of node 1; kill node 1;
  assert the replica repoints to node 2 within the watch interval and its
  GTID continues; restore node 1; assert no duplicate or skipped events
  (row counts equal on all three).
- **Upgrade**: existing pre-split `carlos-emr` install upgrades cleanly to
  the split packages with no conffile prompt and no service interruption
  beyond what an upgrade already causes; `carlos-ctl check` passes before
  and after.

---

## 9. Decisions taken (formerly open questions)

Each was put to the maintainer with a recommendation; all were accepted on
2026-09-12. They are kept in question form so the reasoning stays with the
answer.

1. **Package split now, or duplicate-and-Conflict?** — **Split**, as its
   own small PR first (phase B1). Path B needs it because two of the three
   cluster hosts carry no application, and a separate PR keeps the cluster
   change reviewable.
2. **Public listen addresses.** — **Two answers.** Cluster verbs refuse a
   public address outright, no flag: Galera is a LAN design and a public
   cluster port is never right. The asynchronous `replica add` keeps
   `--allow-public` with the VPN warning, because an offsite copy is its
   purpose.
3. **Token contents.** — **Path A tokens carry the application
   passwords** (section 4.4), unchanged. Path B tokens carry none: Galera
   replicates account DDL, so every node has the accounts already.
4. **Semi-sync default (Path A).** — **Off by default.** Moot for Path B,
   which is synchronous by construction.
5. **Binlog retention.** — **Stays at 10 days.** `replica status` warns at
   half the window; a site that needs more raises it knowingly.
6. **Package names.** — **`carlos-emr-db-node` and `carlos-emr-db-arbiter`**
   for Path B; `carlos-emr-db-replica` if Path A ships. Specific names keep
   `apt list` self-describing.
7. **Non-local bind.** — **Keep the sysctl**, with the `check` assertion.
   Ordering MariaDB after a VPN unit has no stable unit name to order
   after; a site that objects binds a physical interface instead.
8. **Token revocation.** — **Accept and document.** Expiry is enforced by
   `join`; server-side password expiry would cut off a running replica's
   reconnects. Revocation is `replica remove` or `--reissue`, and the
   README treats the token like `backup.env`.

---

## 10. Verification record

Facts this plan relies on, and where each was checked, so the
implementation does not re-derive them from memory:

| Fact | Source |
|---|---|
| `mariadb-dump --single-transaction --master-data` takes no `FLUSH TABLES WITH READ LOCK` when `Binlog_snapshot_*` status variables exist; position via `BINLOG_GTID_POS()`; `--flush-logs` or no binlog forces the lock | `client/mysqldump.cc` (11.4): `check_consistent_binlog_pos`, `get_binlog_gtid_pos`, the `opt_single_transaction && opt_master_data` branch in `main` |
| With `--master-data=2 --gtid`: `-- CHANGE MASTER TO MASTER_USE_GTID=slave_pos;` and the commented file/position line at the head; `-- SET GLOBAL gtid_slave_pos='…';` deferred to the end of the dump | `client/mysqldump.cc` (11.4): `fmt_gtid_pos`, `do_show_master_status`, `do_print_set_gtid_slave_pos` |
| `bind_address` accepts a comma-separated list from 10.11.1 | MariaDB KB server system variables; MDEV-24377 |
| `REPLICA` synonyms for `START`/`STOP`/`RESET SLAVE`, `SHOW SLAVE STATUS` from 10.5.1; `CHANGE MASTER TO` remains the statement | MariaDB KB `START REPLICA`, `CHANGE MASTER TO` |
| `MASTER_SSL_VERIFY_SERVER_CERT` validates IP-address SANs since 10.4.5 | MDEV-18131 |
| Replica should be the same or a later version than the primary, minor releases included | MariaDB KB, replication with different versions |
| `slave_parallel_mode` default `optimistic` since 10.5.1; `slave_net_timeout` default 60 s; `report_host` semantics | MariaDB KB replication system variables |
| Semi-sync: `rpl_semi_sync_master_enabled`, `rpl_semi_sync_slave_enabled`, `rpl_semi_sync_master_timeout` default 10000 ms | MariaDB KB semisynchronous replication |
| `binlog_ignore_db` under row-based logging filters on the database actually affected | MariaDB KB replication filters |
| `FLUSH SSL` reloads `ssl_cert`/`ssl_key`/`ssl_ca` without restart | MariaDB KB `FLUSH` |
| Galera: InnoDB only; every table should have a primary key; `DELETE` unsupported without one; quorum is >50 % of the last membership, so two nodes have no fault tolerance without `garbd` | MariaDB KB Galera known limitations; quorum/`garbd` docs |
| Debian/Ubuntu `mariadbd` AppArmor profile grants `/etc/mysql/** r`, `/var/lib/mariadb/** rwk`, `/var/lib/mysql/** rwk`, `/etc/ssl/openssl.cnf r` only, `include if exists <local/mariadbd>`; enforcing from 1:11.8.6-4 and in Ubuntu 26.04 | Debian packaging MR !150 (`debian/apparmor/mariadbd`), Debian bug #1130272 |
| MaxScale 25.01+ is proprietary; earlier BSL releases convert to GPL on their change dates | MariaDB BSL FAQ, MaxScale licence texts |
| `carlos-emr-tomcat` waits on `CARLOS_DB_HOST:CARLOS_DB_PORT` over TCP; `env_get` returns `None` for a missing file; `init-config` never touches `drugref2.properties`; the DrugRef seed load is binlogged while its grants are not | this repository, `debian/assets/` |

---

## 11. Path B — Galera single-writer cluster (chosen)

### 11.1 What it is, and what it is not

Two database nodes in a MariaDB Galera cluster, a third small host running
the Galera arbitrator (`garbd`) as the tie-breaking vote, and the CARLOS
application pinned to one node with a driver-level failover list. Every
committed transaction exists on both nodes before the application is told
it committed; if the node the application is using dies, the driver moves
to the other node on its next connection, and no promotion, re-seed or
operator action is needed to keep serving.

Database-level active-active, application-level single writer. All nodes
are writable — the cluster does not know or care which one CARLOS uses —
but CARLOS uses one at a time, so the certification conflicts that the
application cannot retry never occur. This is why no application code
changes are needed.

It does **not** protect the application host, and in T4 the application
runs *on node 1*. Be precise about what the automatic failover therefore
covers: a MariaDB process failure or planned MariaDB maintenance on node 1
(the application keeps serving from node 2, zero loss), and the loss of
node 2 or the arbitrator (nothing visible). Losing the node 1 *machine*
still stops the clinic until the application runs elsewhere — that is the
warm-standby topology (T3), which Path B makes trivial on the database
side (a standby application host simply lists node 2 first) but which is
separate work. Say this in the README in those words; it is the first
question a technician asks. It does not replace backups either (a `DELETE` is on both nodes in
milliseconds). And it is a **local** design: every commit waits one round
trip for certification, so the nodes belong on the same LAN or campus, not
across a WAN — an offsite copy stays Path A.

### 11.2 Prerequisites, all verified against this tree

- **Primary keys.** Galera requires one on every table (`DELETE` is
  unsupported without it, and rows may sort differently per node). The
  baseline has 22 key-less tables and the BC schema 2 more:
  `InstitutionDepartment`, `IssueGroupIssues`,
  `ProviderPreferenceAppointmentScreenEForm`, `…ScreenForm`,
  `…ScreenQuickLink`, `casemgmt_issue_notes`, `custom_filter_assignees`,
  `custom_filter_providers`, `mdsNTE`, `mdsOBR`, `mdsOBX`, `mdsPID`,
  `mdsPV1`, `mdsZFR`, `mdsZLB`, `mdsZMC`, `mdsZMN`, `mdsZRG`,
  `providerExt`, `provider_facility`, `serviceSpecialists`, `specialty`;
  BC: `billinglocation`, `billingvisit`. All InnoDB, none has a raw SQL
  `DELETE` in the Java code, and the JPA entities that map some of them do
  not depend on the absence of a column. Phase B0 adds an
  `id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY` to each in one
  forward Flyway migration (`common` and `bc`). **The maintainer has
  authorized this schema change** (this repository otherwise keeps
  automated changes out of `database/`; that standing rule is lifted for
  this migration only, on record here). It is written in phase B0 as the
  next free `V1.0.N__add_surrogate_primary_keys.sql` in `common` and `bc`,
  idempotent (`ADD COLUMN IF NOT EXISTS` guarded by an
  `information_schema` check for an existing primary key), and never in a
  published tag's migration set. It is safe and useful on its own — row-based
  replication of Path A also does full-row lookups on key-less tables.
- **Nothing Galera refuses is used.** No `LOCK TABLES`, no
  `GET_LOCK`/`RELEASE_LOCK`, no XA in the Java tree (grepped). All 389
  baseline tables are InnoDB; the Java code creates no MyISAM/Aria table at
  runtime.
- **Auto-increment.** `innodb_autoinc_lock_mode = 2` is mandatory for
  Galera; it goes into the shared 60- drop-in for every role (harmless on a
  standalone host). Galera's `wsrep_auto_increment_control` then sets
  `auto_increment_increment` to the cluster size, so surrogate keys advance
  by 2 or 3 and leave gaps. The 341 `GenerationType.IDENTITY` entities are
  unaffected; the one `GenerationType.TABLE` entity (`Product`) and the 14
  `SELECT MAX(id)+1` sites are only safe because there is a single writer —
  which is a rule, not a hope: `check` fails if `carlos.properties` on any
  application host lists the nodes in a different order.
- **DrugRef seed.** `carlos-emr-drugref.postinst` loads the seed as Aria
  tables and converts them to InnoDB afterwards. Galera does not replicate
  Aria row writes, so a seed loaded *into a running cluster* would leave the
  other node with empty tables. Phase B0 changes the postinst to create the
  seed as InnoDB from the start (`sed` on the shipped SQL at load time, the
  same conversion it already does afterwards), and the README says to
  install `carlos-emr-drugref` before `cluster add` — a joining node's state
  transfer copies whatever exists.
- **Packages.** `mariadb-server` on Ubuntu carries wsrep; `galera-4` is the
  provider library; `mariadb-backup` performs the state transfers (SST);
  `galera-arbitrator-4` is `garbd`. The Debian `mariadbd` AppArmor profile
  was validated against the packaged SST methods, donor and joiner.
- **Network.** Between nodes and the arbitrator: 4567/tcp+udp (group
  communication), 4568/tcp (IST), 4444/tcp (SST). Application to nodes:
  3306. All on the cluster address, never a wildcard — the same
  listen-address rules and `ip_nonlocal_bind` handling phase 1 built.

### 11.3 Roles, packages and configuration

`CARLOS_DB_ROLE` gains two values: `cluster` (a MariaDB node) and `arbiter`
(`garbd` only). `replication.env` records `CARLOS_DB_CLUSTER_NAME`,
`CARLOS_DB_CLUSTER_ADDRESS` (this host's cluster IP) and
`CARLOS_DB_CLUSTER_NODES` (every node's IP, in application preference
order — first is the writer).

| Package | Role | Ships |
|---|---|---|
| `carlos-emr` (+ `carlos-emr-ctl`) | `cluster`, node 1, the writer | as today; `cluster init` is run here |
| `carlos-emr-db-node` (new) | `cluster`, node 2 (and 3 if wanted) | mariadb-server, galera-4, mariadb-backup, `carlos-emr-ctl`; no WAR |
| `carlos-emr-db-arbiter` (new) | `arbiter` | galera-arbitrator-4, `carlos-emr-ctl` (for `cluster join --arbiter`, `status`, `check`); no MariaDB |

Generated drop-in, `/etc/mysql/mariadb.conf.d/63-carlos-emr-galera.cnf`:

```
[mariadbd]
wsrep_on                    = ON
wsrep_provider              = /usr/lib/galera/libgalera_smm.so
wsrep_cluster_name          = <CARLOS_DB_CLUSTER_NAME>
wsrep_cluster_address       = gcomm://<node1>,<node2>[,<node3>]   # arbiter is NOT listed
wsrep_node_address          = <this node's cluster IP>
wsrep_node_name             = <host name>
wsrep_sst_method            = mariabackup
wsrep_sst_auth              = <sst user>:<password>               # 0640 root:mysql like the key
wsrep_gtid_mode             = ON                                   # one GTID stream cluster-wide
wsrep_gtid_domain_id        = 0                                    # SAME on every node: the cluster's stream
gtid_domain_id              = <per node, 1000 + node number>       # DIFFERENT per node: local-only transactions
server_id                   = <per node, derived from the cluster IP>  # overrides the shared 60- file's 1
log_slave_updates           = ON                                   # applied writesets are binlogged
wsrep_provider_options      = "socket.ssl=yes;socket.ssl_ca=…/ca.pem;socket.ssl_cert=…/server.pem;socket.ssl_key=…/server.key;gcache.size=1G;pc.recovery=yes"
bind-address                = 127.0.0.1,<cluster IP>
innodb_flush_log_at_trx_commit = 1
```

**`server_id` must be unique per host — a silent failure otherwise.** The
shared 60- drop-in sets `server_id = 1` on *every* install, which is
correct for one host and wrong the moment a second MariaDB exists: with
`log_slave_updates` and an offsite replica (section 13), events from two
"server 1"s are indistinguishable, and a replica repointed from node 1 to
node 2 would skip events carrying its own recorded source id. The 63-
drop-in therefore sets `server_id` per node (derived from the cluster IP's
last two octets so it is unique by construction, never random), `cluster
join` refuses a token whose node list already contains that id, and
`check` on every node asserts `@@server_id != 1` unless the host is
standalone. The same rule applies to the Path A replica's drop-in (4.2).
`gtid_domain_id` follows the documented Galera pattern: one shared
`wsrep_gtid_domain_id` for the replicated stream, a distinct
`gtid_domain_id` per node for anything written with `wsrep_on = OFF`
(the restore drill, a PITR restore), so local writes can never collide
with cluster GTIDs.

The TLS material is the phase-1 `carlos-emr-cert db-tls` CA and server
certificate, now issued **once, on node 1**, with every node's certificate
signed by the same CA (the token carries a node certificate and key issued
for the joining host, plus the CA). Galera's own transport and the client
port therefore share one trust root. SST over `mariabackup` is encrypted
with the same pair (`[sst] encrypt=4`, `tcert`/`tkey`/`tca`).

`60-carlos-emr.cnf` keeps `log_bin`, `binlog_format = ROW` and `sync_binlog
= 1` on every node: point-in-time recovery still needs the writer's binlog,
and with `wsrep_gtid_mode` the positions are meaningful cluster-wide.

### 11.4 Verbs (`carlos_ctl/cluster.py`)

- `cluster init --listen <ip> [--name <name>]` on node 1: renders the drop-in
  with `gcomm://` empty for the bootstrap, generates the SST account (out of
  the binlog and, since wsrep is not yet on, not replicated either — it is
  carried in every token), applies TLS, runs `galera_new_cluster` (the
  distribution's bootstrap wrapper), waits for `wsrep_ready = ON`, then
  re-renders the drop-in with the real node list. Refuses if the
  primary-key migration is pending (`db-validate`) or
  `innodb_autoinc_lock_mode` is not 2.
- `cluster add <node-ip> [--arbiter]` on node 1 (the writer; refused
  elsewhere so there is one place tokens come from): issues a token (CA, a
  node certificate/key for that IP, cluster name, the current node list,
  SST credentials, `wsrep_gtid_domain_id`, expected MariaDB series, data
  size, the alert channel) and records the IP locally. **No host ever
  writes another host's files.** Membership truth is the cluster's own
  `wsrep_incoming_addresses`; every node's watch timer and `status`
  re-render that node's own `gcomm://` list and `CARLOS_DB_CLUSTER_NODES`
  from it, so a node added or removed anywhere is reflected everywhere
  within one timer tick without ssh, and a node restarted after a long
  absence still finds the current members.
- `cluster join <token>` on `carlos-emr-db-node`: preflight (version, disk
  ≥ 2× data — SST via `mariabackup` is a full physical copy), render the
  drop-in with the full `gcomm://` list, start MariaDB, watch
  `wsrep_local_state_comment` go `Joining → Joined → Synced` (SST progress
  from the donor's `mariabackup` log), refuse to finish until Synced. The
  local `carlos-emr.env` is written from the token as in Path A. With
  `--arbiter` on `carlos-emr-db-arbiter`: renders `/etc/default/garb`
  (`GALERA_NODES`, `GALERA_GROUP`, `GALERA_OPTIONS` with the same
  `socket.ssl_*`), enables `garb.service`, and confirms the cluster size
  rose by one.
- `cluster status`: `wsrep_cluster_status` (must be `Primary`),
  `wsrep_cluster_size` vs the recorded node count (+1 for the arbiter),
  `wsrep_local_state_comment`, `wsrep_ready`, flow-control pause fraction,
  `wsrep_local_cert_failures` (must stay 0 with one writer — a rising count
  means two writers), the writer node per `CARLOS_DB_CLUSTER_NODES`, and
  which node's `grastate.dat` says `safe_to_bootstrap: 1`.
- `cluster remove <ip>` (on node 1): drops the node from node 1's list,
  rotates the SST credential (replicated as DDL), and the other nodes'
  lists follow from `wsrep_incoming_addresses` once the node is gone. Its certificate is **not** revoked
  (there is no CRL machinery in this design), so the README says plainly:
  power the removed host off or wipe its `/etc/mysql/carlos-emr-tls`; a
  host holding a valid node certificate and the old SST credential could
  otherwise rejoin until the credential rotation lands.
- `cluster leave --confirm <server-name>` on a node: stop MariaDB, remove
  the 63- drop-in and the cluster ports from the firewall it manages, set
  the role back to standalone; **the data stays** (the policy that only
  `destroy-data` destroys data holds here too), so a left node can be
  wiped with `destroy-data` or re-joined with `cluster join --reseed`.
- `cluster disband --confirm <server-name>` on node 1: the whole thing off
  in one command — node 1 continues as a standalone MariaDB with all its
  data, the application's host list collapses to loopback, the arbitrator
  and node 2 are told (in the printed output) to run `cluster leave`, and
  the certificate material stays for a future re-enable. A junior
  technician must be able to undo the cluster as easily as it was
  enabled, without a runbook.
- `cluster bootstrap --confirm <server-name>`: the one dangerous verb. After
  a full outage (every node down) Galera refuses to start until one node is
  declared the seed; after a partition that lost quorum the survivor is
  non-Primary and refuses queries. This verb runs `wsrep_recover` on the
  local node, shows its last committed seqno beside the other nodes' (when
  reachable), refuses unless this node's `safe_to_bootstrap` is 1 or
  `--force` names why, then bootstraps (`galera_new_cluster`, or
  `pc.bootstrap=YES` for a live non-Primary node). The other nodes rejoin
  with IST/SST automatically.

### 11.5 The application side (phase B3)

- `CARLOS_DB_HOSTS=<node1>,<node2>` in `carlos-emr.env` (replaces the
  single `CARLOS_DB_HOST` when set; the writer first). `init-config`
  renders `db_uri = jdbc:mysql://node1:3306,node2:3306/` and appends
  `failOverReadOnly=false` to `db_name`'s parameter string — every Galera
  node is writable, so the read-only default of Connector/J's failover
  protocol would be wrong here. The fall-back-to-primary knobs
  (`queriesBeforeRetrySource`, `secondsBeforeRetrySource`) are set so the
  driver returns to node 1 once it answers again; the exact values and the
  connector's behaviour for a *pooled* connection (DBCP2 `testOnBorrow`
  runs `db_validationQuery` on checkout, so a dead node's connections are
  evicted one checkout at a time) are pinned by the smoke test in 11.9.
- **Accounts for the failover path.** Today `carlos`, `drugref` and
  `backup` exist only as `@localhost`/`@127.0.0.1`. When the driver fails
  over, the application on node 1 connects to node 2 *from node 1's cluster
  IP*, and without `carlos@<node1-ip>` the failover "works" at the driver
  and then fails authentication — the exact silent failure this design must
  not have. `cluster init` therefore extends `db-users` to create the
  `@<cluster IP of every application host>` entries (replicated as DDL to
  every node) **with `REQUIRE SSL`**, and `check` proves the account set on
  every node matches the recorded application hosts.
- **The application-to-database path leaves loopback.** Node 1 to node 2
  is a LAN hop carrying PHI, so the JDBC connection must use TLS on that
  hop. `init-config` builds a Java truststore from the cluster CA
  (`keytool` ships with the JDK the package depends on) and renders
  `sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:…&trustCertificateKeyStorePassword=…`
  into the URL parameters. The loopback hop uses the same settings (the
  server certificate carries `IP:127.0.0.1`), so there is one configuration,
  not a special case. The truststore password is not a secret (the store
  holds only a public certificate) and lives in `carlos.properties` like the
  rest.
- **Failover must be fast, not eventual.** A node whose host is down hard
  answers nothing, and a driver waiting on the kernel's SYN timeout turns
  a seconds-long failover into minutes of a frozen EMR. The URL carries
  `connectTimeout=3000&socketTimeout=…` chosen against the longest
  legitimate query the application runs (a large report), and the smoke
  test measures time-to-first-successful-write after killing node 1's
  MariaDB and after pulling its network — both must be under 30 s.
- `drugref2.properties` `db_url` gets the same list and TLS parameters —
  `init-config` renders it from now on (the phase 3 item, pulled forward).
- `carlos-emr-tomcat`'s database wait probes any host in the list, not
  only the first.
- `carlos-ctl db-migrate` runs on the writer only (`check` and the verb
  refuse elsewhere); Galera applies the DDL cluster-wide under total order
  isolation, and `flyway_schema_history` follows with it.
- The application is single-node at a time by configuration, and `check`
  proves the ordering is identical on every application host.

### 11.6 Backups, PITR and the restore drill

- Backups keep running on node 1 (the writer) exactly as today: the dump's
  `--single-transaction` snapshot and the binlog leg are per node and
  unchanged. **While node 1 is down, no backup runs** — and a down host
  cannot alert about its own timers. That is why the `DEGRADED` message
  from the surviving members names it explicitly ("backups are not running
  while node1 is down"), why `check` on node 2 reports the age of node 1's
  last backup stamp when it can read the shared alert state, and why the
  outage is bounded by the binlog retention: node 1 back within 10 days
  resumes with a gap the nightly dump closes; longer than that the README's
  runbook says to take a manual `carlos-ctl backup full` on node 2 before
  anything else. Running the timer on two nodes against one repository is
  deliberately not done (double volume, lock contention, a drill that does
  not know which host it is proving).
- The document store is on node 1 only in T4 (the application's host); it
  is in node 1's backup, not in the cluster. A standby application host
  (T3) needs its own copy, which is that phase's problem, not this one's. `wsrep_gtid_mode` keeps the GTIDs consistent so a restore onto
  a rebuilt cluster is coherent.
- **The credential contract needs one sentence more.** Galera replicates
  `CREATE/ALTER USER` and `GRANT` as DDL regardless of `sql_log_bin`, so the
  accounts `db-users` provisions exist on every node automatically (no
  passwords in tokens for Path B). They stay out of the *writer's* binlog,
  which is the one the backup streams, so the point-in-time contract holds
  as long as backups are taken from the writer. On the other node they
  appear in its binlog through `log_slave_updates`; that node's binlog is
  never used for PITR. Written into the README.
- **The weekly restore drill.** It loads a full copy of the clinical
  database into `carlos_restore_drill`, as the unprivileged backup account.
  Galera replicates every InnoDB write, so that load would cross the
  cluster weekly, roughly doubling the dataset in transit and stalling the
  writer under flow control. Options: (a) accept it; (b) run the drill's
  load step with `wsrep_on = OFF` for the session, which needs a privilege
  the backup account deliberately lacks; (c) a root-owned helper unit that
  does (b) on the non-writer node — more machinery for the least-privilege
  drill to call; (e) load the drill's copy into **Aria** tables: Galera
  does not replicate Aria writes unless `wsrep_mode` asks for it (it does
  not, by default), so the drill stays local, unprivileged, and unchanged
  in shape — one `sed` on the dump's `ENGINE=InnoDB` at load time. The
  trade-off is that the drill then proves the dump *loads*, not that it
  loads into InnoDB specifically (InnoDB-only limits such as row size would
  not be exercised). The plan takes **(e)** as the turnkey answer and keeps
  (c) as the option for a site that wants an InnoDB-faithful drill; the
  README states the trade-off in one sentence. Open question 9.
- A point-in-time restore is now a *cluster* rebuild: restore on node 1
  with `wsrep_on = OFF`, then `cluster bootstrap`, then `cluster join
  --reseed` on node 2 (its SST wipes and re-copies). The runbook says so.

### 11.7 Failure behaviour, stated so nobody is surprised

- Node 1 dies: the driver's next connection lands on node 2 (writable);
  in-flight requests on node 1 fail once. Node 1 rejoins with IST when it
  returns; the driver falls back to it. Data loss: none.
- Node 2 dies: nothing visible; node 1 and the arbitrator keep quorum.
- The arbitrator dies: nothing visible; two nodes keep quorum. `status`
  and the watch timer say the cluster is at 2/3.
- Two of three lose each other (partition): the side without a majority
  goes non-Primary and **refuses every query** — CARLOS shows errors rather
  than accepting writes that could later conflict. That is the correct
  outcome for a clinical record, and it is also the scenario a technician
  will meet as "the server is fine but the EMR says database error":
  node 2 *and* the arbitrator unreachable (a switch failure, both on one
  hypervisor) leaves a perfectly healthy node 1 refusing to serve. So
  `carlos-ctl status` must say, in those words, what happened and the one
  command that fixes it, and `cluster bootstrap --confirm` must itself
  probe the other members and refuse without `--force` if either still
  answers — the tool, not the technician, is the one that decides whether
  forcing is safe.
- Full power loss: no node starts serving until `cluster bootstrap` on the
  node whose `grastate.dat` has `safe_to_bootstrap: 1` (Galera marks the
  last node to leave). `status` names it.
- Heavy write bursts (a large lab import) make the writer pause under flow
  control while node 2 catches up; `status` shows the pause fraction and
  `gcache.size` bounds how long a node can be away before IST becomes SST.

### 11.8 Watch timer and `check`

The phase-1 `check` replication section becomes role-aware: on `cluster`
nodes it asserts `wsrep_ready`, `Primary`, the expected size, `Synced`, a
zero certification-failure delta, the TLS pair and CA on the cluster
ports, the ports listening on the cluster IP only, `innodb_autoinc_lock_mode
= 2`, the pending primary-key migration absent, and (on application hosts)
that `db_uri` lists the nodes in the recorded order. On the arbiter:
`garb.service` active and the cluster size it reports. The watch timer
alerts on size drops, a non-Primary component, a node not Synced for longer
than `CARLOS_DB_REPL_MAX_LAG_SECONDS`, and a rising certification-failure
counter — the last is the fingerprint of an accidental second writer.

### 11.9 Verification before code (the Path B smoke list)

- Connector/J failover semantics on this WAR: `failOverReadOnly=false`
  actually writes on the secondary; the fall-back timing knobs; behaviour of
  pooled connections after the writer disappears (DBCP2 eviction via
  `testOnBorrow`).
- `wsrep_gtid_mode` with `log_bin` on MariaDB 11.4/11.8: GTID continuity
  across a failover and after IST.
- `galera_new_cluster` and `garb` unit names and `/etc/default/garb` keys on
  Ubuntu 26.04; `galera-arbitrator-4` availability.
- SST with `mariabackup` over the phase-1 TLS pair, donor and joiner under
  the enforcing AppArmor profile.
- The DrugRef seed created as InnoDB replicates row-for-row.
- The primary-key migration applies cleanly on a populated demo database
  and the JPA entities on those tables still load.
- Cost of the restore drill under option (e) versus (a), measured, and
  that `wsrep_mode`'s default really leaves Aria writes local on 11.4/11.8.
- Failover latency with the chosen `connectTimeout`/`socketTimeout`, for a
  killed MariaDB process and for a pulled network cable, under 30 s each.
- The JDBC TLS parameters against the WAR's Connector/J version
  (`sslMode=VERIFY_CA` with a file truststore).

Integration test: three systemd containers (node 1 = full server with demo
data and DrugRef, node 2 = `carlos-emr-db-node`, arbiter), `cluster init`,
`cluster add` ×2, `cluster join` ×2, assert size 3 and equal row counts;
write through the app; kill node 1's MariaDB and assert the next app write
lands on node 2 with zero loss; restart node 1 and assert IST and the
driver's fall-back; partition node 2 alone and assert it refuses queries;
stop everything and assert `cluster bootstrap` on the `safe_to_bootstrap`
node brings the cluster back; run a restore drill and assert no writeset
crossed to the writer.

### 11.11 Upgrades and maintenance (the runbook a technician follows)

- **Rolling, one host at a time, writer last:** node 2, then the
  arbitrator, then node 1. Each step is "apt upgrade; wait for
  `carlos-ctl status` to say HEALTHY". `status` refuses to say HEALTHY
  until the upgraded node is `Synced`, so the technician cannot move on
  too early. A MariaDB series upgrade (11.4 → 11.8) follows the same order;
  Galera supports one series of skew during the roll and `join` refuses
  anything else.
- **A `carlos-emr` upgrade that carries a Flyway migration** runs it on
  node 1 as today; the DDL applies cluster-wide under total order
  isolation, so every node is briefly blocked for that statement. The
  postinst prints that it is a cluster-wide DDL window when the role is
  `cluster`.
- **Planned MariaDB maintenance on node 1** (a restart for a drop-in
  change) is the automatic-failover case: the driver moves to node 2 and
  back; `db-apply-settings` says so before restarting on a cluster node.
- **Replacing a failed host:** `cluster remove <old-ip>` on node 1, install
  the package on the new host, `cluster add`/`cluster join` as for a new
  member. The same three commands for a node or an arbitrator.

### 11.10 Decisions taken (Path B; formerly open questions)

9. **Restore drill placement.** — **Option (e): load into Aria tables.**
   The drill stays local, unprivileged and unchanged in shape; it no longer
   exercises InnoDB-specific limits, which the README states. The
   root-helper option (c) stays available for a site that wants an
   InnoDB-faithful drill.
10. **Two nodes plus an arbitrator, or three nodes?** — **Two plus the
    arbitrator** by default; three only where a third capable server
    already exists.
11. **Which host runs the arbitrator?** — **One that fails independently
    of both nodes**: a small VM on a different hypervisor, a NAS, or a
    router-class box. Never node 1 or node 2.
12. **Does Path A stay in the roadmap?** — **Yes, as an optional offsite
    copy after Path B ships.** The phase-1 code stays; an asynchronous
    replica of a Galera node is a supported topology.
13. **Firewall automation scope.** — **Manage `ufw` rules when it is
    installed and active, print exact rules otherwise, `--no-firewall` to
    opt out.**
14. **JDBC TLS on the loopback hop too.** — **One configuration for both
    hops.** The server certificate already carries the loopback address,
    the overhead is negligible here, and one configuration is what a
    technician can reason about.

---

## 12. Turnkey and junior-technician requirements (binding on every phase)

The bar the maintainer set: enable, operate, and remove by a junior
technician, with no silent failure. These rules are checked in review and
tested in section 8/11.9; they are not aspirations.

### 12.1 The vocabulary is fixed

`carlos-ctl status` (and the first line of `check`) reports the redundancy
state in exactly one of four words, followed by one sentence and, when
action is needed, one command:

| Word | Meaning | Example second line |
|---|---|---|
| `NOT CONFIGURED` | standalone; nothing to do | `Enable a cluster: sudo carlos-ctl cluster init --listen <ip>` |
| `HEALTHY` | every recorded member present and in sync | `3 of 3 members; database on node1; node2 in sync` |
| `DEGRADED` | the clinic keeps working, something needs fixing | `node2 unreachable since 10:14 — fix node2; the clinic is running on node1 only` / `node1 down since 02:10 — the clinic is running on node2; BACKUPS ARE NOT RUNNING until node1 is back` / `offsite replica 3 h behind` |
| `DOWN` | the database refuses service | `this node lost quorum (node2 and the arbitrator are unreachable). If they are really off, run: sudo carlos-ctl cluster bootstrap --confirm emr.clinic` |

The watch timer sends the same words to the alert channel, once per
transition, with the same command.

### 12.2 Every refusal names the next command

No verb exits non-zero with a diagnosis alone. The message ends with the
exact command to run next (`re-run ... after ...`, `on node2 run ...`), the
way the phase-1 `replica add` and the existing postinst messages already
do. A message that cannot name a next command is a design defect.

### 12.3 The happy path is short and the flags are optional

Enable (three hosts):

```
node1:    sudo carlos-ctl cluster init --listen 10.0.0.5 --alert-webhook https://…
node1:    sudo carlos-ctl cluster add 10.0.0.6
node1:    sudo carlos-ctl cluster add 10.0.0.7 --arbiter
          (each prints the scp line for its token)
node2:    sudo apt install carlos-emr-db-node && sudo carlos-ctl cluster join ./join.token
arbiter:  sudo apt install carlos-emr-db-arbiter && sudo carlos-ctl cluster join ./join.token
anywhere: sudo carlos-ctl status
```

Remove (two commands): `cluster disband --confirm <name>` on node 1, then
`cluster leave --confirm <name>` on each other host — or just `apt remove`
them; the data stays until `destroy-data`.

Rules that keep it short:

- `--listen` omitted: the verb **lists this host's addresses** and refuses
  with "re-run with --listen <one of these>" — never guesses. A globally
  routable address is refused by the cluster verbs with no override
  (decision 2).
- `--alert-webhook`/`--alert-email` omitted on `cluster init`: refused,
  unless `--no-alerts` is given, because a cluster nobody hears about is a
  hidden single point of failure. `check` keeps nagging.
- `cluster` with no subcommand prints the state and the next sensible
  step for this host.
- Every verb accepts `--explain`: it prints what it would do, file by
  file and statement by statement, and exits 0 without doing it. This is
  how a technician (or a reviewer) sees the plan before the change.
- Every verb is idempotent and says "already done" instead of failing when
  re-run.
- Unattended installs preseed the same things the verbs ask for:
  `carlos-emr/cluster-listen-ip`, `carlos-emr/cluster-alert-webhook`,
  `carlos-emr-db-node/join-token`, `carlos-emr-db-arbiter/join-token`, plus
  the phase-1 `replica-*` keys — all low priority, all empty by default.

### 12.4 The firewall is handled, not delegated

Printing nft rules and walking away is how a junior technician gets a join
that hangs on SST forever. So:

- `cluster join` and `cluster add` **test the ports first** (3306, 4567
  tcp+udp, 4568, 4444 to every member) and refuse with the exact blocked
  port and the exact rule, before MariaDB is touched.
- When `ufw` is installed and active, the verbs **add the rules
  themselves** (`--no-firewall` opts out), scoped to the member addresses,
  and `cluster leave`/`disband` remove them. With nftables or another
  firewall the rules are printed as today.
- SST and IST have **timeouts with progress**: `join` shows the donor's
  transfer progress and fails with a named cause after a bound, never a
  silent hang.

### 12.5 Preflight checks with remedies

`cluster init`, `add` and `join` refuse, each with a remedy, on: a
different MariaDB series; clocks unsynchronised (`timedatectl`, because
TLS validation fails silently on a wrong clock); free disk under 2× the
data size (SST is a full physical copy); the pending primary-key
migration; `innodb_autoinc_lock_mode` not 2; DrugRef installed *after*
another node joined (its Aria seed would be missing there — 11.2); a
cluster IP not currently assigned; a public address without
`--allow-public`.

### 12.6 The two traps are named in the tool, not only in the README

- **Healthy node, refusing service** (11.7): `status` prints `DOWN` with
  the bootstrap command; `bootstrap` probes the other members and refuses
  to force while any answers.
- **A second writer by accident** (11.2): `check` fails on any application
  host whose node list order differs, and the watch timer alerts on a
  rising `wsrep_local_cert_failures`.

### 12.7 Applies to Path A too

The shipped `replica add` now prints the host's candidate addresses when
`--listen` is missing (implemented alongside this review). In the next
Path A touch it gains the same firewall handling, the mandatory alert
channel, `--explain`, and the replica side gains `replica leave --confirm
<name>` (stop streaming, keep the data, back to standalone) so an offsite
copy is removed as easily as a cluster member: `replica remove <ip>` on the
primary, `replica leave` on the replica, and nothing else to remember.

---

## 13. Combining an offsite copy (Path A) with a local cluster (Path B)

Both are optional and independent; this section is what makes them
*compatible*, so a clinic can run a two-node cluster in the building and
an asynchronous replica in another. It is a supported MariaDB topology (an
asynchronous replica of a Galera node), and everything below exists so it
is turnkey rather than merely possible.

- **One CA.** Path B's node certificates and Path A's server certificate
  come from the same `carlos-emr-cert db-tls` CA on node 1, so the offsite
  replica pins one CA and can verify *any* cluster node.
- **One account, everywhere.** `replica add <offsite-ip>` runs on node 1
  only (as `cluster add` does). Its `CREATE USER … REQUIRE SSL` and grants
  are DDL, which Galera replicates, so the replication account exists on
  every node without a token per node.
- **Candidate primaries.** The join token lists every cluster node as a
  candidate source in application order (`primary.candidates`), and the
  replica records them in `CARLOS_DB_REPL_PRIMARY_CANDIDATES`. Because
  `wsrep_gtid_mode` gives the cluster one GTID stream and every node keeps
  a binlog (`log_slave_updates`), the replica can be repointed to any node
  with the same `gtid_slave_pos`. The replica-side watch timer does this
  itself: when the current source is unreachable for longer than one timer
  interval and another candidate answers, it issues `CHANGE MASTER TO
  MASTER_HOST=<next>` with the pinned CA and reports the switch as
  `DEGRADED` (the offsite copy is streaming from node 2, node 1 is down).
  It never repoints while the current source still answers, and it never
  repoints to a host outside the recorded candidates.
- **Unique `server_id` and domains** (11.3) are what make the repoint
  safe; the phase-1 shared drop-in's `server_id = 1` is overridden on every
  cluster node and on the replica.
- **Promotion is fenced against the whole cluster.** `replica promote` on
  the offsite host probes *every* candidate, not only the current source,
  and refuses without `--force` while any answers: an offsite copy must
  never become a second writer beside a living cluster.
- **Point-in-time restore order:** restore on node 1 (`wsrep_on = OFF`),
  `cluster bootstrap`, `cluster join --reseed` on node 2, then `replica join
  --reseed` on the offsite host — the runbook lists all four, and `status`
  on the offsite host detects the GTID discontinuity (strict mode stops
  it) and prints that last step.
- **Removal** is per layer: `replica remove` + `replica leave` takes the
  offsite copy away and leaves the cluster untouched; `cluster disband` +
  `cluster leave` takes the cluster away and the offsite replica simply
  keeps streaming from node 1, now a standalone primary.
- **Semi-sync is never enabled for the offsite hop** when a cluster exists:
  the cluster already guarantees durability locally, and a WAN
  acknowledgement in the commit path would stall the clinic on the
  offsite link.

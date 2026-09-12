# MariaDB replication for the Debian deployment — design and implementation plan

**Status: plan, not yet implemented.** This document is the design the
`carlos-emr` packaging will follow to add an *optional*, turnkey MariaDB
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
| Replication style | **Asynchronous MariaDB primary → replica, GTID-based, ROW format**, with semi-synchronous as a later opt-in | Built into the MariaDB the package already depends on; zero new dependencies; fits the app's single static JDBC URL; the existing binary log is already the right shape |
| Multi-master (Galera) | **Not now.** Re-evaluate only if a second *application* host is ever wanted | One app instance writes to one node, so multi-master buys nothing today; the OSCAR-lineage schema has 22 baseline tables without a primary key, which Galera handles badly; the application has no deadlock/retry handling for certification failures; cross-site commit latency |
| "Judge" / arbiter node | **Not needed for phase 1.** Promotion is manual and fenced, so there is no automatic election to split-brain | An arbiter only matters for automatic quorum (Galera `garbd`, or an orchestrator). Reserved as a future role package |
| Automatic failover | **No.** `carlos-ctl replica promote` is a human act, and refuses while the old primary is reachable | Unfenced automatic promotion of a clinical record is how two divergent copies happen. The app's connection string is static; repointing it is one env edit plus `init-config` + `restart` |
| Packaging | **Split the admin tool into its own package (`carlos-emr-ctl`), add one new role package (`carlos-emr-db-replica`)**; `carlos-ctl` becomes role-aware | One tool, one code path, on every host. A db-only host cannot depend on `carlos-emr` (it would pull Tomcat, nginx, the WAR) |
| Role model | Derived, not declared twice: *app present?* (webapp installed) × *database role* (`standalone` / `primary` / `replica`) recorded in `/etc/carlos-emr/replication.env` | Leaves room for "app host with a remote database" and "warm-standby app host whose local MariaDB is a replica" without new packages |
| Network exposure | MariaDB keeps loopback; the primary additionally binds **one** operator-named LAN/VPN address; the replication account is host-restricted to the replica's IP and `REQUIRE SSL`; server certificate pinned by the replica | The drop-in's "structural, not administrative" loopback posture is preserved: the only new listener is the one the operator explicitly asked for |
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
of the above. Galera stays a documented future option (section 9) for a site
that reaches "two application hosts" and has done the primary-key audit.

Proxies (MaxScale, ProxySQL) are also out of scope for phase 1: MaxScale is
BSL-licensed and not in the Ubuntu archive, and none of them are needed while
the application talks to exactly one database host.

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

---

## 4. Design

### 4.1 Packages and roles

**New source-package layout** (same `debian/`, same build):

| Package | Depends (delta) | Ships |
|---|---|---|
| `carlos-emr-ctl` (new) | python3, mariadb-client, openssl, curl, iproute2, procps | `carlos_ctl/` Python package, `/usr/sbin/carlos-ctl` shim, `carlos-emr-cert`, the shared MariaDB drop-in `60-carlos-emr.cnf`, `carlos-ctl.8`, the shared skeletons it renders (`replication.env`) |
| `carlos-emr` | `carlos-emr-ctl (= ${binary:Version})` | Everything it ships today minus what moved. `Breaks`/`Replaces: carlos-emr (<< <first split version>)` on `carlos-emr-ctl` so the file move is clean on upgrade |
| `carlos-emr-db-replica` (new, `Architecture: all`) | `carlos-emr-ctl (= ${binary:Version})`, mariadb-server (>= 1:11.4), mariadb-client, `Conflicts: carlos-emr` **in phase 2 only** (lifted in T3 work) | The `carlos-emr-replica-watch.service/.timer`, `replica-watch` README section. No WAR, no Tomcat, no nginx |
| `carlos-emr-drugref`, `carlos-emr-eform-renderer` | unchanged | unchanged |

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
bind-address        = 127.0.0.1                      # until promoted; promotion adds the listen IP
```

**Replication protocol:** GTID (`MASTER_USE_GTID = slave_pos`), ROW events,
TLS with the pinned CA and `MASTER_SSL_VERIFY_SERVER_CERT = 1`. Both schemas
(`carlos`, `drugref2`) replicate; nothing else on the server is worth
replicating, and `carlos_restore_drill` is already filtered at the binlog.
No `replicate_do_db` filters: they are a well-known source of silent
divergence with ROW events and cross-schema statements.

MariaDB 11.4 accepts both the legacy `MASTER_*` / `SLAVE` and the newer
`REPLICA` spellings for the status and control statements; the tool uses one
consistently (whichever the 11.4 smoke test in section 8 confirms for every
statement it needs, including what `mariadb-dump --gtid` emits) and parses
`SHOW SLAVE STATUS` / `SHOW REPLICA STATUS` by column name, never by
position.

**Binlog retention vs. a disconnected replica.** The primary keeps 10 days
of binlogs (`binlog_expire_logs_seconds`, shared with the backup's PITR
window). A replica that is down longer than that cannot resume and must be
re-seeded. `carlos-ctl replica status` on the primary warns when a known
replica's last-seen GTID is older than half the window, and says "re-seed"
outright past it. The retention value is not changed by this work.

### 4.3 Network and TLS

- The primary binds loopback **plus exactly one** address the operator names
  (`carlos-ctl replica add <replica-ip> --listen <primary-ip>`). `0.0.0.0`
  and `::` are refused. An address that is not on a local interface is
  refused. A globally routable address prints a warning recommending a VPN
  (WireGuard) and requires `--allow-public`.
- The tool does **not** manage the host firewall (the package only Suggests
  `nftables`). It prints the exact rule to add (nft and ufw forms) for
  "3306 from `<replica-ip>` only" and `check` reports whether 3306 is
  reachable from anywhere else it can tell.
- A private **replication CA** and a server certificate for the primary are
  generated once by a new `carlos-emr-cert db-tls` sub-command into
  `/etc/mysql/carlos-emr-tls/` (root:mysql, 0640 keys), 10-year CA, 2-year
  server certificate with the listen IP and host name as SANs, renewed by
  the existing `carlos-emr-cert renew` timer when under 30 days. The CA's
  public certificate travels in the join token; the replica pins it. Client
  certificates (mutual TLS, `REQUIRE SUBJECT`) are a phase-3 hardening
  option, not phase 1.
- **AppArmor:** Ubuntu's `mariadbd` profile confines file access; the 60-
  drop-in's own comments record that a new path is how the server fails to
  start after an upgrade. The certificate directory location must be
  verified against the enforcing profile on the target release before the
  path is fixed (implementation task; `/etc/mysql/` is the expected
  permitted tree, and `check` already asserts the profile is enforcing).

### 4.4 Credentials and the join token

Created on the primary by `replica add`, written 0600 root to
`/var/lib/carlos-emr/replicas/<replica-ip>/join.token`, moved to the replica
by the operator (`scp`), consumed and shredded by `replica join`. JSON, one
schema version field, and:

| Field | Purpose |
|---|---|
| `primary.host`, `primary.port` | where to stream from (the listen IP) |
| `primary.mariadb_version` | refuse a join across major.minor |
| `primary.ca_pem` | server certificate pinning |
| `repl.user`, `repl.password` | `repl_<replica-ip-mangled>@<replica-ip>`, `REQUIRE SSL`, grants = the backup account's set, host-restricted |
| `accounts.carlos`, `accounts.drugref`, `accounts.backup` | the passwords the primary's own `carlos.properties`, `drugref2.properties`, `backup.env` hold |
| `accounts.app_hosts` | IPs the `carlos`/`drugref` accounts must accept **after promotion** (the app host's address), so promotion needs no account work |
| `db.name`, `db.province` | `CARLOS_DB_NAME`, the schema province — recorded on the replica so a later full install on it inherits the right migrations |
| `issued_at`, `expires_at` | 24 h; the primary rotates the `repl` password on `replica add --reissue` |

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
`sql_log_bin = 0`, with the extra `@<app-host-ip>` host entries. The replica
gets its own `/etc/carlos-emr/carlos.properties` (from the shared skeleton)
and `backup.env` so that a future full install on it (T2/T3) and
`carlos-ctl db`, `db-info`, `db-validate` work there unchanged.

### 4.5 Seeding

Phase 1 seeds with a logical dump the **replica pulls** over the replication
port, as the `repl` account, over TLS:

```
mariadb-dump --host=<primary> --port=3306 --ssl-ca=<pinned> --ssl-verify-server-cert \
  --user=repl_… --single-transaction --gtid --master-data=2 \
  --hex-blob --routines --events --triggers --no-tablespaces \
  --default-character-set=utf8mb4 --databases carlos drugref2 \
| { echo 'SET SESSION sql_log_bin = 0; SET NAMES utf8mb4 COLLATE utf8mb4_general_ci; SET SESSION sql_mode="";'; cat; } \
| mariadb --protocol=socket --user=root
```

The same flags the nightly backup uses (`--single-transaction --hex-blob
--routines --events --triggers --no-tablespaces`), so the seed is a copy the
project already trusts to restore. `max_allowed_packet = 1G` is in the
shared drop-in on both sides. The GTID position is read from the dump
header and used for `SET GLOBAL gtid_slave_pos` before `CHANGE MASTER`.
The dump streams straight into the local server; nothing lands on disk.

Refusals before any byte moves: MariaDB version mismatch, the `carlos`
schema already holds tables (unless `--reseed`, which requires the
`--confirm <server-name>` idiom `destroy-data` uses), free space under
2 × the primary's reported data size, the primary unreachable over TLS with
the pinned CA, token expired.

Later seed methods (section 9): `--seed physical` (`mariadb-backup` streamed
over ssh, for very large sites) and `--seed restic` (restore the newest
backup snapshot, useful when the primary's WAN link is the constraint).

### 4.6 Health, alerting, and `check`

- **`carlos-emr-replica-watch.timer`** (every 5 min, on replica hosts): IO
  and SQL threads running, `Seconds_Behind_Master` under
  `CARLOS_DB_REPL_MAX_LAG_SECONDS`, `Last_IO_Error`/`Last_SQL_Error` empty,
  GTID position advancing when the primary's is, `read_only` still ON, disk
  above 10 %. Any failure alerts once (webhook / email, same payload shape
  as the backup's `alert()`, extracted into `carlos_ctl/alert.py` so the
  two share one implementation) and writes
  `/var/lib/carlos-emr/.replica-last-ok`; recovery clears it.
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
   shape (`read_only` off, listen IP added, keep the new server_id), run the
   compare-then-restart path; set `CARLOS_DB_ROLE=primary`.
4. **Print the runbook** for the application host, exactly:
   `CARLOS_DB_HOST=<this ip>` in `carlos-emr.env`, `carlos-ctl init-config`,
   `carlos-ctl restart`, then `carlos-ctl check` — and the reminder that the
   nightly backup on the app host must be moved or re-targeted (section 7).
5. **Offer the reverse** once the old primary is repaired: on the new
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
- **Offloading the nightly dump to the replica** (section 9) is the natural
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
add` right after `apt install` (no restart penalty: MariaDB has just been
restarted for the CARLOS drop-in anyway, and the two restarts fold into
one when the drop-ins are rendered in the same configure run).

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
- the seed dump runs as `--single-transaction`, so it does not lock the
  live database; on a large site run `replica join` outside clinic hours
  for the network load, not for locking;
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
   `60-carlos-emr.cnf` into it in `debian/rules`; `Breaks`/`Replaces`
   against pre-split `carlos-emr`; `carlos-emr` `Depends: carlos-emr-ctl (=
   ${binary:Version})`. Adjust `deb-packages.yml` expectations for the new
   artifact name (workflow edits are a maintainer task; the plan lists the
   exact asset-name checks that change: the `startswith("carlos-emr_")`
   assertions and the upload loop).
2. **`carlos_ctl/role.py`** with the detection above; `cli.py` gates verbs by
   role and prints the role in `--help`; `validate.py` skips app-only
   sections on a db-only host.
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
   `/etc/mysql/carlos-emr-tls/`, `status` reports it, `renew` renews it.
   Verify the path against the enforcing `mariadbd` AppArmor profile on
   Ubuntu 26.04 and record the outcome in the 60- drop-in's comments.

### Phase 1 — primary side

6. **`carlos_ctl/replication.py`** (new module; verbs `replica add`,
   `replica remove`, `replica status`, `replica add --reissue`):
   - listen-IP validation (local interface, not wildcard, public needs
     `--allow-public`);
   - `62-carlos-emr-replication.cnf` renderer (primary shape) +
     `db-apply-settings` extended so its compare set includes
     `bind_address`, `have_ssl`, `gtid_strict_mode`;
   - `repl_<ip>@<ip>` account with the backup grant set, `REQUIRE SSL`,
     `sql_log_bin = 0`;
   - token writer (schema v1), per-replica state dir, expiry, `--reissue`;
   - `replica remove <ip>`: drop account, delete state; if it was the last
     replica, drop the listen address from the drop-in (TLS material
     stays);
   - firewall hint printer;
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

### Phase 2 — replica package and join

10. **`carlos-emr-db-replica`** binary package: depends, `Conflicts:
   carlos-emr` (phase 2 only), postinst that creates
   `/etc/carlos-emr/` from the shared skeletons, applies the shared 60-
   drop-in via `db-apply-settings`, installs the watch timer disabled until
   `join` succeeds. `postrm` never drops the replicated schema (same
   contract as `carlos-emr`: only `destroy-data` destroys data).
11. **`replica join <token> [--reseed --confirm <name>] [--credentials-only]`**:
    preflight → replica drop-in → local accounts with injected passwords and
    app-host entries → streamed seed → `gtid_slave_pos` → `CHANGE MASTER`
    (TLS, pinned CA, GTID) → `START SLAVE` → wait for lag 0 → write
    `replication.env` → enable timer → shred token.
12. **`carlos-emr-replica-watch`** service + timer, `replica status`
    (replica role), `check` replication section (replica role).
13. **`replica promote --confirm <name> [--force]`** as in 4.7.
14. **Integration test** (section 8) exercised end to end.

### Phase 3 — remote database on the app host, hardening

14. `carlos-emr-backup`: dump and drill legs accept a TCP+TLS endpoint when
    `CARLOS_DB_HOST` is not local (today socket-only by design); the `backup`
    account on the promoted replica already exists from the token.
15. `carlos-ctl` root-socket verbs (`db`, `db-users`, `bootstrap-admin`,
    `destroy-data`) either refuse clearly on an app-only host ("run this on
    the database host") or take `--db-host`; `check` tolerates no local
    `mariadb.service` when the database is remote.
16. Semi-synchronous replication as `replica add --semi-sync` (primary
    `rpl_semi_sync_master_enabled`, timeout fallback to async; replica
    `rpl_semi_sync_slave_enabled`), off by default, recommended for a LAN
    replica, discouraged over a WAN.
17. Optional mutual TLS for the replication account.

### Phase 4 — later (each its own decision)

18. Backup offload to the replica (`--dump-slave` anchoring).
19. Lift `Conflicts: carlos-emr` for the warm-standby topology (T3); the
    app on such a host must refuse to start while `CARLOS_DB_ROLE=replica`
    and `CARLOS_DB_HOST` is local (it would only get read-only errors).
20. Physical and restic seed methods.
21. Galera evaluation (primary-key audit for the 22 PK-less baseline
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
| `carlos-emr.service` `Wants=mariadb.service` | unit file | harmless with a remote DB; the start wrapper's DB wait already uses `CARLOS_DB_HOST` |

---

## 8. Test plan

- **Unit** (phase 0 onward, pytest): drop-in renderers are byte-exact;
  token round-trips and rejects expired/foreign-version tokens; listen-IP
  validation; `SHOW SLAVE STATUS` parsing by column name for both spellings;
  role detection matrix; fence logic in `promote` with a stubbed
  reachability probe.
- **MariaDB 11.4 smoke** (once, recorded in the module docstring): which
  spellings `mariadb-dump --gtid --master-data=2` emits and which `CHANGE
  MASTER` / `START SLAVE` / `SHOW SLAVE HOSTS` forms the server accepts;
  `bind_address` list support; AppArmor permits the TLS directory.
- **Integration** (phase 2), scripted like `carlos-podman/tests/db-migrate-integration.sh`,
  on two systemd containers or VMs from the built `.deb`s:
  1. install A with demo data; `replica add`; verify MariaDB listens on
     loopback + listen IP only, `repl` account exists with `REQUIRE SSL`;
  2. install B, `replica join`; assert equal `gtid_binlog_pos`, equal
     `COUNT(*)` on `demographic`, `casemgmt_note`, `document`, and an
     `information_schema` checksum of table lists for both schemas;
  3. write on A (an INSERT through the app account), observe on B within
     the lag bound; attempt a write on B as the app account, expect the
     read-only error;
  4. stop A's MariaDB, `replica promote` on B, re-point A's app
     (`CARLOS_DB_HOST`), `carlos-ctl check` on A passes with the
     replication section reporting remote-DB;
  5. repair: `replica add` on B, `replica join --reseed` on A, assert
     equality again;
  6. failure drills: expired token refused; join against a populated
     schema refused; `promote` refused while A is up; watch timer alerts
     when B's SQL thread is stopped by hand.
- **Upgrade**: existing pre-split `carlos-emr` install upgrades cleanly to
  the split packages with no conffile prompt and no service interruption
  beyond what an upgrade already causes; `carlos-ctl check` passes before
  and after.

---

## 9. Open questions for the maintainer

1. **Package split now, or duplicate-and-Conflict?** The plan recommends
   the split (`carlos-emr-ctl`) as phase 0 because it is mechanical and
   it is what makes "one tool, role-aware" true. The fallback — ship a
   second copy of `carlos_ctl` in the replica package with
   `Conflicts: carlos-emr` — is smaller but closes off T3 until the split
   happens anyway.
2. **Public listen addresses.** Refuse-unless-flagged (as drafted) or
   refuse outright and require a VPN? The drafted behaviour prints the
   WireGuard recommendation loudly and requires `--allow-public`.
3. **Token contents.** Carrying the application passwords is the
   recommendation (section 4.4). The alternative — generate fresh passwords
   at promotion and rotate the app host afterwards — trades a one-time
   secret file for a two-host credential rotation during an outage.
4. **Semi-sync default.** Off by default (drafted). A LAN-only site may
   prefer on; the README will say when to choose it.
5. **Binlog retention** stays at 10 days (drafted); a site wanting to
   survive a longer replica outage raises it knowingly in the drop-in
   (disk cost stated in the README).
6. **Name of the replica package.** `carlos-emr-db-replica` (drafted) versus
   a generic `carlos-emr-db` with the role chosen at join time. The specific
   name keeps `apt list` self-describing.

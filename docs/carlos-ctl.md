# carlos-ctl — post-install configuration and administration

`carlos-ctl` is the administration command for a CARLOS EMR server installed
from the Debian packages. It is the one tool an operator needs day to day:
every routine job — health checks, logs, restarts, schema migrations,
certificates, the web application firewall, backups and restore drills — is a
`carlos-ctl` verb, run with `sudo`.

This page is the GitHub-readable companion to the documentation that installs
with the package (`man carlos-ctl` and
`/usr/share/doc/carlos-emr/README.Debian`), so you can read it *before* you
have a server. For the install itself, start at
[docs/install-deb.md](install-deb.md).

Under the hood the command is a small shim (`/usr/bin/carlos-ctl`) around a
Python package installed at `/usr/lib/carlos-emr/carlos_ctl/`. The
[carlos-podman](https://github.com/carlos-emr/carlos-podman) deployment ships
its own `carlos-ctl` with the same name, language, and overlapping verbs
(`check`, `db`, `db-migrate`, `db-users`, `backup full|verify|status`,
`cert-renew`, `rotate`), so operators can move between the two deployments
without relearning. The old `carlosctl` spelling still works and prints a
deprecation note.

## The one command to remember

```bash
sudo carlos-ctl check
```

`check` probes the whole deployment — services, process ownership, network
exposure, TLS, live WAF blocking, a live DrugRef lookup, schema state, and
backup freshness — and tells you what is wrong in plain language. Run it
after installing, after upgrading, after changing configuration, and first
thing when anything misbehaves.

## The configuration loop

All operator-owned configuration lives under `/etc/carlos-emr/`. The package
writes these files once at install time and **never overwrites them again** —
an upgrade will not undo your edits. The loop is always the same: edit the
file, then run the verb beside it.

| File | What it holds | After editing, run |
|---|---|---|
| `/etc/carlos-emr/carlos-emr.env` | Site settings: host name, listen address, billing province (read-only after install), Java heap, timezone | `sudo carlos-ctl init-config` — re-renders *and applies*: nginx reload, certificate refresh, and it tells you if a restart is also needed |
| `/etc/carlos-emr/carlos.properties` | CARLOS application settings | `sudo carlos-ctl restart` |
| `/etc/carlos-emr/backup.env` | Backup repository and credentials (`RESTIC_REPOSITORY`, `RESTIC_PASSWORD`) | nothing — the next timer run reads it; prove it now with `sudo carlos-ctl backup full` |
| `/etc/carlos-emr/modsecurity/` | WAF policy and site-specific rule exclusions | `sudo carlos-ctl waf reload` |
| `/etc/carlos-emr/tomcat/` | Tomcat server configuration | `sudo carlos-ctl restart` |

Notes a first-time operator should not learn the hard way:

- `carlos-emr.env` is parsed by systemd, **not** by a shell: no command
  substitution, no variable expansion, and values with spaces need quotes.
- Upgrades never add new keys to your copy. After an upgrade, compare with
  the shipped skeleton in `/usr/share/carlos-emr/skel/` to see what is new.
- `backup.env` is deliberately unreadable by the application account — that
  separation is what stops a compromised EMR from deleting or decrypting its
  own backups. **Copy `RESTIC_PASSWORD` somewhere off the host**: without it
  every backup the machine has ever written is permanently unreadable.

## Post-install configuration, in order

The first-hour walkthrough in [docs/install-deb.md](install-deb.md#quickstart--the-first-hour)
covers this end to end; the short version, in the order that retires the most
risk first:

1. **Verify** — `sudo carlos-ctl check`, and confirm the eForm render browser
   (`sudo systemctl status carlos-emr-chromedriver`).
2. **Credentials** — `sudo cat /etc/carlos-emr/initial-admin.txt`, log in,
   complete the forced password reset, create real named accounts, disable
   the seeded `carlosdoc` account, delete the file. If the install-time
   replacement was declined or failed, run `sudo carlos-ctl bootstrap-admin`
   before anything else.
3. **Backups offsite** — set an offsite `RESTIC_REPOSITORY` in `backup.env`,
   copy `RESTIC_PASSWORD` off the host, then prove the pipeline:
   `sudo carlos-ctl backup full && sudo carlos-ctl backup verify`.
4. **Real TLS** — once DNS resolves and port 80 is reachable:
   `sudo carlos-ctl cert acme you@example.ca`.
5. **Read the installed reference** —
   `/usr/share/doc/carlos-emr/README.Debian` marks the decisions only an
   operator can make (**DECIDE THIS**): backup custody and log retention.

## Verb reference

What `carlos-ctl --help` prints, grouped the way `man carlos-ctl` documents
it. Everything runs with `sudo`.

### Diagnosis

| Verb | What it does |
|---|---|
| `check` | Full deployment check — start here |
| `status` | systemd status of the EMR and its timers |
| `logs [args]` | The EMR/Tomcat journal (`journalctl -u carlos-emr`); `-f` follows, `-n 200` tails |

### Service control

| Verb | What it does |
|---|---|
| `restart` | Restart the EMR — applies configuration changes; the webapp takes about two minutes to redeploy |
| `start` / `stop` | Start or stop the EMR |

### Database and schema

| Verb | What it does |
|---|---|
| `db [args]` | SQL shell on the EMR database as root (interactive with no args; `-e` and redirects pass through, e.g. `sudo carlos-ctl db < f.sql`) |
| `db-info` | Show the Flyway schema migration state |
| `db-validate` | Verify the schema matches the deployed WAR |
| `db-migrate` | Apply pending migrations — **back up first** |
| `db-baseline` | Adopt an existing pre-Flyway (OSCAR 19 / OpenO) schema |
| `db-repair` | Fix `flyway_schema_history` after a failed migration |
| `db-apply-settings` | Restart MariaDB if it is not running the settings in the CARLOS drop-in |
| `db-dump` | Consistent dump to stdout |
| `db-users` | (Re)create the databases and least-privilege accounts |

### Certificates

| Verb | What it does |
|---|---|
| `cert status` | What certificate is being served |
| `cert selfsigned` | (Re)generate the self-signed certificate |
| `cert acme <email>` | Switch to a Let's Encrypt certificate (needs public DNS + port 80) |
| `cert manual` | Adopt a certificate you placed at `/etc/carlos-emr/tls/` |
| `cert-renew` | What the twice-daily renewal timer runs |

### Web application firewall

| Verb | What it does |
|---|---|
| `waf status` | ModSecurity engine state and file locations |
| `waf tail [lines]` | Recent WAF blocks, human-readable — the first stop when a request vanishes without reaching the application |
| `waf reload` | Apply edited exclusion/policy files |
| `waf detect-only` | Stop blocking (triage only — heed the warning it prints) |
| `waf blocking` | Resume blocking |

### Backups

| Verb | What it does |
|---|---|
| `backup full` | Take a backup now |
| `backup verify` | Restore the newest backup into a scratch database — the proof the pipeline works |
| `backup status` | When backups and restore drills last succeeded |
| `backup snapshots` | List what is in the repository |
| `backup restic <args>` | Raw restic against the configured repository |

### Provisioning

| Verb | What it does |
|---|---|
| `init-config` | Re-render and apply configuration from `carlos-emr.env` (nginx reload, certificate refresh) |
| `bootstrap-admin` | Reset the seeded administrator credential to random values in `/etc/carlos-emr/initial-admin.txt` |
| `rotate` | Rotate every generated database password |

### Decommissioning

Removing or purging the packages **never** destroys clinical data: not the
databases, not the document store, not the backups. Deliberate destruction is
its own explicit command, and it makes you type the host's own configured
name back to it:

```bash
sudo carlos-ctl destroy-data --confirm <server-name>
```

## Where the rest lives

- `man carlos-ctl` — the same verbs with full detail, `FILES`, and exit
  statuses.
- `/usr/share/doc/carlos-emr/README.Debian` — the operations manual: what
  runs as whom, TLS, WAF tuning and the false-positive workflow, backups and
  point-in-time restore, upgrades, log retention as a compliance decision,
  and troubleshooting.
- [docs/install-deb.md](install-deb.md) — installation, the first hour, and
  a first-day troubleshooting table.

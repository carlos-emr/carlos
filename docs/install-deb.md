# Installing CARLOS on a single server (Debian packages)

This is the supported way to run CARLOS EMR on one Ubuntu machine: three
Debian packages (plus one empty transitional package for upgrades) that install the application, its database, an nginx +
ModSecurity web application firewall, HTTPS, scheduled encrypted backups, and
an administration tool — a working, secured EMR from `apt install`.

> **Status: pre-production.** A site considering production use must complete
> its own technical, security, privacy, backup, restore, and regulatory
> review before this system holds patient information. The installed
> documentation calls out the decisions only an operator can make.

| Package | What it provides |
|---|---|
| `carlos-emr` | CARLOS on a dedicated Tomcat 11 instance, MariaDB with least-privilege accounts, an nginx front door running ModSecurity 3 + OWASP CRS in blocking mode, HTTPS (self-signed by default, Let's Encrypt on request), and nightly restic backups with a weekly restore drill |
| `carlos-ctl` | The `carlos-ctl` administration command (`check`, logs, restarts, schema migrations, certificates, the WAF, backups, the OSCAR 19 import). Its own package, built and released from [carlos-emr/carlos-ctl](https://github.com/carlos-emr/carlos-ctl); `carlos-emr` depends on it, and every CARLOS release re-attaches the pinned `carlos-ctl` release so one download page carries the whole install |
| `carlos-emr-drugref` | DrugRef2, the drug and drug-interaction reference CARLOS queries when prescribing — co-deployed, loopback-only, with the Health Canada Drug Product Database seed loaded on install |
| `carlos-emr-eform-renderer` | Empty transitional package. The sandboxed browser that renders saved eForms to PDF now ships inside `carlos-emr`; this package only lets a 2026.08.0-alpha13 or earlier install upgrade cleanly (see [Upgrades](#upgrades)) |

## Requirements

- Ubuntu 26.04 LTS (the packages target its Tomcat 11 / OpenJDK 25 / MariaDB
  11.8 / nginx stack) on **x86-64 (amd64)**. `carlos-emr` carries the pinned
  Chromium that renders eForms to PDF, which exists only for amd64.
- One dedicated server or VM. As a starting point: 4+ CPU cores, 8 GB RAM
  (2 GB JVM heap + 1 GB database buffer pool by default — both tunable),
  and disk sized for your document store plus backups.
- Root access. The *installation* uses root; the *running system* does not —
  every long-lived component runs as an unprivileged account.
- The `universe` component enabled and the package lists current. Four of the
  dependencies (`tomcat11-common`, `libtomcat11-java`, `modsecurity-crs`,
  `libnginx-mod-http-modsecurity`) live in `universe` (`openjdk-25-jre-headless`
  is in `main`), and
  without it the install stops on unmet dependencies before anything is
  configured. It is enabled by default on Ubuntu Server.

### Do not install the `tomcat11` package

CARLOS runs its **own private Tomcat instance** and installs everything it
needs for one. It depends on `tomcat11-common` and `libtomcat11-java` — the
distribution's Tomcat *code*, so container security updates keep arriving
through `apt` — and then runs a container of its own:
`CATALINA_BASE=/var/lib/carlos-emr/catalina`, as the unprivileged `carlos`
account, with its connector bound to `127.0.0.1:18080` behind the
nginx + ModSecurity front door, serving an application tree the EMR's own
account cannot write to.

Installing the `tomcat11` *service* package on top of that ("CARLOS is a Java
webapp, so it must need Tomcat") starts a **second, unrelated** container as
the `tomcat` user listening on `*:8080` — every interface, with no TLS, WAF,
or rate limiting. This creates an additional network service outside the
packaged front door; it does not serve the CARLOS application by default.
It also competes for the memory the EMR's heap was sized against.

The packages therefore refuse the combination (`Conflicts: tomcat11`): if it
is already installed, `apt` will offer to remove it. If it hosts another
application, move that application to a separate host before proceeding.

### Before you start — a five-minute pre-flight

Each of these is much easier to fix now than mid-install:

- **DNS**: if clinicians will reach the server by name (and for a Let's
  Encrypt certificate this is required), create the DNS record for the host
  name first and confirm it resolves to this machine.
- **Network**: the front door needs inbound **443** (HTTPS) from the clinic,
  and inbound **80** (HTTP) from the internet only if you plan to use Let's
  Encrypt. Keep **22** (SSH) for yourself. Nothing else listens externally.
- **Disk**: the packages themselves are small; the growth is the database,
  the document store under `/var/lib/carlos-emr`, and the local backup tier.
  Start with at least 50 GB free and monitor.
- **Snapshot**: on a VM, take a snapshot before installing. If anything goes
  wrong you roll back in seconds instead of debugging a half-configured host.

## Install

Every CARLOS [GitHub release](https://github.com/carlos-emr/carlos/releases)
carries the `.deb` files, their `.sha256` checksums, and build-provenance
attestations (the `carlos-emr` package ships that release's published WAR,
byte for byte; the `carlos-ctl` package is the release of
[carlos-emr/carlos-ctl](https://github.com/carlos-emr/carlos-ctl) the CARLOS
release pins, re-attached as is). Download all four, verify, install:

```bash
sudo apt update
sha256sum -c carlos-emr_<version>_amd64.deb.sha256
sha256sum -c carlos-ctl_<ctl-version>_all.deb.sha256
sha256sum -c carlos-emr-drugref_<version>_all.deb.sha256
sha256sum -c carlos-emr-eform-renderer_<version>_all.deb.sha256
sudo apt install --no-remove ./carlos-emr_<version>_amd64.deb \
                 ./carlos-ctl_<ctl-version>_all.deb \
                 ./carlos-emr-drugref_<version>_all.deb \
                 ./carlos-emr-eform-renderer_<version>_all.deb
```

`<version>` is the release's Debian version as it appears in the asset name,
with dots throughout — for example `2026.08.0.alpha14`, giving
`carlos-emr_2026.08.0.alpha14_amd64.deb`. `<ctl-version>` is the `carlos-ctl`
release's own version (for example `1.0.0`): the two packages version
independently, and the CARLOS release page carries the `carlos-ctl` file it
was tested with. To verify provenance, `gh attestation verify` each file
against the repository that built it:

```bash
gh attestation verify carlos-emr_<version>_amd64.deb --repo carlos-emr/carlos
gh attestation verify carlos-ctl_<ctl-version>_all.deb --repo carlos-emr/carlos-ctl
```

> **Releases up to and including 2026.09.0~snapshot24** shipped `carlos-ctl`
> inside `carlos-emr`; there is no separate file to download for them, and
> upgrading from one of them needs the `carlos-ctl` file in the same command
> (see [Upgrades](#upgrades)).

> **Releases up to and including 2026.08.0-alpha13** name the packages
> differently: `carlos-emr_<version>_all.deb`, and the renderer as a real
> package, `carlos-emr-eform-renderer_<version>_amd64.deb`. From
> 2026.08.0-alpha14 the renderer is part of `carlos-emr`, which is therefore
> `_amd64`, and `carlos-emr-eform-renderer_<version>_all.deb` is an empty
> transitional package. On a fresh install it does nothing and can be left
> out or removed later.

> **Releases up to and including 2026.08.0-alpha12:** the `.sha256` files
> record the build-time name, which spells the pre-release with a tilde
> (`carlos-emr_2026.08.0~alpha12_all.deb`), while GitHub rewrites that tilde to
> a dot when it stores the asset. `sha256sum -c` therefore fails with
> `No such file or directory` even though the download is intact. Compare the
> digests directly instead:
>
> ```bash
> sha256sum carlos-emr_<version>_all.deb
> cat carlos-emr_<version>_all.deb.sha256
> ```
>
> Later releases record the published name and verify normally.

What each package is for:

| Package | What it does | Leave it out? |
|---|---|---|
| `carlos-emr` | The EMR itself: application, database schema, nginx front door, WAF, TLS, backups, and the browser that turns saved eForms into PDFs (eForm print, fax and archive have no other path). | No. |
| `carlos-ctl` | The administration command; `carlos-emr` depends on it (its installer runs `carlos-ctl` verbs). | No — apt refuses to install `carlos-emr` without it. |
| `carlos-emr-drugref` | Drug and interaction lookups when prescribing. | Only if you never prescribe — searches return nothing without it. |
| `carlos-emr-eform-renderer` | Nothing (transitional). | On a fresh install, yes. When upgrading from 2026.08.0-alpha13 or earlier, **no** — see [Upgrades](#upgrades). |

apt may print this while installing local files. It is harmless:

```
Notice: Download is performed unsandboxed as root as file '...deb'
couldn't be accessed by user '_apt'. - pkgAcquire::Run (13: Permission denied)
```

apt drops to an unprivileged user to fetch packages, and Ubuntu creates home
directories that user cannot read into. Nothing is downloaded from the network
by that step — the file is already on disk and you have just checksummed it.
Installing from a world-traversable directory such as `/tmp` avoids it.

### The installer's questions

The installer asks a handful of questions through debconf. Every answer has a
safe default and can be changed afterwards — **except the billing province**,
which selects the database schema and cannot be switched without starting from
an empty database. (The screenshots below are the installer's real dialogs:
they are captured from debconf's `whiptail` frontend running against the
package's own templates by `scripts/render-debconf-screenshots.py`, which is
re-run whenever the templates change.)

**1. Host name** — becomes the nginx `server_name` and the TLS certificate
subject. Use the fully-qualified name clinicians will reach the server at.
On a first install the field is pre-filled with this machine's own FQDN when
it has one; the template default `localhost` shown here appears only when it
does not.

![Host name question](images/install/01-server-name.png)

> **Reaching the server by IP address.** Clients on the practice LAN may use the
> server's IP instead of a name — that is the common clinic setup and the
> packaged WAF rules account for it (rule 1090 exempts private-range clients
> from CRS 920350, which would otherwise spend 3 of the 5-point anomaly budget
> on every request). Clients arriving from a **public** address get no such
> exemption, by design: they start each request part-way to the blocking
> threshold, so users see occasional unexplained `403` errors on ordinary
> clinical work with nothing in the application log. **If anyone will reach this
> server from outside the practice network, give it a DNS name and have them use
> the name** — which is also what a real TLS certificate is issued for. See
> "The web application firewall" in `README.Debian` for the full reasoning.

**2. Listen address** — `0.0.0.0` serves every interface (the usual clinic
setup); `127.0.0.1` keeps it local while staging or behind a separate proxy.
The application server and database always listen on loopback only.

![Listen address question](images/install/02-bind-ip.png)

**3. Billing province** — Ontario (`on`), British Columbia (`bc`), or `other`.
Pick `other` for a clinic anywhere else: it installs the Ontario schema and
billing configuration, so the deployment is an Ontario one in every respect —
it exists as a separate answer only so you are not asked to claim a province
you are not in. **This one is permanent** — `on` and `bc` create different
tables, and although `dpkg-reconfigure` will show the question again, the
answer is not written back.

![Billing province question](images/install/03-province.png)

**4. Java heap** — on a fresh install the installer pre-fills a recommendation
sized from the machine's memory: half of physical RAM, between `2g` and `8g`
(on reconfiguration or upgrade it offers the currently configured value).
Accept it on a dedicated box; lower it only if the host is shared or the
database runs elsewhere (CARLOS needs at least `2g`; the rest of RAM is left
for MariaDB and ~1 GB for the OS).

![Java heap question](images/install/04-java-heap.png)

**5. TLS mode** — `selfsigned` (HTTPS from minute one, browsers warn until you
replace it), `acme` (free Let's Encrypt cert; needs public DNS + port 80), or
`manual` (you supply the chain and key).

![TLS mode question](images/install/05-tls-mode.png)

**6. Let's Encrypt email** — shown only when TLS mode is `acme`. Leave empty to
skip requesting a certificate now.

![Let's Encrypt email question](images/install/06-acme-email.png)

**7. Replace the seeded admin password** — the migrations seed `carlosdoc` with
a password published in the source repository. Accept (the default) to replace
it with random values written to `/etc/carlos-emr/initial-admin.txt`
(root-only). Decline **only** on a throwaway development machine.

![Reset seeded admin question](images/install/07-reset-seed-admin.png)

**8. Load the fictitious demonstration dataset** — default **no**. Accept only
on an evaluation, training or development machine: it fills the new database
with ~3000 `FAKE-`-prefixed patients and demonstration providers, and there is
no way to remove it short of destroying the database. See
[Optional demonstration data](#optional-demonstration-data).

![Demonstration data question](images/install/08-install-demo-data.png)

**9. Done (only if you accepted the seeded-admin replacement)** — a final
note tells you where the initial credentials were written and points you at
`carlos-ctl check` and `README.Debian`. If you declined at step 7 — or the
replacement failed, which the install log warns about loudly — no completion
note is shown.

![Install complete note (shown only after the seeded-admin replacement)](images/install/09-initial-credentials.png)

Installation then provisions the database and its accounts, applies the
schema with Flyway, replaces the seeded administrator credential with random
values, generates a self-signed certificate, wires nginx, and starts the
application. The webapp takes about two minutes to deploy; the installer
waits and says so.

To build the packages from source instead, see the header of
[`debian/rules`](../debian/rules) — including how to reuse a prebuilt WAR —
and [`release/README.md`](../release/README.md) for where the packaging
lives and why. To validate a build end to end — install into a disposable VM
with the demo dataset and run the whole Playwright suite through the WAF —
follow [`docs/ui-tests/deb-install-validation.md`](ui-tests/deb-install-validation.md).

## Quickstart — the first hour

**1. Verify the deployment.** One command probes the whole system — services,
process ownership, network exposure, TLS, live WAF blocking, the web-service
gates (hidden CXF catalog, an unauthenticated SOAP call refused, a dot-segment
path bypass rejected), a live DrugRef lookup, schema state, and backup freshness:

```bash
sudo carlos-ctl check
```

Then confirm the eForm render browser specifically, because `carlos-ctl check`
covers the EMR rather than that service:

```bash
sudo systemctl status carlos-emr-render-browser          # should be active
sudo carlos-ctl logs | grep -i "renderer startup check"
```

You want `eForm browser renderer startup check passed.` The browser runs as its
own account (`carlos-render`) under its own service, sandboxed — if it cannot
start, eForm print/fax/archive fail rather than quietly producing PDFs from an
unsandboxed browser, and the line above tells you so at boot instead of at the
moment a clinician tries to print.

**2. Know where the logs are.** Before anything goes wrong, not after. CARLOS
spans several services and each writes to its own place — looking in the wrong
one is the most common way to conclude "nothing is logged":

```bash
sudo carlos-ctl logs -n 200        # the EMR and Tomcat  (= journalctl -u carlos-emr)
sudo carlos-ctl logs -f            # follow it live
sudo journalctl -u carlos-emr-render-browser -n 50   # the eForm render browser
sudo journalctl -u nginx -n 50     # TLS and the front door
sudo tail -f /var/log/carlos-emr/modsec/modsec_audit.log   # the WAF
```

Two that catch people out:

- A request **blocked by the WAF never reaches the application**, so it appears
  in the modsec log and nowhere else. `carlos-ctl waf tail` explains why.
- The **render browser is a separate service with a separate journal**. A failed
  eForm print can leave the application log completely silent.

**3. Log in.** The initial administrator credentials were generated at
install time and written, readable only by root, to:

```bash
sudo cat /etc/carlos-emr/initial-admin.txt
```

Browse to `https://<your-host>/` (the browser warns about the self-signed
certificate until you replace it — the connection is still encrypted). The
seeded account lands on a **forced password reset** first — deliberate,
because the generated password exists in a file on disk. Complete the reset,
create real named accounts for each clinician, disable the seeded account,
and delete the credentials file.

**4. Point backups off the host.** The default backup repository is a local
directory — a real first tier, but not disaster recovery. Set an offsite
`RESTIC_REPOSITORY` in `/etc/carlos-emr/backup.env`, **copy the
`RESTIC_PASSWORD` somewhere off this machine** (without it every backup is
permanently unreadable), and prove the pipeline end to end:

```bash
sudo carlos-ctl backup full
sudo carlos-ctl backup verify   # restores the newest dump into a scratch db
```

**5. Real TLS.** Once the host name resolves in public DNS and port 80 is
reachable:

```bash
sudo carlos-ctl cert acme you@example.ca
```

## Day-two administration

The loop is: edit the file, run the verb beside it.

| You edited | Then run |
|---|---|
| `/etc/carlos-emr/carlos-emr.env` (host name, listen address, heap, timezone) | `sudo carlos-ctl init-config` — re-renders *and applies*: nginx reload, certificate refresh, and it tells you if a restart is also needed |
| `/etc/carlos-emr/carlos.properties` (application settings) | `sudo carlos-ctl restart` |
| `/etc/carlos-emr/backup.env` | nothing — the next timer run reads it; prove it with `sudo carlos-ctl backup full` |
| `/etc/carlos-emr/modsecurity/` (WAF policy, site exclusions) | `sudo carlos-ctl waf reload` |

`carlos-ctl --help` lists every verb; `man carlos-ctl` documents them, and
**[docs/carlos-ctl.md](https://github.com/carlos-emr/carlos-ctl/blob/main/docs/carlos-ctl.md)**
in the carlos-ctl repository is the same reference readable on GitHub, with
a walkthrough of the post-install configuration files. The
tool shares its name, language, and overlapping verb set (`check`, `db`,
`db-migrate`, `db-users`, `backup full|verify|status`, `cert-renew`,
`rotate`) with the carlos-podman deployment's `carlos-ctl`, so operators can
move between the two without relearning.

### Upgrades

An upgrade is `apt install` of the newer packages — same command as the
install. Supply the main package and each companion that is already installed,
all from the same release: DrugRef depends on the matching main-package
version. For the standard installation this means all four files (the
`carlos-ctl` file too: if the release pins the `carlos-ctl` version you
already have, apt reports it as already the newest and moves on).
If you intentionally omitted DrugRef, supply only the packages you use and
add `--no-install-recommends` to keep it absent.
Offering only a newer main package
can cause apt to propose removing a companion. Keep `--no-remove` so that
proposal fails instead of removing prescription lookup.

**Upgrading from 2026.09.0~snapshot24 or earlier** (when `carlos-ctl` was
part of `carlos-emr`): the `carlos-ctl_<ctl-version>_all.deb` file must be in
the same command. The new `carlos-emr` depends on it, so without the file apt
refuses the whole transaction up front and the old install keeps running —
nothing is half-upgraded. With it, apt unpacks `carlos-ctl` (which takes over
`/usr/sbin/carlos-ctl`, `carlosctl` and the man page from the old
`carlos-emr`), then the new `carlos-emr`, and configures them in that order,
so the installer's own `carlos-ctl` calls find the new command. `carlos-emr`
and `carlos-ctl` can be upgraded separately afterwards: a CLI fix ships as a
`carlos-ctl` release alone (its install touches no database and restarts
nothing), and a newer `carlos-emr` alone works whenever the installed
`carlos-ctl` satisfies its `Depends` floor. A `carlos-ctl` install is
refused while an OSCAR 19 import is in progress on the host; finish or
clean up the import first. `apt remove carlos-emr` leaves `carlos-ctl`
installed; it then answers every verb with "carlos-emr is not installed".

**Upgrading from 2026.08.0-alpha13 or earlier** (when the renderer was its own
`_amd64` package and `carlos-emr` was `_all`): include
`carlos-emr-eform-renderer_<version>_all.deb` in the same command, so the old
renderer package is *upgraded* to the empty transitional one; with
`--no-remove`, leaving the file out makes apt stop instead of removing it. The
browser and the existing render token move into `carlos-emr`: the token file is
now `/etc/carlos-emr/renderer.env` and the service `carlos-emr-render-browser`
(it was `render-browser.env` and `carlos-emr-chromedriver`). Removing or purging
the old renderer package, then or later, cannot affect them, so
`sudo apt remove carlos-emr-eform-renderer` after the upgrade is safe. One side
effect: if apt *removed* the old renderer (the transitional file was left out
and `--no-remove` was not used), purging it later runs its old script, which
re-applies the configuration and restarts the EMR once (about two minutes), so
do that outside clinic hours.

The application's ~2-minute redeploy happens once per upgrade, even with DrugRef
in the same command, and `apt` returns only once the application answers.
The schema migrates before the service restarts, your configuration
files are never overwritten, and the application refuses to start against a
schema it was not built for rather than failing mid-consultation. Two habits
make upgrades boring:

1. `sudo carlos-ctl backup full` first — a known-good backup that predates
   the upgrade.
2. `sudo carlos-ctl check` after — the same probe as at install time proves
   the upgraded system end to end.

If something does go wrong, point-in-time restore is documented in
`/usr/share/doc/carlos-emr/README.Debian` (section 7, *Backups and
restore*), driven by the backup you just took.

**Removing the packages never destroys clinical data.** Neither `remove` nor
`purge` touches the databases, the document store, the backups, or the two
files whose loss would make retained data unreadable. Deliberate
decommissioning is its own explicit command
(`carlos-ctl destroy-data --confirm <server-name>`), which requires typing
the host's own configured name back to it.

## Optional demonstration data

For evaluation, training, and development installs, the installer can fill
the new database with a fictitious practice: about 3000 fake patients (every
name carries a `FAKE-` prefix), demonstration providers, appointments,
clinical notes, labs, prescriptions, 60 clearly-fake referral specialists,
and a small set of synthetic Administration fixtures (labelled
`Local Test -`: billing CSS styles, an inbox forwarding rule,
patient-independent eForms, referral doctors, a report template, query
favourites, appointment types and prevention lot numbers) so the data-backed
Administration screens have something to show. Answer **yes** to the
`Load the FICTITIOUS demonstration dataset?` question during install, or run
it later by hand:

```bash
sudo carlos-ctl demo-data
```

What it does — and refuses to do:

- **Empty databases only.** The load refuses to run against any database
  that already holds a demographic record. There is no force flag.
- **Additive to the migrations.** It only *adds* to the reference data the
  Flyway migrations install — on any collision the migrated row wins. The
  one exception: on a British Columbia install it **replaces** the seeded
  provincial specialist directory (`billingreferral`,
  `professionalSpecialists`, and their `serviceSpecialists` links) with the
  fake demonstration list, so demo systems never carry the real physician
  directory.
- **Once.** A completed load leaves a marker table; re-runs (including
  `dpkg-reconfigure carlos-emr`) are no-ops.
- **No removal short of destruction.** The only supported way to get the
  demonstration data out is `carlos-ctl destroy-data` and re-provisioning.

A note on credentials: the demonstration data adds no login accounts (the
`locktest` account the devcontainer seeds for lock testing is deliberately
not shipped) and works with the seeded `carlosdoc` account. If you accepted
the default *replace the seeded administrator password* question, log in
with the random credentials from `/etc/carlos-emr/initial-admin.txt`; on a
disposable demo box you may prefer to decline that question and keep the
well-known development credentials.

A system holding this dataset contains publicly-known demonstration content
and must **never** hold real patient information.

## Troubleshooting

The full troubleshooting guide installs with the package (`README.Debian`,
section 11) — this table covers the symptoms that account for most first-day
support requests. The first move is always the same:

```bash
sudo carlos-ctl check
```

| Symptom | Where to look | Usual cause |
|---|---|---|
| Install reported no error, but `carlos-ctl check` says the installation never finished, or that the database has **no tables** | `sudo carlos-ctl finish-install`, then `journalctl -u mariadb` | One or more provisioning steps failed or did not run; read the recorded reason and installation output. Those steps are deliberately non-fatal (a database problem must not leave dpkg half-configured), so apt still reports success. `finish-install` completes the install — schema, accounts, administrator credential, demo data if you asked for it — and returns nonzero while any requested step is unfinished. Boot retries require the marker and never migrate a populated invalid schema. If the DrugRef database is empty, also run `sudo dpkg-reconfigure carlos-emr-drugref`, then `sudo carlos-ctl check`. Existing DrugRef tables without a completion marker need a backup and administrator review before any reload |
| Drug lookups silent **and** the EMR is stopped | `sudo carlos-ctl check` | One failure, not two: DrugRef is a second webapp in the EMR's Tomcat, so it answers nothing while the EMR is not running. Fix the EMR first |
| Browser cannot connect at all | `sudo journalctl -u nginx -n 50` | nginx not running, or the listen address is `127.0.0.1` while you are connecting remotely |
| Certificate warning in the browser | `sudo carlos-ctl cert status` | Expected with the default self-signed certificate — the connection is still encrypted; switch with `sudo carlos-ctl cert acme <email>` |
| "502 Bad Gateway" or a long spinner right after install/restart | `sudo carlos-ctl logs -f` | The webapp takes about two minutes to deploy; if it never comes up, the log says why |
| Login rejected with the seeded credentials | `sudo cat /etc/carlos-emr/initial-admin.txt` | Using the repository's published dev password instead of the generated one. Also: the PIN must be exactly four digits — a longer PIN fails with a message blaming the *password* |
| `carlos-ctl check` says `carlos-emr is not running`, and `journalctl -u carlos-emr` shows the unit start, exit and restart several times before giving up | `sudo journalctl -u carlos-emr -n 200`, then `free -h` | The unit exhausted its restart limit (six failures in thirty minutes) and stays down until started by hand; an out-of-memory exit loops exactly this way, because the JVM runs with `-XX:+ExitOnOutOfMemoryError`. Give the machine enough memory for the heap (`CARLOS_JAVA_XMX` in `/etc/carlos-emr/carlos-emr.env`) plus MariaDB and the OS, or lower the heap, then `sudo systemctl reset-failed carlos-emr && sudo carlos-ctl restart` |
| Something answers on port `8080`, or an older `carlos-ctl check` reported the JVM running as `tomcat` | `dpkg -l tomcat11` | The distribution's `tomcat11` *service* package is installed. CARLOS does not use it and current packages refuse to coexist with it (see [Do not install the `tomcat11` package](#do-not-install-the-tomcat11-package)): `sudo apt purge tomcat11` |
| Install finished but the EMR is stopped, and `carlos-ctl check` says the unit is DISABLED | `sudo carlos-ctl check`, then `sudo carlos-ctl finish-install` | The installer could not replace the seeded `carlosdoc` credential (published in the source repository), so it stopped and disabled the service rather than expose the EMR with a known administrator password. It stays disabled across reboots on purpose. A successful `finish-install` verifies the credential, re-enables the unit, removes the start guard and starts the EMR |
| A specific page or action fails, but nothing in the application log | `sudo carlos-ctl waf tail` | The WAF blocked the request before it reached the application — the tail explains which rule and why |
| eForm print/fax produces no PDF, application log silent | `sudo journalctl -u carlos-emr-render-browser -n 50` | The render browser is its own service with its own journal; if it cannot start, eForm rendering fails by design |
| Drug search returns nothing when prescribing | `sudo carlos-ctl check` (DrugRef probe) | `carlos-emr-drugref` not installed, or its service is down |
| Administration > Update Drugref reports a failed update, or stays "updating" | The message on that page; `sudo journalctl -u carlos-emr \| grep -E 'DrugRef (database update\|updateDB failed)'` | The Tomcat JVM has no outbound HTTPS to `www.canada.ca` (proxy not passed via `CARLOS_JAVA_OPTS`, see `/usr/share/doc/carlos-emr-drugref/README.Debian`). A failed run keeps the previous drug data |
| "no space left on device" anywhere | `df -h /var` | Database, document store and the local backup tier all live under `/var` — grow the disk or move backups offsite |

## The full operator reference

The complete documentation installs with the package and covers what this
page compresses: the security model and what runs as whom, WAF tuning and
the false-positive workflow, point-in-time restore, log retention as a
compliance decision, and the decisions marked **DECIDE THIS**:

```
/usr/share/doc/carlos-emr/README.Debian
/usr/share/doc/carlos-emr-drugref/README.Debian
man carlos-ctl
```

## Other installation methods

- **Development:** the [devcontainer](../.devcontainer/README.md) is the
  supported development environment — synthetic data, disposable, not for
  patient information.
- **Containers (under development):**
  [carlos-podman](https://github.com/carlos-emr/carlos-podman) deploys
  CARLOS as rootless Podman pods with a separate WAF pod, an optional
  observability stack (metrics, log search, alerting), TPM-sealed secrets,
  and Ansible-driven multi-instance provisioning — a richer operational
  surface for sites that want it, where this package deliberately stays
  simple. Start with its
  [README](https://github.com/carlos-emr/carlos-podman#readme) and
  [QUICKSTART](https://github.com/carlos-emr/carlos-podman/blob/main/QUICKSTART.md).

# Installing CARLOS on a single server (Debian packages)

This is the supported way to run CARLOS EMR on one Ubuntu machine: three
Debian packages that install the application, its database, an nginx +
ModSecurity web application firewall, HTTPS, scheduled encrypted backups, and
an administration tool — a working, secured EMR from `apt install`.

> **Status: pre-production.** A site considering production use must complete
> its own technical, security, privacy, backup, restore, and regulatory
> review before this system holds patient information. The installed
> documentation calls out the decisions only an operator can make.

| Package | What it provides |
|---|---|
| `carlos-emr` | CARLOS on a dedicated Tomcat 11 instance, MariaDB with least-privilege accounts, an nginx front door running ModSecurity 3 + OWASP CRS in blocking mode, HTTPS (self-signed by default, Let's Encrypt on request), nightly restic backups with a weekly restore drill, and the `carlos-ctl` admin CLI |
| `carlos-emr-drugref` | DrugRef2, the drug and drug-interaction reference CARLOS queries when prescribing — co-deployed, loopback-only, with the Health Canada Drug Product Database seed loaded on install |
| `carlos-emr-eform-renderer` | The sandboxed browser service that renders saved eForms to PDF for print, fax and archive |

## Requirements

- Ubuntu 26.04 LTS (the packages target its Tomcat 11 / OpenJDK 21 / MariaDB
  11.8 / nginx stack).
- One dedicated server or VM. As a starting point: 4+ CPU cores, 8 GB RAM
  (2 GB JVM heap + 1 GB database buffer pool by default — both tunable),
  and disk sized for your document store plus backups.
- Root access. The *installation* uses root; the *running system* does not —
  every long-lived component runs as an unprivileged account.

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
byte for byte). Download all three, verify, install:

```bash
sha256sum -c carlos-emr_<version>_all.deb.sha256
sha256sum -c carlos-emr-drugref_<version>_all.deb.sha256
sha256sum -c carlos-emr-eform-renderer_<version>_amd64.deb.sha256
sudo apt install ./carlos-emr_<version>_all.deb \
                 ./carlos-emr-drugref_<version>_all.deb \
                 ./carlos-emr-eform-renderer_<version>_amd64.deb
```

Three packages, and it is worth knowing what each is for:

| Package | What it does | Leave it out? |
|---|---|---|
| `carlos-emr` | The EMR itself: application, database schema, nginx front door, WAF, TLS, backups. | No. |
| `carlos-emr-drugref` | Drug and interaction lookups when prescribing. | Only if you never prescribe — searches return nothing without it. |
| `carlos-emr-eform-renderer` | The browser that turns saved eForms into PDFs. | Only if the clinic does not use eForms — **there is no fallback**, so eForm print, fax and archive simply do not work without it. |

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
sudo systemctl status carlos-emr-chromedriver          # should be active
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
sudo journalctl -u carlos-emr-chromedriver -n 50   # the eForm render browser
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
**[docs/carlos-ctl.md](carlos-ctl.md)** is the same reference readable here
on GitHub, with a walkthrough of the post-install configuration files. The
tool shares its name, language, and overlapping verb set (`check`, `db`,
`db-migrate`, `db-users`, `backup full|verify|status`, `cert-renew`,
`rotate`) with the carlos-podman deployment's `carlos-ctl`, so operators can
move between the two without relearning.

### Upgrades

An upgrade is `apt install` of the newer packages — same command as the
install. The schema migrates before the service restarts, your configuration
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
clinical notes, labs, prescriptions, and 60 clearly-fake referral
specialists. Answer **yes** to the `Load the FICTITIOUS demonstration
dataset?` question during install, or run it later by hand:

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

A note on credentials: the demonstration data works with the seeded
`carlosdoc` account. If you accepted the default *replace the seeded
administrator password* question, log in with the random credentials from
`/etc/carlos-emr/initial-admin.txt`; on a disposable demo box you may prefer
to decline that question and keep the well-known development credentials.

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
| Browser cannot connect at all | `sudo journalctl -u nginx -n 50` | nginx not running, or the listen address is `127.0.0.1` while you are connecting remotely |
| Certificate warning in the browser | `sudo carlos-ctl cert status` | Expected with the default self-signed certificate — the connection is still encrypted; switch with `sudo carlos-ctl cert acme <email>` |
| "502 Bad Gateway" or a long spinner right after install/restart | `sudo carlos-ctl logs -f` | The webapp takes about two minutes to deploy; if it never comes up, the log says why |
| Login rejected with the seeded credentials | `sudo cat /etc/carlos-emr/initial-admin.txt` | Using the repository's published dev password instead of the generated one. Also: the PIN must be exactly four digits — a longer PIN fails with a message blaming the *password* |
| Install finished but the EMR is stopped, and `carlos-ctl check` says the unit is DISABLED | `sudo carlos-ctl check`, then `sudo carlos-ctl bootstrap-admin` | The installer could not replace the seeded `carlosdoc` credential (published in the source repository), so it stopped and disabled the service rather than expose the EMR with a known administrator password. It stays disabled across reboots on purpose. A successful `bootstrap-admin` re-enables the unit automatically; then `sudo systemctl start carlos-emr` |
| A specific page or action fails, but nothing in the application log | `sudo carlos-ctl waf tail` | The WAF blocked the request before it reached the application — the tail explains which rule and why |
| eForm print/fax produces no PDF, application log silent | `sudo journalctl -u carlos-emr-chromedriver -n 50` | The render browser is its own service with its own journal; if it cannot start, eForm rendering fails by design |
| Drug search returns nothing when prescribing | `sudo carlos-ctl check` (DrugRef probe) | `carlos-emr-drugref` not installed, or its service is down |
| Administration > Update Drugref reports a failed update, or stays "updating" | The message on that page; `sudo journalctl -u carlos-emr \| grep 'DrugRef database update'` | The Tomcat JVM has no outbound HTTPS to `www.canada.ca` (proxy not passed via `CARLOS_JAVA_OPTS`, see `/usr/share/doc/carlos-emr-drugref/README.Debian`). A failed run keeps the previous drug data |
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

# Installing CARLOS on a single server (Debian packages)

This is the supported way to run CARLOS EMR on one Ubuntu machine: two Debian
packages that install the application, its database, an nginx + ModSecurity
web application firewall, HTTPS, scheduled encrypted backups, and an
administration tool — a working, secured EMR from `apt install`.

> **Status: pre-production.** A site considering production use must complete
> its own technical, security, privacy, backup, restore, and regulatory
> review before this system holds patient information. The installed
> documentation calls out the decisions only an operator can make.

| Package | What it provides |
|---|---|
| `carlos-emr` | CARLOS on a dedicated Tomcat 11 instance, MariaDB with least-privilege accounts, an nginx front door running ModSecurity 3 + OWASP CRS in blocking mode, HTTPS (self-signed by default, Let's Encrypt on request), nightly restic backups with a weekly restore drill, and the `carlos-ctl` admin CLI |
| `carlos-emr-drugref` | DrugRef2, the drug and drug-interaction reference CARLOS queries when prescribing — co-deployed, loopback-only, with the Health Canada Drug Product Database seed loaded on install |

## Requirements

- Ubuntu 26.04 LTS (the packages target its Tomcat 11 / OpenJDK 21 / MariaDB
  11.8 / nginx stack).
- One dedicated server or VM. As a starting point: 4+ CPU cores, 8 GB RAM
  (2 GB JVM heap + 1 GB database buffer pool by default — both tunable),
  and disk sized for your document store plus backups.
- Root access. The *installation* uses root; the *running system* does not —
  every long-lived component runs as an unprivileged account.
- The `universe` component enabled and the package lists current. Five of the
  dependencies (`tomcat11-common`, `libtomcat11-java`, `openjdk-21-jre-headless`,
  `modsecurity-crs`, `libnginx-mod-http-modsecurity`) live in `universe`, and
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
the `tomcat` user listening on `*:8080` — every interface, with no TLS, no
WAF, no rate limiting and the default manager application. On a machine
holding patient records that is a way straight around every control this
package installs. It also competes for the memory the EMR's heap was sized
against.

The packages therefore refuse the combination (`Conflicts: tomcat11`): if it
is already installed, `apt` will offer to remove it. Let it.

## Install

Every CARLOS [GitHub release](https://github.com/carlos-emr/carlos/releases)
carries both `.deb` files, their `.sha256` checksums, and build-provenance
attestations (the `carlos-emr` package ships that release's published WAR,
byte for byte). Download the pair, verify, install:

```bash
sudo apt update
sha256sum -c carlos-emr_<version>_all.deb.sha256
sha256sum -c carlos-emr-drugref_<version>_all.deb.sha256
sudo apt install ./carlos-emr_<version>_all.deb ./carlos-emr-drugref_<version>_all.deb
```

`<version>` is the release's Debian version as it appears in the asset name,
with dots throughout — for example `2026.08.0.alpha8`, giving
`carlos-emr_2026.08.0.alpha8_all.deb`.

> **Releases up to and including 2026.08.0-alpha8:** the `.sha256` files
> record the build-time name, which spells the pre-release with a tilde
> (`carlos-emr_2026.08.0~alpha8_all.deb`), while GitHub rewrites that tilde to
> a dot when it stores the asset. `sha256sum -c` therefore fails with
> `No such file or directory` even though the download is intact. Compare the
> digests directly instead:
>
> ```bash
> sha256sum carlos-emr_<version>_all.deb
> cat carlos-emr_<version>_all.deb.sha256
> ```
>
> Releases after alpha8 record the published name and verify normally.

The installer asks a handful of questions (debconf): the host name clinicians
will use, the listen address, the billing province (Ontario or British
Columbia — **not changeable later**; the two create different database
schemas), the Java heap size, and the TLS mode. Everything can be answered
with the defaults and adjusted afterwards — except the province.

Installation then provisions the database and its accounts, applies the
schema with Flyway, replaces the seeded administrator credential with random
values, generates a self-signed certificate, wires nginx, and starts the
application. The webapp takes about two minutes to deploy; the installer
waits and says so.

To build the packages from source instead, see the header of
[`debian/rules`](../debian/rules) — including how to reuse a prebuilt WAR —
and [`release/README.md`](../release/README.md) for where the packaging
lives and why.

## Quickstart — the first hour

**1. Verify the deployment.** One command probes the whole system — services,
process ownership, network exposure, TLS, live WAF blocking, a live DrugRef
lookup, schema state, and backup freshness:

```bash
sudo carlos-ctl check
```

**2. Log in.** The initial administrator credentials were generated at
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

**3. Point backups off the host.** The default backup repository is a local
directory — a real first tier, but not disaster recovery. Set an offsite
`RESTIC_REPOSITORY` in `/etc/carlos-emr/backup.env`, **copy the
`RESTIC_PASSWORD` somewhere off this machine** (without it every backup is
permanently unreadable), and prove the pipeline end to end:

```bash
sudo carlos-ctl backup full
sudo carlos-ctl backup verify   # restores the newest dump into a scratch db
```

**4. Real TLS.** Once the host name resolves in public DNS and port 80 is
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
| `/etc/carlos-emr/backup.env` | nothing — the next timer run reads it; prove it with `carlos-ctl backup full` |
| `/etc/carlos-emr/modsecurity/` (WAF policy, site exclusions) | `sudo carlos-ctl waf reload` |

`carlos-ctl --help` lists every verb; `man carlos-ctl` documents them. The
tool shares its name, language, and overlapping verb set (`check`, `db`,
`db-migrate`, `db-users`, `backup full|verify|status`, `cert-renew`,
`rotate`) with the carlos-podman deployment's `carlos-ctl`, so operators can
move between the two without relearning.

Upgrades are `apt install` of the newer pair: the schema migrates before the
service restarts, your configuration files are never overwritten, and the
application refuses to start against a schema it was not built for rather
than failing mid-consultation.

**Removing the packages never destroys clinical data.** Neither `remove` nor
`purge` touches the databases, the document store, the backups, or the two
files whose loss would make retained data unreadable. Deliberate
decommissioning is its own explicit command
(`carlos-ctl destroy-data --confirm <server-name>`), which requires typing
the host's own configured name back to it.

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

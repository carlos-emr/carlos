# `release/` — release data assets

This directory holds **data** that ships with a CARLOS release: reference SQL,
eForm bundles, form images and the OSCAR 19 migration script. It is no longer a
packaging directory.

## Where the Debian/Ubuntu packaging went

The hand-rolled `dpkg -b` installer that used to live here — `make_CARLOS_deb.sh`
plus a `DEBIAN/` control set (`control`, `config`, `templates`, `postinst`,
`prerm`, `postrm`, `rules`) — has been **replaced** by a standard Debian source
package at the repository root:

    debian/

Release builds are published automatically: the `Debian Packages` workflow
(`.github/workflows/deb-packages.yml`) runs when a release is published,
builds both packages inside an `ubuntu:26.04` container from that release's
own attested WAR, and attaches the `.deb`s, checksums and provenance
attestations to the release.

Build it locally the normal way:

    sudo apt build-dep .          # or: apt install debhelper maven openjdk-21-jdk-headless tomcat11
    dpkg-buildpackage -us -uc -b

That produces `carlos-emr` and `carlos-emr-drugref` targeting Ubuntu 26.04 LTS
(the release whose Tomcat 11 and OpenJDK 21 packages satisfy the build
dependencies). See `debian/carlos-emr.README.Debian` for what the packages
install and how to
operate the result, and `debian/rules` for the build inputs (including how to
skip the Maven compile by supplying a prebuilt WAR).

### Why the change

The old script was written for OSCAR 19 on Tomcat 9 and carried assumptions
that no longer hold: an `oscar_15` database name, a 54 KB `postinst` that
created the schema with legacy shell scripts, Let's Encrypt and backup helpers
that predate systemd timers, and a build that had to run as root. It also
produced a package that ran the EMR under the distribution's shared `tomcat`
user.

The `debian/` packaging targets the current stack (Tomcat 11, Java 21, MariaDB
11.8, the Flyway migration set) and runs the application under its own
unprivileged account behind an nginx/ModSecurity front door.

### Files that moved rather than disappeared

| Removed from `release/`                | Replaced by                                           |
|----------------------------------------|-------------------------------------------------------|
| `make_CARLOS_deb.sh`                   | `debian/rules`                                        |
| `control`, `config`, `templates`       | `debian/control`, `debian/carlos-emr.{config,templates}` |
| `postinst`, `prerm`, `postrm`, `rules` | `debian/carlos-emr.{postinst,prerm,postrm}`           |
| `carlos_backup.sh`, `restore.sh`       | `carlos-ctl backup` (restic, systemd timers)           |
| `letsencrypt.cron`, `gateway.sh`       | `carlos-ctl cert acme` / `cert-renew` (certbot + nginx)                    |
| `reOscar.sh`                           | `systemctl restart carlos-emr`                        |
| `tomcat9server.xml`, `tomcat9LEserver.xml` | `debian/assets/tomcat/server.xml`                 |

## What remains here

- `019toCARLOS.sql` — OSCAR 19 to CARLOS schema migration.
- Reference and dashboard SQL (`OLIS.sql`, `ontarioLab.sql`, `FIT.sql`,
  `opr2017.sql`, `DoBC_dashboard.sql`, `bc_billing_dashboard.sql`,
  `indicatorTemplatePANEL.sql`, `tallMAN*.sql`, `RNGPA.sql`, `special.sql`,
  `OfficeCodes.sql`, `drugref.sql`).
- Demo data (`demo.sql`, `unDemo.sql`) — development only; never load it on a
  system that will hold patient information.
- eForm bundles and their assets (`ndss.zip`/`ndss.sql`, `rbr2014.zip`,
  `RourkeEform*.sql`, `Document/`, `OPR-2017*.png`, `4422-84v9-1.png`,
  `labDecisionSupport.js`, `editControl2.js`).
- Operational helpers that are clinical rather than packaging concerns:
  `ExcellerisDownload.sh` (lab download), `run_rxquery.sh`,
  `drugrefUpdate.cron`.

These are not installed by the `carlos-emr` package: they are optional
site-by-site data loads. Apply one with, for example:

    sudo carlos-ctl db < release/ontarioLab.sql

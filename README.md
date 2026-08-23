# CARLOS

[![License: GPL-2.0](https://img.shields.io/github/license/carlos-emr/carlos)](https://github.com/carlos-emr/carlos/blob/develop/LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/carlos-emr/carlos/maven-project.yml?branch=develop)](https://github.com/carlos-emr/carlos/actions/workflows/maven-project.yml)
[![Good First Issues](https://img.shields.io/github/issues/carlos-emr/carlos/good%20first%20issue?color=7057ff&label=good%20first%20issues)](https://github.com/carlos-emr/carlos/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/carlos-emr/carlos/blob/develop/CONTRIBUTING.md)
[![Last Commit](https://img.shields.io/github/last-commit/carlos-emr/carlos)](https://github.com/carlos-emr/carlos/commits/develop)

## What is CARLOS

CARLOS (Clinical Assisting Recording Ledger Open Source) is an electronic medical records system forked from the OpenO EMR project. This software is licensed under the **GNU General Public License (GPL) v2**. The CARLOS contributors and the many others involved with development of this codebase believe strongly in the value of open-source software in healthcare. This license means:

1. **Freedom to Modify and Distribute**: You are free to modify and distribute the software, but any modifications or derived works must also be licensed under GPL v2. This ensures that the software remains open-source, and any improvements are shared back with the community.

2. **Source Code Availability**: If you distribute any modified versions or binaries, you must also provide access to the corresponding source code. This helps maintain transparency and allows other users to build on your changes.

3. **No Warranty or Liability**: As with all GPL-licensed software, CARLOS comes with **no warranty**, either express or implied. The software is provided "as is," meaning that CARLOS contributors and any others involved are not liable for any issues, damages, or legal claims arising from the use of the software. Users are responsible for ensuring compliance with relevant laws and regulations in their jurisdiction when using or distributing the software.

Please review the GPL v2 license for further details.
CARLOS EMR continues to evolve through community-driven development, providing a robust solution tailored for modern healthcare challenges.

## Installation

**Running CARLOS on a server** — install the Debian packages. Every
[release](https://github.com/carlos-emr/carlos/releases) ships `carlos-emr`
and `carlos-emr-drugref` `.deb` files (with checksums and provenance
attestations) that set up the whole system on one Ubuntu 26.04 machine: the
application, MariaDB, an nginx + ModSecurity web application firewall, HTTPS,
scheduled encrypted backups, and the `carlos-ctl` administration tool — with
every long-running component under an unprivileged account.

```bash
sudo apt install ./carlos-emr_<version>_all.deb ./carlos-emr-drugref_<version>_all.deb
sudo carlos-ctl check
```

Full install and quickstart guide: **[docs/install-deb.md](docs/install-deb.md)**.

**Developing CARLOS** — use the [devcontainer](.devcontainer/README.md), a
complete disposable development environment with synthetic data. It is for
development only, never for patient information.

**Containers (under development)** — the
[carlos-podman](https://github.com/carlos-emr/carlos-podman) project deploys
CARLOS as rootless Podman pods with an optional observability stack and
Ansible provisioning; see its
[README](https://github.com/carlos-emr/carlos-podman#readme) and
[QUICKSTART](https://github.com/carlos-emr/carlos-podman/blob/main/QUICKSTART.md).

## Releases

Published source archives, versioned WAR files, checksums, and SBOMs are
available on the [GitHub Releases](https://github.com/carlos-emr/carlos/releases)
page. Signed provenance attestations are verifiable as described in the release
process. Alpha, beta, and release-candidate builds are prereleases and should be
evaluated before production deployment.

CARLOS follows a CalVer maintenance-branch model. Maintainers and contributors
should follow the canonical [release process](docs/release-process.md) for
versioning, target-branch selection, tags, hotfixes, and release verification.

In brief, normal work starts from and targets `develop`; high-priority fixes for
supported releases start from and target the oldest affected `release/YYYY.MM`
branch before they are forward-merged; and release-preparation PRs promote
current-train exact versions to `main`. Older supported patches remain on their
maintenance branch. Ongoing branches use
`-SNAPSHOT` with SCM tag `HEAD`. Release tags are annotated, immutable, and
created only on exact non-snapshot release commits.

## Contributing

We welcome community involvement! Whether you're reporting bugs, improving documentation,
or writing code, your contributions help build a better EMR for healthcare providers.

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for everything you need to get started, including
development environment setup, code standards, and the pull request process.

## Project History and Affiliations

CARLOS is forked from the OpenO EMR project, which itself was forked from OSCAR McMaster.

**Important Disclaimers:**
- This project has **no affiliation** with OpenOSP, the organization that developed OpenO EMR
- This project has **no affiliation** with McMaster University
- OSCAR is an official mark of McMaster University
- The OSCAR name is used in this project solely for descriptive and historical purposes

For detailed copyright attribution and project heritage information, see [NOTICE.md](NOTICE.md).

## Licensing
CARLOS is licensed under the **GNU General Public License (GPL) v2**, providing transparency and freedom for users to modify and improve the software. See the LICENSE file for full details.

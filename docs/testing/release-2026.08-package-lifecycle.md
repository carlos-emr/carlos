# Installed package lifecycle execution

`package-lifecycle-core` runs three independent Ubuntu 26.04/systemd cells per
province: candidate clean install and equal-version reinstall, pinned current
main upgrade, and distinct pinned beta5 upgrade. The two upgrade cells execute
the same read-only client source before and after the package transaction. They
require the historical CLI 1.3.0 dependency refusal and compatible 1.3.1
resolution without mutation, create clinical canaries through the UI, reopen them after upgrade, clean their
owned graph, and independently compare all cells in eleven clinical tables and
the document store. The earlier failed manual attempts remain separate evidence.

The host requires the exclusive disposable local Docker daemon at
`unix:///var/run/docker.sock`. Cells publish no ports, use the normal bridge and
session proxies/CA, and mount source, browser and package inputs read-only. Their
systemd setup requires privileged containers and private cgroup namespaces. Run
only on a disposable qualification host. The runtime contains test clients but no
preinstalled application, JDK, Maven or application server. An example recipe is
[package-lifecycle-runtime.Dockerfile](package-lifecycle-runtime.Dockerfile).
The resulting immutable `sha256:…` image ID is required; mutable image names fail.

Set `CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1` and
`CARLOS_PACKAGE_LIFECYCLE_CONFIG` to a private JSON file outside the checkout.
The configuration has this shape; replace every placeholder with verified local
inputs. Predecessor and CLI digests must match the committed
[baseline manifest](release-2026.08-lifecycle-baselines.json). Candidate packages
must contain the exact WAR supplied to the release runner, with only the two
reviewed Debian transforms. The final in-container application attestation checks
every entry, rather than trusting the package version or health endpoint.

```json
{
  "runtimeImage": "sha256:<64 lowercase hexadecimal characters>",
  "caFile": "/absolute/session-ca.pem",
  "browserDirectory": "/absolute/locked-chromium-directory",
  "packages": {
    "target": {
      "emr": {"path": "/absolute/candidate-emr.deb", "sha256": "<sha256>"},
      "drugref": {"path": "/absolute/candidate-drugref.deb", "sha256": "<sha256>"},
      "ctl": {"path": "/absolute/carlos-ctl_1.3.1_all.deb", "sha256": "<sha256>"}
    },
    "2026.08.0-beta6": {
      "emr": {"path": "/absolute/main-emr.deb", "sha256": "<sha256>"},
      "drugref": {"path": "/absolute/main-drugref.deb", "sha256": "<sha256>"},
      "ctl": {"path": "/absolute/carlos-ctl_1.3.1_all.deb", "sha256": "<sha256>"}
    },
    "2026.08.0-beta5": {
      "emr": {"path": "/absolute/prior-emr.deb", "sha256": "<sha256>"},
      "drugref": {"path": "/absolute/prior-drugref.deb", "sha256": "<sha256>"},
      "ctl": {"path": "/absolute/carlos-ctl_1.3.0_all.deb", "sha256": "<sha256>"}
    }
  }
}
```

Use the normal source-bound release runner with `--check package-lifecycle-core`,
`--profile ON` (then BC) and `--artifact` pointing to its built WAR. The runner
keeps `packaged: true`; the explicit orchestrated deployment scope validates all
three in-container application records both during collection and final receipt
verification. Missing, duplicated or altered cell evidence fails. There is no
requirement for a candidate application installed on the Docker host.

First-login reset runs before ordinary authentication. The fixture setup enables
the documented username-lock mode and installs the exact bundled synthetic
consultation signature only when absent; it refuses conflicting existing bytes.
Browser identity must match the committed Playwright lock. Version ordering uses
Debian control fields, including `~`, and rejects downgrade/equal-version upgrade
before mutation.

Private recovery configuration, journals and browser artifacts remain in each
owned evidence directory. Do not publish them. A failed cell is retained, never
retried in place or removed by this controller. A successful cell is removed only
after evidence retention; all its input files are hashed into the command result.
Host interruption may leave its container running: use its recorded exact name to
inspect and reconcile it before another operation. Do not certify cleanup merely
because the Docker client stopped.

The broader `deb-clean-upgrade-reinstall` acceptance remains independently
blocking for actual unsupported-schema/dependency refusals, interrupted/failed
transaction recovery and remaining service-recovery cases. This core controller
does not turn those unimplemented or unexecuted cases green.

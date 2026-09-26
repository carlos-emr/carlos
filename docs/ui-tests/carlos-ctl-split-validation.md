# carlos-ctl package split — validation record (2026-09-26)

Validation of the carlos-ctl split (issue #4001): the CLI moved to
[carlos-emr/carlos-ctl](https://github.com/carlos-emr/carlos-ctl) and ships as
its own package that `carlos-emr` depends on; the OSCAR 19 import manifests
ship as JSON with `carlos-emr`. Everything below was run from the branch under
review (based on `release/2026.08`), on Ubuntu 26.04 (`ubuntu:26.04`
containers on a 4-core, 15 GB host). The matrix was run twice: first with
developer-snapshot versions (2026.09.0~snapshot24 → snapshot25, carlos-ctl
1.0.0 → 1.0.1), then, after the branch was retargeted at `release/2026.08`,
with RELEASE-shaped versions stamped as the release workflow stamps them
(2026.08.0~alpha15 → 2026.08.0~alpha16, carlos-ctl 1.1.0 → 1.1.1). The second
run is the one that exercises the shipped `Breaks`/`Replaces` boundary
(`carlos-emr (<< 2026.08.0~alpha16~)`); both passed with the same counts.

## Packages built

All three builds ran in an `ubuntu:26.04` container with the distribution's
debhelper, and `lintian --fail-on error` passed where it is gated:

| Package | Source | Notes |
|---|---|---|
| `carlos-ctl_1.1.0_all.deb`, `carlos-ctl_1.1.1_all.deb` (first run: 1.0.0, 1.0.1) | carlos-ctl branch, changelog stamped as the release workflow does | the build runs the CLI's own suite (1454 tests) and the groff gate; lintian clean |
| `carlos-emr_2026.08.0~alpha16_amd64.deb` (+ transitional renderer; first run: 2026.09.0~snapshot25) | this branch | `CARLOS_WAR` = the published 2026.08.0-alpha13 WAR (its `.sha256` verified); `SKIP_DRUGREF=1 SKIP_EFORM_RENDERER=1` (no Maven, no Chromium download); lintian clean apart from the pre-existing bashism warning |
| `carlos-emr_2026.08.0~alpha15_amd64.deb` (pre-split; first run: 2026.09.0~snapshot24 from `main`) | `origin/release/2026.08` | same WAR and knobs; the "before" side of the matrix |

Both application packages carry the same WAR and migration set, so the
matrix exercises the packaging transition alone (`EXPECT_NEW` empty, Flyway
stays at 23 applied for Ontario).

## Unit and contract suites

| Suite | Result |
|---|---|
| carlos-ctl, standalone (fixture manifests), Python 3.11/3.12/3.13 | 1454 tests, 0 failures (contract tests skipped without `CARLOS_SRC`) |
| carlos-ctl with `CARLOS_SRC` = this checkout | 1433 tests before the two new files, 0 failures, 20 skipped (MariaDB-only) |
| carlos `scripts/migration/o19/tests` (generator + shipped manifests, `CARLOS_CTL_SRC` set) | 244 tests, 0 failures |
| carlos `debian/assets/tests` (packaging contracts) | 26 tests, 0 failures |
| `scripts/deb-install-completion.test.js` (`CARLOS_CTL_SRC` set) | 23 tests, 0 failures |
| carlos-ctl GitHub CI on the pushed branch (3.12, 3.14, man page, deb) | green after gating one CSV-reader test to 3.13+ |

## Upgrade matrix (`scripts/deb-split-matrix.sh`)

Run in a systemd `ubuntu:26.04` container (host networking; the stock image's
`policy-rc.d` removed so services start under apt) with the packages above,
`PAUSE_BEFORE_REMOVE=1`. Result: **131 passed, 0 failed** (both runs).

| Case | What was asserted | Result |
|---|---|---|
| 0 fresh pre-split install | front door 200, `carlos-ctl check` passes, `/usr/sbin/carlos-ctl` owned by carlos-emr | pass |
| 2 new carlos-emr offered without the carlos-ctl file | apt refuses up front naming carlos-ctl; versions, status, the running EMR and the old CLI unchanged | pass |
| 1 pre-split → split pair in one transaction | the 30 existing upgrade assertions (schema, credential, config, TLS, rows, documents, build tag, front door) plus the split contract: command, alias and both man pages owned by carlos-ctl, nothing of the old copy or its bytecode cache under `/usr/lib/carlos-emr`, manifests at format 1, `check` names both versions, provisioning unit starts `/usr/sbin/carlos-ctl`, carlos-ctl configured before carlos-emr | pass (46) |
| 5 carlos-ctl upgrade with a staged import | preinst refuses with the remedy; ledger byte-identical; installed CLI intact and configured | pass |
| 4 carlos-ctl only upgraded (1.1.0 → 1.1.1) | no EMR restart (same PID), front door 200, `check` passes and names 1.0.1, no bytecode written, `--write-standalone` yields a runnable assessment script | pass |
| 3 carlos-emr only upgraded (repacked 2026.08.0~alpha16+matrix1) | the upgrade assertions again with the split contract (44) | pass |
| 6 `apt remove carlos-emr` | carlos-ctl remains; every verb answers "carlos-emr is not installed" (rc 1, no traceback); `--help` answers; database and document store intact | pass |
| 7 purge both | dpkg knows neither; `/usr/lib/carlos-ctl`, the command and alias gone; database and document store intact | pass |

## Browser check (`scripts/deb-login-playwright-checks.js`)

Run from the host against the paused container after case 3 (split pair,
carlos-emr alpha16 + carlos-ctl 1.1.1), with the credential from
`/etc/carlos-emr/initial-admin.txt`: **7 passed, 0 failed** (both runs) — login page over
TLS with HSTS, wrong PIN refused, installer credential lands on the forced
reset, reset reaches the provider schedule, a fresh session signs in with the
new password, the old password no longer signs in, no console errors.

## Not covered here

- DrugRef and the eForm renderer payload were left out of the test builds;
  their maintainer scripts only gained a visible `Depends: carlos-ctl`.
- The release workflow's carlos-ctl fetch step needs a published carlos-ctl
  release with the pinned tag (`debian/carlos-ctl.pin`: `1.1.0`) and its
  attestation; it cannot run until that release exists.
- A pre-split DEVELOPER snapshot (2026.09.0~snapshot24 or earlier) is not a
  covered upgrade source: it sorts above every 2026.08 version, so the
  `Replaces` boundary does not reach it; remove `carlos-emr` first on such a
  host. The first (snapshot-versioned) run passed only because that run's
  carlos-ctl carried the snapshot boundary.
- A real VM boot (`carlos-emr-provision.service` at boot) was not exercised;
  the unit's `ExecStart` is asserted by the matrix and by
  `scripts/deb-install-completion.test.js`.

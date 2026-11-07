# PR #5705 packaged before/after evidence

Evidence only. This directory is not part of PR #5705 and is not meant to be merged.

Synthetic data only: the patient is `FAKE-GROWTHVIS, Visual` (DOB 2025-05-01), created for this run.

## What was compared

| | Before | After |
|---|---|---|
| Source | `release/2026.08` @ `1b7cbfee` | PR head tree `166ecea0` (= `0f7d02d0`; `cf4389d1` changes tests and docs only) |
| Package | `carlos-emr_2026.09.0~snapshot28_amd64.deb` sha256 `93e1e4b7…` | same version, sha256 `f62ae05d…` |

Both sets of debs were built with `dpkg-buildpackage` in an `ubuntu:26.04` (26.04.1) container
(OpenJDK 25, Maven, debhelper 13.31). They were installed with carlos-ctl 1.3.1 by
`scripts/deb-docker-validation.sh up` into a systemd Ubuntu 26.04 container (Ontario, demo data,
MariaDB 11.8.6, nginx + ModSecurity). `carlos-ctl check` passed after the install and after the upgrade.

The install was upgraded **in place** from the before debs to the after debs, so the new JPA code read
rows that the legacy JDBC code wrote.

## Results

- **Screenshots and print PDFs:** 0 changed pixels for the new form, the redisplay after Save, the
  legacy-saved row, a legacy row with all twenty slots NULL, both print PDFs, the eChart Forms
  module and the eChart note list. The legacy-saved row and the JPA-saved row with the same input
  also render with 0 changed pixels. The full eChart page differs only in its running encounter
  timer (00:00 vs 00:06).
- **Stored row:** the same values were saved through the legacy JDBC code (row 7) and through the
  new JPA code (row 11). A null-safe (`<=>`) comparison of all 131 columns differs only in `ID` and
  `formEdited`.
- **Form fields:** every input value matched before and after on each Growth page.
- **Packaged Playwright suite:** 15 checks, with the same result on both builds (14 pass). These
  include all 11 `form-rourke2020-growth*` variants, `form-rourke2017`, `form-catalog-smoke`,
  `select-forms-panel` and `echart-navbar-modules`. Both runs made 184 assertions, and the
  Growth/Rourke plotting details match (plotted points, PDF pages).
- **`echart-navbar-modules` fails on both builds** with the same message ("catalogued link
  `Forms` is missing or changed"), so the failure was already there before this PR.
- **Server log audit:** passes for the after build's suite window, with 0 unexplained events.

## Files

- `compare/*-compare.png`: before | after | diff (red marks pixels that changed).
  `compare/summary.json` holds the pixel counts.
- `scripts/`: the Playwright and diff scripts used here (`growth-visual.js`, `notes-view.js`,
  `compare.py`).
- `suite/`: JUnit reports from both suite runs and the server log audit of the after build.

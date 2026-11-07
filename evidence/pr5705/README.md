# PR #5705 packaged before/after verification

Evidence for [carlos-emr/carlos#5705](https://github.com/carlos-emr/carlos/pull/5705)
(Growth 0–36 JPA persistence). This branch only hosts the evidence; it is not
part of the pull request. Synthetic data only.

## What was run

| Item | Value |
| --- | --- |
| Container | `ubuntu:26.04` (Ubuntu 26.04.1 LTS), systemd PID 1, via `scripts/deb-docker-validation.sh` |
| Before | `release/2026.08` at `1b7cbfee`, packaged as `carlos-emr 2026.09.0~snapshot28+local.base1` |
| After | PR head `0f7d02d0` (PR merged with that base), packaged as `2026.09.0~snapshot28+local.pr1` |
| Other packages | `carlos-emr-drugref` from its pinned ref, `carlos-ctl 1.3.1` (published release) |
| Database | MariaDB 11.8.6, Ontario schema plus demonstration data |
| Upgrade | Before packages installed fresh; after packages installed over them (`apt-get install`), 0 migrations, `carlos-ctl check` passed both times |

The after pass ran on the same database, so the PR code reopened the Growth row the
old code saved (row 1) before saving its own.

## Results

* `compare/`: before | after | diff for each screen. All six comparisons show
  **0 changed pixels** (`reports/compare.json`):
  new form, filled form, redisplay after Save, Forms list, reopen of the old row,
  and the old code's view of its row against the new code's view of its own row.
* Stored rows (`reports/*-report.json`): all 131 columns equal except `ID` and
  `formEdited`; the old row is unchanged after the PR save.
* Browser suite (`reports/suite-*.txt`): 9 of 9 pass before and after:
  `form-rourke2020-growth` (+ `-print-dob`, `-window-title`, `-graph-title`),
  `echart-forms-latest-item`, `echart-forms-list-paging`, `popup-opener-echart-forms`,
  `rourke-measurement`, `get-reject-rourke-form`.
* Smoke tier on the after install (`reports/smoke-after.txt`): 12 of 12 pass.
* `negative/` (after only): opening another patient's row, and saving with another
  patient's row as the source, are refused with the generic error page. No data from the
  other patient is shown, no row is written, the source row is unchanged, and the log
  carries only `missing required sec object (_form)`.
* With `save_as_xml=true` the after package writes
  `formGrowth0_36_<patient>_<timestamp>.xml` (131 columns) after the commit.

Scripts: `reports/growth-visual.js`, `reports/growth-negative.js`, `reports/compare.py`,
`reports/patient.sql`.

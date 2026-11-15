# Independent growth chart geometry

Target: `release/2026.08`; source request [#5717](https://github.com/carlos-emr/carlos/issues/5717).
Run `npm run test:form-growth-clinical-geometry-playwright` with `EXCLUSIVE=1`
against the disposable installed application. Poppler and the shipped WHO PDF
reference assets are prerequisites. This check must not run concurrently with
other checks (live wrapper: EXCLUSIVE=1).

The check reuses the original Rourke/Growth native form workflows and keeps their
page-count, point-count, renderability, saved-field, reopen and print assertions.
Existing finding selectors remain unchanged. The new entry claims the graph,
measurement inclusion, combined-source and Growth DOB concerns; all other
original concerns retain their original entries and finding ownership.

## Independent clinical inputs

Two marker-owned charts have fixed birth dates, 2024-11-01 (female) and
2024-11-02 (male). Values are synthetic kg/cm observations, independently stated
in `form-growth-clinical-geometry-playwright-checks.js`.

| Age in days | Rourke weight | Length | Head circumference |
| --- | --- | --- | --- |
| 0 | 3.2 | 50.5 | 34.5 |
| 7 | 3.6 | 52.1 | 35.5 |
| 14 | 3.9 | 53.6 | 36.1 |
| 61 | 5.1 | 57.2 | 38.5 |
| 274 | 8.2 | 70.3 | 43.1 |
| 548 | 10.2 | 80.1 | 46.8 |

Growth 0–36 supplies ages 0, 1, 3.9, 12 and 23.95 months on days
2, 30, 120, 365 and 729. Their respective weight/length/head values are
3.3/50/34.5, 4.4/54.8/37.2, 6.5/61.5/40.8, 9.2/74.5/45.5 and
12.4/87.6/48.7. This covers irregular dates, birth and the upper 24-month edge.
Rourke uses observation dates; the Growth form uses its explicit age fields.
An independent date oracle converts elapsed days using 365.2425 days per year.
The documented point tolerance includes the release's fractional-calendar-month
placement, rather than copying that implementation.

The Measurements fixtures are two identical WT 6.4/HT 62.5 pairs at day 100.
The date-based charts retain both pairs: 12 original circles become 16. The
weight-for-length view shows one location for the identical same-date pairs;
combining Rourke, Measurements and the five Growth rows yields 23 circles.
The oracle compares a multiset, including the required concentric date-series
circles, rather than hiding duplicates with a set. This asserts the existing
representation; it does not change a clinical deduplication policy.

A third owned infant has plausible distinct WT 6.123/HT 63.1 controls at day 111.
Its complete clinical snapshot must stay identical. Its observations must never
appear in either tested chart.

## Output oracle

The actual graph controls produce PDFs through the normal front door. The
original lossless capture forwards the same upstream bytes to the browser.
The new oracle reads only those bytes and independently pinned WHO reference
assets, never the active servlet, graphic class or graphic-property files.

* Vector paths identify every 1.5-point observation circle in the page overlay,
  its RGB series and its top-left page coordinates. Unclassified curves, missing
  points, wrong multiplicity or a populated older-child page fail.
* Visible reference-grid anchors calibrate age, kg, length and head circumference.
  The boy head/weight-for-length panels have separate calibrations. The maximum
  Euclidean point error is 2 PDF points at 72 dpi, covering grid-centre measurement,
  rounding and fractional-calendar age without concealing a shifted observation.
* The pinned sex-specific WHO Canada 2006 assets are the independent curve and
  unit reference. Rendered comparison includes the chart title, sex, units,
  axes, grid, labels and curves on the first and older-child pages. It excludes
  only native identity/value-table overlays and a five-pixel disk around each
  already-verified observation. Any remaining pixel differing by more than 3 RGB
  levels fails; no percentage of changed chart pixels is waived.
* WHO weight-for-age medians independently establish explicit relationships:
  the 12-month 9.2 kg Growth point is above the female 8.9 kg median and below the
  male 9.6 kg median. The 18-month 10.2 kg Rourke point lies on the female 10.2 kg
  median and below the male 10.9 kg median. No new percentile calculation is added.
  References are the embedded PDF source notice and the
  [WHO weight-for-age standards](https://www.who.int/tools/child-growth-standards/standards/weight-for-age).

Saved Rourke and Growth history snapshots are taken after their native setup
saves, including the existing autosave history. Every subsequent graph/print
must preserve those exact rows and all four Measurements rows. The oracle does
not assume one history row when the native setup has acknowledged more than one.

## Qualification and cleanup

The final whole-chart oracle passes both sexes on the installed ON and BC
snapshot 31 packages. All reference comparisons have zero unexplained pixels;
the worst reached coordinate difference is 1.14 points. The BC suite entry
passes in 263.3 seconds with no failure or skip. Native form/history/control
and exact owned cleanup checks pass. Source digests are unchanged between the
qualification runs and the published candidate. The full Ubuntu Node suite
passes 7,645 cases in 543 files without failures, skips, cancellation or TODO.
Earlier failing attempts remain unqualified. The consolidated acceptance register
binds these results to the exact source/package identity.

Original form/boolean/measurement cleanup remains in the shared workflow.
Extra patient ownership is registered before INSERT, chart locks are released
before closing pages, and the exact marker-owned form registrations are removed.
An unacknowledged server write retains the fixtures. Independent final SQL,
configuration, package and unrelated-state audits are required.

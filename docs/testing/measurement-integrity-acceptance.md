# Measurement and chart integrity: shipping contract

Target: `release/2026.08`, PR [#6102](https://github.com/carlos-emr/carlos/pull/6102).
This register describes the current implementation. Test totals and CI outcomes belong
to the revision reported by the PR checks, not to an earlier screenshot capture.

## Lab handling decisions

- CML filing retains the OSCAR 19 whole-report contract: the current version replaces
  measurements from all earlier versions. A partial correction also replaces the whole
  earlier report; omitted readings remain available in the original report/PDF.
- CML ordering uses result stage, comparable lab revision times (OBR-22), correction
  status, final-result count and then arrival ID. Collection/observation/transport times
  do not stand in for missing revision times. Within a stage, clocks are compared only
  when every version supplies them, keeping a transitive order. A late partial final
  cannot displace a complete final with the same revision. An otherwise indistinguishable
  reissue follows arrival order; the feed cannot establish a stronger order.
- Acknowledgement files all older versions resolved before its transaction starts and
  preserves prior acknowledgement audit fields. The response reports exact affected IDs
  and cleared NEW routing counts. `retainedVersionIds: []` remains only as a wire alias
  for cached clients; there is no partial-chain retention policy.
- Measurement extraction preserves the source's case while matching the existing
  clinic filter against a `Locale.ROOT` lowercase shadow. Overlong values are not cut
  into different clinical values; comments are explicitly bounded to the schema width.
- Unlink continues to operate on one report. There is no new match-version token,
  whole-chain unlink, sticky unmatch, historical repair page, per-observation projection,
  import chain lock or source overlay in histories/grids.

The questions about qualified graph values, unlink scope and filter-excluded literals
remain in [#6118](https://github.com/carlos-emr/carlos/issues/6118) and
[#6120](https://github.com/carlos-emr/carlos/issues/6120). This PR does not close
#5827, #5828 or #5835 as delivered enhancements.

## Charts and reports

- Single points have visible markers. Constant-value domains and ticks remain useful
  for zero, small decimals and large values. Labels use Java HALF_EVEN formatting and
  server locale/date conventions without changing plotted values.
- Pending the qualified-result decision, `<5`, `>100` and a single number with a unit
  retain the existing numeric plotting policy. A bounded left-to-right parser rejects titres,
  dates, exponents, ranges, malformed grouping and multi-number text instead of joining
  their digits. No excluded-result count or exclusion band is drawn.
- Collection times accept typed dates, explicit offsets, offset-free fractions through
  nanoseconds and server-zone wall times through DST transitions. Malformed dates fail
  visibly rather than silently replacing the date.
- Chart and PDF rendering share two browser slots, FIFO queues bounded to 16 chart
  and four PDF waiters, and admission that prevents repeated overtaking between classes.
  Timeout, interruption and cleanup are tested on the active admission implementation.
- Graph errors return explicit 422/500/503 responses as appropriate. Lab and medication
  image embeds offer visible retry controls. Failure logs include reason and exception
  class, excluding exception messages/stack traces that can contain clinical values.
- CDM forms bind session-private, bounded, expiring snapshots to their originating tab.
  INPUT rendering restores those definitions even when Struts conversion short-circuits
  `execute()`. A corrected resubmit keeps its token. Blank counts produce a value error;
  invalid/stale selectors refuse the report instead of querying another form's group.
- Lab history/grid/graph reads retain patient-level `_lab` authorization. Unmatched
  history requests keep the existing invalid-demographic message rather than a bare 400.

## Repeatable validation

All application fixtures are synthetic. Relevant maintained checks include:

| Area | Checks |
| --- | --- |
| CML ordering and filing | `CmlVersionPolicyUnitTest`, `CmlVersionFilingIntegrationTest`, actual packaged `lab-version-ingestion` workflow |
| Acknowledgement and inbox | `LabAcknowledgementAuditIntegrationTest`, report action tests, inbox notification and Node client regressions |
| Numeric/date/chart behavior | `MeasurementGraphAction22ActionUnitTest`, `MeasurementChartDataUnitTest`, graph error tests, `chart-draw-playwright-checks.js` |
| Renderer admission and cleanup | `RenderAdmissionUnitTest`, chart/PDF service unit and browser smoke tests |
| CDM isolation and recovery | context/action unit tests, installed `report-cdm` and `cdm-measurement-report` browser workflows |
| Packaging and release | dependency-locked Debian build, Ubuntu 26.04 ON/BC installed smoke, Java/Node suites, coverage freshness and floors |

The French renderer input lives in `scripts/fixtures/chart/french-date-ticks.json`;
release checks do not depend on a PR evidence folder. Earlier screenshots, capture tools
and hash-only tests for removed features were removed from this PR. Their original
receipts remain in Git history, not as qualification claims for the shipping code.

See the [live checks](https://github.com/carlos-emr/carlos/pull/6102/checks) and PR
description for revision-specific validation. Coverage ratchets must be refreshed from
measured reports after Java changes, without lowering established floors.

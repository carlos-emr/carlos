# Measurement and chart integrity acceptance

Target: `release/2026.08`. Screenshot baseline: `9004536dc8314d5f50542b48905ca3ca4099a916`.
This register distinguishes implementation from qualification. Unchecked requirements
must not be used to close a ticket. Retained browser/database evidence uses synthetic,
owned data. This PR remains a draft: current-version selection and historical repair
are incomplete.

[Installed ON/BC comparisons: 98 screenshot pairs](measurement-native-screenshots.md) ·
[Renderer comparisons: 45 screenshot pairs](measurement-chart-screenshots.md)

| Tickets | Required outcome | Implementation / qualification |
| --- | --- | --- |
| #5814, #5854 | Visible single point, useful domain, distinct ticks; constant values in the millions render | Implemented. Eight native geometry cases pass in each province; actual response PNGs retained. |
| #5827 | Bounds, titres, dates and qualitative grades never become exact numeric points | Bounded exact-number parser and visible exclusion counts implemented. Native qualified-results comparisons pass in both provinces, with numeric controls and actual report PDFs. |
| #6091 | Java HALF_EVEN point labels without altering stored/plotted values | Server-formatted labels and real-browser regressions pass; rounding boundaries retained in renderer gallery. Full installed print parity remains a gate. |
| #6092 | Server locale/date/number conventions preserved | Localized labels and date metadata implemented. English/French renderer comparisons pass; full installed locale matrix remains a gate. |
| #3935 | Session-private, bounded, expiring per-form definitions; explicit invalid/stale outcome | Implemented. All three native reports preserve each tab's type/count in both provinces. Cross-session, expiry, capacity and invalid-selector tests pass. |
| #5820, #5819 | Source-aware current selection and atomic active-measurement projection | **Not implemented.** Native ON correction retake still observes both old and corrected active projections, with the original selected as current. |
| #5835 | Atomic complete-chain unlink, provider reconciliation, stale retry protection | Implemented. Both provinces pass real MariaDB late-audit-fault rollback, normal retry, stale 409, projection archival, independent/automatic provider controls and later-delivery unmatch fence. Concurrent physical ingestion/unlink qualification remains a gate. |
| #5828 | Literal preservation and visible filter-excluded source results | Implemented. Native source histories/grids retain case and Pending rows, actual PDFs retain source values, and read/plot/print leave clinical state unchanged in both provinces. |
| #5853 | Fair bounded renderer admission, typed failures, all consumers show failures | Implemented. Native renderer stop, visible warning/Retry, 503/Retry-After, restart/recovery and subsequent actual eForm PDF controls pass in both provinces. Installed saturation/interruption/load remains a gate. |
| #5472 | Verify existing #5518 replacements, packaging and documented differences | Existing replacement is already in the release baseline. Native XLSX/CSV, JSTL/EL, chart and eForm PDF controls pass in both provinces; existing output-difference sign-off remains with #5472. |

## Required qualification before merge

- [ ] Obtain the affected sources' full-report versus partial-correction contracts; do not infer clinical deletion semantics from delivery order alone.
- [ ] Implement and qualify canonical current selection and atomic active projection for #5819/#5820.
- [ ] Coordinate ingestion, matching, unlinking and repair for each clinical chain, and exercise physical concurrency.
- [ ] Distinguish deliberate clinician deletions from supersession, unlink and repair archival.
- [ ] Provide fingerprinted, idempotent, dry-run-first historical repair for unambiguous damage.
- [x] Exercise actual image/PDF contents with independent geometry/value associations, cross-patient controls and unchanged-read checks.
- [x] Compile all 995 JSPs with Tomcat 11 Jasper; check the packaged WAR dependency/API contents.
- [x] Install and exercise native qualified results, chart geometry, CDM and unlink workflows in both ON and BC packages.
- [x] Exercise real MariaDB late audit INSERT failure and verify complete rollback before successful retry.
- [x] Exercise a real renderer-service outage and visible recovery, followed by actual eForm PDF compatibility controls.
- [ ] Complete the installed locale/print matrix and concurrent renderer saturation/interruption/cleanup tests, including CLS lock latency.
- [ ] Resolve valid review findings and obtain green checks for the final proposed merge revision.

## Evidence and limits

The installed candidate revision and package/WAR hashes are recorded in the native
[manifest](../images/pr6102/native/manifest.json). The native gallery retains 260 PNGs
(216 browser screenshots and 44 downloaded endpoint PNGs) and 22 actual PDFs.
Together with the renderer gallery there are 306 browser screenshots and 143
before/after pairs. Supplemental unlink provenance and eForm controls have no
baseline pair. Fixtures forward actual production response bytes; screenshots are
not edited or composited.

At `a7b22e9b63f`, the clean package passes 84 selected Java cases and a separate
137-case upload/action/real-PDF retake passes: 221 selected cases in total, zero
failures, errors or skips. All 995 JSP compilations and WAR API/dependency checks
pass. Both installed deployment checks identify that revision and pass. It
includes actual PNG/JPEG rendering, real eForm PDFs, context/action validation,
source literal overlays and transactional unlink tests. Native startup exposed two
production audit DAO beans; the constructor qualifier was fixed and a two-bean
Spring wiring regression added before these installed candidate captures.

Full Java/JSP verification at `0ede7b1b0e9` passed 34,509 cases with zero failures
or errors and 143 explicit skips. The post-release-merge local attempt ran 34,565
cases: four child-process deadline failures, zero errors, 143 skips. All three
affected classes passed unchanged in an isolated 71-case retake (zero skips).
This local full attempt is not recorded as green. The `tests` CI job at
`0866965e803` passed; that head has 45 successful checks, seven skips and two
Sonar reliability failures. The four reported reliability findings are fixed at
`a7b22e9b63f`; final-head CI remains required. No gate is weakened.

The post-merge Node run has 7,707 cases: 7,703 pass, zero fail and four optional
CLI-source skips. With the pinned CLI source supplied, the three affected contract
files pass all 43 cases without skips; these overlap the full run and are not
added to its total. Python 3.14 release tooling passes all 356 cases. Locale key
parity and ASCII bundle-escape checks pass.

All nine native candidate workflows pass in each province at `a7b22e9b63f`,
including the subsequent spreadsheet-export and deployed JSTL/EL controls. All
41 existing CodeRabbit threads are resolved. The latest review request was rate
limited, so a fresh review of the newest changes is still outstanding.

The native ON version-retake observes the unresolved correction defect before its
legacy browser workflow times out; it is not a passing version suite. The BC
retake times out before that observation and supplies no ordering qualification.
The screenshot gallery does not establish canonical ordering or historical repair.

## Compatibility and implementation notes

- Regex selection still uses the existing server-locale lowercase shadow; extraction
  maps exact boundaries back to original source case. Ambiguous Unicode expansion
  splits are refused rather than altering the clinical literal.
- Source overlays are read-only, preserve clinician-edited projections, and visibly
  explain unprojected or multiple observations. Source-read errors remain explicit.
- Unlink requires the displayed `matchVersion` on POST. Pages open before upgrade
  must reload; missing/stale versions return explicit conflicts. Original source
  extensions are retained with an `UNLINK` archival marker.
- Unlink auditing persists through the transactional DAO so a late audit failure
  rolls back clinical writes. Independent provider access remains and reopens;
  obsolete automatic MRP access is removed.
- Source coordination takes negative keys in the existing signed lock table before
  positive report locks. Collisions serialize unrelated work without establishing
  clinical membership. CLS deliberately uses a source-wide key because legacy
  accession lookup can produce overlapping chains; throughput is not qualified.

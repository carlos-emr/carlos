# Measurement and chart integrity acceptance

Target: release/2026.08. Baseline: 9004536dc8314d5f50542b48905ca3ca4099a916.
This register distinguishes implementation from qualification. An unchecked requirement
is not complete and must not be used to close a ticket. Only synthetic data may be
used for retained browser/database evidence.

| Tickets | Required outcome | Implementation / qualification |
| --- | --- | --- |
| #5814, #5854 | Visible single point, useful domain, distinct ticks; constant values in the millions render | Chart changes and Chromium regression implemented; native ON/BC qualification pending |
| #5827 | Bounds, titres, dates and qualitative grades never become exact numeric points | Strict parser and tests implemented; visible exclusion band implemented; installed workflows pending |
| #6091 | Java HALF_EVEN point labels without altering stored/plotted values | Server-formatted point labels and regression implemented; installed print parity pending |
| #6092 | Server locale/date/number conventions preserved | Locale metadata and localized labels implemented; full locale parity qualification pending |
| #3935 | Session-private, bounded, expiring per-form definitions; explicit invalid/stale outcome | Per-form contexts and regressions implemented; native two-tab workflow pending |
| #5820, #5819 | Source-aware current selection and atomic active-measurement projection | Not implemented |
| #5835 | Atomic complete-chain unlink, provider reconciliation, stale retry protection | Transactional archive/routing/audit, optimistic match version, shared ingestion lock and later-delivery unmatch fence implemented; physical/native concurrency qualification pending |
| #5828 | Literal preservation and visible filter-excluded source results | Case-preserving extraction and read-only source history/grid overlay implemented; installed ON/BC qualification pending |
| #5853 | Fair bounded renderer admission, typed failures, all consumers show failures | Shared two-browser admission, safe reason codes, error responses and embedded-image recovery implemented; installed load/fault qualification pending |
| #5472 | Verify existing #5518 replacements, packaging and documented differences | Existing implementation landed; final acceptance audit pending |

## Required qualification before merge

- [ ] Define full-report versus partial-correction contracts per affected source.
- [ ] Serialize ingestion, matching, unlinking and repair for the same clinical chain.
- [ ] Distinguish deliberate clinician deletions from supersession/unlink/repair archival.
- [ ] Provide fingerprinted, idempotent, dry-run-first repair for unambiguous legacy damage.
- [ ] Pass focused and complete Java/Node suites; report every skip separately.
- [ ] Compile JSPs with Tomcat 11 Jasper and check the built WAR's dependency/API contents.
- [ ] Run native lab versions, qualified results, measurement history/geometry and CDM workflows on installed ON and BC packages.
- [ ] Verify actual image/PDF contents independently of production calculations.
- [ ] Exercise cross-patient controls, patient locks, authorization, unchanged reads and transaction failures.
- [ ] Exercise renderer saturation, interruption, outage, cleanup and recovery alongside eForm operations.
- [ ] Resolve valid review findings and qualify the final proposed merge revision.

## Evidence recorded so far

- Chromium, actual bundled chart scripts: 21 reference fixtures plus constant-value
  boundary cases pass. The single-point exception deliberately asserts a useful
  date span, distinct value ticks and actual marker pixels, replacing the legacy
  invisible point / 2ms domain. Unaffected reference recordings remain unchanged.
- Complete Node suite: 7,701 cases; 7,697 pass, zero failures, four explicit skips.
- JDK 25 / Maven 3.9.11: 114 focused tests pass without skips, including actual
  renderer admission, failure classification, exact-number parsing, label formatting,
  context selection, expiry, cross-session isolation and populated POST validation.
- Initial full Java/JSP attempt: 34,444 tests, zero assertion failures, seven errors
  from missing Node in the test container, 147 explicit skips. Node was installed;
  the affected CPP metadata class passes in the focused retake. This is not a
  successful final full-suite result.
- Python 3.14 release tooling: all 356 tests pass. The chart fixture source hash
  was updated after a real observed run with 1,790 assertions; its pre-existing
  minimum assertion threshold remains unchanged.
- No installed-package, physical database, repair or full-release acceptance claim.

## Additional retakes

- Exact package-pinned Chromium/ChromeDriver 154.0.8025.0, SHA-256 verified:
  38 Java cases pass with zero skips, including actual PNG/JPEG generation and
  three real eForm PDF browser smoke tests, bounded numeric parsing and renderer readiness.
- The subsequent full Java/JSP run executed 34,453 tests: zero errors, five
  failures, 147 explicit skips. All five failures were old readiness-warning
  assertions; the eight-case readiness class passes after updating the required
  warning to include measurement graphs. A final all-green full-suite/CI result
  remains a separate requirement.
- Final complete Node run: 7,697 pass, zero fail, four skip. The pinned carlos-ctl
  source was then supplied to the two optional contract files: 31 pass, no skips.
- The screenshot gallery records 90 images (45 pairs), source/configuration hashes,
  actual browser version, synthetic fixtures and the deliberately limited renderer scope.
- Raw numeric parsing has a 512-character bound before grammar validation and
  excludes nonzero results that underflow to zero.

## Subsequent lab integrity changes (qualification in progress)

- All 226 focused cases pass with no skips, including strict numeric syntax,
  actual existing HL7 upload/duplicate/rollback regressions and the new unlink
  transaction tests. Injected failures in archival, extensions, measurement
  removal, patient routing, provider reconciliation and audit all roll back the
  transaction. These fault tests use real H2 transaction advice and instrumented
  DAO writes; they do not substitute for physical clinical-row/native qualification.
- An additional 20-case persisted-row/access/DAO run passes without skips. Real
  H2 clinical rows prove successful complete-chain unlink, extension retention,
  stale retry refusal and rollback after an actual late audit INSERT. The new
  patient-routing locking read refreshes a cached match and keeps lab namespaces
  separate. This is still distinct from the physical MariaDB/native workflow tier.
- Numeric syntax now uses a bounded linear character parser (no regex), and
  browser admission uses a private monitor, addressing the subsequent security
  scan findings without suppressing them.
- The measurement filter remains unchanged. Case-insensitive matching extracts
  the original literal. Read-only lab history discovers reports through patient
  routing; the grid overlays exact source literals and visibly explains results
  without active measurements or multiple source observations. It never writes
  clinical projections. Unreadable source data produces an explicit failure.
- Unlink now requires the displayed `matchVersion` on its existing POST endpoint.
  Missing/duplicate fields are refused; a stale fingerprint returns 409. Existing
  open pages from before an upgrade must reload before unlinking. The browser
  handles every non-success outcome explicitly. Patient and record locks are
  checked before clinical writes. Archive extensions retain the original source
  references and carry a `lab_projection_archive=UNLINK` marker.
- Source/accession coordination uses negative ids in the existing signed
  `providerLabRoutingLock` table, before positive report locks. Hash collisions
  only serialize unrelated chains; they never establish clinical membership.
  CLS uses a source-wide key because its legacy glucose-tolerance chain can
  span accessions. An explicit unmatch fence prevents later deliveries from
  automatically restoring that wrong-patient match. Ambiguous current clinical
  ordering, feed correction contracts and historical repair remain separate,
  unchecked requirements above.
- CLS source-wide serialization is deliberate: the legacy accession/filler-order
  lookup can produce overlapping chains, so narrowing to one filler number is
  not yet proven safe. Database lock failures are logged by class without source
  or patient data and propagate as failures. Large-CLS transaction/wait latency
  qualification remains a merge gate; no throughput claim is made.
- Full Java/JSP verification at 0ede7b1b0e9 succeeds: 34,509 cases, zero failures
  or errors, 143 explicit skips. The exact pinned browser was enabled. Subsequent
  review corrections require their own focused checks and current-head CI.
- CodeRabbit review retake: 93 focused cases pass without skips, including real
  PNG/JPEG and eForm PDF rendering, day-precision HL7 chart data, exclusion-count
  accumulation, source/projection display controls, POST authorization, CDM
  binding and transaction faults. The review changes localize warnings/retry
  controls, retain healthy grid rows under an explicit source-load failure, and
  distinguish internal I/O defects from defined retryable availability failures.

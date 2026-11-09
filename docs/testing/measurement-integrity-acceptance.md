# Measurement and chart integrity acceptance

Target: `release/2026.08`; release base `74f240a4b9f92188300a8700cf11f1d74bc2b58d`.
Qualified runtime: `989f0eb94a529c2cdb30f3c6b93e8b0ca2062389`.
All retained clinical fixtures are synthetic and owned by the tests.

[Installed ON/BC gallery: 126 Playwright pairs](measurement-native-screenshots.md) ·
[Renderer gallery: 45 pairs](measurement-chart-screenshots.md) ·
[Artifact, package and helper hashes](../images/pr6102/native/manifest.json) ·
[Live PR checks](https://github.com/carlos-emr/carlos/pull/6102/checks)

## Ticket outcomes

| Tickets | Required outcome | Implementation and qualification |
| --- | --- | --- |
| #5814, #5854 | Visible single point, useful domain, distinct ticks; large constants render | Eight installed geometry cases pass in each province, including zero, ±0.05 and constants through 45 million. Actual PNGs and paired screenshots are retained. |
| #5827 | Bounds, titres, dates and qualitative grades never become exact numeric points | Bounded exact-number parsing and visible exclusion counts. Both provinces pass literal history/grid/PDF checks, independent point geometry, numeric controls, cross-patient isolation and unchanged-read assertions. |
| #6091 | Java HALF_EVEN point labels without changing stored/plotted values | Real-browser boundaries and five native server-locale PNG/PDF profiles pass. Nine renderer component/script files are byte-identical between the earlier renderer gallery candidate and the final qualified WAR. |
| #6092 | Preserve server locale/date/number conventions | Native en-CA, fr-CA, es-ES, pl-PL and pt-BR rendering/print controls pass in both provinces. Source/clinical warnings retain their meaning in all supported translations. |
| #3935 | Bounded, expiring, session-private definitions per rendered CDM form | Deep serializable snapshots, actual selector validation and explicit stale/invalid outcomes. All three reports retain the originating tab's type and count in both installed provinces; session/expiry/capacity/serialization guards pass. |
| #5819, #5820 | Source-aware current selection and atomic active projections | CML uses stable observation identities and clinical revision times, independent of result count or delivery order. Correction/older/replay installed workflows pass; original review states and raw versions remain intact. Twenty-eight real-DAO integration cases cover partial reports, explicit withdrawal, ambiguity, clinician protection, stale repair, rollback and concurrency. |
| #5835 | Atomic confirmed-chain unlink, provider reconciliation and stale protection | Both provinces pass real MariaDB late-audit rollback/retry, stale 409, provenance retention, automatic/independent provider controls and the later-delivery fence. Physical unlink/import contention passes. |
| #5828 | Preserve source literals and show filter-excluded results | Case-preserving extraction retains the existing filter semantics. Read-only source history/grid overlays retain excluded and multiple readings, including all stored readings sharing a report. Actual source PDFs and unchanged-read controls pass. History loading reads sources once per automatic request. |
| #5853 | Bounded fair renderer admission and visible failures | Two shared browser slots, bounded queues, explicit typed failures, warning/retry and cached-image recovery. Installed outage/recovery, mixed actual PNG/PDF renders, PDF fairness, queue overflow, interruption, permit/session cleanup and subsequent eForm PDF controls pass. |
| #5472 | Verify the existing library replacements and packaging | #5518 is already in the release baseline. Both installed provinces pass actual XLSX/CSV heading/alias/type/literal checks, JSTL/EL, chart and saved eForm PDF controls. The final WAR has 253 jars, required JSTL and no bundled container-provided API. |

## Clinical reconciliation contract

CML reconciles each explicit observation using panel/code namespaces and its
sub-identifier. Final/corrected/withdrawn revisions outrank preliminary results;
within that stage clinical revision time applies. A transport/replay MSH time is
never invented as a clinical revision. Conflicting content at the latest clinical
revision requires review. A newer unambiguous explicit correction resolves older conflicting content regardless of
arrival order. An omitted or blank result leaves the prior explicit observation intact;
only an explicit D withdraws it. Other source handlers retain their existing
version/projection policies.

Clinician changes and deliberate deletions protect the affected observation;
unrelated safe observations can still reconcile. Unknown historical deletion
intent remains protected and appears as a manual-review outcome. Repair never
assumes that an unmarked archive was superseded. Raw source, patient/provider
review state and source provenance remain retained. Acknowledging or filing a
partial correction cannot automatically file an older source containing omitted
observations. Only fully covered older reports are eligible; prior acknowledgments
retain their audit data. An unresolved conflict blocks automatic chain review
without changing any routing rows and shows a visible recovery message.

Missing/unusable revision times keep explicit raw reports, inbox counts, PDFs and
attachments readable. Their views explain the review requirement; no arbitrary
clinical ordering is used for a projection or repair. Incomplete source patient
identity never infers peer membership or automatic projection; the explicitly
selected raw report remains accessible and can be unlinked with authorization.

Repair preview is read-only. Apply recomputes the exact fingerprint under the
same coordination as import, matching, unlink and manual deletion. A changed plan
returns 409. A fresh no-op plan makes no clinical/audit changes. A failed late
audit or archival write rolls back the transaction. Archive/origin keys fit the
physical 20-character extension-key column, and legacy full/truncated aliases
remain readable.

## Completed qualification

- [x] Implement canonical CML selection/projection and conservative historical repair.
- [x] Distinguish clinician deletion, supersession, repair and unlink provenance.
- [x] Exercise read-only preview, missing projections, clinician protection, stale/no-op behavior and late write rollback with real DAOs.
- [x] Prove actual repair/import and unlink/import transactions wait on the same physical MariaDB coordination row; verify the final clinical state.
- [x] Measure controlled CLS contention while an unrelated CML upload completes.
- [x] Verify partial-review coverage, blocked conflicting acknowledgment and recovery through a newer explicit correction with actual native upload/review controls.
- [x] Preserve raw HTML/PDF access and unlink when clinical time is unavailable.
- [x] Verify actual graph/PDF contents, independent geometry, numeric controls, other-patient isolation and unchanged clinical reads.
- [x] Compile all 996 JSPs; exercise actual EL 6 record-property resolution beyond compilation.
- [x] Build/install Ubuntu 26.04 packages and run all 18 installed checks in both provinces: 36 passes.
- [x] Verify 6,216 deployed classes, 253 jars and associated resources before and after tests: 7,070 artifact entries per province. Check the two intentional Debian property rewrites against exact package bytes.
- [x] Exercise renderer outage/retry, actual mixed work, bounded queues, PDF fairness, queued interruption and recovery with zero remaining sessions and both permits restored.
- [x] Exercise five server-locale PNG/PDF profiles, actual eForm PDF, spreadsheet/JSTL controls and unrestricted/boundary/empty range printing.
- [x] Retain extensive unmodified before/after screenshots, actual PNG/PDF bytes, workflow receipts and hashes.
- [x] Resolve valid review findings and request another CodeRabbit review. CodeRabbit acknowledged the latest command but subsequently rate limited analysis; no fresh approval is claimed.

Required CI remains a merge condition; consult the live PR checks for the current
head. All local/native qualification above is complete. Every branch commit has
Signed-off-by.

## Validation records and compatibility

The final focused package passes 771 Java cases with zero failures/errors/skips,
including 28 real-DAO CML projection cases, 43 acknowledgment cases, filing,
consultation and actual browser PNG/PDF smoke controls. The separate routing-audit
integration fixture also passes. The full Node suite passes 7,712 cases with four
existing optional skips. Final-head CI supplies the complete Java, database,
packaging and static-analysis checks.

The native gallery contains 290 browser screenshots, 64 additional raw/native
renderer PNGs and 44 actual PDFs. Together with the renderer gallery there are
380 browser screenshots, 171 Playwright before/after pairs and 444 PNGs overall.
The ten native server-locale PNG/PDF pairs are counted separately from Playwright
screenshots. The original 98 native pairs use revision `9004536dc831`; the 24 CML
pairs and supplemental locale controls explicitly use `a7b22e9b63f` as their
before revision. Four partial-review pairs use `a3934f88a44e` as their explicit
before revision and demonstrate the previously unread observation being filed.

The qualified WAR SHA-256 is
`0899c34796b1cf81283d3964b4e9b165d67a6604104e8afa9e43cf3f5a8b8ecf`;
the application `.deb` SHA-256 is
`351ec478b5243c15b929cbf54ad41ff921cd04daae527d834d40d0914dbaeb1a`.
The existing Debian version `2026.09.0~snapshot28` contains Maven application
version `2026.08.0-beta7-SNAPSHOT`; this PR targets `release/2026.08`.

Internal unlink forms require their displayed match version. Pages opened before
upgrade must reload; missing/stale versions produce an explicit conflict and
recovery path. Clinical literals, extraction configuration, access boundaries,
existing source-specific policies and PNG/JPEG/PDF/export formats are retained.

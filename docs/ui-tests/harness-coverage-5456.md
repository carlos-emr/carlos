# Harness coverage — PR #5456

Target: `release/2026.08`; refreshed base `2dd9fcbbfbcbba10f05ed8075cbb15b8438c39a9`.
The initial tests-only baseline was `b4c0dfc94e677514e6f64dd271358f0295270739`; its runtime evidence is identified separately below.
The checks below use owned synthetic data. A registered check, an expected product
failure, and a passing live workflow are different results.

## Review refresh — 2026-11-01 (America/Toronto)

Merge `690e2cb7925` incorporates the current release base. The tickler conflict
retains the release's shared search-draw and edit-refresh helpers, including
completed-response and HTTP-success checks, alongside this PR's diagnostics and
cancellation handling. There are still no net application changes under `src/`.

`11db11b51cc` fixes annotation-fixture retirement: a document routed to another
patient is retained with its PDF and recovery receipt, using the shared
transactional reference guard. File retirement uses the shared private quarantine
and identity check so a replacement cannot be unlinked. The refusal regression
fails against the preceding implementation. `7af7c699280` addresses both subsequent
Cubic comments: receipt removal is registered immediately at directory creation,
and the SQL mock tracks patient links independently. A subsequent transaction
failure control also verifies document/PDF/link/parent/receipt retention.

At `7af7c699280`, GitHub's Node suite passes 6,913 tests, with zero failures or
skips and one existing TODO. The focused annotation, file-retirement, stored-PDF,
tickler and appointment-JSP batch passes 36/36. The installed-schema MariaDB
negative control also passes: an owned cross-patient DOC route blocks deletion,
the document/link/PDF/receipt remain, and exact recovery restores the SQL counts
and document-store byte/metadata baseline.

The fresh packaged smoke suite passes 12/12, including tickler CRUD, on Chromium
154.0.8025.0. Its server-log window has no unexplained signatures (six known
signatures, seven deliberately provoked events).

The full native annotation retake exposed a response-capture race: Save-and-fax
can navigate before Chromium's response body is read. The fixture now captures
and records the durable API response before releasing it to the page; delayed
and capacity-retry POSTs fall through to that capture. Unconfirmed capture still
retains the fixtures. An additional timing fix observes the status region's CSS
only while its text is empty. Regressions preserve an incorrectly visible CSS
result as a failure. The expanded focused batch passes 48/48.

The final annotation browser retake passes every viewer assertion and completes
Save/fax-cover/cancel in 69.9 seconds. Its owned UI-only sender has polling
disabled; configuration/polling restore exactly and no fax jobs are created.
Fixture cleanup leaves no annotation recovery receipt. The initial failed run's
16 source-linked documents and 28 source/cache files were reconciled using the
private receipt, exact recorded hashes, creator/patient links and source graph;
only those verified fixtures and their patient were removed. The first retake's
status-timing failure remains a failed attempt, separate from the final pass.

The EMR and pinned DrugRef source compile successfully in an Ubuntu 26.04
container with JDK 25. Full Debian packages include the checksum-verified pinned
Chromium renderer. The application build is from `11db11b51cc`; the subsequent
test changes do not alter its application source. A separate Ubuntu 26.04 systemd
container installs all three packages with demo data, passes `carlos-ctl check`,
completes the mandatory first-login reset, and initializes the native vascular
dictionary. These are local packages, not published releases.

| Package | SHA-256 |
|---|---|
| EMR `2026.09.0~snapshot27` | `57ad0e1d85aa06adfba6aa84320e2327c4de3d1b817db5d774486541b9b64c5c` |
| DrugRef `2026.09.0~snapshot27` | `006036b8c5c3ca54ed6d337fb0e466bffb10cf49e800811a14a44b9ca69c332b` |
| CLI `1.3.0` | `e1238a34e7fc39ffa184c488a727abec889add1ffd656eb39505c5d01e5b7d2b` |

Fresh full and incremental CodeRabbit reviews were requested. The full request
was declined because the PR exceeds 300 files and the included review allowance
was exhausted; the incremental command was also declined. The historical
CodeRabbit reviews below are not a new review of this refresh. Both new Cubic
threads were fixed and resolved. Final CI and additional browser-run results are
reported separately in the PR conversation; the earlier broad runtime inventory
below remains historical evidence.

## Earlier build and execution evidence

The local EMR WAR is `2026.08.0-beta3-SNAPSHOT`, packaged by the release packaging
rules as `carlos-emr_2026.09.0~snapshot27_amd64.deb`. This is a local build, not a
published release. DrugRef is pinned at `0d240f07ee5c32464060dd7eecd98f317b6b3a08`;
the CLI package is `carlos-ctl_1.3.0_all.deb`. Packages were built and installed in
Ubuntu 26.04, with MariaDB 11.8 and Chromium 154.0.8025.0. Harness source is mounted
read-only into the validation container; each run records its separate result.

| Package | SHA-256 |
|---|---|
| EMR | `ad9924fe5a032090eb9ef7842e1cc21ecddd90b5755eeddaac6fc0b9fbe819f7` |
| DrugRef | `2887a231dd5fb191ad924111838b0b4263b87659073ac87fbd2485fc6de6662e` |
| CLI | `d6d1b56e4c070e262799933cdd4fe2b2f7f776cdf5a56fb3ceba3749f33c14e6` |

The current package compiles the refreshed release application: this PR has
no net changes under `src/`. Earlier exploratory packages with application fixes
are superseded and are not acceptance evidence for the affected workflows.
Published beta2 → this local package passed the full upgrade verifier (45 checks),
reopened all four UI-created clinical canaries, then passed 12 smoke and three
read-only core checks with an unchanged residue baseline. The real Secure-cookie
HTTP session proof passed against this package.

Full Node regressions pass in GitHub Actions at `c8df481ee5e` (6,899 passes,
zero failures/skips, one existing TODO); the final
commit must independently pass every required check. Focused final review
regressions pass 227/227 in the review-fix batch identified below, including
quoted diagnostic redaction, editor completion and real
child-process interruption. Registration
alone does not certify a live workflow.

## Earlier runtime run identities

The latest reviewed upgrade retake completed on 2026-11-01, using the refreshed
package above and harness source at `797ce6782038`: published beta2 → local beta3,
45 verifier assertions, all four retained clinical canaries (including reopening
the native appointment editor), smoke 12/12 and read-only core 3/3. The installation
residue baseline is unchanged. An earlier retake correctly failed an overly strict
truncated appointment-link assertion; that failed attempt is not counted as a pass.

Authenticated Secure-cookie HTTP GET/HEAD and accepted-write SIGINT/SIGTERM proofs
also pass: exit 130/143, structured interruption, successful cleanup and zero
SQL/file changes. These are real OS signals. The checkpoint interruption auditor
uses synchronous handler dispatch and is identified separately below.

The refreshed package was exercised across 66 hygiene identities, 91 coverage
identities and seven initial managed profiles, followed by focused retakes.
Those inventory counts include failures and missing prerequisites. Each default
check records its source commit, script hashes, independent residue result and
separate server-log audit. Long profile runs record their starting source commit;
they are not claimed to have run every later harness edit. Product failures retain
exact finding/step pins. Unexpected failures remain failures. A residue difference
stops a driver for exact owned-fixture reconciliation before further checks.

Six reviewed profile retakes completed: address-lock, no-PIN, CAISI administration,
appointment options and private consent exit 0; multisite exits 1 (six passes, one
consultation navigation failure after WAF 403 and one missing operator-membership
prerequisite). No-PIN includes two passes plus exact known finding 347. Profile exit
0 can include exact known failures and is not a claim that those product behaviors
passed. Every retake restores the configuration byte for byte. Five profile server
audits exit 0; private consent's restoration window has unclassified filter
`IllegalStateException` errors and its separate audit exits 1. An audit with no
journal lines exits 2 and is not certified clean.

Retakes pass responsive checks on eight surfaces at two resolutions, four locale
crawls, note-editor autosave, diagnosis/flowsheet, document metadata, role matrix,
fracture Paste, minor interactions, schedule deletion, messenger administration,
lab upload, APCache rendering, patient Add and clinical lab-requisition follow-up.
Earlier failures caused by harness timing or ownership gaps remain separate attempts.
An earlier full WAF corpus run had two failures (chart reload request aborts and
an eForm control's chart XHR 500). After awaiting native AJAX Save completion and
removing the extra reload, the focused chart-note/eForm retake passes both rows
with no residue and no browser diagnostics. A subsequent full 24-row run at
`70dd3d840b1` passes those rows and reaches one failure in Rx custom-drug Save:
the corpus persists exactly, but a background `/rx/updateDrugrefDB` fetch fails
with `net::ERR_FAILED`. The whole run reports failure; no blanket finding pin or
request exemption is used. Its residue and server audits both pass. The
two-patient suite reaches a Vascular Tracker
Save & Exit failure; role administration reaches a late iframe timeout. These are
not blanket-pinned or counted as successful certification. All final retakes cited
as clean independently pass the residue audit.

The final targeted retake passes minor interactions (server audit 0) and clinical
lab-requisition follow-up (server audit 2: no journal lines). Chart-history
access-control and print/group checks pass before a later native compact-save
invariant reports a change outside the selected editor state; the whole check
fails, its residue audit passes, and its server audit exits 1. The qualified
restricted-login schedule refusal is no longer the stopping point. This later
failure is not suppressed or presented as a complete chart-history pass.

Historical evidence uses the earlier tests-only application/package at
`b4c0dfc94e677514e6f64dd271358f0295270739`, EMR SHA-256
`437fc19cabcaf7fd3100f3555b900abce6b76000a9419eecb2a967640f17c3b8`.
It remains distinct from the refreshed package. Exploratory application-fix packages
are excluded entirely.

## Ticket map

| Ticket | Implementation and observed validation |
|---|---|
| #3317 | Browser URL and SQL-host guards use the shared contract. Static guard/regression checks pass. Private-LAN targets require the explicit non-local opt-in. |
| #4718 | `authenticated-probe` replays application cookies with redirects disabled and proves the server-side session through a heartbeat immediately before every read. It rejects invalidated sessions and foreign origins; the separate authentication-state transport supports intentional pre-login/facility/logout assertions. Real Playwright HTTP-transport regression tests pass for GET/HEAD with a Secure cookie, including logout invalidation. |
| #5451 | Prescription-signature checks retain the originating demographic context and separate disabled Print/Save assertions. The complete refreshed-package workflow passed with no residue. |
| #5378 | Patient-photo denial replays the allowed native manager request against the locked patient's actual identity, with unchanged SQL/blob checks and an allowed-patient control. Complete final-package workflow PASS, including upload/replace/refusal/clear, cross-patient and locked-patient controls; no residue. |
| #5262 | Native measurement editors save exact values/units/dates/provider. The subsequent native Refresh reproduces finding 338: missing drug/panel parameters. Cancelled-editor and post-refresh isolation assertions remain behind that product defect; no residue. |
| #5263 | Thirty-one independent cases seed their initial observations before opening the native drug card; CML fixtures include their real report parent. Initial clinical rendering and Refresh are separate assertions. Findings 338–340 record the Refresh, nonnumeric creatinine and infinite-creatinine defects. All 31 refreshed-package cases were exercised; passing initial-render cases and exact known failures remain separate results. |
| #5264 | Twelve independent new/saved monthly/yearly recurrence cases; inclusive boundaries, rejected range/retry and duplicate prevention. All twelve passed on the refreshed package with no residue. Future leap/common anchors and stable manifest IDs replace expiring absolute dates. |
| #5265–#5267 | `appointment-options-roundtrip`: critical/reason/MC add, unrelated edit, cancellation, change, clear, reopened values and unchanged control. The complete managed profile passed live after distinguishing an empty SQL value from a missing row; no residue and byte-exact configuration restoration. |
| #5268 | `appointment-email-option` drives actual checked/unchecked Add/Edit requests, cancellation and repeated saves. Finding 329 records the missing implementation. Appointment creation is never presented as successful reminder delivery. |
| #5269 | Fracture Paste for male/female patients, exact persisted note content, reopen/edit and other-patient/cancel controls. The complete refreshed-package workflow passed with no residue. |
| #5270 | LU-code native offered links, repeated append, exact drug/instructions, control card, second patient, Save Only, reopened Rx and nested print preview. The refreshed package reproduces finding 341 after independent persistence/reprint assertions pass, with no residue. |
| #5271 | Controlled local interaction responses exercise minor severity, all warning thresholds, fallback and partner removal. The complete refreshed-package workflow passed with no residue. |
| #5280 | Patient status AC/DE/MO/FI, dates/archive/search/report membership, reactivation and midwife assign/replace/clear/cancel. Complete final-package workflow PASS; no residue. |
| #5281 | Refreshed-package metadata/Inbox and Public visibility checks pass. Metadata assertions now inspect rendered fields as well as SQL. The strengthened clinical follow-up uses native Forward and a distinct owned clinician; it reproduces finding 348 because the actual Inbox description cell is empty. Later review/task/completion steps remain blocked by that defect. Earlier weaker full-follow-up passes are not substitutes for the strengthened check. |
| #4492 | Upgrade tooling, managed profiles, UI quality, missing workflow extensions and cancellation migrations; per-task status below. Not closed by registration alone. |
| #5177 | Harness repairs and stronger fixture/cleanup contracts are included. This is not a current-develop full-harness certification; the external/import prerequisites below remain. |
| #4102 | Blocked on this target: the release branch lacks the production SMS queue/settings code/schema and develop's first-consent race fix. Existing MariaDB CI/DAO tests are not claimed as the requested race reproductions. |
| #5445 | Explicitly targets develop's `EmailComposeWorkingDirectory` lifecycle. That production class/submission lifecycle is absent from this release. No dummy lifecycle or copied test is used to claim coverage. |

## #4492 task map

| Task | Coverage and status |
|---|---|
| 17 | `upgrade <published-tag>` verifies exact release asset names/digests, installs N-1, creates retained clinical canaries through the UI, upgrades, derives expected migrations from the target inventory/history difference, verifies PRE/POST, then runs smoke and read-only core. Published beta2 → final tests-only local beta3 package PASS: 45 verifier checks, four clinical canaries reopened, smoke 12/12 and read-only core 3/3. |
| 35–37 | Existing `lab-recall-delegate`, `clinic-address-admin`, `billing-on-report-center-claims` and `report-obec-file` retain their positive UI/storage/download assertions. |
| 38 | Existing appointment delete/billed-cancel, Unbill and eForm-delete checks cover dismiss/accept. Separate document/task confirmation variants reproduce missing prompts (findings 336/337), with no residue; existing accepted lifecycle checks remain independent. |
| 39 | Existing finding pins are retained; current-build runtime verification must be recorded independently, not inferred from old findings. |
| 40 | Automatic apply/restart/run/restore/restart profiles; byte-exact snapshots, real SIGINT/SIGTERM child cleanup tests and unexpected restart detection. Intentional restarts reset systemd's operator-start budget. |
| 41 | Address-lock live PASS: two real loopback source addresses, two accounts, scoped native Unlock and audit attribution, no residue. On the tests-only release-base `8def72af8ede` EMR package (SHA-256 `ad9924fe5a032090eb9ef7842e1cc21ecddd90b5755eeddaac6fc0b9fbe819f7`), no-PIN password and all 14 full-login assertions passed; MFA enablement reproduces finding 347 and later MFA steps are blocked. The managed-profile retake reports two passes and one exact known failure, with restoration and no residue. |
| 42 | Satellite-site administration, schedule visibility, Rx remembered/printed address and MOH file movement, plus native clinic-NBR editing/billing. The NBR positive requires existing active operator membership and never fabricates it. The reviewed multisite retake has six passes, a consultation navigation failure after a WAF 403, and a clinic-NBR prerequisite skip because active operator membership is absent. The profile reports failure; it is not certified complete. |
| 43 | Existing `demographic-relations` passed in the managed `legacy-contacts` profile, with no residue and configuration restoration. |
| 44 | The reviewed CAISI-only profile has six passes covering facility messages, expiry calendar, activity/population and CDS/MIS reports. Only Issue Editor initializes its native dictionary before the residue baseline. System Messages and Issue Editor reproduced findings 330/331; their later steps remain unverified. Maintainer decision about retaining CAISI client mode is outstanding. |
| 45 | Existing letter-template deletion, invoice logo, APCache approval/replay, measurement archive and planner permission/file assertions remain complementary coverage. |
| 46 | Existing CPP Copy/Position and pregnancy checks; new informed-consent and Forms-heading paging checks. Informed Consent reproduces finding 332 (missing confirmation) with no residue. Forms heading identifies all eleven independently dated fixture records and reproduces ignored ten-record pagination (finding 335), with no residue. |
| 47 | Responsive hit testing passed all eight surfaces at both resolutions; report-only accessibility passed all eight; locale crawls passed fr/es/pl/pt-BR. No patient HTML or accessible names are written to accessibility reports. |
| 48 | All database manifest entries now reach a signal-aware entry point. Legacy checks await accepted work, checkpoint within protected scopes, preserve original catch/finally cleanup and disable Playwright's competing signal handlers. Static AST and real child-process SIGINT/SIGTERM tests pass. The live interruption auditor requires exit 130, an interrupted structured result, successful cleanup, no SQL residue, and unchanged file bytes/type/ownership/mode in configured stores and legacy scratch prefixes. Its read-only inventories resolve configured store roots and do not follow entry symlinks or expose names/content in summaries; a skip is BLOCKED. The historical b4c-source midpoint SIGINT inventory completed: 65 PASS with no SQL or file changes; three BLOCKED (corpus, imported o19 data, fresh-install initial password reset). Annotation-copy, native default-property, uploaded-image/scratch and schedule-wizard leaks are repaired and their interrupted reruns pass. The schedule wizard owns a throwaway provider and verifies its exact range/template/creator before cleanup. Real SIGINT/SIGTERM cleanup and configuration restoration are also exercised by child-process regressions and the refreshed-package accepted-write proof. Current checkpoint dispatch is handler injection, not a second real-OS-signal inventory. This is not a claim that all 65 historical cases ran against final harness source or were separately interrupted with SIGTERM. |

## Preserved prerequisites

#5177 H45 explicitly forbids inventing SMS tables, imported-data fixtures or live
fax credentials. The following are not passing workflows: imported-o19 checks
without a real imported database and break-glass credentials; PathNet without its
configured fixture; corpus soak without its corpus; and live fax loopback/inbox/
dedup without authorized SRFax credentials. No live fax or patient email is sent.

Strict server-log accounting remains request/fault scoped. Database residue is
checked separately from append-only audit growth; neither is described as a
blanket clean server-log certification. Old-source #5067/develop findings are
not presented as regressions in this release build.

## Review and cleanup evidence

CodeRabbit completed its final requested review at `c8df481ee5e`. Its two inline
findings and two outside-diff findings are addressed: clearer wording, preserved
cancellation identity, TLS for shared credential configuration and health checks,
and awaiting the native chart's AJAX editor replacement before the next save.
The extra chart reload is removed; all request failures stay strict.

The final focused batch passes 227/227. Its files are `legacy-check.test.js`,
`playwright-harness.test.js`, `base-url-credentials.test.js`,
`clinical-standalone-browser-diagnostics.test.js`,
`waf-clinical-text-corpus.test.js`, `waf-chart-save.test.js` and
`owned-chart-page-cleanup.test.js`. This batch tests the source in this revision;
it is separate from the earlier overlapping 139-test and 213-test batches at
`76c31d21b32` and `26f6990de09`. Counts apply only to their listed test sets and
are not added together. It includes real SIGINT/SIGTERM
structured interruption, remote HTTP refusal before reading credentials, and
waiting for the full Save response and old/new editor transition. HTTP 403/409/500
responses return to the row reporter without waiting for a successful editor
replacement, preserving refusal diagnostics. The focused
installed-package chart/eForm retake separately verifies that transition live.

Valid findings from CodeRabbit and Cubic are addressed with targeted regressions;
each thread is resolved only after its disposition is recorded. The final PR
status determines CI and unresolved-thread completion.

Cleanup guards cover accepted-but-unconfirmed writes, asynchronous installer
process groups, retries after SQL cleanup failure, preferences created on first provider use,
quick-list SQL NULLs, exact program references, and atomic document/link retirement.
A real MariaDB interleaving proof inserts a foreign string-valued document reference
after preflight and confirms that the transaction preserves the document, links and
file. File retirement uses a private quarantine with inode/byte checks and refuses
to unlink a replacement. Annotation-response ambiguity retains fixtures for exact
reconciliation rather than claiming successful cleanup. Synthetic fixture residue
found during exploratory attempts was reconciled with exact identity/hash guards;
no full clinical database backup or fabricated application schema is required.

The four tickets left open are #4102, #5445, #4492 and #5177 for the target-branch,
maintainer-decision and real-environment prerequisites stated above. The remaining
16 tickets concern implemented coverage; exact product findings do not imply the
release product was changed or that the corresponding behavior passed.

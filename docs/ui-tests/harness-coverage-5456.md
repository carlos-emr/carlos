# Harness coverage — PR #5456

Target: `release/2026.08`; refreshed base `8def72af8ede728c4f0a3fca9f6066737a625420`.
The initial tests-only baseline was `b4c0dfc94e677514e6f64dd271358f0295270739`; its runtime evidence is identified separately below.
The checks below use owned synthetic data. A registered check, an expected product
failure, and a passing live workflow are different results.

## Build and execution

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

An earlier complete local Node run passed 6,817 tests with zero failures or skips and
one existing TODO. Subsequent focused regressions exercise the schedule ownership
guards, root URL adapter, signature request identity and narrowly deferred native
LU errors. Final CI runs the entire suite against the final commit. Registration
alone does not certify a live workflow.

## Runtime run identities

The refreshed package above passed the complete beta2 upgrade on 2026-11-01:
45 verifier assertions, all four retained clinical canaries, smoke 12/12 and
read-only core 3/3 with an unchanged installation baseline. Its authenticated
Secure-cookie HTTP transport and accepted-write SIGINT/SIGTERM proofs also pass:
exit 130/143, structured interrupted result, successful cleanup and zero SQL/file
changes. A modern browser remains alive long enough to drain the accepted write.

The earlier tests-only package at `b4c0dfc94e6` had EMR SHA-256
`437fc19cabcaf7fd3100f3555b900abce6b76000a9419eecb2a967640f17c3b8`.
The detailed workflow observations below initially refer to that run; they are
not inferred to be refreshed-package passes. The refreshed-package 66-identity
hygiene run, 91-identity coverage run and seven managed profiles are in progress.
Each check records its source commit, script hashes, independent residue audit
and separate server-log audit. Known product failures retain exact finding/step
pins and are reported separately from passing workflows. A residue difference
stops the run for investigation.

## Ticket map

| Ticket | Implementation and observed validation |
|---|---|
| #3317 | Browser URL and SQL-host guards use the shared contract. Static guard/regression checks pass. Private-LAN targets require the explicit non-local opt-in. |
| #4718 | `authenticated-probe` replays application cookies with redirects disabled and proves the server-side session through a heartbeat immediately before every read. It rejects invalidated sessions and foreign origins; the separate authentication-state transport supports intentional pre-login/facility/logout assertions. Real Playwright HTTP-transport regression tests pass for GET/HEAD with a Secure cookie, including logout invalidation. |
| #5451 | Prescription-signature checks retain the originating demographic context and separate disabled Print/Save assertions. The complete initial tests-only package retest passed; refreshed-package rerun is queued. |
| #5378 | Patient-photo denial replays the allowed native manager request against the locked patient's actual identity, with unchanged SQL/blob checks and an allowed-patient control. Complete final-package workflow PASS, including upload/replace/refusal/clear, cross-patient and locked-patient controls; no residue. |
| #5262 | Native measurement editors save exact values/units/dates/provider. The subsequent native Refresh reproduces finding 338: missing drug/panel parameters. Cancelled-editor and post-refresh isolation assertions remain behind that product defect; no residue. |
| #5263 | Thirty-one independent cases seed their initial observations before opening the native drug card; CML fixtures include their real report parent. Initial clinical rendering and Refresh are separate assertions. Findings 338–340 record the Refresh, nonnumeric creatinine and infinite-creatinine defects. Final per-case retakes remain separate. |
| #5264 | Twelve independent new/saved monthly/yearly recurrence cases; inclusive boundaries, rejected range/retry and duplicate prevention. All twelve passed on the initial tests-only package. Future leap/common anchors and stable manifest IDs replace expiring absolute dates; the refreshed-package rerun is queued. |
| #5265–#5267 | `appointment-options-roundtrip`: critical/reason/MC add, unrelated edit, cancellation, change, clear, reopened values and unchanged control. The complete managed profile passed live after distinguishing an empty SQL value from a missing row; no residue and byte-exact configuration restoration. |
| #5268 | `appointment-email-option` drives actual checked/unchecked Add/Edit requests, cancellation and repeated saves. Finding 329 records the missing implementation. Appointment creation is never presented as successful reminder delivery. |
| #5269 | Fracture Paste for male/female patients, exact persisted note content, reopen/edit and other-patient/cancel controls. The initial tests-only package retest passed; refreshed-package rerun is queued. |
| #5270 | LU-code native offered links, repeated append, exact drug/instructions, control card, second patient, Save Only, reopened Rx and nested print preview. The initial tests-only package reproduces finding 341 after independent persistence/reprint assertions pass; refreshed-package rerun is queued. |
| #5271 | Controlled local interaction responses exercise minor severity, all warning thresholds, fallback and partner removal. The initial tests-only package retest passed; refreshed-package rerun is queued. |
| #5280 | Patient status AC/DE/MO/FI, dates/archive/search/report membership, reactivation and midwife assign/replace/clear/cancel. Complete final-package workflow PASS; no residue. |
| #5281 | Independent metadata/Inbox, Public visibility and clinical-follow-up checks passed live before the reviewer was strengthened to a distinct owned clinician. The current follow-up uses native Forward, that clinician’s fresh login and attribution, review/comment/acknowledgement, linked task, clinical note, cancelled/final completion and unchanged controls; the complete distinct-reviewer retest passed on the initial tests-only package; refreshed-package rerun is queued. |
| #4492 | Upgrade tooling, managed profiles, UI quality, missing workflow extensions and cancellation migrations; per-task status below. Not closed by registration alone. |
| #5177 | Harness repairs and stronger fixture/cleanup contracts are included. This is not a current-develop full-harness certification; the external/import prerequisites below remain. |
| #4102 | Blocked on this target: the release branch lacks the production SMS queue/settings code and schema required by two requested race tests. Existing MariaDB CI/DAO tests are not claimed as those reproductions. |
| #5445 | Explicitly targets develop's `EmailComposeWorkingDirectory` lifecycle. That production class/submission lifecycle is absent from this release. No dummy lifecycle or copied test is used to claim coverage. |

## #4492 task map

| Task | Coverage and status |
|---|---|
| 17 | `upgrade <published-tag>` verifies exact release asset names/digests, installs N-1, creates retained clinical canaries through the UI, upgrades, derives expected migrations from the target inventory/history difference, verifies PRE/POST, then runs smoke and read-only core. Published beta2 → final tests-only local beta3 package PASS: 45 verifier checks, four clinical canaries reopened, smoke 12/12 and read-only core 3/3. |
| 35–37 | Existing `lab-recall-delegate`, `clinic-address-admin`, `billing-on-report-center-claims` and `report-obec-file` retain their positive UI/storage/download assertions. |
| 38 | Existing appointment delete/billed-cancel, Unbill and eForm-delete checks cover dismiss/accept. Separate document/task confirmation variants reproduce missing prompts (findings 336/337), with no residue; existing accepted lifecycle checks remain independent. |
| 39 | Existing finding pins are retained; current-build runtime verification must be recorded independently, not inferred from old findings. |
| 40 | Automatic apply/restart/run/restore/restart profiles; byte-exact snapshots, real SIGINT/SIGTERM child cleanup tests and unexpected restart detection. Intentional restarts reset systemd's operator-start budget. |
| 41 | Address-lock live PASS: two real loopback source addresses, two accounts, scoped native Unlock and audit attribution, no residue. Current-package no-PIN password and all 14 full-login assertions passed; MFA enablement reproduces finding 347 and later MFA steps are blocked. The managed-profile retake reports two passes and one exact known failure, with restoration and no residue. |
| 42 | Satellite-site administration, schedule visibility, Rx remembered/printed address and MOH file movement, plus native clinic-NBR editing/billing. The NBR positive requires existing active operator membership and never fabricates it. Native clinic-NBR profile PASS against the final package, no residue; complete multisite profile result remains separate. |
| 43 | Existing `demographic-relations` passed in the managed `legacy-contacts` profile, with no residue and configuration restoration. |
| 44 | The CAISI-only module profile passed facility messages, expiry calendar, activity/population and CDS/MIS report checks. Native dictionary initialization precedes the residue baseline. System Messages and Issue Editor reproduced findings 330/331; their later steps remain unverified. Maintainer decision about retaining CAISI client mode is outstanding. |
| 45 | Existing letter-template deletion, invoice logo, APCache approval/replay, measurement archive and planner permission/file assertions remain complementary coverage. |
| 46 | Existing CPP Copy/Position and pregnancy checks; new informed-consent and Forms-heading paging checks. Informed Consent reproduces finding 332 (missing confirmation) with no residue. Forms heading identifies all eleven independently dated fixture records and reproduces ignored ten-record pagination (finding 335), with no residue. |
| 47 | Responsive hit testing passed all eight surfaces at both resolutions; report-only accessibility passed all eight; locale crawls passed fr/es/pl/pt-BR. No patient HTML or accessible names are written to accessibility reports. |
| 48 | All database manifest entries now reach a signal-aware entry point. Legacy checks await accepted work, checkpoint within protected scopes, preserve original catch/finally cleanup and disable Playwright's competing signal handlers. Static AST and real child-process SIGINT/SIGTERM tests pass. The live interruption auditor requires exit 130, an interrupted structured result, successful cleanup, no SQL residue, and unchanged file bytes/type/ownership/mode in configured stores and legacy scratch prefixes. Its read-only inventories resolve configured store roots and do not follow entry symlinks or expose names/content in summaries; a skip is BLOCKED. The live midpoint SIGINT inventory completed: 65 PASS with no SQL or file changes; three BLOCKED (corpus, imported o19 data, fresh-install initial password reset). Annotation-copy, native default-property, uploaded-image/scratch and schedule-wizard leaks are repaired and their interrupted reruns pass. The schedule wizard owns a throwaway provider and verifies its exact range/template/creator before cleanup. Real SIGTERM cleanup and configuration restoration are also exercised by child-process regressions; this is not a claim that all 65 live cases were separately interrupted with SIGTERM. |

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

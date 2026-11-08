# Consolidated test-ticket acceptance

Target: `release/2026.08`. This is the working acceptance register for the single
follow-up PR. No ticket closes merely because it appears here or because unit
tests pass. The source issue remains the authoritative full acceptance contract.
Product defects must remain visible; expected failures, skipped prerequisites,
missing cases, failed cleanup and incomplete receipts cannot become release passes.

## Ticket inventory

| Ticket | Scope | Status |
| --- | --- | --- |
| [#4102](https://github.com/carlos-emr/carlos/issues/4102) | test: run the locking and race tests against a real MariaDB in CI | Implementation and installed acceptance pending. |
| [#4492](https://github.com/carlos-emr/carlos/issues/4492) | test(scripts): Playwright coverage plan, upgrade-path check, remaining Ontario checks, configuration profiles and hardening (plan Tasks 17 and 35–48) | Implementation and installed acceptance pending. |
| [#5177](https://github.com/carlos-emr/carlos/issues/5177) | Consolidated validation-harness findings and packaged retakes | Outstanding original source acceptance and complete ON/BC repetitions remain pending. |
| [#5700](https://github.com/carlos-emr/carlos/issues/5700) | Test: Release 2026.08 full verification coverage plan (Java, Node, Playwright) | Existing merged tooling retained. Scope ownership, compatibility decisions, installed execution and vendor prerequisites remain open. |
| [#5707](https://github.com/carlos-emr/carlos/issues/5707) | Test: cover successful key-pair creation through the administration UI | Native creation/download/signature/reopen/duplicate lifecycle passes on the installed ON package with a temporary synthetic primary prerequisite, whose removal restores its original absence; strict missing-prerequisite failure retained. Both ON and BC focused installed retakes pass; final full qualification remains pending. |
| [#5708](https://github.com/carlos-emr/carlos/issues/5708) | Test: cover multi-recipient lab-forwarding rule updates and selective removal | Installed ON two-recipient/reopen/repeat-save, three-upload routing, cancellation, selective removal and admin isolation pass. Status is source-wide; per-recipient status is unavailable. Original method/CSRF coverage retained. |
| [#5709](https://github.com/carlos-emr/carlos/issues/5709) | Test: broaden lookup-list CRUD coverage and verify consumer/list isolation | Actual list/consumer/operation inventory documented. ON/BC three-list native adds, duplicate/empty behavior, exact ordering/identity, all consumer save/reopen and selective isolation pass. Full removal fails correctly on provider inactive options (#5816); GET still returns 200 and changes order (existing finding 58/#3059). Independent native cache restoration and exact SQL cleanup pass after both failures. PHU is hidden in ON and absent in BC; no positive edit/reorder UI exists. |
| [#5715](https://github.com/carlos-emr/carlos/issues/5715) | Test: verify clinical-record preservation through populated patient merge and unmerge | Implementation and installed acceptance pending. |
| [#5716](https://github.com/carlos-emr/carlos/issues/5716) | Test: verify measurement graph dates, values and plotted positions | Installed ON/BC five-point irregular-date geometry, independent rendered-axis calibration, three-pixel tolerance, native selected/multi-delete, clinical isolation and four sensitivity controls pass. Empty history hides Plot; single-point PNG output is observed, with its invisible point/indistinguishable ticks separately tracked as #5814. No date-range controls exist. |
| [#5717](https://github.com/carlos-emr/carlos/issues/5717) | Test: validate growth-chart point placement and reference curves independently | Implementation and installed acceptance pending. |
| [#5718](https://github.com/carlos-emr/carlos/issues/5718) | Test: broaden decision-support guideline condition and operator coverage | 41 independently specified rules reach all 205 ON chart/list/detail observations, including expanded warnings, native diagnosis resolution, alternating patients, provider scope and unchanged clinical snapshots. Exactly 40 mismatches remain: sex all/notall across four surfaces on five visits (#5817). Other selected operator cases and zero-residue cleanup pass; no pin. Inventory records unsupported/dynamic-context families and missing actual-value readers. |
| [#5719](https://github.com/carlos-emr/carlos/issues/5719) | Test: cover Ontario RA re-import, settlement replay and subsequent adjustments | Implementation and installed acceptance pending. |
| [#5724](https://github.com/carlos-emr/carlos/issues/5724) | Test: cover OAuth access-token expiry and deletion of a client with issued tokens | Native consent, exact expiry-second semantics, client deletion and independent token/session controls pass on installed ON and BC with no clinical writes or residue. |
| [#5725](https://github.com/carlos-emr/carlos/issues/5725) | Test: verify Report-by-Template results and exports remain isolated across tabs | Installed ON/BC four-tab, shared-login native runs, exact CSV/XLS schemas/values/types, empty results, Back beacons and tab closure pass with clean residue. List parameters used; separate empty-text-parameter defect tracked as #5813. |
| [#5726](https://github.com/carlos-emr/carlos/issues/5726) | Test: ingest original, corrected and out-of-order HL7 results for one accession | Implementation and installed acceptance pending. |
| [#5727](https://github.com/carlos-emr/carlos/issues/5727) | Test: cover First Nations field save, edit, clear and community-selection round trips | Native lifecycle implemented; installed ON enabled-profile attempt fails on the real undefined focus handler and reopened First Nation flag (#5815). Later steps remain unreached. Owned fixtures are removed and the temporary properties/lookup profile is restored exactly; no pin or waiver. |
| [#5728](https://github.com/carlos-emr/carlos/issues/5728) | Test: verify exactly-once Messenger delivery to overlapping groups and direct recipients | Native ON/BC two overlapping groups plus a direct recipient produce exactly one delivery each; membership edits, a legacy duplicate and a removed-member control pass. Exclusive membership coordination and exact cleanup are required. |
| [#5729](https://github.com/carlos-emr/carlos/issues/5729) | Test: verify Messenger read and archive state is independent for each recipient | Native ON/BC read, mark-new, archive and restore transitions preserve other recipients, shared content, attachments, patient links and Sent access. |
| [#5730](https://github.com/carlos-emr/carlos/issues/5730) | Test: expand document-upload coverage across supported formats and malformed content | Implementation and installed acceptance pending. |
| [#5731](https://github.com/carlos-emr/carlos/issues/5731) | Test: preserve scanned and mixed-geometry PDFs through incoming-document edits | Implementation and installed acceptance pending. |
| [#5732](https://github.com/carlos-emr/carlos/issues/5732) | Test: verify shared contact edits and patient-specific relationship isolation | Installed ON/BC native creation, two-patient linking, shared-detail editing, independent flags, cancelled edits/removal and selective removal pass; unrelated control and cleanup verified. |
| [#5733](https://github.com/carlos-emr/carlos/issues/5733) | Test: verify complete lab-report content across PDF page breaks | Native ON/BC eight-page PDFs preserve all 60 ordered clinical rows and their comment tokens, numeric columns, flags, patient identity, footers and margins. Extracted coordinates and actual raster ink are checked; omission, duplication and blank-raster controls reject. Source and unrelated-report controls pass. |
| [#5734](https://github.com/carlos-emr/carlos/issues/5734) | Test: preserve multiple same-severity Rx interaction partners when one is removed | Installed ON/BC native current/staged moderate partners, exact JSON/marker/tooltip identities, staged removal/re-add, current-medication discontinuation/staged re-add, threshold 1/2/3/4 and patient overrides all pass. DrugRef pairs are read-only and independently validated; metformin is a noninteracting control, with no unexpected prescription writes and clean owned cleanup. |
| [#5735](https://github.com/carlos-emr/carlos/issues/5735) | Test: cover Schedule of Benefits imports that update existing codes and multiple rows | Installed ON native multi-row preview/apply, malformed-file refusal/retry, abandonment, GET/CSRF refusals, exact appended periods, historical claim preservation, duplicate upload and code-admin reopening pass. Error feedback is file-level; per-line diagnostics are absent. |
| [#5736](https://github.com/carlos-emr/carlos/issues/5736) | Test: cover BC payment amount boundaries, overpayments and supported refunds | Installed BC refusals, exact-cent partial/overpayment, receipt/list/accounting reconciliation and control-invoice preservation pass. No native refund control exists; negative amounts are refused. |
| [#5737](https://github.com/carlos-emr/carlos/issues/5737) | Test: verify appointment-slot lock expiry and recovery after an abandoned browser | Implementation and installed acceptance pending. |
| [#5738](https://github.com/carlos-emr/carlos/issues/5738) | Test: cover abandoned appointment cut/paste and stale clipboard recovery | Implementation and installed acceptance pending. |
| [#5771](https://github.com/carlos-emr/carlos/issues/5771) | Test: Tomcat shutdown leak smoke check doesn't match MariaDB Connector/J thread names | Matcher and delayed-output CLI controls pass; actual installed ON stop/start log contains zero target leak warnings. Full lifecycle repetitions remain pending. |
| [#5779](https://github.com/carlos-emr/carlos/issues/5779) | Test: #5548 inbox counts are only partly exercised on physical MariaDB | Seven cases pass on both physical ON and BC Flyway schemas with zero skips at 239cd330e1a. CI at fb0401df0c1 passes both native lanes including expanded normal/abnormal assertions; final-head evidence still required after further edits. |
| [#5793](https://github.com/carlos-emr/carlos/issues/5793) | Test: Release adds 11 expected-failure pins, including security and audit checks | All release-added pins reconciled to open ownership below; retired 335 distinguished. No release waiver or product fix claimed. |
| [#5794](https://github.com/carlos-emr/carlos/issues/5794) | Test: Front-door check for sanitized error bodies was removed while ResponseSanitizationFilter was rewritten | Installed ON REQUEST exception, /ws and observed ERROR-dispatch 500 sanitization pass; exact descriptor restoration and fixture removal verified. Both ON and BC focused installed retakes pass; final full qualification remains pending. |
| [#5795](https://github.com/carlos-emr/carlos/issues/5795) | Test: Missing browser-check prerequisites now SKIP instead of failing, including the CDS import gate | Packaged runner enforces strict release/residue checking and fails on report loss; packaged runs pending. |
| [#5796](https://github.com/carlos-emr/carlos/issues/5796) | Test: Stored partial-precision allergy start dates are no longer covered (allergy-partial-start-date removed) | Installed ON year/year-month display and both reaction-only amendments pass with clean residue audit. Both ON and BC focused installed retakes pass; final full qualification remains pending. |
| [#5797](https://github.com/carlos-emr/carlos/issues/5797) | Test: Two new tests never run in CI, and the H2 test job doesn't fail on unexpected skips | Dedicated permission and legacy-library CI lanes plus exact-result checks implemented; full CI/native acceptance pending. |

## Execution and review

Use a disposable Ubuntu 26.04 container, Java 25, physical MariaDB and the locked
Node/browser dependencies. Install the actual Debian package set for browser and
lifecycle acceptance and bind results to exact source/package identities. Raw
logs and synthetic browser artifacts remain in private execution storage.

For #5794, after provisioning a disposable systemd package container and loading
its normal suite environment, run:

```sh
CARLOS_DISPOSABLE_HOST=true python3 scripts/ci/run-installed-error-sanitization.py
```

The adapter adds temporary exact fault mappings and a numeric 500 error-page mapping to the installed `web.xml`, tests
through the normal HTTPS front door, restores its original bytes, removes the
owned class and restarts the service. It refuses existing fixture directories or
linked descriptors. Concurrent descriptor changes fail and are retained for
explicit recovery; they are never silently overwritten. These routes bypass the
CXF servlet to exercise the installed filter mappings; separate application/CXF
coverage remains necessary. No fixture is packaged. Direct execution without the
fixture is an explicit prerequisite skip and cannot pass strict qualification.

Three local review rounds are required: scope and coverage; correctness, failures and
cleanup; then final source, installed evidence, CI and review-thread reconciliation.
Round 1 (scope/failure review): found and corrected missing NOT NULL fixture
fields, native family-floor/routing drift, the fault adapter prerequisite in
packaged/lifecycle suite entry points, report-copy failures previously ignored,
and concurrent descriptor changes that must be retained rather than overwritten.
Focused checks pass; remaining rounds require actual native/installed results.

Round 2 (correctness and sensitivity): require all three new inbox case identities
on each native lane, with unrelated replacement controls under normal and
optimized Python. Corrected test discovery for those controls, added missing
JDK/parser runtime prerequisites, and made descriptor publication atomic while
preserving package ownership/mode/timestamps. The key-creation check runs
independently of the original negative GET pin. A real ON/BC MariaDB run passes
7 cases per province; the exact permission regression passes unprivileged.
CodeRabbit received the full-review request but declined it due to its account
rate limit; no successful CodeRabbit review is claimed.

Round 3 (deployment-path review, still awaiting final execution): distinguish
REQUEST exception handling from actual Tomcat ERROR dispatch. A test-only
`sendError` path now renders a tainted error page through the installed ERROR
mapping, with an independent dispatch witness and missing-mapping negative
control. The HTML servlet uses a JSP-suffixed exact mapping to pass the real
Struts exclusion contract. APT index failures are fatal in both image/setup
steps. Final installed retakes, green final-head CI and CodeRabbit remain open.

Installed ON evidence at harness head `458c284d9fd0` uses the locally built
`carlos-emr_2026.09.0~snapshot28_amd64.deb` (SHA-256
`428f5a7f525359fc8a15f80bb84bf04d402aa7d83dbc43ba382e306ff78ecf28`)
from source `239cd330e1a6`, the matching pinned DrugRef package and CLI 1.3.1,
on Ubuntu 26.04.1 with Java 25 and Playwright Chromium 149. No production bytes
changed in the intervening harness commits. Error sanitization and both stored
allergy precision amendments pass. Key creation first reports a strict
prerequisite skip; after separately staging an owned synthetic primary key,
creation/download/independent signature/reopen/duplicate checks pass. Its
prerequisite is removed with exact ownership verification, restoring the
original absence. All three checks report clean residue audits. These are
focused evidence, not a full qualification receipt or issue-closure claim.

The first full CI H2 evidence contains 34,077 cases, no failures, and 139 skips.
Four additional exact root-sensitive methods were discovered by the new gate.
They join startup refusal in a dedicated five-case lane; all five pass
unprivileged on Ubuntu 26.04 without skips. The gate rejects missing, substituted,
skipped and failed required methods, and sanitized artifacts now preserve the
separate permission and legacy reports. CodeRabbit accepted a full-review
request after its rate-limit window; review completion remains pending.

## Expected-failure ownership (#5793)

The current target has 26 pins. The issue's earlier count of 27 includes finding
335, whose fix and pin removal are already on this branch. The following are
tracking links, with no release waiver. Every remaining known failure is fatal
under `--strict-release`; later assertions behind a pinned failure remain
unreached. Tracker state was checked on 7 November 2026.

| Release-added finding | Check | Open defect ownership / disposition |
| --- | --- | --- |
| 329 | `appointment-email-option` | [#4788, explicit 329 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284); related implementation #4472 does not repair the existing checkbox by itself. |
| 330 | `admin-messages` | [#4788, explicit 330 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284), with retention/retirement #5383. |
| 331 | `admin-issue-editor` | [#4788, explicit 331 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284), with retention #5383 and method/CSRF #3059. |
| 332 | `chart-informed-consent` | [#4788, explicit 332 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284). |
| 335 | `echart-forms-list-paging` | Fixed by #5691/#5684; no current expected-failure pin. Installed qualification is still part of the full release plan. |
| 337 | `dialog-confirmations-tickler` | [#5683](https://github.com/carlos-emr/carlos/issues/5683). |
| 342 | `authz-read-admin-objects` | [#4223](https://github.com/carlos-emr/carlos/issues/4223). |
| 343 | `authz-read-print-export` | [#4223](https://github.com/carlos-emr/carlos/issues/4223); closed #5406 is historical evidence, not a live fix tracker. |
| 344 | `audit-log-locked-patient` | [#4172](https://github.com/carlos-emr/carlos/issues/4172). |
| 347 | `login-mfa-no-pin` | [#4788, explicit 347 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284). |
| 348 | `document-clinical-followup` | [#5685](https://github.com/carlos-emr/carlos/issues/5685). |

The older security/authorization pins retain these fix owners: API-key GET
creation (163) → #3059; chart note and billing authorization (198, 200), document
download authorization (202) → #4223; missing chart-read audit (141) → #4172.
The stored MCEDT credential (189) and output-encoding findings (118, 122, 123,
148) remain open findings in the browser-defect umbrella #4788 and its source
acceptance, rather than being silently treated as accepted release behavior.

Round 3 retake and external review: CodeRabbit's completed review found seven
inline items and one output-drain race outside the diff. Corrections distinguish
missing keys from malformed counts, correlate allergy reload with the save
response, cover all legacy/refusal branches, assert adapter wiring, require
specific negative-control failure reasons and document fixture behavior. The
shutdown subprocess now drains stdout/stderr through `close`; a descendant
writing a leak after parent exit independently proves the failure remains seen.
The fixture's creation date is verified from its first Git commit.

The additional #5708 check passes on the installed ON package: exactly two
recipients, repeat-save without duplicates, source-wide New → Filed, three fresh
CML uploads, unchanged earlier routes, cancelled removal, selective archival and
Administration source isolation. The existing single-recipient and negative
checks remain registered. Failed development attempts and their exact recovery
are retained privately; the final run and the allergy retake have clean residue
audits. The application offers a source-wide status, so a per-recipient status
update cannot be claimed. Four legacy compatibility cases also pass without
skips on Ubuntu 26.04 with the pinned historical libraries.

The installed `--include-warn` log audit still fails on nine signatures: missing
property defaults, Drools build/evaluation warnings, Hibernate mapping/dialect
warnings and the deliberately malformed satellite input's controlled-400 log.
They are not waived or hidden. Only exact disposable #5794 routes and the exact
fixture stack are explained, with independent controls proving ordinary routes,
malformed tokens and production servlet exceptions still fail the log audit.
Full release readiness remains blocked by these warnings and the other
uncompleted acceptance rows above.

Final focused installed retakes at harness content `1894471c154` (and the same
locked driver as `927f33ee3b2`) pass 4/4 ON and 3/3 BC, zero failures/skips, with
clean residue. Both wrappers verify exact descriptor restoration and fixture
removal; both synthetic primary keys are removed, restoring their original
absence. The ON actual installed stop/start log is scanned by the leak detector
with zero target or other-application leak warnings.

A direct root capability-drop execution exposed the purge test's username-only
assumption. It now checks actual directory readability after removing mode bits,
with permission restoration in `finally`. All five exact methods pass both
unprivileged and with root's DAC bypass capabilities removed. The root reports
pass the H2 gate alongside the 34,077-case CI report. A full qualification claim
still requires final-head CI and all outstanding register entries.

All seven initial CodeRabbit inline threads were individually answered and
resolved after their corrections and validation; the separate output-drain
issue was corrected too. CodeRabbit's second full review completed on
2026-11-07 at 22:52 UTC against fb0401df0c1 and raised twelve comments.
The corrections and their completed validation are recorded below; review
threads are reconciled after publication. No third external review completion
or final review clearance is claimed.

Additional independent lifecycle coverage (#5724, #5732, #5736) passes through
the same installed package front doors. The ON batch passes 2/2 and BC passes
3/3, with zero failures/skips and clean residue. OAuth tokens are obtained using
real consent; the finite token is tested within its exact expiry second and
again immediately afterward. A separately authorized active client and an
ordinary browser session remain usable across expiry and native deletion.
Captured client IDs keep cleanup effective after the application deletes a
client, including its owned request/access tokens and nonces.

The contact lifecycle creates the directory record natively and keeps patient
A/B in separate real sessions to avoid the application's reused named windows.
Both link rows are snapshotted independently; shared edits preserve both,
patient-specific flag edits preserve B, abandoned directory/relationship/removal
edits preserve storage, and removing A preserves B and the directory. An
unrelated contact/patient control remains byte-for-byte unchanged. Native
self-closing controls are awaited with their actual close and opener reload.

The BC amount lifecycle retains the original positive and method checks, adds
a tokenless POST refusal, and verifies zero, negative, malformed, over-width and
sub-cent refusals leave no financial rows. A corrected 10.01 partial payment
and 20.00 overpayment produce exact 30.01 received and -7.01 credit on a 23.00
invoice, with paid-private status and the selected payment method. The reopened
receipt, header-labelled Invoice List columns and accounting CSV agree. The
refund checkbox is absent on this target, so no refund UI flow is claimed.
The original partial/exact-balance check separately passes after the extension.
All amounts are checked with decimal SQL or integer cents, not floating-point
comparison. Development failures remain recorded; none was converted to a pin
or a passing incomplete result.


The next review correction batch requires the exact fully qualified inbox class
and empty container, preserves independent skip routes, parses each report once,
and applies the same report-directory/identity checks to historical oracles.
The package fault adapter now has 1,860 seconds for its two bounded restarts,
compilation, counters, browser suite and restoration. Partial OAuth/contact
setup is reconciled by exact ownership, and cleanup continues across independent
fixtures while aggregating failures. A changed owner still prevents deletion.
Forwarding provider IDs remain registered as intents before INSERT; a missing
row is harmless during cleanup, and other owned providers are still attempted.

Ubuntu 26.04 / Node 22.22.1 now passes all 7,379 cases with zero failures,
skips, cancellations or TODOs. The release Node inventory is regenerated from
that complete execution, including all 518 test files and the leak CLI case.
The initial Ubuntu attempt lacked jq/Poppler and failed; its report is retained,
the missing distribution packages were installed, and the complete run was
repeated. All 349 Python helper cases pass in the Ubuntu environment. The new
cleanup controls cover a lost INSERT-ID response, changed ownership, independent
cleanup and an uncreated secondary patient.

The ON review retake passes OAuth, shared contacts and native key generation
(3/3, no skip/residue); the synthetic primary key is removed afterward.
The refactored default key workflow reaches its retained finding-163 GET-refusal
pin after its preceding steps pass; this remains a known failure, not a release
pass. Both installed provinces pass the four-tab Report by Template workflow.
The normal Back beacons are observed without interception; only the exact
unloading ping's ERR_ABORTED may be consumed. An empty result offers no export,
and does not replace the populated instances' table or downloaded data.
The separate lone-empty-text-parameter upload defect is tracked in
[#5813](https://github.com/carlos-emr/carlos/issues/5813), with the failed native
attempts retained. No repair of that product defect is claimed here.

All thirteen CI workflows pass at fb0401df0c1, including the Rourke font/cache
matrix. The downloaded actual CI reports also pass the revised H2/permission
and four-case historical gate: 34,077 H2 cases, zero failures, 139 exactly routed
skips, five actual passing permission cases and four actual passing historical
comparisons. The native summary records 3,079 generated-schema, 230 ON Flyway
and 64 BC Flyway cases, all passing without skips, including all seven inbox
cases in each Flyway lane. Further commits still require their own green CI.

The final ON forwarding/SOB retake also passes 2/2 with clean residue. The
forwarding source-wide status limitation remains explicit. Malformed mixed SOB
files offer no partial changes, and a corrected native retry preserves both
previous fee periods and an existing claim; same-schedule reupload creates no
new effective period. Fee cleanup continues independently across code owners.

The target advanced to b6fa82558f7 and was merged at d67651993b1. Its CI H2
execution has 34,123 cases, no failures and 143 skips. Four newly introduced
physical-type cases lacked H2 routing declarations; the strict gate correctly
refused them. They are now individually routed to BC Flyway, whose actual CI
summary confirms all four pass. The native gate additionally requires their
exact class/method identities and empty containers; four same-count replacement
controls prove unrelated cases cannot substitute. The revised gate accepts the
actual H2/permission artifacts, and all 350 Python helper tests pass on Ubuntu.
This correction still requires green CI on its own published commit.

A fresh d67651993b1 WAR and matching DrugRef were packaged as the local
snapshot29 Debian set and installed in both Ubuntu 26.04 provinces. The version
bump is confined to the packaging checkout. Application package SHA-256 is
ec40d8d8e803832de98df86735ae3c22966b99e57b208ec76e17f80ac6839af5;
DrugRef package SHA-256 is
51351fdff4e27b1fe21066d4489c40024e1f17a0205d52582a4fe24c199558d5.
Both installed package checks and dpkg verification pass. Nineteen focused
browser retakes pass (10 ON, 9 BC), with no skips or residue, including the
new Messenger and eight-page lab-PDF workflows. These focused runs do not
replace the outstanding complete qualification and acceptance rows.

The new measurement oracle independently reads the actual printed date/value
ticks and rendered red series, rather than reusing the application's dataset or
range formulas. All five deliberately non-collinear observations and the four
remaining after native deletion match within three native PNG pixels in both
provinces. Missing, swapped, shifted and foreign-observation controls reject.
The history supplies `in kg`; the graph's labels are Weight, Days and Wt, without
a separate printed kg unit. Native empty and single-output transitions are
observed. A single sample has no point glyph and repeats rounded value ticks;
that product limitation is reported as [#5814](https://github.com/carlos-emr/carlos/issues/5814),
not claimed repaired or waived here.

Chromium CDP corrupts the main-frame PNG bytes when the server declares a UTF-8
charset, despite displaying the image correctly. The capture helper intercepts
only the exact native UI graph request for the owned patient/type, fetches that
one request losslessly with redirects disabled, and fulfills it with identical
bytes/headers. It does not replay a guessed URL or re-encode the image. The
original PNG/content-type assertion is retained and passes on installed ON.
Nine controls cover exact bytes, ambiguous identity, failures, missing/duplicate
navigation and handler removal. The Docker validation image includes pinned
pngjs and distribution Tesseract/English data.

The complete Ubuntu 26.04 / Node 22.22.1 execution for this batch passes all
7,398 cases in 520 files, with no failures, skips, cancellations, TODOs or test
source changes. Its reviewed Node inventory is regenerated from that report.

The additional lookup lifecycle passes four native positive phases on both
installed provinces, then independently reports both the inactive-provider-option
defect [#5816](https://github.com/carlos-emr/carlos/issues/5816) and existing GET
mutation finding 58/#3059 (HTTP 200 with changed stored order). Both failures
remain fatal, without new pins. Cleanup separately adds/removes an owned cache
sentinel after SQL teardown and reopens every selected consumer to prove no
marker remains; this does not count as successful normal removal. Full unrelated
lookup rows and configuration are restored. The ON `phu` list exists but its
consumer is hidden by `phu.hide=true`; BC has no `phu` list. These limitations
and all native UI operations are in `lookup-list-consumer-inventory.md`.

The enabled First Nations ON profile uses two owned synthetic communities and
the actual installed module. Its first native round trip reaches correct stored
values, then fails on the reopened First Nation flag; the strict recorder also
captures the undefined `toggleFirstNationFields` focus handler. These product
failures are tracked in [#5815](https://github.com/carlos-emr/carlos/issues/5815).
Later lifecycle steps remain unreached. All owned demographic/control/support
rows are removed. The app is stopped, the exact original properties and lookup
rows are verified/restored, and the original profile is started again, clearing
temporary lookup caches. No successful full-profile qualification is claimed.

CodeRabbit's third requested full review completed on 2026-11-08 at 00:48 UTC
against 9b8de7c95fe. Its one inline finding correctly identified omitted shared
fixture declarations. The OAuth clients, Messenger groups/members/providers,
BC payment/forwarding providers, forwarding rules, report templates, benefit
codes and key-manager entries now declare their clinic-wide mutations and
exclusivity wording. All 68 manifest checks pass after the correction. The
review's docstring advisory is distinct from required CI and is not claimed as
a passed qualification check. Final scope, new-code review and CI reconciliation
remain necessary as work continues.

The decision-support final retake reaches 41 rules over A/B/A, A after native
diagnosis resolution, then B again (205 rule/patient observations). The actual
six-row chart expander is used. Forty fatal mismatches are confined to sex
all/notall on four surfaces per visit, tracked as #5817. All other selected
operators, source transitions, provider scope and clinical snapshots agree;
owned guideline, patient and provider counts return to zero. Quoted DSL note
literals and exact list-row identity were corrected during development; those
earlier failed attempts are retained. `decision-support-operator-inventory.md`
records implementation limits and the missing notes/billing actual-text reader.

The multi-partner Rx retakes pass in both installed provinces. Ciprofloxacin's
two moderate partners (current phenytoin and staged warfarin) remain distinct
through selective removal and re-addition. Native discontinuation removes only
the current source; a staged re-add restores both. Exact authoritative effect,
evidence/comment and partner identities survive all applicable thresholds and
patient/provider overrides. Only an owned clinician's preferences change, and
the DrugRef catalog is not modified. Source fixtures and cleanup are verified.
